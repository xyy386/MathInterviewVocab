import Foundation

struct UpdateNetworkChunk: Sendable {
    let data: Data
    let consumed: @Sendable () -> Void
}
struct UpdateNetworkResponse: Sendable {
    let status: Int
    let length: Int64?
    let range: String?
    let chunks: AsyncThrowingStream<UpdateNetworkChunk, any Error>
    let cancel: @Sendable () -> Void
}
typealias UpdateNetworkOpen = @Sendable (URL, Int64) async throws -> UpdateNetworkResponse

/** URLSession 的回调状态由锁保护；数据消费完才恢复任务，避免慢磁盘导致无界缓冲。 */
final class UpdateNetwork: NSObject, URLSessionDataDelegate, @unchecked Sendable {
    private let lock = NSLock()
    private var task: URLSessionDataTask?
    private var session: URLSession?
    private var reply: CheckedContinuation<UpdateNetworkResponse, any Error>?
    private var stream: AsyncThrowingStream<UpdateNetworkChunk, any Error>.Continuation?
    private var firstByte: DispatchWorkItem?
    private var idle: DispatchWorkItem?
    private var finished = false
    private var offset: Int64 = 0
    private let allowFixtureOrigin: URL?

    init(fixtureOrigin: URL? = nil) { self.allowFixtureOrigin = fixtureOrigin }

    static func open(_ url: URL, offset: Int64 = 0) async throws -> UpdateNetworkResponse {
        try await UpdateNetwork().start(url, offset: offset)
    }
    private func checked(_ url: URL, redirected: Bool) throws -> URL {
        // 仅明确注入的 DEBUG 夹具可使用回环源；正式请求仍执行严格 HTTPS 白名单。
        if let origin = allowFixtureOrigin, url.scheme == "http", url.host == origin.host, url.port == origin.port,
           url.user == nil, url.password == nil, url.query == nil, url.fragment == nil { return url }
        return try DesktopUpdatePolicy.checkedURL(url, redirected: redirected)
    }
    func start(_ url: URL, offset: Int64 = 0) async throws -> UpdateNetworkResponse {
        _ = try checked(url, redirected: false)
        return try await withTaskCancellationHandler {
            try await withCheckedThrowingContinuation { continuation in
                lock.lock()
                if finished { lock.unlock(); continuation.resume(throwing: CancellationError()); return }
                reply = continuation; self.offset = offset
                let configuration = URLSessionConfiguration.ephemeral
                configuration.httpCookieStorage = nil; configuration.httpShouldSetCookies = false; configuration.urlCache = nil
                configuration.timeoutIntervalForRequest = 12; configuration.timeoutIntervalForResource = 15 * 60
                let session = URLSession(configuration: configuration, delegate: self, delegateQueue: nil)
                var request = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData)
                request.httpShouldHandleCookies = false
                request.setValue("identity", forHTTPHeaderField: "Accept-Encoding")
                request.setValue("no-cache", forHTTPHeaderField: "Cache-Control")
                request.setValue("MathInterviewVocab-Updater", forHTTPHeaderField: "User-Agent")
                if offset > 0 { request.setValue("bytes=\(offset)-", forHTTPHeaderField: "Range") }
                let task = session.dataTask(with: request)
                self.session = session; self.task = task
                let timer = DispatchWorkItem { [weak self] in self?.fail(UpdateTransferError.failure("更新线路首包超时。")) }
                firstByte = timer
                lock.unlock()
                DispatchQueue.global(qos: .utility).asyncAfter(deadline: .now() + 6, execute: timer)
                task.resume()
            }
        } onCancel: { self.fail(CancellationError()) }
    }
    private func fail(_ error: any Error) {
        lock.lock()
        guard !finished else { lock.unlock(); return }
        finished = true
        let reply = self.reply; self.reply = nil
        let stream = self.stream; self.stream = nil
        let task = self.task; self.task = nil
        let session = self.session; self.session = nil
        firstByte?.cancel(); idle?.cancel()
        lock.unlock()
        reply?.resume(throwing: error); stream?.finish(throwing: error)
        task?.cancel(); session?.invalidateAndCancel()
    }
    private func resetIdle() {
        lock.lock()
        idle?.cancel()
        let timer = DispatchWorkItem { [weak self] in self?.fail(UpdateTransferError.failure("更新线路读取超时。")) }
        idle = timer; lock.unlock()
        DispatchQueue.global(qos: .utility).asyncAfter(deadline: .now() + 12, execute: timer)
    }
    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse,
                    newRequest request: URLRequest, completionHandler: @escaping @Sendable (URLRequest?) -> Void) {
        guard task.countOfBytesReceived == 0, let url = request.url,
              (task.currentRequest?.value(forHTTPHeaderField: "X-Math-Redirects").flatMap(Int.init) ?? 0) < 5 else {
            completionHandler(nil); fail(UpdateTransferError.failure("更新重定向次数过多。")); return
        }
        do {
            _ = try checked(url, redirected: true)
            var next = request
            let count = (task.currentRequest?.value(forHTTPHeaderField: "X-Math-Redirects").flatMap(Int.init) ?? 0) + 1
            next.setValue(String(count), forHTTPHeaderField: "X-Math-Redirects")
            if offset > 0 { next.setValue("bytes=\(offset)-", forHTTPHeaderField: "Range") }
            next.httpShouldHandleCookies = false
            completionHandler(next)
        } catch { completionHandler(nil); fail(error) }
    }
    func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive response: URLResponse,
                    completionHandler: @escaping @Sendable (URLSession.ResponseDisposition) -> Void) {
        guard let http = response as? HTTPURLResponse, [200, 206, 416].contains(http.statusCode),
              http.value(forHTTPHeaderField: "Content-Encoding").map({ $0.lowercased() == "identity" }) ?? true else {
            completionHandler(.cancel); fail(UpdateTransferError.failure("更新线路返回无效状态或压缩响应。")); return
        }
        let sequence = AsyncThrowingStream<UpdateNetworkChunk, any Error>.makeStream(bufferingPolicy: .bufferingOldest(8))
        lock.lock()
        firstByte?.cancel()
        let reply = self.reply; self.reply = nil; stream = sequence.continuation
        lock.unlock()
        let result = UpdateNetworkResponse(status: http.statusCode,
            length: response.expectedContentLength >= 0 ? response.expectedContentLength : nil,
            range: http.value(forHTTPHeaderField: "Content-Range"), chunks: sequence.stream, cancel: { [self] in fail(CancellationError()) })
        reply?.resume(returning: result)
        resetIdle(); completionHandler(.allow)
    }
    func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive data: Data) {
        dataTask.suspend()
        lock.lock(); let stream = finished ? nil : self.stream; lock.unlock()
        guard let stream else { dataTask.cancel(); return }
        let result = stream.yield(UpdateNetworkChunk(data: data, consumed: { dataTask.resume() }))
        if case .dropped = result { fail(UpdateTransferError.failure("更新缓冲超出上限，已保留断点。")) }
        resetIdle()
    }
    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: (any Error)?) {
        if let error { fail(error); return }
        lock.lock()
        finished = true; firstByte?.cancel(); idle?.cancel()
        let stream = self.stream; self.stream = nil
        let reply = self.reply; self.reply = nil; self.task = nil; self.session = nil
        lock.unlock()
        reply?.resume(throwing: UpdateTransferError.failure("更新响应没有有效头部。")); stream?.finish()
        session.finishTasksAndInvalidate()
    }
}

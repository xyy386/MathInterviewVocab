import Foundation
import Network

private struct RelayRequest: Sendable { let head: Bool; let resource: String }

/** 只监听回环的单会话只读交接层，不是通用文件服务器或网络代理。 */
actor UpdateRelay {
    private var listener: NWListener?
    private var token = UUID().uuidString.lowercased()
    private var port: UInt16?
    private var connections: [UUID: NWConnection] = [:]
    private var tasks: [UUID: Task<Void, Never>] = [:]
    private var starting: CheckedContinuation<URL, any Error>?
    private var feed = Data()
    private var archive: UpdateIndexAsset?
    private var provider: (@Sendable (@escaping @Sendable (Data) async throws -> Void) async throws -> Void)?
    private let queue = DispatchQueue(label: "MathInterviewVocab.UpdateRelay", qos: .utility)

    func start(feed: Data, archive: UpdateIndexAsset,
               provider: @escaping @Sendable (@escaping @Sendable (Data) async throws -> Void) async throws -> Void) async throws -> URL {
        stop()
        self.feed = feed; self.archive = archive; self.provider = provider
        token = UUID().uuidString.lowercased()
        let session = token
        let parameters = NWParameters.tcp
        parameters.requiredLocalEndpoint = .hostPort(host: .ipv4(.loopback), port: .any)
        let listener = try NWListener(using: parameters)
        self.listener = listener
        return try await withCheckedThrowingContinuation { continuation in
            starting = continuation
            listener.stateUpdateHandler = { [weak self, weak listener] state in
                Task { await self?.listenerState(state, port: listener?.port?.rawValue, session: session) }
            }
            listener.newConnectionHandler = { [weak self] connection in Task { await self?.accept(connection, session: session) } }
            listener.start(queue: queue)
        }
    }
    private func listenerState(_ state: NWListener.State, port: UInt16?, session: String) {
        guard session == token else { return }
        switch state {
        case .ready:
            guard let port else { starting?.resume(throwing: UpdateTransferError.failure("本机交接端口不可用。")); starting = nil; return }
            self.port = port
            starting?.resume(returning: URL(string: "http://localhost:\(port)/\(token)/appcast.xml")!)
            starting = nil
        case .failed(let error): starting?.resume(throwing: error); starting = nil
        case .cancelled: starting?.resume(throwing: CancellationError()); starting = nil
        default: break
        }
    }
    func archiveURL() -> URL? {
        guard let port else { return nil }
        return URL(string: "http://localhost:\(port)/\(token)/archive.zip")
    }
    func stop() {
        starting?.resume(throwing: CancellationError()); starting = nil
        listener?.cancel(); listener = nil; port = nil
        tasks.values.forEach { $0.cancel() }; tasks.removeAll()
        connections.values.forEach { $0.cancel() }; connections.removeAll()
        provider = nil; feed = Data(); archive = nil
    }
    private func accept(_ connection: NWConnection, session: String) {
        guard session == token else { connection.cancel(); return }
        guard connections.count < 4 else { connection.cancel(); return }
        let id = UUID(); connections[id] = connection
        connection.start(queue: queue)
        connection.stateUpdateHandler = { [weak self] state in
            if case .failed = state { Task { await self?.cancel(id) } }
            if case .cancelled = state { Task { await self?.cancel(id) } }
        }
        tasks[id] = Task { [weak self] in
            guard let self else { connection.cancel(); return }
            await self.serve(connection, id: id)
        }
    }
    private func cancel(_ id: UUID) {
        tasks.removeValue(forKey: id)?.cancel(); connections.removeValue(forKey: id)?.cancel()
    }
    private nonisolated func receive(_ connection: NWConnection) async throws -> Data {
        try await withTaskCancellationHandler {
            try await withCheckedThrowingContinuation { continuation in
                connection.receive(minimumIncompleteLength: 1, maximumLength: 8192) { data, _, complete, error in
                    if let error { continuation.resume(throwing: error) }
                    else if let data, !data.isEmpty { continuation.resume(returning: data) }
                    else { continuation.resume(throwing: complete ? CancellationError() : UpdateTransferError.failure("交接请求为空。")) }
                }
            }
        } onCancel: { connection.cancel() }
    }
    private nonisolated func send(_ data: Data, over connection: NWConnection) async throws {
        try Task.checkCancellation()
        try await withTaskCancellationHandler {
            try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, any Error>) in
                connection.send(content: data, completion: .contentProcessed { error in
                    if let error { continuation.resume(throwing: error) } else { continuation.resume() }
                })
            }
        } onCancel: { connection.cancel() }
    }
    private func parse(_ data: Data) throws -> RelayRequest {
        guard let text = String(data: data, encoding: .utf8), let port else { throw UpdateTransferError.failure("交接请求无效。") }
        let lines = text.components(separatedBy: "\r\n")
        let parts = lines.first?.split(separator: " ").map(String.init) ?? []
        guard parts.count == 3, ["GET", "HEAD"].contains(parts[0]), parts[2] == "HTTP/1.1",
              lines.filter({ $0.lowercased().hasPrefix("host:") }).count == 1,
              lines.contains(where: { $0.lowercased() == "host: localhost:\(port)" }),
              !lines.contains(where: { $0.lowercased().hasPrefix("origin:") }),
              ["/\(token)/appcast.xml", "/\(token)/archive.zip"].contains(parts[1]) else {
            throw UpdateTransferError.failure("拒绝非当前会话的交接请求。")
        }
        return RelayRequest(head: parts[0] == "HEAD", resource: parts[1].hasSuffix("appcast.xml") ? "appcast" : "archive")
    }
    private func serve(_ connection: NWConnection, id: UUID) async {
        defer { cancel(id) }
        do {
            let header = try await updateDeadline(6) { [self] in
                var data = Data()
                while data.range(of: Data("\r\n\r\n".utf8)) == nil {
                    data.append(try await receive(connection))
                    guard data.count <= 8192 else { throw UpdateTransferError.failure("交接请求过大。") }
                }
                return data
            }
            let request = try parse(header)
            guard let archive, let provider else { throw CancellationError() }
            let count = request.resource == "appcast" ? Int64(feed.count) : archive.bytes
            let type = request.resource == "appcast" ? "application/xml" : "application/octet-stream"
            let disposition = request.resource == "archive" ? "Content-Disposition: attachment; filename=\"\(archive.name)\"\r\n" : ""
            try await send(Data("HTTP/1.1 200 OK\r\nContent-Length: \(count)\r\nContent-Type: \(type)\r\n\(disposition)Cache-Control: no-store\r\nConnection: close\r\n\r\n".utf8), over: connection)
            if !request.head {
                if request.resource == "appcast" { try await send(feed, over: connection) }
                else { try await provider { [self] data in try await send(data, over: connection) } }
            }
            try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, any Error>) in
                connection.send(content: nil, contentContext: .finalMessage, isComplete: true, completion: .contentProcessed { error in
                    if let error { continuation.resume(throwing: error) } else { continuation.resume() }
                })
            }
        } catch { connection.cancel() }
    }
}

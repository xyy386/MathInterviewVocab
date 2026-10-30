import AppKit
import CryptoKit
import Darwin
import Foundation
import Observation

struct AppUpdateTransport: Sendable {
    var data: @Sendable (URL, Int64) async throws -> Data
    var download: @Sendable (URL, Int64) async throws -> URL

    static let live = Self(data: { url, limit in
        let networkGuard = UpdateNetworkGuard()
        let session = makeSession(delegate: networkGuard)
        defer { session.invalidateAndCancel() }
        let (bytes, response) = try await session.bytes(for: request(url), delegate: networkGuard)
        try validate(response, maximumBytes: limit)
        var data = Data()
        for try await byte in bytes {
            try Task.checkCancellation()
            guard data.count < limit else { throw AppUpdateError.tooLarge }
            data.append(byte)
        }
        return data
    }, download: { url, limit in
        let networkGuard = UpdateNetworkGuard()
        let session = makeSession(delegate: networkGuard)
        defer { session.invalidateAndCancel() }
        let (bytes, response) = try await session.bytes(for: request(url), delegate: networkGuard)
        try validate(response, maximumBytes: limit)
        return try await writeBoundedArchive(bytes, maximumBytes: limit)
    })

    static func writeBoundedArchive<Bytes: AsyncSequence>(
        _ bytes: Bytes, maximumBytes: Int64,
        temporaryDirectory: URL = FileManager.default.temporaryDirectory
    ) async throws -> URL where Bytes.Element == UInt8 {
        guard maximumBytes > 0, maximumBytes <= AppUpdatePolicy.maximumArchiveBytes,
              temporaryDirectory.isFileURL else { throw AppUpdateError.tooLarge }
        let file = temporaryDirectory.appendingPathComponent("MathVocabUpdate-\(UUID().uuidString).partial")
        try Data().write(to: file, options: .withoutOverwriting)
        var complete = false
        defer { if !complete { try? FileManager.default.removeItem(at: file) } }
        let handle = try FileHandle(forWritingTo: file)
        defer { try? handle.close() }
        var buffer = Data()
        buffer.reserveCapacity(65_536)
        var total: Int64 = 0
        // 不依赖下载完成后的检查或进度回调，在读入与写盘过程中强制限制大小。
        for try await byte in bytes {
            guard total < maximumBytes else { throw AppUpdateError.tooLarge }
            total += 1
            buffer.append(byte)
            if buffer.count == 65_536 {
                try Task.checkCancellation()
                try handle.write(contentsOf: buffer)
                buffer.removeAll(keepingCapacity: true)
            }
        }
        try Task.checkCancellation()
        guard total > 0 else { throw AppUpdateError.invalidArchive }
        if !buffer.isEmpty { try handle.write(contentsOf: buffer) }
        try handle.close()
        complete = true
        return file
    }

    private static func makeSession(delegate: UpdateNetworkGuard) -> URLSession {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.httpShouldSetCookies = false
        configuration.httpCookieStorage = nil
        configuration.urlCredentialStorage = nil
        configuration.urlCache = nil
        configuration.requestCachePolicy = .reloadIgnoringLocalCacheData
        configuration.timeoutIntervalForRequest = 30
        configuration.timeoutIntervalForResource = 600
        configuration.waitsForConnectivity = false
        return URLSession(configuration: configuration, delegate: delegate, delegateQueue: nil)
    }

    private static func request(_ url: URL) throws -> URLRequest {
        guard AppUpdatePolicy.isAllowedNetworkURL(url) else { throw AppUpdateError.invalidURL }
        var request = URLRequest(url: url)
        request.setValue("MathInterviewVocab-Update", forHTTPHeaderField: "User-Agent")
        request.setValue("application/vnd.github+json", forHTTPHeaderField: "Accept")
        request.setValue("2026-03-10", forHTTPHeaderField: "X-GitHub-Api-Version")
        return request
    }

    static func validate(_ response: URLResponse, maximumBytes: Int64) throws {
        guard let response = response as? HTTPURLResponse,
              let url = response.url, AppUpdatePolicy.isAllowedNetworkURL(url) else {
            throw AppUpdateError.invalidURL
        }
        guard response.statusCode == 200 else { throw AppUpdateError.status(response.statusCode) }
        guard response.expectedContentLength <= maximumBytes else { throw AppUpdateError.tooLarge }
    }
}

private final class UpdateNetworkGuard: NSObject, URLSessionTaskDelegate, Sendable {
    func urlSession(
        _ session: URLSession, task: URLSessionTask,
        willPerformHTTPRedirection response: HTTPURLResponse, newRequest request: URLRequest,
        completionHandler: @escaping @Sendable (URLRequest?) -> Void
    ) {
        guard let url = request.url, AppUpdatePolicy.isAllowedNetworkURL(url) else {
            completionHandler(nil)
            return
        }
        completionHandler(request)
    }

    func urlSession(
        _ session: URLSession, task: URLSessionTask, didReceive challenge: URLAuthenticationChallenge,
        completionHandler: @escaping @Sendable (URLSession.AuthChallengeDisposition, URLCredential?) -> Void
    ) {
        // 只使用系统 TLS 验证，不查询凭据或响应网站的账号认证请求。
        completionHandler(
            challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust
                ? .performDefaultHandling : .cancelAuthenticationChallenge,
            nil
        )
    }
}

enum AppUpdatePhase: Equatable {
    case idle
    case checking
    case current
    case available
    case downloading
    case ready
    case failed(String)
}

@MainActor
@Observable
final class UpdateService {
    let currentVersion: String
    private(set) var phase = AppUpdatePhase.idle
    private(set) var release: AppUpdateRelease?
    private(set) var downloadedURL: URL?

    @ObservationIgnored private let transport: AppUpdateTransport
    @ObservationIgnored private let downloadsDirectory: URL?
    @ObservationIgnored private var operation: Task<Void, Never>?
    @ObservationIgnored private var operationID = UUID()

    init(
        currentVersion: String = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "未知",
        transport: AppUpdateTransport = .live,
        downloadsDirectory: URL? = FileManager.default.urls(for: .downloadsDirectory, in: .userDomainMask).first
    ) {
        self.currentVersion = currentVersion
        self.transport = transport
        self.downloadsDirectory = downloadsDirectory
    }

    var isBusy: Bool { phase == .checking || phase == .downloading }

    func checkForUpdates() {
        guard !isBusy else { return }
        let id = UUID()
        operationID = id
        phase = .checking
        release = nil
        downloadedURL = nil
        operation = Task {
            do {
                let data = try await transport.data(AppUpdatePolicy.latestURL, AppUpdatePolicy.maximumMetadataBytes)
                try Task.checkCancellation()
                guard operationID == id else { return }
                switch try AppUpdatePolicy.check(data, currentVersion: currentVersion) {
                case .current: phase = .current
                case .available(let available):
                    release = available
                    phase = .available
                }
            } catch {
                guard operationID == id else { return }
                phase = .failed(Self.message(for: error))
            }
        }
    }

    func downloadUpdate() {
        guard !isBusy, let release else { return }
        let id = UUID()
        operationID = id
        phase = .downloading
        downloadedURL = nil
        operation = Task {
            var temporaryURL: URL?
            defer { if let temporaryURL { try? FileManager.default.removeItem(at: temporaryURL) } }
            do {
                let checksums = try await transport.data(release.checksums.url, AppUpdatePolicy.maximumChecksumBytes)
                guard checksums.count == release.checksums.bytes else { throw AppUpdateError.invalidChecksum }
                if let declared = release.checksums.digest, Self.digest(checksums) != declared {
                    throw AppUpdateError.invalidChecksum
                }
                let expected = try AppUpdatePolicy.checksum(in: checksums, for: release.archive.name)
                guard release.archive.digest == nil || release.archive.digest == expected else {
                    throw AppUpdateError.invalidChecksum
                }
                try Task.checkCancellation()
                let file = try await transport.download(release.archive.url, release.archive.bytes)
                temporaryURL = file
                try await Self.verifyArchive(file, expectedBytes: release.archive.bytes, expectedDigest: expected)
                try Task.checkCancellation()
                guard operationID == id else { return }
                downloadedURL = try saveVerifiedArchive(file, release: release)
                phase = .ready
            } catch {
                guard operationID == id else { return }
                phase = .failed(Self.message(for: error))
            }
        }
    }

    func cancelDownload() {
        guard phase == .downloading else { return }
        operationID = UUID()
        operation?.cancel()
        phase = release == nil ? .idle : .available
    }

    func showDownloadedArchive() {
        guard phase == .ready, let downloadedURL,
              FileManager.default.fileExists(atPath: downloadedURL.path) else { return }
        NSWorkspace.shared.activateFileViewerSelecting([downloadedURL])
    }

    func showReleaseNotes() {
        guard let release else { return }
        NSWorkspace.shared.open(release.pageURL)
    }

    func waitForOperation() async { await operation?.value }

    static func verifyArchive(_ url: URL, expectedBytes: Int64, expectedDigest: String) async throws {
        guard url.isFileURL, AppUpdatePolicy.isDigest(expectedDigest),
              expectedBytes > 0, expectedBytes <= AppUpdatePolicy.maximumArchiveBytes,
              let attributes = try? FileManager.default.attributesOfItem(atPath: url.path),
              attributes[.type] as? FileAttributeType == .typeRegular,
              (attributes[.size] as? NSNumber)?.int64Value == expectedBytes else {
            throw AppUpdateError.invalidArchive
        }
        let handle = try FileHandle(forReadingFrom: url)
        defer { try? handle.close() }
        var hasher = SHA256()
        var total: Int64 = 0
        while let data = try handle.read(upToCount: 1_048_576), !data.isEmpty {
            try Task.checkCancellation()
            total += Int64(data.count)
            guard total <= expectedBytes else { throw AppUpdateError.invalidArchive }
            hasher.update(data: data)
            // 分块校验期间让出界面执行权，取消无需等到整个文件处理完成。
            await Task.yield()
        }
        guard total == expectedBytes,
              hasher.finalize().map({ String(format: "%02x", $0) }).joined() == expectedDigest else {
            throw AppUpdateError.invalidArchive
        }
    }

    private func saveVerifiedArchive(_ file: URL, release: AppUpdateRelease) throws -> URL {
        guard let downloadsDirectory, downloadsDirectory.isFileURL else { throw AppUpdateError.downloadsUnavailable }
        let parent = downloadsDirectory.appendingPathComponent("MathInterviewVocab Updates", isDirectory: true)
        let directory = parent.appendingPathComponent("v\(release.version)-\(UUID().uuidString)", isDirectory: true)
        do {
            try FileManager.default.createDirectory(at: parent, withIntermediateDirectories: true)
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: false)
        } catch { throw AppUpdateError.downloadsUnavailable }
        let destination = directory.appendingPathComponent(release.archive.name)
        do {
            try FileManager.default.moveItem(at: file, to: destination)
            try DownloadQuarantine.mark(destination)
        }
        catch {
            try? FileManager.default.removeItem(at: directory)
            if let updateError = error as? AppUpdateError { throw updateError }
            throw AppUpdateError.downloadsUnavailable
        }
        return destination
    }

    private static func digest(_ data: Data) -> String {
        SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
    }

    private static func message(for error: Error) -> String {
        if let error = error as? AppUpdateError { return error.localizedDescription }
        if (error as? URLError)?.code == .timedOut { return "连接 GitHub 超时，请检查网络后重试。" }
        if error is CancellationError { return "下载已取消，可重新检查或下载。" }
        return "无法完成更新，请检查网络、磁盘空间和下载文件夹权限后重试。"
    }
}

enum DownloadQuarantine {
    static let attributeName = "com.apple.quarantine"

    static func mark(_ file: URL) throws {
        guard file.isFileURL,
              let attributes = try? FileManager.default.attributesOfItem(atPath: file.path),
              attributes[.type] as? FileAttributeType == .typeRegular else {
            throw AppUpdateError.quarantineUnavailable
        }
        let timestamp = String(Int(Date().timeIntervalSince1970), radix: 16)
        let marker = Data("0081;\(timestamp);MathInterviewVocab;\(UUID().uuidString)".utf8)
        // 保留下载来源隔离提示，不代表 Developer ID 签名或公证；已有标记绝不覆盖。
        let result = file.withUnsafeFileSystemRepresentation { path -> Int32 in
            guard let path else { return -1 }
            return marker.withUnsafeBytes { bytes in
                Darwin.setxattr(path, attributeName, bytes.baseAddress, bytes.count, 0, XATTR_CREATE | XATTR_NOFOLLOW)
            }
        }
        guard result == 0 else { throw AppUpdateError.quarantineUnavailable }
    }
}

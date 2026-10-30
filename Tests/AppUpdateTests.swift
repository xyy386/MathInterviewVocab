import CryptoKit
import Darwin
import Foundation
import Testing
@testable import MathInterviewVocab

struct AppUpdateTests {
    @Test(arguments: ["1.2", "1.2.0.1", "01.2.0", "1.02.0", "1.2.00", "1.2.0-beta", "v1.2.0", " 1.2.0", "-1.2.0", "١.2.0", "9999999999999999999999999.0.0"])
    func malformedVersionsAreRejected(_ text: String) {
        #expect(AppVersion(text) == nil)
    }

    @Test func versionsCompareNumericallyWithoutDowngrading() throws {
        let current = try #require(AppVersion("1.2.0"))
        #expect(current < AppVersion("1.10.0")!)
        #expect(current < AppVersion("2.0.0")!)
        #expect(AppVersion("1.1.99")! < current)
        #expect(current.description == "1.2.0")
        let fixture = UpdateFixture()
        #expect(try AppUpdatePolicy.check(fixture.metadata(), currentVersion: "1.3.0") == .current)
        #expect(try AppUpdatePolicy.check(fixture.metadata(version: "1.2.0", assets: []), currentVersion: "1.2.0") == .current)
        #expect(try AppUpdatePolicy.check(fixture.metadata(version: "1.1.2", assets: []), currentVersion: "1.2.0") == .current)
    }

    @Test func newerReleaseBindsOfficialPagePlatformAndChecksumAssets() throws {
        let fixture = UpdateFixture()
        guard case .available(let release) = try AppUpdatePolicy.check(fixture.metadata(), currentVersion: "1.2.0") else {
            Issue.record("应发现更新")
            return
        }
        #expect(release.version.description == "1.3.0")
        #expect(release.archive.name == fixture.archiveName)
        #expect(release.archive.bytes == fixture.archive.count)
        #expect(release.checksums.name == "SHA256SUMS.txt")
        #expect(release.notes == "测试正式版更新说明。")
        #expect(release.pageURL.absoluteString == "https://github.com/xyy386/MathInterviewVocab/releases/tag/v1.3.0")
    }

    @Test(arguments: ["draft", "prerelease", "wrongTag", "wrongPage", "query", "fragment", "missingArchive", "duplicateArchive", "missingChecksums", "badURL", "zeroBytes", "tooLarge", "badDigest", "uploading"])
    func invalidReleaseMetadataCannotOfferDownloads(_ mutation: String) throws {
        let fixture = UpdateFixture()
        var object = fixture.object()
        var assets = try #require(object["assets"] as? [[String: Any]])
        switch mutation {
        case "draft": object["draft"] = true
        case "prerelease": object["prerelease"] = true
        case "wrongTag": object["tag_name"] = "v1.3.0-beta"
        case "wrongPage": object["html_url"] = "https://github.com/other/MathInterviewVocab/releases/tag/v1.3.0"
        case "query": object["html_url"] = "https://github.com/xyy386/MathInterviewVocab/releases/tag/v1.3.0?q=1"
        case "fragment": object["html_url"] = "https://github.com/xyy386/MathInterviewVocab/releases/tag/v1.3.0#x"
        case "missingArchive": assets.removeFirst()
        case "duplicateArchive": assets.append(assets[0])
        case "missingChecksums": assets.removeLast()
        case "badURL": assets[0]["browser_download_url"] = "https://example.com/payload.zip"
        case "zeroBytes": assets[0]["size"] = 0
        case "tooLarge": assets[0]["size"] = AppUpdatePolicy.maximumArchiveBytes + 1
        case "badDigest": assets[0]["digest"] = "sha256:not-a-hash"
        case "uploading": assets[0]["state"] = "new"
        default: break
        }
        object["assets"] = assets
        let data = try JSONSerialization.data(withJSONObject: object)
        #expect(throws: AppUpdateError.invalidRelease) {
            try AppUpdatePolicy.check(data, currentVersion: "1.2.0")
        }
    }

    @Test(arguments: [
        "http://github.com/file", "https://github.com.evil.invalid/file", "https://user@github.com/file",
        "https://github.com:444/file", "https://github.com/file#fragment", "file:///tmp/update.zip",
        "https://evil.invalid@github.com/file", "https://api.github.com.evil.invalid/latest"
    ])
    func unsafeNetworkDestinationsAreRejected(_ text: String) throws {
        let url = try #require(URL(string: text))
        #expect(!AppUpdatePolicy.isAllowedNetworkURL(url))
    }

    @Test func allowedRedirectURLsRemainHTTPSOnly() throws {
        for host in AppUpdatePolicy.allowedHosts {
            let url = try #require(URL(string: "https://\(host)/file?temporary-signature=example"))
            #expect(AppUpdatePolicy.isAllowedNetworkURL(url))
        }
        let fixture = UpdateFixture()
        var object = fixture.object()
        var assets = try #require(object["assets"] as? [[String: Any]])
        assets[0]["browser_download_url"] = "https://github.com/xyy386/MathInterviewVocab/releases/download/v1.3.0/%2e%2e.zip"
        object["assets"] = assets
        #expect(throws: AppUpdateError.invalidRelease) {
            try AppUpdatePolicy.check(JSONSerialization.data(withJSONObject: object), currentVersion: "1.2.0")
        }
    }

    @Test func checksumsAcceptStandardTextBinaryAndCRLF() throws {
        let fixture = UpdateFixture()
        let digest = fixture.hash(fixture.archive)
        for separator in ["  ", " *"] {
            let text = "\(digest)\(separator)\(fixture.archiveName)\r\n"
            #expect(try AppUpdatePolicy.checksum(in: Data(text.utf8), for: fixture.archiveName) == digest)
        }
    }

    @Test(arguments: ["duplicate", "traversal", "absolute", "backslash", "uppercase", "missing", "invalidUTF8", "tooLarge", "short"])
    func ambiguousOrUnsafeChecksumsFailClosed(_ mutation: String) throws {
        let fixture = UpdateFixture()
        let digest = fixture.hash(fixture.archive)
        var data = fixture.checksums
        switch mutation {
        case "duplicate": data.append(data)
        case "traversal": data = Data("\(digest)  ../\(fixture.archiveName)\n".utf8)
        case "absolute": data = Data("\(digest)  /\(fixture.archiveName)\n".utf8)
        case "backslash": data = Data("\(digest)  dir\\\(fixture.archiveName)\n".utf8)
        case "uppercase": data = Data("\(digest.uppercased())  \(fixture.archiveName)\n".utf8)
        case "missing": data = Data("\(digest)  other.zip\n".utf8)
        case "invalidUTF8": data = Data([0xff, 0xfe])
        case "tooLarge": data = Data(repeating: 32, count: Int(AppUpdatePolicy.maximumChecksumBytes + 1))
        case "short": data = Data("abcdef  \(fixture.archiveName)\n".utf8)
        default: break
        }
        #expect(throws: AppUpdateError.invalidChecksum) {
            try AppUpdatePolicy.checksum(in: data, for: fixture.archiveName)
        }
    }

    @Test func responseValidationRejectsErrorsAndOversizeBeforeReading() throws {
        let url = AppUpdatePolicy.latestURL
        for status in [302, 403, 404, 429, 500] {
            let response = try #require(HTTPURLResponse(url: url, statusCode: status, httpVersion: "HTTP/1.1", headerFields: nil))
            #expect(throws: AppUpdateError.status(status)) {
                try AppUpdateTransport.validate(response, maximumBytes: 10)
            }
        }
        let response = try #require(HTTPURLResponse(url: url, statusCode: 200, httpVersion: "HTTP/1.1", headerFields: ["Content-Length": "11"]))
        #expect(throws: AppUpdateError.tooLarge) {
            try AppUpdateTransport.validate(response, maximumBytes: 10)
        }
    }

    @Test(arguments: ["exact", "overLimit", "empty", "streamError"])
    @MainActor func streamingDownloadEnforcesBoundsWithoutContentLength(_ scenario: String) async throws {
        let fixture = UpdateFixture()
        try await fixture.withDirectory { directory in
            let stream = AsyncThrowingStream<UInt8, Error> { continuation in
                if scenario != "empty" {
                    for byte in [UInt8(1), 2, 3, 4] { continuation.yield(byte) }
                }
                if scenario == "streamError" { continuation.finish(throwing: URLError(.networkConnectionLost)) }
                else { continuation.finish() }
            }
            if scenario == "exact" {
                let file = try await AppUpdateTransport.writeBoundedArchive(stream, maximumBytes: 4, temporaryDirectory: directory)
                #expect(try Data(contentsOf: file) == Data([1, 2, 3, 4]))
            } else {
                await #expect(throws: (any Error).self) {
                    try await AppUpdateTransport.writeBoundedArchive(
                        stream, maximumBytes: scenario == "overLimit" ? 3 : 4, temporaryDirectory: directory
                    )
                }
                #expect(try FileManager.default.contentsOfDirectory(atPath: directory.path).isEmpty)
            }
        }
    }

    @Test @MainActor func cancellingStreamRemovesItsPartialFile() async throws {
        let fixture = UpdateFixture()
        try await fixture.withDirectory { directory in
            let stream = AsyncStream<UInt8> { continuation in continuation.yield(1) }
            let download = Task {
                try await AppUpdateTransport.writeBoundedArchive(stream, maximumBytes: 4, temporaryDirectory: directory)
            }
            await Task.yield()
            download.cancel()
            await #expect(throws: (any Error).self) { try await download.value }
            let remainingFiles = try FileManager.default.contentsOfDirectory(atPath: directory.path)
            #expect(remainingFiles.isEmpty)
        }
    }

    @Test @MainActor func checkingIsExplicitAndVerifiedDownloadDoesNotInstallAnything() async throws {
        let fixture = UpdateFixture()
        try await fixture.withDirectory { directory in
            let service = UpdateService(currentVersion: "1.2.0", transport: try fixture.transport(in: directory), downloadsDirectory: directory)
            #expect(service.phase == .idle)
            #expect(service.release == nil)
            #expect(try FileManager.default.contentsOfDirectory(atPath: directory.path).isEmpty)
            service.checkForUpdates()
            service.checkForUpdates()
            await service.waitForOperation()
            #expect(service.phase == .available)
            service.downloadUpdate()
            await service.waitForOperation()
            #expect(service.phase == .ready)
            let downloaded = try #require(service.downloadedURL)
            #expect(downloaded.lastPathComponent == fixture.archiveName)
            #expect(downloaded.path.hasPrefix(directory.path + "/MathInterviewVocab Updates/"))
            #expect(try Data(contentsOf: downloaded) == fixture.archive)
            #expect(try quarantineMarker(downloaded).hasPrefix("0081;"))
            #expect(!FileManager.default.fileExists(atPath: directory.appendingPathComponent("temporary.partial").path))
        }
    }

    @Test @MainActor func downloadedArchiveReceivesQuarantineWithoutChangingBytesOrExistingMarkers() async throws {
        let fixture = UpdateFixture()
        try await fixture.withDirectory { directory in
            let file = directory.appendingPathComponent("download.zip")
            try fixture.archive.write(to: file)
            try DownloadQuarantine.mark(file)
            let marker = try quarantineMarker(file)
            let parts = marker.split(separator: ";")
            #expect(parts.count == 4)
            #expect(parts[0] == "0081")
            #expect(Int(parts[1], radix: 16) != nil)
            #expect(parts[2] == "MathInterviewVocab")
            #expect(UUID(uuidString: String(parts[3])) != nil)
            #expect(try Data(contentsOf: file) == fixture.archive)
            #expect(throws: AppUpdateError.quarantineUnavailable) { try DownloadQuarantine.mark(file) }
            #expect(try quarantineMarker(file) == marker)
        }
    }

    @Test @MainActor func quarantineFailureNeverKeepsAnUnmarkedFinalZIP() async throws {
        let fixture = UpdateFixture()
        try await fixture.withDirectory { directory in
            var transport = try fixture.transport(in: directory)
            transport.download = { _, _ in
                let file = directory.appendingPathComponent("temporary.partial")
                try fixture.archive.write(to: file)
                try DownloadQuarantine.mark(file)
                return file
            }
            let service = UpdateService(currentVersion: "1.2.0", transport: transport, downloadsDirectory: directory)
            service.checkForUpdates()
            await service.waitForOperation()
            service.downloadUpdate()
            await service.waitForOperation()
            #expect(service.phase == .failed(AppUpdateError.quarantineUnavailable.localizedDescription))
            #expect(service.downloadedURL == nil)
            let parent = directory.appendingPathComponent("MathInterviewVocab Updates")
            #expect(try FileManager.default.contentsOfDirectory(atPath: parent.path).isEmpty)
            #expect(!FileManager.default.fileExists(atPath: directory.appendingPathComponent("temporary.partial").path))
        }
    }

    private func quarantineMarker(_ file: URL) throws -> String {
        let count = file.withUnsafeFileSystemRepresentation { path in
            guard let path else { return -1 }
            return Darwin.getxattr(path, DownloadQuarantine.attributeName, nil, 0, 0, XATTR_NOFOLLOW)
        }
        guard count > 0, count <= 1024 else { throw AppUpdateError.quarantineUnavailable }
        var data = Data(count: count)
        let size = data.count
        let read = file.withUnsafeFileSystemRepresentation { path in
            guard let path else { return -1 }
            return data.withUnsafeMutableBytes { bytes in
                Darwin.getxattr(path, DownloadQuarantine.attributeName, bytes.baseAddress, size, 0, XATTR_NOFOLLOW)
            }
        }
        guard read == count, let marker = String(data: data, encoding: .utf8) else {
            throw AppUpdateError.quarantineUnavailable
        }
        return marker
    }

    @Test(arguments: ["hash", "size", "checksum", "network"])
    @MainActor func failuresNeverLeaveAFinalArchiveAndCanRetry(_ mutation: String) async throws {
        let fixture = UpdateFixture()
        try await fixture.withDirectory { directory in
            var transport = try fixture.transport(in: directory)
            if mutation == "network" {
                transport.download = { _, _ in throw URLError(.notConnectedToInternet) }
            } else if mutation == "checksum" {
                let metadata = try fixture.metadata()
                transport.data = { url, _ in url == AppUpdatePolicy.latestURL ? metadata : Data("broken".utf8) }
            } else {
                let bytes = mutation == "hash" ? Data(repeating: 0, count: fixture.archive.count) : Data([1])
                transport.download = { _, _ in
                    let file = directory.appendingPathComponent("temporary.partial")
                    try bytes.write(to: file)
                    return file
                }
            }
            let service = UpdateService(currentVersion: "1.2.0", transport: transport, downloadsDirectory: directory)
            service.checkForUpdates()
            await service.waitForOperation()
            service.downloadUpdate()
            await service.waitForOperation()
            guard case .failed = service.phase else { Issue.record("应报告更新失败"); return }
            #expect(service.downloadedURL == nil)
            #expect(try FileManager.default.contentsOfDirectory(atPath: directory.path).isEmpty)
            service.checkForUpdates()
            await service.waitForOperation()
            #expect(service.phase == .available)
        }
    }

    @Test @MainActor func cancellingDownloadCannotOverwriteANewerCheckState() async throws {
        let fixture = UpdateFixture()
        try await fixture.withDirectory { directory in
            var transport = try fixture.transport(in: directory)
            transport.download = { _, _ in
                try await Task.sleep(nanoseconds: 60_000_000_000)
                throw URLError(.cancelled)
            }
            let service = UpdateService(currentVersion: "1.2.0", transport: transport, downloadsDirectory: directory)
            service.checkForUpdates()
            await service.waitForOperation()
            service.downloadUpdate()
            let cancelledOperation = Task { await service.waitForOperation() }
            // 先让等待任务捕获正在下载的旧 operation，再取消并启动新检查。
            await Task.yield()
            service.cancelDownload()
            #expect(service.phase == .available)
            service.checkForUpdates()
            await service.waitForOperation()
            await cancelledOperation.value
            #expect(service.phase == .available)
            #expect(service.downloadedURL == nil)
            #expect(try FileManager.default.contentsOfDirectory(atPath: directory.path).isEmpty)
        }
    }
}

private struct UpdateFixture: Sendable {
    let archive = Data("PK\u{03}\u{04}update-test-archive".utf8)
    let archiveName = "MathInterviewVocab-macOS-universal-v1.3.0.zip"
    var checksums: Data { Data("\(hash(archive))  \(archiveName)\n".utf8) }

    func hash(_ data: Data) -> String {
        SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
    }

    func object(version: String = "1.3.0", assets: [[String: Any]]? = nil) -> [String: Any] {
        let entries: [[String: Any]] = [
            ["name": archiveName, "size": archive.count, "state": "uploaded", "digest": "sha256:\(hash(archive))",
             "browser_download_url": "https://github.com/xyy386/MathInterviewVocab/releases/download/v\(version)/\(archiveName)"],
            ["name": "SHA256SUMS.txt", "size": checksums.count, "state": "uploaded", "digest": "sha256:\(hash(checksums))",
             "browser_download_url": "https://github.com/xyy386/MathInterviewVocab/releases/download/v\(version)/SHA256SUMS.txt"]
        ]
        return ["tag_name": "v\(version)", "html_url": "https://github.com/xyy386/MathInterviewVocab/releases/tag/v\(version)",
                "draft": false, "prerelease": false, "body": "测试正式版更新说明。", "assets": assets ?? entries]
    }

    func metadata(version: String = "1.3.0", assets: [[String: Any]]? = nil) throws -> Data {
        try JSONSerialization.data(withJSONObject: object(version: version, assets: assets))
    }

    func transport(in directory: URL) throws -> AppUpdateTransport {
        let metadata = try metadata()
        let checksums = checksums
        let archive = archive
        return AppUpdateTransport(data: { url, _ in
            url == AppUpdatePolicy.latestURL ? metadata : checksums
        }, download: { _, _ in
            let file = directory.appendingPathComponent("temporary.partial")
            try archive.write(to: file)
            return file
        })
    }

    @MainActor func withDirectory(_ action: @MainActor (URL) async throws -> Void) async throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent("AppUpdateTests-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: false)
        defer { try? FileManager.default.removeItem(at: directory) }
        try await action(directory)
    }
}

import CryptoKit
import AppKit
import Foundation
import SwiftUI
import Testing
@testable import MathInterviewVocab

private let updateFixturePayload = Data("PK deterministic native update archive fixture".utf8)
private struct NativeUpdateFixture: Sendable {
    let signed: SignedMacUpdateIndex
    let key: String
    let feed = Data("<rss>native fixture</rss>".utf8)
    init(version: String = "1.5.0") throws {
        let privateKey = Curve25519.Signing.PrivateKey()
        key = privateKey.publicKey.rawRepresentation.base64EncodedString()
        let index = MacUpdateIndex(schema: 1, platform: "macos-universal", version: version, build: "7", notes: "测试说明", assets: [
            UpdateIndexAsset(name: "MathInterviewVocab-macOS-universal-v\(version).zip", bytes: Int64(updateFixturePayload.count), sha256: DesktopUpdatePolicy.digest(updateFixturePayload)),
            UpdateIndexAsset(name: "appcast.xml", bytes: Int64(feed.count), sha256: DesktopUpdatePolicy.digest(feed))
        ])
        let data = try JSONEncoder().encode(index)
        signed = SignedMacUpdateIndex(index: index, data: data, signature: Data(try privateKey.signature(for: data).base64EncodedString().utf8))
    }
}
private actor UpdateTestCollector {
    var bytes = Data()
    var states: [MacTransferState] = []
    var offsets: [Int64] = []
    var requests: [String] = []
    var cancellations = 0
    func append(_ data: Data) { bytes.append(data) }
    func state(_ state: MacTransferState) { states.append(state) }
    func requested(_ url: URL, offset: Int64) { requests.append(url.absoluteString); if url.path.hasSuffix(".zip") { offsets.append(offset) } }
    func cancelled() { cancellations += 1 }
}
private func nativeResponse(_ data: Data, status: Int = 200, range: String? = nil) -> UpdateNetworkResponse {
    let stream = AsyncThrowingStream<UpdateNetworkChunk, any Error> { continuation in
        continuation.yield(UpdateNetworkChunk(data: data, consumed: {})); continuation.finish()
    }
    return UpdateNetworkResponse(status: status, length: Int64(data.count), range: range, chunks: stream, cancel: {})
}
private func updateTestDirectory() throws -> URL {
    let base = ProcessInfo.processInfo.environment["MATH_VOCAB_VALIDATION_DIRECTORY"].flatMap { $0.hasPrefix("/") ? URL(fileURLWithPath: $0) : nil }
        ?? FileManager.default.temporaryDirectory
    let url = base.appendingPathComponent("mac-update-unit-\(UUID().uuidString)", isDirectory: true)
    try FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
    return url
}
private func nativeEngine(_ fixture: NativeUpdateFixture, directory: URL, collector: UpdateTestCollector,
                          archive: UpdateNetworkOpen? = nil) -> UpdateDownloadEngine {
    UpdateDownloadEngine(currentVersion: "1.4.0", currentBuild: "6", directory: directory, publicKey: fixture.key,
        open: { url, offset in
            await collector.requested(url, offset: offset)
            if url.absoluteString.hasSuffix("latest.json.sig") { return nativeResponse(fixture.signed.signature) }
            if url.absoluteString.hasSuffix("latest.json") { return nativeResponse(fixture.signed.data) }
            if url.path.hasSuffix("appcast.xml") { return nativeResponse(fixture.feed) }
            if let archive { return try await archive(url, offset) }
            return nativeResponse(updateFixturePayload.subdata(in: Int(offset)..<updateFixturePayload.count), status: offset > 0 ? 206 : 200,
                range: offset > 0 ? "bytes \(offset)-\(updateFixturePayload.count - 1)/\(updateFixturePayload.count)" : nil)
        }, changed: { value in Task { await collector.state(value) } })
}

struct AcceleratedUpdateTests {
    @Test func localHandoffDoesNotEnableArbitraryInsecureExternalLoads() throws {
        let ats = try #require(Bundle.main.infoDictionary?["NSAppTransportSecurity"] as? [String: Any])
        #expect(ats["NSAllowsLocalNetworking"] as? Bool == true)
        #expect(ats["NSAllowsArbitraryLoads"] == nil)
        #expect(ats["NSExceptionDomains"] == nil)
    }
    @Test(arguments: ["platform", "schema", "build", "asset", "extra"]) func validSignatureDoesNotPermitInvalidIndexFields(_ field: String) throws {
        let fixture = try NativeUpdateFixture(); let signingKey = Curve25519.Signing.PrivateKey()
        var object = try #require(JSONSerialization.jsonObject(with: fixture.signed.data) as? [String: Any])
        switch field {
        case "platform": object["platform"] = "windows-x64"
        case "schema": object["schema"] = 2
        case "build": object["build"] = "07"
        case "asset": object["assets"] = [["name": "arbitrary.zip", "bytes": 1, "sha256": String(repeating: "0", count: 64)]]
        default: object["url"] = "https://untrusted.invalid/package.zip"
        }
        let data = try JSONSerialization.data(withJSONObject: object)
        let signature = Data(try signingKey.signature(for: data).base64EncodedString().utf8)
        #expect(throws: (any Error).self) {
            try DesktopUpdatePolicy.verify(data, signature: signature, publicKey: signingKey.publicKey.rawRepresentation.base64EncodedString())
        }
    }
    @Test func signedIndexRequiresOriginalBytesAndExpectedPlatform() throws {
        let fixture = try NativeUpdateFixture()
        #expect(try DesktopUpdatePolicy.verify(fixture.signed.data, signature: fixture.signed.signature, publicKey: fixture.key).index.version == "1.5.0")
        var changed = fixture.signed.data; changed.append(0x20)
        #expect(throws: (any Error).self) { try DesktopUpdatePolicy.verify(changed, signature: fixture.signed.signature, publicKey: fixture.key) }
        #expect(throws: (any Error).self) { try DesktopUpdatePolicy.verify(fixture.signed.data, signature: Data("invalid".utf8), publicKey: fixture.key) }
    }
    @Test func directModeNeverIncludesMirrorsOrCDNs() throws {
        #expect(DesktopUpdatePolicy.metadataCandidates(.direct).map { $0.0.id } == ["direct"])
        let fixture = try NativeUpdateFixture()
        let url = DesktopUpdatePolicy.assetURL(fixture.signed.index.archive, in: fixture.signed.index)
        #expect(DesktopUpdatePolicy.assetCandidates(url, mode: .direct, preferred: "ghfast").map { $0.0.id } == ["direct"])
        #expect(DesktopUpdatePolicy.assetCandidates(url, mode: .auto, preferred: nil).allSatisfy { !$0.1.absoluteString.contains("jsdelivr") })
        for (_, candidate) in DesktopUpdatePolicy.metadataCandidates(.auto) { #expect(throws: Never.self) { try DesktopUpdatePolicy.checkedURL(candidate) } }
    }
    @Test(arguments: [
        "http://github.com/xyy386/MathInterviewVocab/file", "https://ghfast.top/https://github.com/other/repo/file",
        "https://gh-proxy.com/https://raw.githubusercontent.com/xyy386/MathInterviewVocab/main/Android/latest-release.json",
        "https://testingcf.jsdelivr.net/gh/xyy386/MathInterviewVocab@main/Windows/resources/audio/file.mp3",
        "https://raw.githubusercontent.com/xyy386/MathInterviewVocab/main/updates/macos/latest.json?extra=1",
        "https://release-assets.githubusercontent.com/unbound-file", "https://user@github.com/xyy386/MathInterviewVocab"
    ]) func disallowedURLsFailBeforeNetworking(_ text: String) async throws {
        let url = try #require(URL(string: text))
        #expect(throws: (any Error).self) { try DesktopUpdatePolicy.checkedURL(url) }
        await #expect(throws: (any Error).self) { try await UpdateNetwork.open(url) }
    }
    @Test func strictRangeResponseRejectsMissingOrWrongOffsets() throws {
        #expect(try DesktopUpdatePolicy.effectiveOffset(status: 206, length: 90, range: "bytes 10-99/100", offset: 10, total: 100) == 10)
        #expect(try DesktopUpdatePolicy.effectiveOffset(status: 200, length: 100, range: nil, offset: 10, total: 100) == 0)
        for value in [nil, "bytes 0-99/100", "bytes 10-98/100", "bytes 10-99/101", "bytes 10-99/*"] {
            #expect(throws: (any Error).self) { try DesktopUpdatePolicy.effectiveOffset(status: 206, length: 90, range: value, offset: 10, total: 100) }
        }
    }
    @Test func localRestoreAndPreferenceChangeDoNotRequestNetwork() async throws {
        let directory = try updateTestDirectory(); defer { try? FileManager.default.removeItem(at: directory) }
        let collector = UpdateTestCollector(); let fixture = try NativeUpdateFixture()
        let engine = nativeEngine(fixture, directory: directory, collector: collector)
        await engine.restore(); await engine.setRouteMode(.direct)
        #expect(await collector.requests.isEmpty)
    }
    @Test func metadataHedgeCancelsTheSlowerOfficialRequests() async throws {
        let directory = try updateTestDirectory(); defer { try? FileManager.default.removeItem(at: directory) }
        let collector = UpdateTestCollector(); let fixture = try NativeUpdateFixture()
        let engine = UpdateDownloadEngine(currentVersion: "1.4.0", currentBuild: "6", directory: directory, publicKey: fixture.key,
            open: { url, offset in
                await collector.requested(url, offset: offset)
                if url.host == "raw.githubusercontent.com" {
                    do { try await Task.sleep(for: .seconds(10)) }
                    catch { await collector.cancelled(); throw error }
                }
                return nativeResponse(url.absoluteString.hasSuffix(".sig") ? fixture.signed.signature : fixture.signed.data)
            }, changed: { _ in })
        let signed = try await engine.check()
        #expect(signed.index.version == "1.5.0")
        #expect(await collector.cancellations == 2)
        #expect(await collector.requests.count <= 6)
    }
    @Test func knownHigherVersionRejectsOlderSignedCDNAndProxyResponses() async throws {
        let directory = try updateTestDirectory(); defer { try? FileManager.default.removeItem(at: directory) }
        try Data(#"{"routeMode":"auto","knownVersion":"1.6.0"}"#.utf8).write(to: directory.appendingPathComponent("preferences.json"))
        let engine = nativeEngine(try NativeUpdateFixture(), directory: directory, collector: UpdateTestCollector())
        await #expect(throws: (any Error).self) { try await engine.check() }
        #expect(await engine.snapshot().status == "error")
    }
    @Test func completeArchiveIsHashedAndDeliveredExactlyOnce() async throws {
        let directory = try updateTestDirectory(); defer { try? FileManager.default.removeItem(at: directory) }
        let collector = UpdateTestCollector(); let fixture = try NativeUpdateFixture()
        let engine = nativeEngine(fixture, directory: directory, collector: collector)
        await engine.setRouteMode(.direct)
        let signed = try await engine.check(); let feed = try await engine.feed(for: signed)
        try await engine.streamArchive(signed, feed: feed, replace: false) { await collector.append($0) }
        #expect(await collector.bytes == updateFixturePayload)
        #expect(try Data(contentsOf: directory.appendingPathComponent("archive.zip")) == updateFixturePayload)
        #expect(await collector.offsets == [0])
    }
    @Test func failedTransferRestoresAndResumesAfterEngineRecreation() async throws {
        let directory = try updateTestDirectory(); defer { try? FileManager.default.removeItem(at: directory) }
        let fixture = try NativeUpdateFixture(); let firstCollector = UpdateTestCollector()
        let first = nativeEngine(fixture, directory: directory, collector: firstCollector, archive: { _, _ in
            let stream = AsyncThrowingStream<UpdateNetworkChunk, any Error> { continuation in
                continuation.yield(UpdateNetworkChunk(data: updateFixturePayload.prefix(9), consumed: {}))
                continuation.finish(throwing: UpdateTransferError.failure("模拟断流"))
            }
            return UpdateNetworkResponse(status: 200, length: Int64(updateFixturePayload.count), range: nil, chunks: stream, cancel: {})
        })
        await first.setRouteMode(.direct)
        let signed = try await first.check(); let feed = try await first.feed(for: signed)
        await #expect(throws: (any Error).self) { try await first.streamArchive(signed, feed: feed, replace: false) { _ in } }
        #expect(try Data(contentsOf: directory.appendingPathComponent("archive.partial")).count == 9)
        let secondCollector = UpdateTestCollector(); let second = nativeEngine(fixture, directory: directory, collector: secondCollector)
        await second.restore(); #expect(await secondCollector.requests.isEmpty)
        let cached = try #require(await second.cachedIndex())
        try await second.streamArchive(cached, feed: feed, replace: false) { await secondCollector.append($0) }
        #expect(await secondCollector.offsets == [9]); #expect(await secondCollector.bytes == updateFixturePayload)
    }
    @Test func wrongHashNeverDeliversTheFinalByteToSparkle() async throws {
        let directory = try updateTestDirectory(); defer { try? FileManager.default.removeItem(at: directory) }
        let collector = UpdateTestCollector(); let fixture = try NativeUpdateFixture()
        let engine = nativeEngine(fixture, directory: directory, collector: collector, archive: { _, _ in nativeResponse(Data(repeating: 0, count: updateFixturePayload.count)) })
        await engine.setRouteMode(.direct)
        let signed = try await engine.check(); let feed = try await engine.feed(for: signed)
        await #expect(throws: (any Error).self) { try await engine.streamArchive(signed, feed: feed, replace: false) { await collector.append($0) } }
        #expect(await collector.bytes.count == updateFixturePayload.count - 1)
        #expect(!FileManager.default.fileExists(atPath: directory.appendingPathComponent("archive.zip").path))
    }
    @Test func ignoredRangeSafelyRestartsWithoutDuplicatingTheSavedPrefix() async throws {
        let directory = try updateTestDirectory(); defer { try? FileManager.default.removeItem(at: directory) }
        try updateFixturePayload.prefix(9).write(to: directory.appendingPathComponent("archive.partial"))
        let collector = UpdateTestCollector(); let fixture = try NativeUpdateFixture()
        let engine = nativeEngine(fixture, directory: directory, collector: collector, archive: { _, _ in nativeResponse(updateFixturePayload) })
        await engine.setRouteMode(.direct)
        let signed = try await engine.check(); let feed = try await engine.feed(for: signed)
        try await engine.streamArchive(signed, feed: feed, replace: false) { await collector.append($0) }
        #expect(await collector.offsets == [9]); #expect(await collector.bytes == updateFixturePayload)
    }
    @Test func incorrectRangeCannotModifyOrDeliverAnExistingPartial() async throws {
        let directory = try updateTestDirectory(); defer { try? FileManager.default.removeItem(at: directory) }
        let prefix = updateFixturePayload.prefix(9)
        try prefix.write(to: directory.appendingPathComponent("archive.partial"))
        let collector = UpdateTestCollector(); let fixture = try NativeUpdateFixture()
        let engine = nativeEngine(fixture, directory: directory, collector: collector, archive: { _, _ in
            nativeResponse(updateFixturePayload.dropFirst(9), status: 206, range: "bytes 0-44/45")
        })
        await engine.setRouteMode(.direct)
        let signed = try await engine.check(); let feed = try await engine.feed(for: signed)
        await #expect(throws: (any Error).self) { try await engine.streamArchive(signed, feed: feed, replace: false) { await collector.append($0) } }
        #expect(await collector.bytes.isEmpty)
        #expect(try Data(contentsOf: directory.appendingPathComponent("archive.partial")) == prefix)
    }
    @Test func disconnectedOfficialRouteContinuesThroughOneMirrorWithTheExactRange() async throws {
        let directory = try updateTestDirectory(); defer { try? FileManager.default.removeItem(at: directory) }
        let collector = UpdateTestCollector(); let fixture = try NativeUpdateFixture()
        let engine = nativeEngine(fixture, directory: directory, collector: collector, archive: { url, offset in
            if url.host == "github.com" {
                let stream = AsyncThrowingStream<UpdateNetworkChunk, any Error> { continuation in
                    continuation.yield(UpdateNetworkChunk(data: updateFixturePayload.prefix(9), consumed: {}))
                    continuation.finish(throwing: UpdateTransferError.failure("模拟断网"))
                }
                return UpdateNetworkResponse(status: 200, length: Int64(updateFixturePayload.count), range: nil, chunks: stream, cancel: {})
            }
            return nativeResponse(updateFixturePayload.dropFirst(Int(offset)), status: 206,
                range: "bytes \(offset)-\(updateFixturePayload.count - 1)/\(updateFixturePayload.count)")
        })
        let signed = try await engine.check(); let feed = try await engine.feed(for: signed)
        try await engine.streamArchive(signed, feed: feed, replace: false) { await collector.append($0) }
        #expect(await collector.offsets == [0, 9]); #expect(await collector.bytes == updateFixturePayload)
    }
    @Test func tamperedCacheRecordFailsLocallyWithoutNetworking() async throws {
        let directory = try updateTestDirectory(); defer { try? FileManager.default.removeItem(at: directory) }
        let fixture = try NativeUpdateFixture(); let collector = UpdateTestCollector()
        try Data(#"{"schema":1,"index":"e30=","signature":""}"#.utf8).write(to: directory.appendingPathComponent("transfer.json"))
        let engine = nativeEngine(fixture, directory: directory, collector: collector)
        await engine.restore()
        #expect(await engine.snapshot().status == "error"); #expect(await collector.requests.isEmpty)
    }
    @Test func cacheClearPreservesUnrelatedRecords() async throws {
        let directory = try updateTestDirectory(); defer { try? FileManager.default.removeItem(at: directory) }
        let engine = nativeEngine(try NativeUpdateFixture(), directory: directory, collector: UpdateTestCollector())
        await engine.restore()
        let record = directory.appendingPathComponent("progress.json"); try Data("preserve".utf8).write(to: record)
        await engine.clearCache(); #expect(try Data(contentsOf: record) == Data("preserve".utf8))
    }
    @Test func completedUpgradeClearsOnlyPayloadAndPublishesTheSavedRoute() async throws {
        let directory = try updateTestDirectory(); defer { try? FileManager.default.removeItem(at: directory) }
        let fixture = try NativeUpdateFixture(); let collector = UpdateTestCollector()
        let first = nativeEngine(fixture, directory: directory, collector: collector)
        await first.setRouteMode(.direct)
        let signed = try await first.check(); let feed = try await first.feed(for: signed)
        try await first.streamArchive(signed, feed: feed, replace: false) { _ in }
        let upgraded = UpdateDownloadEngine(currentVersion: "1.5.0", currentBuild: "7", directory: directory, publicKey: fixture.key,
            open: { _, _ in throw UpdateTransferError.failure("No startup network") }, changed: { _ in })
        await upgraded.restore()
        #expect(await upgraded.snapshot().routeMode == .direct)
        #expect(!FileManager.default.fileExists(atPath: directory.appendingPathComponent("archive.zip").path))
        #expect(FileManager.default.fileExists(atPath: directory.appendingPathComponent("preferences.json").path))
    }
    @Test func loopbackRelayServesOnlyTheCurrentSessionAndSurvivesRestart() async throws {
        let relay = UpdateRelay(); let fixture = try NativeUpdateFixture()
        let first = try await relay.start(feed: fixture.feed, archive: fixture.signed.index.archive) { send in try await send(updateFixturePayload) }
        let session = URLSession(configuration: .ephemeral)
        defer { session.invalidateAndCancel() }
        let (feed, response) = try await session.data(from: first)
        #expect(feed == fixture.feed); #expect((response as? HTTPURLResponse)?.statusCode == 200)
        let archive = try #require(await relay.archiveURL())
        let (bytes, _) = try await session.data(from: archive); #expect(bytes == updateFixturePayload)
        let second = try await relay.start(feed: fixture.feed, archive: fixture.signed.index.archive) { send in try await send(updateFixturePayload) }
        let (next, _) = try await session.data(from: second); #expect(next == fixture.feed)
        await relay.stop()
    }
}

@Suite(.serialized)
@MainActor
struct UpdateProgressLayoutTests {
    @Test(arguments: [1000.0, 1220.0, 1440.0]) func nativeProgressFitsTheWindowWithoutCoveringReadingContent(_ width: Double) throws {
        var state = MacTransferState()
        state.status = "downloading"; state.version = "1.5.0"; state.activeRouteLabel = "加速镜像 1 · gh-proxy"
        state.downloadedBytes = 41_000_000; state.totalBytes = 50_000_000; state.speedBytesPerSecond = 800_000
        let content = UpdateProgressContent(state: state, busy: true, pause: {}, resume: {}, view: {})
            .frame(width: width).environment(\.colorScheme, .light)
        let host = NSHostingView(rootView: content)
        host.frame = NSRect(x: 0, y: 0, width: width, height: 80)
        let window = NSWindow(contentRect: host.frame, styleMask: [.titled], backing: .buffered, defer: false)
        window.isReleasedWhenClosed = false; defer { window.close() }
        window.contentView = host; host.layoutSubtreeIfNeeded()
        #expect(host.fittingSize.width <= width + 1)
        #expect(host.fittingSize.height >= 45 && host.fittingSize.height <= 100)
        if let directory = ProcessInfo.processInfo.environment["MATH_VOCAB_VALIDATION_DIRECTORY"],
           let bitmap = host.bitmapImageRepForCachingDisplay(in: host.bounds) {
            host.cacheDisplay(in: host.bounds, to: bitmap)
            try bitmap.representation(using: .png, properties: [:])?.write(to: URL(fileURLWithPath: directory)
                .appendingPathComponent("mac-update-layout-\(Int(width)).png"))
        }
    }
}

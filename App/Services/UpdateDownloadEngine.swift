import CryptoKit
import Foundation

private struct UpdateCacheRecord: Codable { let schema: Int; let index: Data; let signature: Data }
private struct UpdatePreferencesRecord: Codable { let routeMode: UpdateRouteMode; let knownVersion: String }
private enum IndexRaceEvent: Sendable {
    case hedge, grace
    case success(SignedMacUpdateIndex, UpdateRoute)
    case failure(String)
}

func updateDeadline<T: Sendable>(_ seconds: Double, work: @escaping @Sendable () async throws -> T) async throws -> T {
    try await withThrowingTaskGroup(of: T.self) { group in
        group.addTask { try await work() }
        group.addTask { try await Task.sleep(for: .seconds(seconds)); throw UpdateTransferError.failure("更新请求超时，断点仍会保留。") }
        defer { group.cancelAll() }
        return try await group.next()!
    }
}

/** 独立 actor 承担网络和磁盘工作；窗口关闭、切换模块不会销毁任务。 */
actor UpdateDownloadEngine {
    private let currentVersion: String
    private let currentBuild: String
    private var directory: URL
    private let open: UpdateNetworkOpen
    private let publicKey: String
    private let changed: @Sendable (MacTransferState) -> Void
    private var state = MacTransferState()
    private var restored = false
    private var canonicalized = false
    private var cached: SignedMacUpdateIndex?
    private var selected: SignedMacUpdateIndex?
    private var knownVersion: String
    private var preferredRoute: String?
    private var cancelWork: (@Sendable () async -> Void)?

    init(currentVersion: String, currentBuild: String, directory: URL,
         publicKey: String = DesktopUpdatePolicy.publicKey,
         open: @escaping UpdateNetworkOpen = { try await UpdateNetwork.open($0, offset: $1) },
         changed: @escaping @Sendable (MacTransferState) -> Void) {
        self.currentVersion = currentVersion; self.currentBuild = currentBuild
        self.directory = directory.standardizedFileURL; self.knownVersion = currentVersion
        self.publicKey = publicKey; self.open = open; self.changed = changed
    }
    private func file(_ name: String) -> URL { directory.appendingPathComponent(name) }
    private func publish(_ mutation: (inout MacTransferState) -> Void) {
        mutation(&state); state.sequence += 1; changed(state)
    }
    private func safeDirectory() throws {
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true, attributes: [.posixPermissions: 0o700])
        let attributes = try FileManager.default.attributesOfItem(atPath: directory.path)
        guard attributes[.type] as? FileAttributeType == .typeDirectory else { throw UpdateTransferError.failure("更新缓存目录不是普通目录。") }
        let resolved = directory.resolvingSymlinksInPath().standardizedFileURL
        guard !canonicalized || resolved == directory else { throw UpdateTransferError.failure("更新缓存目录被重定向。") }
        directory = resolved; canonicalized = true
    }
    private func size(_ name: String, maximum: Int64) throws -> Int64? {
        let url = file(name)
        do {
            let attributes = try FileManager.default.attributesOfItem(atPath: url.path)
            guard attributes[.type] as? FileAttributeType == .typeRegular,
                  let count = attributes[.size] as? NSNumber, count.int64Value <= maximum,
                  url.resolvingSymlinksInPath() == url else { throw UpdateTransferError.failure("更新缓存被修改或超出大小。") }
            return count.int64Value
        } catch let error as NSError where error.domain == NSCocoaErrorDomain && error.code == NSFileReadNoSuchFileError { return nil }
    }
    private func digest(_ url: URL, bytes: Int64) throws -> String {
        let handle = try FileHandle(forReadingFrom: url); defer { try? handle.close() }
        var hash = SHA256(); var count: Int64 = 0
        while let data = try handle.read(upToCount: 64 * 1024), !data.isEmpty {
            try Task.checkCancellation(); count += Int64(data.count)
            guard count <= bytes else { throw UpdateTransferError.failure("更新缓存大小发生变化。") }
            hash.update(data: data)
        }
        guard count == bytes else { throw UpdateTransferError.failure("更新缓存不完整。") }
        return hash.finalize().map { String(format: "%02x", $0) }.joined()
    }
    private func savePreferences() throws {
        try safeDirectory()
        try JSONEncoder().encode(UpdatePreferencesRecord(routeMode: state.routeMode, knownVersion: knownVersion))
            .write(to: file("preferences.json"), options: .atomic)
    }
    private func removePayload() throws {
        // 只删除固定下载文件；符号链接仅删除链接自身，不递归处理目录。
        for name in ["archive.partial", "archive.zip", "appcast.xml", "transfer.json"] {
            let url = file(name)
            do {
                let attributes = try FileManager.default.attributesOfItem(atPath: url.path)
                guard attributes[.type] as? FileAttributeType != .typeDirectory else { throw UpdateTransferError.failure("缓存位置包含非预期目录，请检查后重试。") }
                try FileManager.default.removeItem(at: url)
            } catch let error as NSError where error.domain == NSCocoaErrorDomain && error.code == NSFileReadNoSuchFileError { continue }
        }
        cached = nil
    }
    func restore() {
        guard !restored else { return }; restored = true
        do {
            try safeDirectory()
            if try size("preferences.json", maximum: 4096) != nil {
                let prefs = try JSONDecoder().decode(UpdatePreferencesRecord.self, from: Data(contentsOf: file("preferences.json")))
                state.routeMode = prefs.routeMode
                if let version = AppVersion(prefs.knownVersion), let current = AppVersion(knownVersion), version > current { knownVersion = prefs.knownVersion }
            }
            guard try size("transfer.json", maximum: 128 * 1024) != nil else { publish { _ in }; return }
            let record = try JSONDecoder().decode(UpdateCacheRecord.self, from: Data(contentsOf: file("transfer.json")))
            guard record.schema == 1 else { throw UpdateTransferError.failure("缓存版本信息无效。") }
            let signed = try DesktopUpdatePolicy.verify(record.index, signature: record.signature, publicKey: publicKey)
            guard let candidate = AppVersion(signed.index.version), let installed = AppVersion(currentVersion), candidate > installed,
                  UInt64(signed.index.build)! > (UInt64(currentBuild) ?? 0) else { try removePayload(); publish { _ in }; return }
            cached = signed; selected = signed
            let archive = signed.index.archive
            let partial = try size("archive.partial", maximum: archive.bytes) ?? 0
            let complete = try size("archive.zip", maximum: archive.bytes)
            var count = partial
            if complete == archive.bytes {
                if try digest(file("archive.zip"), bytes: archive.bytes) == archive.sha256 { count = archive.bytes }
                else { try FileManager.default.removeItem(at: file("archive.zip")) }
            } else if complete != nil {
                try FileManager.default.removeItem(at: file("archive.zip"))
            }
            publish {
                $0.status = "paused"; $0.message = "已保留下载断点，点击继续；不会自动联网。"
                $0.version = signed.index.version; $0.notes = signed.index.notes; $0.downloadedBytes = count; $0.totalBytes = archive.bytes
                $0.canResume = true; $0.hasCachedDownload = true; $0.cachedIdentity = signed.index.identity
            }
        } catch { publish { $0.status = "error"; $0.message = "无法读取更新缓存：\(error.localizedDescription)"; $0.hasCachedDownload = true } }
    }
    func setRouteMode(_ mode: UpdateRouteMode) {
        restore(); guard cancelWork == nil, !state.busy else { return }
        do { state.routeMode = mode; try savePreferences(); publish { $0.activeRouteLabel = "" } }
        catch { publish { $0.status = "error"; $0.message = "无法保存更新线路：\(error.localizedDescription)" } }
    }
    func clearCache() {
        restore(); guard cancelWork == nil else { return }
        do {
            try safeDirectory(); try removePayload()
            publish { $0.status = "idle"; $0.canResume = false; $0.hasCachedDownload = false; $0.cachedIdentity = nil
                $0.downloadedBytes = 0; $0.message = "下载缓存已清除，学习记录与设置未改变。" }
        } catch { publish { $0.status = "error"; $0.message = error.localizedDescription } }
    }
    func cachedIndex() -> SignedMacUpdateIndex? { cached }
    func snapshot() -> MacTransferState { state }
    func nativeReady() {
        publish { $0.status = "ready"; $0.canResume = false; $0.downloadedBytes = $0.totalBytes
            $0.message = "Sparkle 校验通过，请在原生窗口确认安装并重启。" }
    }
    func pause() async {
        if let cancelWork { await cancelWork() }
        if cached != nil { publish { $0.status = "paused"; $0.canResume = true; $0.speedBytesPerSecond = 0; $0.message = "已暂停，断点会保留到下次手动继续。" } }
    }
    private func perform<T: Sendable>(_ body: @escaping @Sendable () async throws -> T) async throws -> T {
        guard cancelWork == nil else { throw UpdateTransferError.failure("已有更新任务正在运行。") }
        let task = Task { try await body() }
        cancelWork = { task.cancel(); _ = try? await task.value }
        defer { cancelWork = nil }
        return try await withTaskCancellationHandler { try await task.value } onCancel: { task.cancel() }
    }
    private func small(_ url: URL, maximum: Int, expected: Int64? = nil) async throws -> Data {
        let response = try await open(url, 0); defer { response.cancel() }
        guard response.status == 200, response.length.map({ $0 <= Int64(maximum) && (expected == nil || $0 == expected) }) ?? true else {
            throw UpdateTransferError.failure("更新元数据响应大小无效。")
        }
        var result = Data()
        for try await chunk in response.chunks {
            defer { chunk.consumed() }; try Task.checkCancellation()
            guard result.count + chunk.data.count <= maximum else { throw UpdateTransferError.failure("更新元数据超过大小上限。") }
            result.append(chunk.data)
        }
        try Task.checkCancellation()
        guard expected == nil || Int64(result.count) == expected else { throw UpdateTransferError.failure("更新元数据未完整下载。") }
        return result
    }
    private func fetchIndex(_ route: UpdateRoute, url: URL) async throws -> (SignedMacUpdateIndex, UpdateRoute) {
        async let data = small(url, maximum: DesktopUpdatePolicy.maximumIndex)
        async let signature = small(URL(string: url.absoluteString + ".sig")!, maximum: 1024)
        let signed = try DesktopUpdatePolicy.verify(await data, signature: await signature, publicKey: publicKey)
        guard AppVersion(signed.index.version)! >= AppVersion(knownVersion)! else { throw UpdateTransferError.failure("此线路返回旧版缓存。") }
        return (signed, route)
    }
    private func raceIndex() async throws -> (SignedMacUpdateIndex, UpdateRoute) {
        let candidates = DesktopUpdatePolicy.metadataCandidates(state.routeMode)
        let mode = state.routeMode
        return try await withThrowingTaskGroup(of: IndexRaceEvent.self) { group in
            func worker(_ index: Int) {
                let (route, url) = candidates[index]
                group.addTask {
                    do { let result = try await self.fetchIndex(route, url: url); return .success(result.0, result.1) }
                    catch { return .failure(error.localizedDescription) }
                }
            }
            worker(0)
            group.addTask { try await Task.sleep(for: .milliseconds(mode == .mirror ? 400 : 1200)); return .hedge }
            var next = 1; var active = 1; var fallback: (SignedMacUpdateIndex, UpdateRoute)?; var failure = "所有更新线路均不可用。"
            var hasGrace = false
            defer { group.cancelAll() }
            while let event = try await group.next() {
                switch event {
                case .hedge:
                    while active < 3 && next < candidates.count { worker(next); next += 1; active += 1 }
                case .grace: if let fallback { return fallback }
                case .success(let signed, let route):
                    active -= 1
                    if AppVersion(signed.index.version)! > AppVersion(currentVersion)! || route.id == "direct" { return (signed, route) }
                    if fallback == nil || AppVersion(signed.index.version)! > AppVersion(fallback!.0.index.version)! { fallback = (signed, route) }
                    if !hasGrace { hasGrace = true; group.addTask { try await Task.sleep(for: .milliseconds(800)); return .grace } }
                    while active < 3 && next < candidates.count { worker(next); next += 1; active += 1 }
                case .failure(let message):
                    failure = message; active -= 1
                    while active < 3 && next < candidates.count { worker(next); next += 1; active += 1 }
                }
                if active == 0 && next == candidates.count {
                    if let fallback { return fallback }; throw UpdateTransferError.failure(failure)
                }
            }
            throw UpdateTransferError.failure(failure)
        }
    }
    func check() async throws -> SignedMacUpdateIndex {
        restore()
        return try await perform { try await updateDeadline(45) { try await self.checkBody() } }
    }
    private func checkBody() async throws -> SignedMacUpdateIndex {
        publish { $0.status = "checking"; $0.message = "正在探测并验证更新线路…" }
        do {
            let (signed, route) = try await raceIndex(); try Task.checkCancellation()
            knownVersion = signed.index.version; try savePreferences(); selected = signed; preferredRoute = route.id
            let isNewer = AppVersion(signed.index.version)! > AppVersion(currentVersion)!
            guard !isNewer || UInt64(signed.index.build)! > (UInt64(currentBuild) ?? 0) else { throw UpdateTransferError.failure("更新构建号没有递增。") }
            publish { $0.status = isNewer ? "available" : "current"; $0.version = signed.index.version; $0.notes = signed.index.notes
                $0.totalBytes = signed.index.archive.bytes; $0.activeRouteLabel = route.label
                $0.message = isNewer ? "发现新版本，正在准备原生更新窗口。" : "当前已是最新 macOS 正式版。"
                $0.canResume = cached?.index.identity == signed.index.identity }
            return signed
        } catch { publish { $0.status = "error"; $0.message = error.localizedDescription }; throw error }
    }
    func feed(for signed: SignedMacUpdateIndex) async throws -> Data {
        try safeDirectory()
        if cached?.index.identity == signed.index.identity, try size("appcast.xml", maximum: 64 * 1024) != nil {
            let data = try Data(contentsOf: file("appcast.xml"))
            if DesktopUpdatePolicy.digest(data) == signed.index.appcast.sha256 { return data }
        }
        var failure: any Error = UpdateTransferError.failure("无法读取签名更新源。")
        let asset = signed.index.appcast
        for (route, url) in DesktopUpdatePolicy.assetCandidates(DesktopUpdatePolicy.assetURL(asset, in: signed.index), mode: state.routeMode, preferred: preferredRoute) {
            do {
                let data = try await small(url, maximum: 64 * 1024, expected: asset.bytes)
                guard DesktopUpdatePolicy.digest(data) == asset.sha256 else { throw UpdateTransferError.failure("更新源与签名索引摘要不一致。") }
                try Task.checkCancellation(); preferredRoute = route.id
                return data
            } catch { try Task.checkCancellation(); failure = error }
        }
        throw failure
    }
    func streamArchive(_ signed: SignedMacUpdateIndex, feed: Data, replace: Bool,
                       send: @escaping @Sendable (Data) async throws -> Void) async throws {
        try await perform {
            try await updateDeadline(15 * 60) { try await self.streamBody(signed, feed: feed, replace: replace, send: send) }
        }
    }
    private func sendFile(_ name: String, limit: Int64, send: @Sendable (Data) async throws -> Void) async throws {
        let handle = try FileHandle(forReadingFrom: file(name)); defer { try? handle.close() }
        var sent: Int64 = 0
        while sent < limit {
            try Task.checkCancellation()
            let data = try handle.read(upToCount: Int(min(64 * 1024, limit - sent))) ?? Data()
            guard !data.isEmpty else { throw UpdateTransferError.failure("缓存前缀不完整。") }
            do { try await send(data) } catch { throw CancellationError() }
            sent += Int64(data.count)
        }
    }
    private func streamBody(_ signed: SignedMacUpdateIndex, feed: Data, replace: Bool,
                            send: @escaping @Sendable (Data) async throws -> Void) async throws {
        let asset = signed.index.archive
        guard AppVersion(signed.index.version)! > AppVersion(currentVersion)!, UInt64(signed.index.build)! > (UInt64(currentBuild) ?? 0) else {
            throw UpdateTransferError.failure("不安装相同或更低版本。")
        }
        try safeDirectory()
        if let cached, cached.index.identity != signed.index.identity {
            guard replace else { throw UpdateTransferError.failure("请确认清除旧版断点后再下载新版本。") }
            try removePayload()
        }
        cached = signed; selected = signed
        try JSONEncoder().encode(UpdateCacheRecord(schema: 1, index: signed.data, signature: signed.signature)).write(to: file("transfer.json"), options: .atomic)
        guard DesktopUpdatePolicy.digest(feed) == signed.index.appcast.sha256 else { throw UpdateTransferError.failure("更新源摘要不一致。") }
        try feed.write(to: file("appcast.xml"), options: .atomic)
        publish { $0.status = "downloading"; $0.version = signed.index.version; $0.notes = signed.index.notes; $0.totalBytes = asset.bytes
            $0.hasCachedDownload = true; $0.cachedIdentity = signed.index.identity; $0.canResume = false; $0.speedBytesPerSecond = 0 }
        do {
            if try size("archive.zip", maximum: asset.bytes) == asset.bytes, try digest(file("archive.zip"), bytes: asset.bytes) == asset.sha256 {
                try DownloadQuarantine.ensure(file("archive.zip"))
                publish { $0.downloadedBytes = asset.bytes; $0.activeRouteLabel = "已验证本地缓存" }
                try await sendFile("archive.zip", limit: asset.bytes, send: send)
                publish { $0.status = "verifying"; $0.message = "Sparkle 正在验证并准备安装。" }; return
            }
            let candidates = DesktopUpdatePolicy.assetCandidates(DesktopUpdatePolicy.assetURL(asset, in: signed.index), mode: state.routeMode, preferred: preferredRoute)
            var delivered: Int64 = 0
            var lastFailure: any Error = UpdateTransferError.failure("安装包未完整下载。")
            for (routeNumber, candidate) in candidates.enumerated() {
                try Task.checkCancellation()
                let (route, url) = candidate
                var offset = try size("archive.partial", maximum: asset.bytes) ?? 0
                if offset == asset.bytes {
                    if try digest(file("archive.partial"), bytes: asset.bytes) == asset.sha256 {
                        if try size("archive.zip", maximum: asset.bytes) != nil { try FileManager.default.removeItem(at: file("archive.zip")) }
                        try FileManager.default.moveItem(at: file("archive.partial"), to: file("archive.zip"))
                        try DownloadQuarantine.ensure(file("archive.zip"))
                        try await sendFile("archive.zip", limit: asset.bytes, send: send)
                        publish { $0.status = "verifying"; $0.downloadedBytes = asset.bytes; $0.message = "Sparkle 正在验证并准备安装。" }; return
                    }
                    try FileManager.default.removeItem(at: file("archive.partial")); offset = 0
                }
                publish { $0.activeRouteLabel = route.label; $0.downloadedBytes = offset; $0.status = "downloading"
                    $0.message = offset > 0 ? "正在通过 \(route.label) 断点续传…" : "正在通过 \(route.label) 下载更新…" }
                do {
                    let response = try await open(url, offset); defer { response.cancel() }
                    let start = try DesktopUpdatePolicy.effectiveOffset(status: response.status, length: response.length, range: response.range, offset: offset, total: asset.bytes)
                    // 已交给 Sparkle 的前缀不能重复发送；不支持 Range 的后继线路留待新会话安全重下。
                    guard start != 0 || delivered == 0 else { throw UpdateTransferError.failure("此线路不支持续传，正在尝试其他线路。") }
                    if offset > 0 && start == 0 { publish { $0.downloadedBytes = 0; $0.message = "此线路不支持续传，正在安全重新下载。" } }
                    if try size("archive.partial", maximum: asset.bytes) == nil { FileManager.default.createFile(atPath: file("archive.partial").path, contents: nil) }
                    if delivered == 0 && start > 0 { try await sendFile("archive.partial", limit: start, send: send); delivered = start }
                    let handle = try FileHandle(forWritingTo: file("archive.partial"))
                    if start == 0 { try handle.truncate(atOffset: 0) } else { try handle.seekToEnd() }
                    var count = start; var tail = Data(); var speed: Int64 = 0; var windowBytes: Int64 = 0
                    let began = ProcessInfo.processInfo.systemUptime; var windowStart = began; var lastPublish = began; var lowSince: Double?
                    do {
                        for try await chunk in response.chunks {
                            defer { chunk.consumed() }; try Task.checkCancellation()
                            count += Int64(chunk.data.count); windowBytes += Int64(chunk.data.count)
                            guard count <= asset.bytes else { throw UpdateTransferError.failure("安装包超过签名声明大小。") }
                            try handle.write(contentsOf: chunk.data)
                            var outgoing = chunk.data
                            if count == asset.bytes { tail = Data(outgoing.suffix(1)); outgoing.removeLast() }
                            if !outgoing.isEmpty { do { try await send(outgoing) } catch { throw CancellationError() }; delivered += Int64(outgoing.count) }
                            let now = ProcessInfo.processInfo.systemUptime
                            if now - windowStart >= 1 {
                                speed = Int64(Double(windowBytes) / max(0.001, now - windowStart)); windowBytes = 0; windowStart = now
                                if now - began >= 3, routeNumber < candidates.count - 1 {
                                    if speed < 40 * 1024 {
                                        if let lowSince, now - lowSince >= 8 { throw UpdateTransferError.failure("当前线路持续低速，正在换线续传。") }
                                        if lowSince == nil { lowSince = now }
                                    } else { lowSince = nil }
                                }
                            }
                            if now - lastPublish >= 0.2 { publish { $0.downloadedBytes = count; $0.speedBytesPerSecond = speed }; lastPublish = now }
                        }
                        try Task.checkCancellation()
                        guard count == asset.bytes else { throw UpdateTransferError.failure("下载中断，断点已保留。") }
                        try handle.synchronize(); try handle.close()
                    } catch { try? handle.synchronize(); try? handle.close(); throw error }
                    publish { $0.status = "verifying"; $0.speedBytesPerSecond = 0; $0.message = "正在验证完整安装包…" }
                    guard try digest(file("archive.partial"), bytes: asset.bytes) == asset.sha256 else {
                        try FileManager.default.removeItem(at: file("archive.partial")); throw UpdateTransferError.failure("安装包摘要无效，已移除损坏内容。")
                    }
                    if try size("archive.zip", maximum: asset.bytes) != nil { try FileManager.default.removeItem(at: file("archive.zip")) }
                    try FileManager.default.moveItem(at: file("archive.partial"), to: file("archive.zip"))
                    try DownloadQuarantine.ensure(file("archive.zip"))
                    if !tail.isEmpty { do { try await send(tail) } catch { throw CancellationError() } }
                    publish { $0.status = "verifying"; $0.downloadedBytes = asset.bytes; $0.message = "下载完成，Sparkle 正在验证并准备安装。" }
                    return
                } catch is CancellationError { throw CancellationError() }
                catch {
                    try Task.checkCancellation(); lastFailure = error
                    if delivered >= asset.bytes - 1 { throw error }
                }
            }
            throw lastFailure
        } catch {
            let paused = error is CancellationError || Task.isCancelled
            let actual = (try? size("archive.partial", maximum: asset.bytes)) ?? 0
            publish { $0.status = paused ? "paused" : "error"; $0.canResume = true; $0.speedBytesPerSecond = 0
                $0.downloadedBytes = actual; $0.message = paused ? "已暂停，断点会保留到下次手动继续。" : error.localizedDescription }
            throw error
        }
    }
}

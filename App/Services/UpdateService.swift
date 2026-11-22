import AppKit
import Foundation
import Observation
import Sparkle

enum AppUpdatePhase: Equatable {
    case idle
    case checking
    case current
    case available(String)
    case downloading
    case verifying
    case ready
    case installing
    case deferred
    case failed(String)
}

enum UpdateInstallationError: LocalizedError {
    case unsavedProgress
    case invalidConfiguration
    case invalidRelease
    case manualChecksOnly

    var errorDescription: String? {
        switch self {
        case .unsavedProgress:
            "学习记录尚未成功读取或保存。请先处理设置中的保存错误，再安装更新；现有文件不会被重置。"
        case .invalidConfiguration:
            "更新配置不完整，请从官方 GitHub Release 重新安装完整版本。"
        case .invalidRelease:
            "更新信息与官方 macOS 安装包不一致，已停止更新。"
        case .manualChecksOnly:
            "仅允许你主动检查和安装更新。"
        }
    }
}

enum UpdateInstallationGuard {
    @MainActor
    static func saveProgress(_ progress: ProgressStore) throws {
        // 读取失败时内存中可能是空记录，不得将其覆盖到原文件。
        guard progress.lastPersistenceError == nil else {
            throw UpdateInstallationError.unsavedProgress
        }
        try progress.save()
    }
}

struct SparkleReleaseCandidate: Sendable {
    let version: String
    let build: String
    let archiveURL: URL?
    let bytes: UInt64
    let pageURL: URL?
    let releaseNotesURL: URL?

    func validate(
        currentVersion: String, currentBuild: String,
        archiveOrigin: String = "https://github.com/xyy386/MathInterviewVocab"
    ) throws {
        guard let version = AppVersion(version), let current = AppVersion(currentVersion), version > current,
              let buildNumber = UInt64(build), String(buildNumber) == build,
              let currentBuildNumber = UInt64(currentBuild), buildNumber > currentBuildNumber,
              bytes > 0, bytes <= UInt64(AppUpdatePolicy.maximumArchiveBytes) else {
            throw UpdateInstallationError.invalidRelease
        }
        let page = "https://github.com/xyy386/MathInterviewVocab/releases/tag/v\(version)"
        let archive = "\(archiveOrigin)/releases/download/v\(version)/MathInterviewVocab-macOS-universal-v\(version).zip"
        guard archiveURL?.absoluteString == archive,
              pageURL == nil || pageURL?.absoluteString == page,
              releaseNotesURL == nil || releaseNotesURL?.absoluteString == page else {
            throw UpdateInstallationError.invalidRelease
        }
    }
}

@MainActor
@Observable
final class UpdateService: NSObject, SPUUpdaterDelegate {
    static let feedURL = "https://raw.githubusercontent.com/xyy386/MathInterviewVocab/main/updates/macos/appcast.xml"
    static let publicKey = "IBdRtdCy/pTzCtxPyODNo0saDZ5zbprza4wRUJ5O1DU="

    let currentVersion: String
    let currentBuild: String
    private(set) var phase = AppUpdatePhase.idle
    private(set) var hasPendingInstallation = false
    private(set) var transfer = MacTransferState()
    private(set) var nativeSessionActive = false

    @ObservationIgnored private let prepareForInstallation: @MainActor () throws -> Void
    @ObservationIgnored private var controller: SPUUpdater?
    @ObservationIgnored private var nativeDriver: NativeUpdateUserDriver?
    @ObservationIgnored private var resumeInstallation: (() -> Void)?
    @ObservationIgnored private var downloadEngine: UpdateDownloadEngine?
    @ObservationIgnored private let relay = UpdateRelay()
    @ObservationIgnored private var relayFeedURL: URL?
    @ObservationIgnored private var relayArchiveURL: URL?
    @ObservationIgnored private var activeIndex: SignedMacUpdateIndex?
    @ObservationIgnored private var preparingTask: Task<Void, Never>?
    @ObservationIgnored private var lastSequence: UInt64 = 0
    @ObservationIgnored private var pausedByUser = false
    @ObservationIgnored private var needsReplacement = false
    @ObservationIgnored private var replaceForSession = false
    @ObservationIgnored private let cacheDirectory: URL
    @ObservationIgnored private let networkOpen: UpdateNetworkOpen?
    private(set) var preparing = false

    init(
        currentVersion: String = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "未知",
        currentBuild: String = Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "0",
        cacheDirectory: URL? = nil,
        networkOpen: UpdateNetworkOpen? = nil,
        prepareForInstallation: @escaping @MainActor () throws -> Void
    ) {
        self.currentVersion = currentVersion
        self.currentBuild = currentBuild
        self.cacheDirectory = cacheDirectory ?? FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("com.xingyiyao.MathInterviewVocab/Updates", isDirectory: true)
        self.networkOpen = networkOpen
        self.prepareForInstallation = prepareForInstallation
        super.init()
        // 不在初始化或启动时创建、启动更新器，测试宿主和离线学习均不会访问网络。
    }

    var isBusy: Bool { preparing || nativeSessionActive }
    var hasActiveTransfer: Bool { preparing || ["downloading", "checking", "verifying"].contains(transfer.status) }
    var canCheckForUpdates: Bool {
        !isBusy && !hasPendingInstallation && (controller?.canCheckForUpdates ?? true)
    }
    var canRetryInstallation: Bool { resumeInstallation != nil }

    @discardableResult
    func showExistingUpdateWindow() -> Bool {
        guard controller?.sessionInProgress == true else { return false }
        nativeDriver?.showUpdateInFocus(); return true
    }

    func checkForUpdates() {
        if showExistingUpdateWindow() { return }
        guard canCheckForUpdates else { return }
        // 既有 DEBUG 回归夹具仍可直接走原生 Sparkle，不进入生产线路配置。
        if Self.fixtureFeedURL(in: Bundle.main.infoDictionary ?? [:]) != nil {
            do { try startNativeUpdater() } catch { phase = .failed(error.localizedDescription) }
            return
        }
        prepareSession(resume: false)
    }

    private func engine() -> UpdateDownloadEngine {
        if let downloadEngine { return downloadEngine }
        let callback: @Sendable (MacTransferState) -> Void = { [weak self] value in
            Task { @MainActor [weak self] in self?.accept(value) }
        }
        let engine = UpdateDownloadEngine(currentVersion: currentVersion, currentBuild: currentBuild,
            directory: cacheDirectory, open: networkOpen ?? { try await UpdateNetwork.open($0, offset: $1) }, changed: callback)
        downloadEngine = engine
        return engine
    }
    private func accept(_ value: MacTransferState) {
        guard value.sequence > lastSequence else { return }; lastSequence = value.sequence
        transfer = value
        if phase == .ready && value.status == "verifying" { transfer.status = "ready" }
    }
    func restoreCache() async {
        let engine = engine(); await engine.restore(); accept(await engine.snapshot())
    }
    func setRouteMode(_ mode: UpdateRouteMode) { guard !isBusy else { return }; Task { await engine().setRouteMode(mode) } }
    func resumeDownload() {
        if controller?.sessionInProgress == true { nativeDriver?.showUpdateInFocus(); return }
        guard !preparing, !hasPendingInstallation else { return }; prepareSession(resume: true)
    }
    func pauseDownload() {
        pausedByUser = true
        nativeDriver?.downloadCancellation?()
        preparingTask?.cancel()
        Task { await engine().pause(); await relay.stop() }
    }
    func pauseForQuit() async {
        pausedByUser = true; nativeDriver?.downloadCancellation?(); preparingTask?.cancel()
        await engine().pause(); await relay.stop()
    }
    func clearDownloadCache() {
        guard !isBusy else { return }
        let alert = NSAlert(); alert.messageText = "清除更新下载缓存？"
        alert.informativeText = "只删除更新安装包和下载断点，不影响学习记录、收藏、错题或设置。"
        alert.addButton(withTitle: "保留缓存"); alert.addButton(withTitle: "清除下载缓存")
        guard alert.runModal() == .alertSecondButtonReturn else { return }
        Task { await engine().clearCache() }
    }
    private func prepareSession(resume: Bool) {
        preparing = true; pausedByUser = false; replaceForSession = false; phase = .checking
        preparingTask = Task { [weak self] in
            guard let self else { return }
            do {
                try validateConfiguration(Bundle.main.infoDictionary ?? [:])
                let engine = engine()
                let cached = await engine.cachedIndex()
                let (signed, feed) = try await updateDeadline(45) {
                    let signed: SignedMacUpdateIndex
                    if resume, let cached { signed = cached } else { signed = try await engine.check() }
                    return (signed, try await engine.feed(for: signed))
                }
                try Task.checkCancellation()
                activeIndex = signed
                needsReplacement = cached != nil && cached?.index.identity != signed.index.identity
                relayFeedURL = try await relay.start(feed: feed, archive: signed.index.archive) { [weak self] send in
                    let replace = await MainActor.run { self?.replaceForSession ?? false }
                    try await engine.streamArchive(signed, feed: feed, replace: replace, send: send)
                }
                relayArchiveURL = await relay.archiveURL()
                try Task.checkCancellation()
                preparing = false; preparingTask = nil
                try startNativeUpdater()
            } catch {
                preparing = false; preparingTask = nil
                phase = pausedByUser ? .deferred : .failed(error.localizedDescription)
                if !pausedByUser { transfer.status = "error"; transfer.message = error.localizedDescription }
                await relay.stop()
            }
        }
    }
    private func startNativeUpdater() throws {
        do {
            try validateConfiguration(Bundle.main.infoDictionary ?? [:])
            if controller == nil {
                let driver = NativeUpdateUserDriver(hostBundle: .main, delegate: nil)
                let controller = SPUUpdater(hostBundle: .main, applicationBundle: .main, userDriver: driver, delegate: self)
                // 覆盖历史偏好；不提供后台检查、自动下载或系统信息上报开关。
                controller.automaticallyChecksForUpdates = false
                controller.automaticallyDownloadsUpdates = false
                controller.sendsSystemProfile = false
                try controller.start()
                self.controller = controller
                self.nativeDriver = driver
            }
            phase = .checking
            nativeSessionActive = true
            controller?.checkForUpdates()
        } catch {
            nativeSessionActive = false
            phase = .failed(error.localizedDescription)
            throw error
        }
    }

    func feedURLString(for updater: SPUUpdater) -> String? { relayFeedURL?.absoluteString }

    func retryInstallation() {
        guard let resumeInstallation else { return }
        do {
            try prepareForInstallation()
            self.resumeInstallation = nil
            phase = .installing
            resumeInstallation()
        } catch {
            phase = .failed(error.localizedDescription)
        }
    }

    // Sparkle 的延迟重启回调并非每次都执行，最终退出还必须经过应用代理的保存门禁。
    func prepareForUpdateTermination() -> Bool {
        guard hasPendingInstallation else { return true }
        do {
            try prepareForInstallation()
            phase = .installing
            return true
        } catch {
            phase = .failed(error.localizedDescription)
            return false
        }
    }

    func validateConfiguration(_ information: [String: Any]) throws {
        let expectedFeed = Self.fixtureFeedURL(in: information)?.absoluteString ?? Self.feedURL
        guard information["SUFeedURL"] as? String == expectedFeed,
              information["SUPublicEDKey"] as? String == Self.publicKey,
              information["SUEnableAutomaticChecks"] as? Bool == false,
              information["SUAutomaticallyUpdate"] as? Bool == false,
              information["SUAllowsAutomaticUpdates"] as? Bool == false,
              information["SUEnableSystemProfiling"] as? Bool == false,
              information["SURequireSignedFeed"] as? Bool == true,
              information["SUVerifyUpdateBeforeExtraction"] as? Bool == true else {
            throw UpdateInstallationError.invalidConfiguration
        }
    }

    func updater(_ updater: SPUUpdater, mayPerform updateCheck: SPUUpdateCheck) throws {
        guard updateCheck == .updates else { throw UpdateInstallationError.manualChecksOnly }
    }

    func allowedSystemProfileKeys(for updater: SPUUpdater) -> [String]? { [] }
    func updaterShouldPromptForPermissionToCheck(forUpdates updater: SPUUpdater) -> Bool { false }

    func updater(_ updater: SPUUpdater, shouldProceedWithUpdate item: SUAppcastItem, updateCheck: SPUUpdateCheck) throws {
        if let index = activeIndex?.index {
            guard item.displayVersionString == index.version, item.versionString == index.build,
                  item.contentLength == UInt64(index.archive.bytes),
                  item.fileURL == DesktopUpdatePolicy.assetURL(index.archive, in: index) else {
                throw UpdateInstallationError.invalidRelease
            }
        }
        let fixtureFeed = Self.fixtureFeedURL(in: Bundle.main.infoDictionary ?? [:])
        let archiveOrigin = fixtureFeed.flatMap { url -> String? in
            guard let port = url.port else { return nil }
            return "http://127.0.0.1:\(port)"
        } ?? "https://github.com/xyy386/MathInterviewVocab"
        try SparkleReleaseCandidate(
            version: item.displayVersionString,
            build: item.versionString,
            archiveURL: item.fileURL,
            bytes: item.contentLength,
            pageURL: item.infoURL,
            releaseNotesURL: item.releaseNotesURL
        ).validate(currentVersion: currentVersion, currentBuild: currentBuild, archiveOrigin: archiveOrigin)
    }

    static func fixtureFeedURL(in information: [String: Any]) -> URL? {
#if DEBUG
        guard information["CFBundleIdentifier"] as? String == "com.xingyiyao.MathInterviewVocab.UpdaterFixture",
              let text = information["SUFeedURL"] as? String, let url = URL(string: text),
              url.scheme == "http", url.host == "127.0.0.1", url.port != nil,
              url.path == "/appcast.xml", url.query == nil, url.fragment == nil,
              url.user == nil, url.password == nil else { return nil }
        return url
#else
        return nil
#endif
    }

    static func isValidationFixture(_ information: [String: Any]) -> Bool {
#if DEBUG
        guard let identifier = information["CFBundleIdentifier"] as? String else { return false }
        let prefix = "com.xingyiyao.MathInterviewVocab.UpdaterFixture"
        if identifier == prefix { return true }
        guard let token = information["MathVocabFixtureToken"] as? String, UUID(uuidString: token) != nil else { return false }
        return identifier == prefix + "." + token.lowercased()
#else
        return false
#endif
    }
    static func validationFixtureOrigin(in information: [String: Any]) -> URL? {
#if DEBUG
        guard isValidationFixture(information), let text = information["MathVocabFixtureOrigin"] as? String,
              let url = URL(string: text), url.scheme == "http", url.host == "127.0.0.1", let port = url.port, port >= 1024,
              url.path.isEmpty, url.user == nil, url.password == nil, url.query == nil, url.fragment == nil else { return nil }
        return url
#else
        return nil
#endif
    }

    func updater(_ updater: SPUUpdater, didFindValidUpdate item: SUAppcastItem) {
        nativeSessionActive = true
        phase = .available(item.displayVersionString)
    }

    func updaterDidNotFindUpdate(_ updater: SPUUpdater, error: any Error) {
        phase = .current
    }

    func updater(_ updater: SPUUpdater, willDownloadUpdate item: SUAppcastItem, with request: NSMutableURLRequest) {
        if let relayArchiveURL {
            if needsReplacement {
                let alert = NSAlert(); alert.messageText = "替换旧的下载断点？"
                alert.informativeText = "下载 \(item.displayVersionString) 将清除旧版更新缓存，不影响学习记录和设置。"
                alert.addButton(withTitle: "保留旧断点"); alert.addButton(withTitle: "下载新版")
                if alert.runModal() != .alertSecondButtonReturn {
                    pausedByUser = true
                    request.url = relayArchiveURL.appendingPathComponent("cancelled")
                    Task { @MainActor in self.nativeDriver?.downloadCancellation?() }
                    return
                }
                replaceForSession = true
            }
            request.url = relayArchiveURL
        }
        phase = .downloading
    }

    func updater(_ updater: SPUUpdater, willExtractUpdate item: SUAppcastItem) {
        phase = .verifying
    }

    func updater(_ updater: SPUUpdater, didExtractUpdate item: SUAppcastItem) {
        phase = .ready
        transfer.status = "ready"; transfer.downloadedBytes = transfer.totalBytes; transfer.canResume = false
        transfer.message = "Sparkle 校验通过，请在原生更新窗口确认安装并重启。"
        Task { await engine().nativeReady() }
    }

    func updater(_ updater: SPUUpdater, willInstallUpdate item: SUAppcastItem) {
        hasPendingInstallation = true
    }

    func updater(_ updater: SPUUpdater, shouldPostponeRelaunchForUpdate item: SUAppcastItem, untilInvokingBlock installHandler: @escaping () -> Void) -> Bool {
        hasPendingInstallation = true
        do {
            try prepareForInstallation()
            phase = .installing
            return false
        } catch {
            resumeInstallation = installHandler
            phase = .failed(error.localizedDescription)
            return true
        }
    }

    func updaterWillRelaunchApplication(_ updater: SPUUpdater) {
        hasPendingInstallation = true
    }

    func userDidCancelDownload(_ updater: SPUUpdater) {
        pausedByUser = true
        phase = .deferred
        Task { await engine().pause(); await relay.stop() }
    }

    func updater(_ updater: SPUUpdater, didAbortWithError error: any Error) {
        hasPendingInstallation = false
        resumeInstallation = nil
        let error = error as NSError
        if error.domain == SUSparkleErrorDomain && error.code == SUError.noUpdateError.rawValue {
            pausedByUser = false
            phase = .current
        } else if pausedByUser {
            phase = .deferred
        } else {
            phase = .failed(error.localizedDescription)
            transfer.status = "error"; transfer.message = error.localizedDescription
        }
    }

    func updater(_ updater: SPUUpdater, didFinishUpdateCycleFor updateCheck: SPUUpdateCheck, error: (any Error)?) {
        nativeSessionActive = false
        nativeDriver?.downloadCancellation = nil
        Task { await relay.stop() }
        if let error {
            self.updater(updater, didAbortWithError: error)
        } else if phase != .installing {
            hasPendingInstallation = false
            if phase != .current { phase = .deferred }
        }
    }
}

@MainActor
final class UpdateAppDelegate: NSObject, NSApplicationDelegate {
    weak var updates: UpdateService?
    private var waitingToQuit = false

    func applicationShouldTerminate(_ sender: NSApplication) -> NSApplication.TerminateReply {
        if let updates, !updates.hasPendingInstallation, updates.hasActiveTransfer {
            if !waitingToQuit {
                waitingToQuit = true
                Task { await updates.pauseForQuit(); sender.reply(toApplicationShouldTerminate: true) }
            }
            return .terminateLater
        }
        guard updates?.prepareForUpdateTermination() != false else {
            let alert = NSAlert()
            alert.messageText = "未安装更新，学习记录尚未安全保存"
            alert.informativeText = "请处理设置中的保存错误后重试安装。应用和现有学习记录均已保留。"
            alert.addButton(withTitle: "返回应用")
            alert.runModal()
            return .terminateCancel
        }
        return .terminateNow
    }
}

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

    @ObservationIgnored private let prepareForInstallation: @MainActor () throws -> Void
    @ObservationIgnored private var controller: SPUStandardUpdaterController?
    @ObservationIgnored private var resumeInstallation: (() -> Void)?

    init(
        currentVersion: String = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "未知",
        currentBuild: String = Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "0",
        prepareForInstallation: @escaping @MainActor () throws -> Void
    ) {
        self.currentVersion = currentVersion
        self.currentBuild = currentBuild
        self.prepareForInstallation = prepareForInstallation
        super.init()
        // 不在初始化或启动时创建、启动更新器，测试宿主和离线学习均不会访问网络。
    }

    var isBusy: Bool { controller?.updater.sessionInProgress == true }
    var canCheckForUpdates: Bool {
        !hasPendingInstallation && (controller?.updater.canCheckForUpdates ?? true)
    }
    var canRetryInstallation: Bool { resumeInstallation != nil }

    func checkForUpdates() {
        guard canCheckForUpdates else { return }
        if let controller, controller.updater.sessionInProgress {
            // 原生 API 会将现有下载／安装窗口带到前台，而不是发起第二次检查。
            controller.checkForUpdates(nil)
            return
        }
        do {
            try validateConfiguration(Bundle.main.infoDictionary ?? [:])
            if controller == nil {
                let controller = SPUStandardUpdaterController(
                    startingUpdater: false, updaterDelegate: self, userDriverDelegate: nil
                )
                // 覆盖历史偏好；不提供后台检查、自动下载或系统信息上报开关。
                controller.updater.automaticallyChecksForUpdates = false
                controller.updater.automaticallyDownloadsUpdates = false
                controller.updater.sendsSystemProfile = false
                try controller.updater.start()
                self.controller = controller
            }
            phase = .checking
            controller?.checkForUpdates(nil)
        } catch {
            phase = .failed(error.localizedDescription)
        }
    }

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

    func updater(_ updater: SPUUpdater, didFindValidUpdate item: SUAppcastItem) {
        phase = .available(item.displayVersionString)
    }

    func updaterDidNotFindUpdate(_ updater: SPUUpdater, error: any Error) {
        phase = .current
    }

    func updater(_ updater: SPUUpdater, willDownloadUpdate item: SUAppcastItem, with request: NSMutableURLRequest) {
        phase = .downloading
    }

    func updater(_ updater: SPUUpdater, willExtractUpdate item: SUAppcastItem) {
        phase = .verifying
    }

    func updater(_ updater: SPUUpdater, didExtractUpdate item: SUAppcastItem) {
        phase = .ready
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
        phase = .deferred
    }

    func updater(_ updater: SPUUpdater, didAbortWithError error: any Error) {
        hasPendingInstallation = false
        resumeInstallation = nil
        let error = error as NSError
        if error.domain == SUSparkleErrorDomain && error.code == SUError.noUpdateError.rawValue {
            phase = .current
        } else {
            phase = .failed(error.localizedDescription)
        }
    }

    func updater(_ updater: SPUUpdater, didFinishUpdateCycleFor updateCheck: SPUUpdateCheck, error: (any Error)?) {
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

    func applicationShouldTerminate(_ sender: NSApplication) -> NSApplication.TerminateReply {
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

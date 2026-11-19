import Foundation
import Sparkle
import Testing
@testable import MathInterviewVocab

@MainActor
struct SparkleUpdateTests {
    @Test func constructionDoesNotStartAnUpdateOrSaveRecords() {
        var saves = 0
        let service = UpdateService(currentVersion: "1.4.0", currentBuild: "6") { saves += 1 }
        #expect(service.phase == .idle)
        #expect(!service.isBusy)
        #expect(service.canCheckForUpdates)
        #expect(!service.hasPendingInstallation)
        #expect(!service.canRetryInstallation)
        #expect(service.prepareForUpdateTermination())
        #expect(saves == 0)
    }

    @Test func shippedConfigurationRequiresSignedManualUpdates() throws {
        let service = UpdateService(prepareForInstallation: {})
        try service.validateConfiguration(Bundle.main.infoDictionary ?? [:])
        let key = try #require(Data(base64Encoded: UpdateService.publicKey))
        #expect(key.count == 32)
    }

    @Test(arguments: [
        "SUFeedURL", "SUPublicEDKey", "SUEnableAutomaticChecks", "SUAutomaticallyUpdate",
        "SUAllowsAutomaticUpdates", "SUEnableSystemProfiling", "SURequireSignedFeed", "SUVerifyUpdateBeforeExtraction"
    ])
    func incompleteOrWeakenedConfigurationFailsClosed(_ key: String) {
        let service = UpdateService(prepareForInstallation: {})
        var information = configuration
        information.removeValue(forKey: key)
        #expect(throws: (any Error).self) { try service.validateConfiguration(information) }
        information = configuration
        if key == "SUFeedURL" || key == "SUPublicEDKey" {
            information[key] = "https://example.invalid/forged"
        } else {
            information[key] = !(configuration[key] as? Bool ?? false)
        }
        #expect(throws: (any Error).self) { try service.validateConfiguration(information) }
    }

    @Test func officialArchiveMustBindVersionBuildSizeAndRepository() throws {
        try candidate().validate(currentVersion: "1.4.0", currentBuild: "6")
    }

    @Test(arguments: [
        "wrongPlatform", "wrongRepository", "http", "query", "fragment", "traversal", "wrongVersionURL",
        "downgrade", "sameVersion", "sameBuild", "oldBuild", "malformedBuild", "zeroBytes", "oversize",
        "externalPage", "externalNotes", "missingArchive"
    ])
    func untrustedOrNonIncreasingCandidatesAreRejected(_ mutation: String) {
        let original = candidate()
        var version = original.version
        var build = original.build
        var archiveURL = original.archiveURL
        var bytes = original.bytes
        var pageURL = original.pageURL
        var releaseNotesURL = original.releaseNotesURL
        switch mutation {
        case "wrongPlatform": archiveURL = URL(string: archiveURL!.absoluteString.replacingOccurrences(of: "macOS-universal", with: "Windows-x64"))
        case "wrongRepository": archiveURL = URL(string: archiveURL!.absoluteString.replacingOccurrences(of: "/xyy386/", with: "/other/"))
        case "http": archiveURL = URL(string: archiveURL!.absoluteString.replacingOccurrences(of: "https:", with: "http:"))
        case "query": archiveURL = URL(string: archiveURL!.absoluteString + "?redirect=1")
        case "fragment": archiveURL = URL(string: archiveURL!.absoluteString + "#download")
        case "traversal": archiveURL = URL(string: archiveURL!.absoluteString.replacingOccurrences(of: "/download/", with: "/download/../"))
        case "wrongVersionURL": archiveURL = URL(string: archiveURL!.absoluteString.replacingOccurrences(of: "1.5.0", with: "1.6.0"))
        case "downgrade": version = "1.3.0"
        case "sameVersion": version = "1.4.0"
        case "sameBuild": build = "6"
        case "oldBuild": build = "5"
        case "malformedBuild": build = "07"
        case "zeroBytes": bytes = 0
        case "oversize": bytes = UInt64(AppUpdatePolicy.maximumArchiveBytes) + 1
        case "externalPage": pageURL = URL(string: "https://example.invalid/notes")
        case "externalNotes": releaseNotesURL = URL(string: "https://example.invalid/notes")
        case "missingArchive": archiveURL = nil
        default: break
        }
        let candidate = SparkleReleaseCandidate(
            version: version, build: build, archiveURL: archiveURL, bytes: bytes,
            pageURL: pageURL, releaseNotesURL: releaseNotesURL
        )
        #expect(throws: (any Error).self) {
            try candidate.validate(currentVersion: "1.4.0", currentBuild: "6")
        }
    }

    @Test func nativeDelegateRejectsBackgroundChecksAndProfiles() throws {
        let service = UpdateService(prepareForInstallation: {})
        let controller = SPUStandardUpdaterController(startingUpdater: false, updaterDelegate: service, userDriverDelegate: nil)
        let updater = controller.updater
        try service.updater(updater, mayPerform: .updates)
        #expect(throws: (any Error).self) { try service.updater(updater, mayPerform: .updatesInBackground) }
        #expect(throws: (any Error).self) { try service.updater(updater, mayPerform: .updateInformation) }
        #expect(service.allowedSystemProfileKeys(for: updater) == [])
        #expect(!service.updaterShouldPromptForPermissionToCheck(forUpdates: updater))
        #expect(service.phase == .idle)
    }

    @Test func finalTerminationVetoCannotBeSkippedWithTheRelaunchCallback() {
        let probe = InstallationProbe()
        let service = UpdateService(currentVersion: "1.4.0", currentBuild: "6") {
            guard probe.maySave else { throw UpdateInstallationError.unsavedProgress }
            probe.saves += 1
        }
        let controller = SPUStandardUpdaterController(startingUpdater: false, updaterDelegate: service, userDriverDelegate: nil)
        service.updaterWillRelaunchApplication(controller.updater)
        #expect(service.hasPendingInstallation)
        #expect(!service.prepareForUpdateTermination())
        #expect(probe.saves == 0)
        probe.maySave = true
        #expect(service.prepareForUpdateTermination())
        #expect(probe.saves == 1)
        #expect(service.phase == .installing)
    }

    @Test func failedPersistenceDoesNotOverwriteAnUnreadableOriginal() throws {
        try withDirectory { directory in
            let file = directory.appendingPathComponent("progress.json")
            let original = Data("not valid JSON, preserve this original".utf8)
            try original.write(to: file)
            let progress = ProgressStore(fileURL: file)
            #expect(progress.lastPersistenceError != nil)
            #expect(throws: (any Error).self) { try UpdateInstallationGuard.saveProgress(progress) }
            #expect(try Data(contentsOf: file) == original)
        }
    }

    @Test func postponedInstallCanRetryOnlyAfterSavingAndResumesOnlyOnce() {
        let probe = InstallationProbe()
        let service = UpdateService(prepareForInstallation: {
            guard probe.maySave else { throw UpdateInstallationError.unsavedProgress }
            probe.saves += 1
        })
        let controller = SPUStandardUpdaterController(startingUpdater: false, updaterDelegate: service, userDriverDelegate: nil)
        let postpone = service.updater(
            controller.updater, shouldPostponeRelaunchForUpdate: SUAppcastItem.empty(),
            untilInvokingBlock: { probe.resumes += 1 }
        )
        #expect(postpone)
        #expect(service.hasPendingInstallation)
        #expect(service.canRetryInstallation)
        service.retryInstallation()
        #expect(probe.resumes == 0)
        probe.maySave = true
        service.retryInstallation()
        service.retryInstallation()
        #expect(probe.saves == 1)
        #expect(probe.resumes == 1)
        #expect(!service.canRetryInstallation)
    }

    @Test func cancellationOrFailureClearsPendingInstallAndCannotReportSuccess() {
        let service = UpdateService(prepareForInstallation: {})
        let controller = SPUStandardUpdaterController(startingUpdater: false, updaterDelegate: service, userDriverDelegate: nil)
        service.updaterWillRelaunchApplication(controller.updater)
        #expect(!service.canCheckForUpdates)
        let previousPhase = service.phase
        service.checkForUpdates()
        #expect(service.phase == previousPhase)
        let failure = NSError(domain: NSURLErrorDomain, code: URLError.notConnectedToInternet.rawValue)
        service.updater(controller.updater, didAbortWithError: failure)
        #expect(!service.hasPendingInstallation)
        #expect(!service.canRetryInstallation)
        #expect(service.canCheckForUpdates)
        guard case .failed = service.phase else { Issue.record("失败不能报告已安装"); return }
        service.userDidCancelDownload(controller.updater)
        #expect(service.phase == .deferred)
        let noUpdate = NSError(domain: SUSparkleErrorDomain, code: Int(SUError.noUpdateError.rawValue))
        service.updater(controller.updater, didFinishUpdateCycleFor: .updates, error: noUpdate)
        #expect(service.phase == .current)
    }

    @Test func successfulPreinstallSavePreservesFavoritesAndPreferences() throws {
        try withDirectory { directory in
            let file = directory.appendingPathComponent("progress.json")
            let progress = ProgressStore(fileURL: file)
            progress.setFavorite(true, for: 7)
            progress.updatePreferences(QuizPreferences(scope: .favorites, questionLength: .all))
            try UpdateInstallationGuard.saveProgress(progress)
            let reloaded = ProgressStore(fileURL: file)
            #expect(reloaded.favoriteIDs == [7])
            #expect(reloaded.quizPreferences == progress.quizPreferences)
            #expect(reloaded.attempts.isEmpty)
            #expect(reloaded.lastPersistenceError == nil)
        }
    }

    @Test func fixtureFeedIsRestrictedToTheDedicatedDebugBundleAndLoopback() {
        var information = configuration
        information["SUFeedURL"] = "http://127.0.0.1:8765/appcast.xml"
        #expect(UpdateService.fixtureFeedURL(in: information) == nil)
        information["CFBundleIdentifier"] = "com.xingyiyao.MathInterviewVocab.UpdaterFixture"
#if DEBUG
        #expect(UpdateService.fixtureFeedURL(in: information)?.absoluteString == "http://127.0.0.1:8765/appcast.xml")
#else
        #expect(UpdateService.fixtureFeedURL(in: information) == nil)
#endif
        for url in ["https://example.invalid/appcast.xml", "http://localhost:8765/appcast.xml", "http://127.0.0.1:8765/other.xml", "http://user@127.0.0.1:8765/appcast.xml", "http://127.0.0.1:8765/appcast.xml?x=1"] {
            information["SUFeedURL"] = url
            #expect(UpdateService.fixtureFeedURL(in: information) == nil)
        }
    }

    private var configuration: [String: Any] {
        [
            "SUFeedURL": UpdateService.feedURL, "SUPublicEDKey": UpdateService.publicKey,
            "SUEnableAutomaticChecks": false, "SUAutomaticallyUpdate": false,
            "SUAllowsAutomaticUpdates": false, "SUEnableSystemProfiling": false,
            "SURequireSignedFeed": true, "SUVerifyUpdateBeforeExtraction": true
        ]
    }

    private func candidate() -> SparkleReleaseCandidate {
        SparkleReleaseCandidate(
            version: "1.5.0", build: "7",
            archiveURL: URL(string: "https://github.com/xyy386/MathInterviewVocab/releases/download/v1.5.0/MathInterviewVocab-macOS-universal-v1.5.0.zip"),
            bytes: 100,
            pageURL: URL(string: "https://github.com/xyy386/MathInterviewVocab/releases/tag/v1.5.0"),
            releaseNotesURL: nil
        )
    }

    private func withDirectory(_ body: (URL) throws -> Void) throws {
        let output = ProcessInfo.processInfo.environment["MATH_VOCAB_TEST_OUTPUT_DIRECTORY"]
        let base = output.map { URL(fileURLWithPath: $0, isDirectory: true) } ?? FileManager.default.temporaryDirectory
        let directory = base.appendingPathComponent("SparkleUpdateTests-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: directory) }
        try body(directory)
    }
}

@MainActor
private final class InstallationProbe {
    var maySave = false
    var saves = 0
    var resumes = 0
}

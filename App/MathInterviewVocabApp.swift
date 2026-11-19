import SwiftUI

@main
struct MathInterviewVocabApp: App {
    @NSApplicationDelegateAdaptor(UpdateAppDelegate.self) private var appDelegate
    @State private var library = TermLibrary()
    @State private var progress: ProgressStore
    @State private var speech: SpeechService
    @State private var updates: UpdateService

    init() {
        let progress: ProgressStore
#if DEBUG
        // 独立升级验收宿主可以显式隔离数据；正式发行忽略此环境变量。
        let fixtureDirectory = Bundle.main.bundleIdentifier == "com.xingyiyao.MathInterviewVocab.UpdaterFixture"
            ? Bundle.main.object(forInfoDictionaryKey: "MathVocabFixtureDataDirectory") as? String
            : nil
        if let directory = fixtureDirectory ?? ProcessInfo.processInfo.environment["MATH_VOCAB_TEST_DATA_DIR"],
           directory.hasPrefix("/"), !directory.isEmpty {
            progress = ProgressStore(fileURL: URL(fileURLWithPath: directory, isDirectory: true).appendingPathComponent("progress.json"))
        } else {
            progress = ProgressStore()
        }
#else
        progress = ProgressStore()
#endif
        let speech = SpeechService()
        let updates = UpdateService {
            try UpdateInstallationGuard.saveProgress(progress)
            speech.stop()
        }
        _progress = State(initialValue: progress)
        _speech = State(initialValue: speech)
        _updates = State(initialValue: updates)
        appDelegate.updates = updates
    }

    var body: some Scene {
        WindowGroup {
            rootContent
        }
        .defaultSize(width: 1220, height: 780)
        .windowResizability(.contentMinSize)
        .commands {
            CommandGroup(after: .appInfo) {
                CheckForUpdatesCommand(updates: updates)
            }
        }

        Settings {
            SettingsView()
                .environment(progress)
                .environment(speech)
                .environment(updates)
                .frame(width: 480, height: 680)
        }
    }

    private var rootContent: some View {
        RootView()
            .environment(library)
            .environment(progress)
            .environment(speech)
            .preferredColorScheme(.light)
            .frame(minWidth: 1000, minHeight: 680)
    }
}

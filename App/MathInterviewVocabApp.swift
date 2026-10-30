import SwiftUI

@main
struct MathInterviewVocabApp: App {
    @State private var library = TermLibrary()
    @State private var progress = ProgressStore()
    @State private var speech = SpeechService()
    @State private var updates = UpdateService()

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

import SwiftUI

@main
struct MathInterviewVocabApp: App {
    @State private var library = TermLibrary()
    @State private var progress = ProgressStore()
    @State private var speech = SpeechService()

    var body: some Scene {
        WindowGroup {
            rootContent
        }
        .defaultSize(width: 1220, height: 780)
        .windowResizability(.contentMinSize)

        Settings {
            SettingsView()
                .environment(progress)
                .environment(speech)
                .frame(width: 480)
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

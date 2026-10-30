import SwiftUI

struct SettingsView: View {
    @Environment(ProgressStore.self) private var progress
    @Environment(SpeechService.self) private var speech

    var showsResetButton = true
    @State private var showingResetConfirmation = false

    var body: some View {
        @Bindable var speech = speech

        Form {
            Section("英语发音") {
                Picker("默认口音", selection: $speech.accent) {
                    ForEach(EnglishAccent.allCases) { accent in
                        Text(accent.fullName).tag(accent)
                    }
                }
                .pickerStyle(.segmented)

                Picker("语速", selection: $speech.speed) {
                    Text("0.75×").tag(0.75)
                    Text("1×").tag(1.0)
                    Text("1.25×").tag(1.25)
                }

                HStack(spacing: 10) {
                    Button {
                        speech.speak(
                            text: "Mathematical Analysis",
                            accent: .american
                        )
                    } label: {
                        Label("试听美式", systemImage: "speaker.wave.2.fill")
                    }

                    Button {
                        speech.speak(
                            text: "Mathematical Analysis",
                            accent: .british
                        )
                    } label: {
                        Label("试听英式", systemImage: "speaker.wave.2.fill")
                    }
                }

                LabeledContent("内置语音", value: "Kokoro 自然语音")
                Text("美式与英式音频随应用内置，无需联网或首次下载。支持完整词条与内置完整单词、词组选区；未收录的字母片段会提示缺少音频。")
                    .font(.caption)
                    .foregroundStyle(.secondary)

                if case .failed = speech.playbackSource {
                    Label("离线音频无法播放，请重新下载完整应用。", systemImage: "exclamationmark.circle")
                        .font(.caption)
                        .foregroundStyle(AcademicTheme.errorRed)
                }
            }

            SoftwareUpdateSection()

            Section("数据") {
                LabeledContent("学习记录", value: "仅保存在当前设备")
                LabeledContent("账号", value: "不需要")
                LabeledContent("学习与发音联网", value: "不需要")

                if showsResetButton {
                    Button("清空本机学习记录", role: .destructive) {
                        showingResetConfirmation = true
                    }
                }
            }
        }
        .formStyle(.grouped)
        .onDisappear {
            speech.stop()
        }
        .confirmationDialog(
            "确定清空学习记录？",
            isPresented: $showingResetConfirmation,
            titleVisibility: .visible
        ) {
            Button("清空记录", role: .destructive) {
                progress.reset(keepingPreferences: true)
            }
            Button("取消", role: .cancel) {}
        } message: {
            Text("答题、错题、掌握状态和收藏都会从当前设备删除。")
        }
    }
}

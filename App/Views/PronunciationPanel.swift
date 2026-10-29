import SwiftUI

struct PronunciationPanel: View {
    @Environment(SpeechService.self) private var speech

    let term: Term
    var compact = false

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text("英美音标")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(AcademicTheme.inkNavy)
                Spacer()
                Label("Google 在线发音", systemImage: "waveform")
                    .font(.caption2.weight(.medium))
                    .foregroundStyle(AcademicTheme.formulaBlue)
            }
            .padding(.bottom, compact ? 8 : 10)

            pronunciationRow(for: .american)

            Divider()
                .overlay(AcademicTheme.gridBlue)
                .padding(.vertical, compact ? 7 : 9)

            pronunciationRow(for: .british)

            statusLine
                .padding(.top, compact ? 8 : 10)
        }
        .padding(compact ? 12 : 14)
        .background(AcademicTheme.formulaBlue.opacity(0.045))
        .overlay {
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .stroke(AcademicTheme.gridBlue, lineWidth: 1)
        }
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    private func pronunciationRow(for accent: EnglishAccent) -> some View {
        let ipa = term.pronunciation.ipa(for: accent)
        let isDefault = speech.accent == accent

        return HStack(alignment: .center, spacing: 12) {
            Text(accent.shortLabel)
                .font(.caption.monospaced().weight(.bold))
                .foregroundStyle(isDefault ? Color.white : AcademicTheme.formulaBlue)
                .frame(width: 34, height: 26)
                .background(
                    isDefault
                        ? AcademicTheme.formulaBlue
                        : AcademicTheme.formulaBlue.opacity(0.10),
                    in: RoundedRectangle(cornerRadius: 7, style: .continuous)
                )

            VStack(alignment: .leading, spacing: 3) {
                HStack(spacing: 6) {
                    Text(accent.chineseName)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(AcademicTheme.inkNavy.opacity(0.72))
                    if isDefault {
                        Text("默认")
                            .font(.caption2.weight(.semibold))
                            .foregroundStyle(AcademicTheme.formulaBlue)
                    }
                }

                Text(ipa.isEmpty ? "音标暂缺" : "/\(ipa)/")
                    .font(.system(size: compact ? 14 : 16, weight: .medium, design: .rounded))
                    .foregroundStyle(
                        ipa.isEmpty
                            ? AcademicTheme.inkNavy.opacity(0.45)
                            : AcademicTheme.inkNavy
                    )
                    .fixedSize(horizontal: false, vertical: true)
                    .textSelection(.enabled)
                    .accessibilityLabel("\(accent.fullName)音标，\(ipa)")
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .layoutPriority(1)

            Button {
                speech.speak(term, accent: accent)
            } label: {
                Label("\(accent.chineseName)朗读", systemImage: "speaker.wave.2.fill")
            }
            .buttonStyle(.bordered)
            .controlSize(compact ? .small : .regular)
            .help("使用 Google 翻译的\(accent.fullName)朗读完整词组")
        }
    }

    @ViewBuilder
    private var statusLine: some View {
        switch speech.playbackSource {
        case let .loading(accent):
            HStack(spacing: 7) {
                ProgressView()
                    .controlSize(.small)
                Text("正在获取 Google \(accent.chineseName)发音…")
            }
            .font(.caption)
            .foregroundStyle(.secondary)
        case let .systemFallback(accent):
            Label(
                "Google 发音暂不可用，已切换为 Mac \(accent.chineseName)系统语音。",
                systemImage: "wifi.exclamationmark"
            )
            .font(.caption)
            .foregroundStyle(AcademicTheme.errorRed)
        case let .google(accent):
            Label(
                "本次使用 Google \(accent.chineseName)发音",
                systemImage: "checkmark.circle.fill"
            )
            .font(.caption)
            .foregroundStyle(AcademicTheme.successGreen)
        case .idle:
            Label(
                "需要联网；不可用时自动回退到同口音的 Mac 系统语音。",
                systemImage: "network"
            )
            .font(.caption)
            .foregroundStyle(.secondary)
        }
    }
}

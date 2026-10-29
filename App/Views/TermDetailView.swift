import SwiftUI

struct TermDetailView: View {
    @Environment(ProgressStore.self) private var progress
    @Environment(SpeechService.self) private var speech

    let term: Term
    @State private var selectedPronunciationText = ""

    private var sanitizedSelection: String? {
        SpeechSelectionSanitizer.sanitize(selectedPronunciationText)
    }

    private var streak: Int {
        min(progress.correctStreak[term.id] ?? 0, 3)
    }

    private var status: (title: String, icon: String, tint: Color) {
        if progress.isMastered(term.id) {
            return ("已掌握", "checkmark.seal.fill", AcademicTheme.successGreen)
        }
        if progress.isWrong(term.id) {
            return ("待复习", "arrow.counterclockwise.circle.fill", AcademicTheme.errorRed)
        }
        if streak > 0 {
            return ("学习中", "circle.lefthalf.filled", AcademicTheme.formulaBlue)
        }
        return ("未检测", "circle.dashed", AcademicTheme.inkNavy.opacity(0.62))
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                HStack {
                    SectionBadge(
                        term.subjectName,
                        systemImage: "function",
                        tint: AcademicTheme.formulaBlue
                    )
                    if term.important {
                        SectionBadge(
                            "重点词汇",
                            systemImage: "bookmark.fill",
                            tint: AcademicTheme.errorRed
                        )
                    }
                    Spacer()
                    Text("#\(term.id)")
                        .font(.caption.monospacedDigit().weight(.semibold))
                        .foregroundStyle(.secondary)
                }

                VStack(alignment: .leading, spacing: 12) {
                    // 独立提供可访问文本，保留复制并避免可选 Text 的标签解析递归。
                    Text(term.chinese)
                        .font(.system(.largeTitle, design: .serif, weight: .bold))
                        .foregroundStyle(AcademicTheme.inkNavy)
                        .textSelection(.enabled)
                        .accessibilityRepresentation { Text(term.chinese) }

                    SelectablePronunciationText(
                        text: MathNotation.display(term.english),
                        selectedText: $selectedPronunciationText
                    ) { selection in
                        speech.speak(text: selection)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)

                    Text("双击选择单词，或拖动选择词组；下方可分别试听英美发音并对照音标。")
                        .font(.caption)
                        .foregroundStyle(.secondary)

                    PronunciationPanel(term: term)

                    HStack(spacing: 12) {
                        Button {
                            speech.speak(term)
                        } label: {
                            Label("朗读完整词组", systemImage: "speaker.wave.2.fill")
                        }
                        .buttonStyle(.borderedProminent)
                        .controlSize(.large)

                        Button {
                            if let selection = sanitizedSelection {
                                speech.speak(text: selection)
                            }
                        } label: {
                            Label("朗读所选", systemImage: "text.cursor")
                        }
                        .buttonStyle(.bordered)
                        .controlSize(.large)
                        .disabled(sanitizedSelection == nil)
                        .help(
                            sanitizedSelection == nil
                                ? "先双击单词或拖动选择一段英文"
                                : "朗读：\(selectedPronunciationText)"
                        )

                        Button {
                            progress.toggleFavorite(term.id)
                        } label: {
                            Label(
                                progress.isFavorite(term.id) ? "取消收藏" : "收藏",
                                systemImage: progress.isFavorite(term.id) ? "star.fill" : "star"
                            )
                        }
                        .buttonStyle(.bordered)
                        .controlSize(.large)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .academicCard(emphasized: term.important)

                VStack(alignment: .leading, spacing: 12) {
                    Text("中文释义")
                        .font(.headline)
                        .foregroundStyle(AcademicTheme.inkNavy)
                    Text(MathNotation.display(term.definition))
                        .font(.body)
                        .foregroundStyle(AcademicTheme.inkNavy.opacity(0.90))
                        .lineSpacing(6)
                        .textSelection(.enabled)
                        .accessibilityRepresentation { Text(MathNotation.display(term.definition)) }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .academicCard()

                VStack(alignment: .leading, spacing: 14) {
                    HStack {
                        Label(status.title, systemImage: status.icon)
                            .font(.headline)
                            .foregroundStyle(status.tint)
                        Spacer()
                        Text("连续答对 \(streak) / 3")
                            .font(.caption.monospacedDigit())
                            .foregroundStyle(.secondary)
                    }
                    KnowledgeAxis(progress: Double(streak) / 3.0, tint: status.tint)
                    Text("连续答对 3 次后标记为已掌握；答错会自动加入错题复习。")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .academicCard()
            }
            .academicContentColumn()
        }
        .background(AcademicTheme.gridBlue.opacity(0.14))
        .onChange(of: term.id) { _, _ in
            selectedPronunciationText = ""
            speech.stop()
        }
        .onDisappear {
            speech.stop()
        }
    }
}

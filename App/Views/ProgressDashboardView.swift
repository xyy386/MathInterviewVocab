import SwiftUI

struct ProgressDashboardView: View {
    @Environment(TermLibrary.self) private var library
    @Environment(ProgressStore.self) private var progress
    @Environment(SpeechService.self) private var speech

    private var attemptedIDs: Set<Int> {
        Set(progress.attempts.map(\.termID))
    }

    private var correctAttempts: Int {
        progress.attempts.filter(\.isCorrect).count
    }

    private var accuracy: Int {
        guard !progress.attempts.isEmpty else { return 0 }
        return Int(
            (Double(correctAttempts) / Double(progress.attempts.count) * 100).rounded()
        )
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                VStack(alignment: .leading, spacing: 8) {
                    Text("学习记录")
                        .font(.system(.largeTitle, design: .serif, weight: .bold))
                        .foregroundStyle(AcademicTheme.inkNavy)
                    Text("记录只保存在当前设备，不需要账号或网络。")
                        .foregroundStyle(.secondary)
                }

                LazyVGrid(
                    columns: [GridItem(.adaptive(minimum: 150), spacing: 12)],
                    spacing: 12
                ) {
                    ProgressMetric(
                        value: "\(attemptedIDs.count)",
                        label: "已检测词汇",
                        icon: "checklist",
                        tint: AcademicTheme.formulaBlue
                    )
                    ProgressMetric(
                        value: "\(progress.masteredIDs.count)",
                        label: "已掌握",
                        icon: "checkmark.seal.fill",
                        tint: AcademicTheme.successGreen
                    )
                    ProgressMetric(
                        value: "\(progress.wrongIDs.count)",
                        label: "待复习",
                        icon: "arrow.counterclockwise",
                        tint: AcademicTheme.errorRed
                    )
                    ProgressMetric(
                        value: "\(accuracy)%",
                        label: "总正确率",
                        icon: "percent",
                        tint: AcademicTheme.inkNavy
                    )
                }

                VStack(alignment: .leading, spacing: 15) {
                    HStack {
                        Text("总体掌握度")
                            .font(.headline)
                            .foregroundStyle(AcademicTheme.inkNavy)
                        Spacer()
                        Text("\(progress.masteredIDs.count) / \(library.terms.count)")
                            .font(.subheadline.monospacedDigit())
                            .foregroundStyle(.secondary)
                    }
                    KnowledgeAxis(progress: overallMastery)
                }
                .academicCard()

                VStack(alignment: .leading, spacing: 16) {
                    Text("分科学习进度")
                        .font(.headline)
                        .foregroundStyle(AcademicTheme.inkNavy)

                    ForEach(library.subjects) { subject in
                        subjectProgressRow(subject)
                    }
                }
                .academicCard()

                if !progress.wrongIDs.isEmpty {
                    VStack(alignment: .leading, spacing: 14) {
                        HStack {
                            Text("待复习错题")
                                .font(.headline)
                                .foregroundStyle(AcademicTheme.inkNavy)
                            Spacer()
                            SectionBadge(
                                "\(progress.wrongIDs.count) 个",
                                tint: AcademicTheme.errorRed
                            )
                        }

                        ForEach(wrongTerms.prefix(12)) { term in
                            HStack(spacing: 12) {
                                VStack(alignment: .leading, spacing: 3) {
                                    Text(term.chinese)
                                        .font(.body.weight(.semibold))
                                        .foregroundStyle(AcademicTheme.inkNavy)
                                    Text(MathNotation.display(term.english))
                                        .font(.subheadline)
                                        .foregroundStyle(.secondary)
                                }
                                Spacer()
                                Button {
                                    speech.speak(term)
                                } label: {
                                    Image(systemName: "speaker.wave.2.fill")
                                }
                                .buttonStyle(.borderless)
                                .accessibilityLabel("播放 \(term.english) 的发音")
                            }

                            if term.id != wrongTerms.prefix(12).last?.id {
                                Divider()
                            }
                        }

                        if wrongTerms.count > 12 {
                            Text("另有 \(wrongTerms.count - 12) 个错题，可在“词汇”中选择“仅看错题”。")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                    .academicCard()
                } else {
                    Label("当前没有待复习词汇", systemImage: "checkmark.circle.fill")
                        .font(.headline)
                        .foregroundStyle(AcademicTheme.successGreen)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .academicCard()
                }

                if let persistenceError = progress.lastPersistenceError {
                    Label(
                        "本地记录保存失败：\(persistenceError)",
                        systemImage: "exclamationmark.triangle.fill"
                    )
                    .font(.caption)
                    .foregroundStyle(AcademicTheme.errorRed)
                    .academicCard()
                }
            }
            .academicContentColumn()
        }
        .background(AcademicTheme.gridBlue.opacity(0.14))
        .onDisappear {
            speech.stop()
        }
    }

    private var overallMastery: Double {
        guard !library.terms.isEmpty else { return 0 }
        return Double(progress.masteredIDs.count) / Double(library.terms.count)
    }

    private var wrongTerms: [Term] {
        library.terms.filter { progress.wrongIDs.contains($0.id) }
    }

    private func subjectProgressRow(_ subject: SubjectSummary) -> some View {
        let subjectTermIDs = Set(
            library.terms
                .filter { $0.subjectID == subject.id }
                .map(\.id)
        )
        let masteredCount = progress.masteredIDs.intersection(subjectTermIDs).count
        let fraction = subject.count == 0 ? 0 : Double(masteredCount) / Double(subject.count)

        return VStack(alignment: .leading, spacing: 7) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(subject.name)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(AcademicTheme.inkNavy)
                    Text(subject.englishName)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer()
                Text("\(masteredCount) / \(subject.count)")
                    .font(.caption.monospacedDigit().weight(.semibold))
                    .foregroundStyle(AcademicTheme.formulaBlue)
            }

            ProgressView(value: fraction)
                .tint(AcademicTheme.formulaBlue)
        }
    }
}

private struct ProgressMetric: View {
    let value: String
    let label: String
    let icon: String
    let tint: Color

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Image(systemName: icon)
                .font(.title3.weight(.semibold))
                .foregroundStyle(tint)
            Text(value)
                .font(.title.monospacedDigit().weight(.bold))
                .foregroundStyle(AcademicTheme.inkNavy)
            Text(label)
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, minHeight: 104, alignment: .leading)
        .academicCard(contentPadding: 16)
    }
}

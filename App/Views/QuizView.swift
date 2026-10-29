import SwiftUI

private extension QuizScope {
    var title: String {
        switch self {
        case .important: "重点词汇"
        case .all: "全部词汇"
        case .subject: "指定科目"
        case .mistakes: "错题复习"
        case .favorites: "我的收藏"
        }
    }

    var icon: String {
        switch self {
        case .important: "bookmark.fill"
        case .all: "square.grid.2x2"
        case .subject: "books.vertical"
        case .mistakes: "arrow.counterclockwise"
        case .favorites: "star.fill"
        }
    }
}

private extension QuizLength {
    var title: String {
        switch self {
        case .ten: "10 题"
        case .twenty: "20 题"
        case .fifty: "50 题"
        case .all: "全部"
        }
    }
}

private enum QuizStage {
    case setup
    case active
    case summary
}

struct QuizView: View {
    @Environment(TermLibrary.self) private var library
    @Environment(ProgressStore.self) private var progress
    @Environment(SpeechService.self) private var speech

    @State private var stage: QuizStage = .setup
    @State private var direction: QuizDirection = .mixed
    @State private var scope: QuizScope = .important
    @State private var selectedSubjectID = "analysis"
    @State private var questionLength: QuizLength = .twenty
    @State private var includeMastered = true

    @State private var questions: [QuizQuestion] = []
    @State private var questionIndex = 0
    @State private var selectedOptionID: Int?
    @State private var feedbackSelectedText = ""
    @State private var answeredCount = 0
    @State private var correctCount = 0
    @State private var hasLoadedPreferences = false

    var body: some View {
        Group {
            switch stage {
            case .setup:
                setupView
            case .active:
                activeQuizView
            case .summary:
                summaryView
            }
        }
        .background(AcademicTheme.gridBlue.opacity(0.14))
        .onAppear(perform: loadPreferencesOnce)
        .onDisappear {
            feedbackSelectedText = ""
            speech.stop()
        }
    }

    private var setupView: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                VStack(alignment: .leading, spacing: 8) {
                    Text("用主动选择检验记忆")
                        .font(.system(.largeTitle, design: .serif, weight: .bold))
                        .foregroundStyle(AcademicTheme.inkNavy)
                    Text("每题四个选项，答题后立即查看释义和英文发音。")
                        .foregroundStyle(.secondary)
                }

                VStack(alignment: .leading, spacing: 16) {
                    QuizSettingLabel(title: "出题方向", icon: "arrow.left.arrow.right")
                    Picker("出题方向", selection: $direction) {
                        Text("中文 → 英文").tag(QuizDirection.zhToEn)
                        Text("英文 → 中文").tag(QuizDirection.enToZh)
                        Text("双向混合").tag(QuizDirection.mixed)
                    }
                    .pickerStyle(.segmented)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .academicCard()

                VStack(alignment: .leading, spacing: 14) {
                    QuizSettingLabel(title: "练习范围", icon: "scope")
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 145), spacing: 10)], spacing: 10) {
                        ForEach(QuizScope.allCases) { candidate in
                            ScopeButton(
                                scope: candidate,
                                count: count(for: candidate),
                                isSelected: scope == candidate
                            ) {
                                scope = candidate
                            }
                        }
                    }

                    if scope == .subject {
                        Picker("选择科目", selection: $selectedSubjectID) {
                            ForEach(library.subjects) { subject in
                                Text("\(subject.name)（\(count(forSubject: subject.id))）")
                                    .tag(subject.id)
                            }
                        }
                        .pickerStyle(.menu)
                    }
                }
                .academicCard()

                VStack(alignment: .leading, spacing: 16) {
                    QuizSettingLabel(title: "题目数量", icon: "number")
                    Picker("题目数量", selection: $questionLength) {
                        ForEach(QuizLength.allCases) { length in
                            Text(length.title).tag(length)
                        }
                    }
                    .pickerStyle(.segmented)

                    Toggle("包含已经掌握的词汇", isOn: $includeMastered)
                        .tint(AcademicTheme.formulaBlue)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .academicCard()

                if availableQuestionPool.isEmpty {
                    ContentUnavailableView(
                        "当前范围没有可练习的词汇",
                        systemImage: "tray",
                        description: Text(emptyScopeMessage)
                    )
                    .academicCard()
                } else {
                    Button(action: startQuiz) {
                        HStack {
                            Label("开始检测", systemImage: "play.fill")
                            Spacer()
                            Text("\(requestedQuestionCount) 题")
                                .font(.subheadline.monospacedDigit())
                                .opacity(0.82)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 5)
                    }
                    .buttonStyle(.borderedProminent)
                    .controlSize(.large)
                }
            }
            .academicContentColumn()
        }
    }

    @ViewBuilder
    private var activeQuizView: some View {
        if questions.indices.contains(questionIndex) {
            let question = questions[questionIndex]

            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    HStack {
                        Text("第 \(questionIndex + 1) / \(questions.count) 题")
                            .font(.subheadline.monospacedDigit().weight(.semibold))
                            .foregroundStyle(AcademicTheme.formulaBlue)
                        Spacer()
                        Text("答对 \(correctCount)")
                            .font(.subheadline.monospacedDigit())
                            .foregroundStyle(.secondary)

                        Button("结束本轮") {
                            stage = .summary
                        }
                        .buttonStyle(.bordered)
                        .controlSize(.small)
                    }

                    ProgressView(value: Double(answeredCount), total: Double(max(questions.count, 1)))
                        .tint(AcademicTheme.formulaBlue)

                    VStack(alignment: .leading, spacing: 10) {
                        SectionBadge(
                            question.direction == .zhToEn ? "选择对应英文" : "选择对应中文",
                            systemImage: "questionmark"
                        )
                        Text(prompt(for: question))
                            .font(.system(.largeTitle, design: .serif, weight: .bold))
                            .foregroundStyle(AcademicTheme.inkNavy)
                            .fixedSize(horizontal: false, vertical: true)
                            .accessibilityAddTraits(.isHeader)
                    }
                    .frame(maxWidth: .infinity, minHeight: 130, alignment: .leading)
                    .academicCard(emphasized: true)

                    VStack(spacing: 11) {
                        ForEach(Array(question.options.enumerated()), id: \.element.id) { index, option in
                            optionButton(
                                option,
                                index: index,
                                question: question
                            )
                        }
                    }

                    if let selectedOptionID {
                        answerFeedback(
                            question: question,
                            selectedOptionID: selectedOptionID
                        )
                    }
                }
                .academicContentColumn()
            }
        } else {
            ContentUnavailableView(
                "无法生成题目",
                systemImage: "exclamationmark.triangle",
                description: Text("请返回并更换练习范围。")
            )
        }
    }

    private func optionButton(
        _ option: Term,
        index: Int,
        question: QuizQuestion
    ) -> some View {
        let isCorrectOption = option.id == question.correctOptionID
        let isSelected = option.id == selectedOptionID

        return Button {
            select(option.id, for: question)
        } label: {
            HStack(spacing: 14) {
                Text(String(UnicodeScalar(65 + index)!))
                    .font(.headline.monospaced())
                    .frame(width: 30, height: 30)
                    .background(optionMarkerColor(isCorrect: isCorrectOption, isSelected: isSelected), in: Circle())
                    .foregroundStyle(selectedOptionID == nil ? AcademicTheme.formulaBlue : Color.white)

                Text(answer(for: option, direction: question.direction))
                    .font(.body.weight(.semibold))
                    .foregroundStyle(AcademicTheme.inkNavy)
                    .multilineTextAlignment(.leading)
                    .frame(maxWidth: .infinity, alignment: .leading)

                if selectedOptionID != nil && isCorrectOption {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundStyle(AcademicTheme.successGreen)
                } else if selectedOptionID != nil && isSelected {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundStyle(AcademicTheme.errorRed)
                }
            }
            .padding(14)
            .background(optionBackground(isCorrect: isCorrectOption, isSelected: isSelected))
            .overlay {
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .stroke(optionBorder(isCorrect: isCorrectOption, isSelected: isSelected), lineWidth: 1.5)
            }
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        }
        .buttonStyle(.plain)
        .disabled(selectedOptionID != nil)
        .keyboardShortcut(KeyEquivalent(Character("\(index + 1)")), modifiers: [])
        .accessibilityLabel("选项 \(index + 1)，\(answer(for: option, direction: question.direction))")
    }

    private func answerFeedback(
        question: QuizQuestion,
        selectedOptionID: Int
    ) -> some View {
        let isCorrect = selectedOptionID == question.correctOptionID

        return VStack(alignment: .leading, spacing: 14) {
            HStack {
                Label(
                    isCorrect ? "回答正确" : "回答错误",
                    systemImage: isCorrect ? "checkmark.circle.fill" : "xmark.circle.fill"
                )
                .font(.headline)
                .foregroundStyle(isCorrect ? AcademicTheme.successGreen : AcademicTheme.errorRed)

                Spacer()
            }

            Text(question.term.chinese)
                .font(.title3.weight(.semibold))
                .foregroundStyle(AcademicTheme.inkNavy)

            SelectablePronunciationText(
                text: MathNotation.display(question.term.english),
                selectedText: $feedbackSelectedText
            ) { selection in
                speech.speak(text: selection)
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            PronunciationPanel(term: question.term, compact: true)

            HStack(spacing: 10) {
                Button {
                    speech.speak(question.term)
                } label: {
                    Label("朗读完整词组", systemImage: "speaker.wave.2.fill")
                }
                .buttonStyle(.bordered)

                Button {
                    if let selection = SpeechSelectionSanitizer.sanitize(
                        feedbackSelectedText
                    ) {
                        speech.speak(text: selection)
                    }
                } label: {
                    Label("朗读所选", systemImage: "text.cursor")
                }
                .buttonStyle(.bordered)
                .disabled(
                    SpeechSelectionSanitizer.sanitize(feedbackSelectedText) == nil
                )
                .help(
                    SpeechSelectionSanitizer.sanitize(feedbackSelectedText) == nil
                        ? "先双击单词或拖动选择一段英文"
                        : "朗读：\(feedbackSelectedText)"
                )
            }

            Text(MathNotation.display(question.term.definition))
                .foregroundStyle(AcademicTheme.inkNavy.opacity(0.88))
                .lineSpacing(5)

            Button(action: advanceQuestion) {
                Label(
                    questionIndex + 1 == questions.count ? "查看结果" : "下一题",
                    systemImage: "arrow.right"
                )
                .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
            .keyboardShortcut(.return, modifiers: [])
        }
        .academicCard(emphasized: true)
    }

    private var summaryView: some View {
        ScrollView {
            VStack(spacing: 22) {
                Image(systemName: resultIcon)
                    .font(.system(size: 58, weight: .semibold))
                    .foregroundStyle(resultColor)

                VStack(spacing: 6) {
                    Text(resultTitle)
                        .font(.system(.largeTitle, design: .serif, weight: .bold))
                        .foregroundStyle(AcademicTheme.inkNavy)
                    Text(answeredCount == 0 ? "本轮尚未作答" : "本轮完成 \(answeredCount) 题")
                        .foregroundStyle(.secondary)
                }

                HStack(spacing: 12) {
                    SummaryMetric(value: "\(correctCount)", label: "答对", tint: AcademicTheme.successGreen)
                    SummaryMetric(value: "\(max(answeredCount - correctCount, 0))", label: "答错", tint: AcademicTheme.errorRed)
                    SummaryMetric(value: "\(accuracyPercentage)%", label: "正确率", tint: AcademicTheme.formulaBlue)
                }

                VStack(spacing: 12) {
                    Button {
                        startQuiz()
                    } label: {
                        Label("再来一轮", systemImage: "arrow.clockwise")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .controlSize(.large)

                    if !progress.wrongIDs.isEmpty {
                        Button {
                            scope = .mistakes
                            startQuiz()
                        } label: {
                            Label("复习当前错题（\(progress.wrongIDs.count)）", systemImage: "arrow.counterclockwise")
                                .frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.bordered)
                        .controlSize(.large)
                    }

                    Button("返回设置") {
                        stage = .setup
                    }
                    .buttonStyle(.plain)
                    .foregroundStyle(AcademicTheme.formulaBlue)
                }
                .frame(maxWidth: 520)
            }
            .academicContentColumn(verticalPadding: 32)
        }
    }

    private var availableQuestionPool: [Term] {
        pool(for: scope)
    }

    private func pool(for scope: QuizScope) -> [Term] {
        QuizPoolBuilder.makePool(
            from: library.terms,
            scope: scope,
            selectedSubjectID: selectedSubjectID,
            wrongIDs: progress.wrongIDs,
            favoriteIDs: progress.favoriteIDs,
            masteredIDs: progress.masteredIDs,
            includeMastered: includeMastered
        )
    }

    private func count(for scope: QuizScope) -> Int {
        pool(for: scope).count
    }

    private func count(forSubject subjectID: String) -> Int {
        QuizPoolBuilder.makePool(
            from: library.terms,
            scope: .subject,
            selectedSubjectID: subjectID,
            wrongIDs: progress.wrongIDs,
            favoriteIDs: progress.favoriteIDs,
            masteredIDs: progress.masteredIDs,
            includeMastered: includeMastered
        ).count
    }

    private var requestedQuestionCount: Int {
        questionLength.resolvedCount(availableCount: availableQuestionPool.count)
    }

    private var emptyScopeMessage: String {
        switch scope {
        case .mistakes: "答错的词汇会自动出现在这里。"
        case .favorites: "先在词汇详情中收藏想要练习的术语。"
        default: "请开启“包含已经掌握的词汇”或选择其他范围。"
        }
    }

    private func loadPreferencesOnce() {
        guard !hasLoadedPreferences else { return }
        hasLoadedPreferences = true
        direction = progress.quizPreferences.direction
        questionLength = progress.quizPreferences.questionLength
        includeMastered = progress.quizPreferences.includeMastered
        selectedSubjectID = progress.quizPreferences.selectedSubjectID
        scope = progress.quizPreferences.scope
    }

    private func startQuiz() {
        let pool = availableQuestionPool
        guard !pool.isEmpty else {
            stage = .setup
            return
        }

        let requestedCount = questionLength.resolvedCount(availableCount: pool.count)
        questions = QuizEngine().makeQuestions(
            from: pool,
            distractorsFrom: library.terms,
            count: requestedCount,
            direction: direction
        )
        guard !questions.isEmpty else {
            stage = .setup
            return
        }

        progress.updatePreferences(
            QuizPreferences(
                direction: direction,
                scope: scope,
                questionLength: questionLength,
                selectedSubjectID: selectedSubjectID,
                includeMastered: includeMastered
            )
        )
        questionIndex = 0
        selectedOptionID = nil
        feedbackSelectedText = ""
        answeredCount = 0
        correctCount = 0
        stage = .active
    }

    private func select(_ optionID: Int, for question: QuizQuestion) {
        guard selectedOptionID == nil else { return }
        selectedOptionID = optionID
        answeredCount += 1
        if progress.recordAnswer(for: question, selectedOptionID: optionID) {
            correctCount += 1
        }
    }

    private func advanceQuestion() {
        speech.stop()
        feedbackSelectedText = ""
        if questionIndex + 1 < questions.count {
            questionIndex += 1
            selectedOptionID = nil
        } else {
            stage = .summary
        }
    }

    private func prompt(for question: QuizQuestion) -> String {
        question.direction == .zhToEn
            ? question.term.chinese
            : MathNotation.display(question.term.english)
    }

    private func answer(for option: Term, direction: QuizDirection) -> String {
        direction == .zhToEn
            ? MathNotation.display(option.english)
            : option.chinese
    }

    private func optionMarkerColor(isCorrect: Bool, isSelected: Bool) -> Color {
        guard selectedOptionID != nil else { return AcademicTheme.gridBlue.opacity(0.72) }
        if isCorrect { return AcademicTheme.successGreen }
        if isSelected { return AcademicTheme.errorRed }
        return AcademicTheme.inkNavy.opacity(0.42)
    }

    private func optionBackground(isCorrect: Bool, isSelected: Bool) -> Color {
        guard selectedOptionID != nil else { return AcademicTheme.paperWhite }
        if isCorrect { return AcademicTheme.successGreen.opacity(0.10) }
        if isSelected { return AcademicTheme.errorRed.opacity(0.10) }
        return AcademicTheme.paperWhite.opacity(0.72)
    }

    private func optionBorder(isCorrect: Bool, isSelected: Bool) -> Color {
        guard selectedOptionID != nil else { return AcademicTheme.gridBlue }
        if isCorrect { return AcademicTheme.successGreen }
        if isSelected { return AcademicTheme.errorRed }
        return AcademicTheme.gridBlue.opacity(0.72)
    }

    private var accuracyPercentage: Int {
        guard answeredCount > 0 else { return 0 }
        return Int((Double(correctCount) / Double(answeredCount) * 100).rounded())
    }

    private var resultTitle: String {
        guard answeredCount > 0 else { return "准备好后再开始" }
        return switch accuracyPercentage {
        case 90...: "掌握得很扎实"
        case 70...: "再复习一轮就更稳了"
        default: "从错题开始巩固"
        }
    }

    private var resultIcon: String {
        guard answeredCount > 0 else { return "pause.circle.fill" }
        return switch accuracyPercentage {
        case 90...: "checkmark.seal.fill"
        case 70...: "chart.line.uptrend.xyaxis"
        default: "arrow.counterclockwise.circle.fill"
        }
    }

    private var resultColor: Color {
        guard answeredCount > 0 else { return AcademicTheme.formulaBlue }
        return switch accuracyPercentage {
        case 90...: AcademicTheme.successGreen
        case 70...: AcademicTheme.formulaBlue
        default: AcademicTheme.errorRed
        }
    }
}

private struct QuizSettingLabel: View {
    let title: String
    let icon: String

    var body: some View {
        Label(title, systemImage: icon)
            .font(.headline)
            .foregroundStyle(AcademicTheme.inkNavy)
    }
}

private struct ScopeButton: View {
    let scope: QuizScope
    let count: Int
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 10) {
                Image(systemName: scope.icon)
                    .frame(width: 20)
                VStack(alignment: .leading, spacing: 2) {
                    Text(scope.title)
                        .font(.subheadline.weight(.semibold))
                    Text("\(count) 个词")
                        .font(.caption.monospacedDigit())
                        .opacity(0.74)
                }
                Spacer(minLength: 0)
            }
            .foregroundStyle(isSelected ? Color.white : AcademicTheme.inkNavy)
            .padding(12)
            .background(
                isSelected ? AcademicTheme.formulaBlue : AcademicTheme.gridBlue.opacity(0.44),
                in: RoundedRectangle(cornerRadius: 12, style: .continuous)
            )
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}

private struct SummaryMetric: View {
    let value: String
    let label: String
    let tint: Color

    var body: some View {
        VStack(spacing: 5) {
            Text(value)
                .font(.title.monospacedDigit().weight(.bold))
                .foregroundStyle(tint)
            Text(label)
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
        .academicCard(contentPadding: 14)
    }
}

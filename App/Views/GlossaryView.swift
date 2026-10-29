import SwiftUI

struct GlossaryView: View {
    @Environment(TermLibrary.self) private var library
    @Environment(ProgressStore.self) private var progress

    @State private var searchText = ""
    @State private var selectedSubjectID: String?
    @State private var importantOnly = false
    @State private var favoritesOnly = false
    @State private var mistakesOnly = false
    @State private var columnVisibility = NavigationSplitViewVisibility.all
    @State private var selectedTermID: Int?

    private var filteredTerms: [Term] {
        library.terms.filter { term in
            let matchesSubject = selectedSubjectID == nil || term.subjectID == selectedSubjectID
            let matchesImportant = !importantOnly || term.important
            let matchesFavorite = !favoritesOnly || progress.favoriteIDs.contains(term.id)
            let matchesMistake = !mistakesOnly || progress.wrongIDs.contains(term.id)
            let normalizedQuery = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
            let matchesSearch = normalizedQuery.isEmpty
                || term.chinese.localizedCaseInsensitiveContains(normalizedQuery)
                || term.english.localizedCaseInsensitiveContains(normalizedQuery)
                || term.definition.localizedCaseInsensitiveContains(normalizedQuery)

            return matchesSubject
                && matchesImportant
                && matchesFavorite
                && matchesMistake
                && matchesSearch
        }
    }

    var body: some View {
        Group {
            if let loadError = library.loadError {
                ContentUnavailableView(
                    "词汇数据无法载入",
                    systemImage: "exclamationmark.triangle",
                    description: Text(loadError)
                )
            } else {
                glossaryContent
            }
        }
        .tint(AcademicTheme.formulaBlue)
    }

    private var glossaryContent: some View {
        NavigationSplitView(columnVisibility: $columnVisibility) {
            VStack(spacing: 0) {
                filterStrip
                Divider()
                termList(selection: $selectedTermID)
            }
            .navigationTitle("专业词汇")
            .navigationSplitViewColumnWidth(min: 320, ideal: 380, max: 460)
        } detail: {
            if let selectedTermID,
               let term = library.term(id: selectedTermID) {
                TermDetailView(term: term)
            } else {
                GlossaryWelcomeView()
            }
        }
        .onChange(of: filteredTerms.map(\.id)) { _, visibleIDs in
            if let selectedTermID, !visibleIDs.contains(selectedTermID) {
                self.selectedTermID = nil
            }
        }
        .navigationSplitViewStyle(.balanced)
    }

    private var filterStrip: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass")
                    .foregroundStyle(AcademicTheme.inkNavy.opacity(0.52))

                TextField("搜索中文、英文或释义", text: $searchText)
                    .textFieldStyle(.plain)

                if !searchText.isEmpty {
                    Button {
                        searchText = ""
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundStyle(.tertiary)
                    }
                    .buttonStyle(.plain)
                    .help("清除搜索")
                }
            }
            .padding(.horizontal, 10)
            .frame(height: 30)
            .background(Color.white.opacity(0.78), in: RoundedRectangle(cornerRadius: 8, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 8, style: .continuous)
                    .stroke(AcademicTheme.gridBlue, lineWidth: 1)
            }

            HStack(spacing: 8) {
                Picker("科目", selection: $selectedSubjectID) {
                    Text("全部科目（\(library.terms.count)）")
                        .tag(String?.none)

                    ForEach(library.subjects) { subject in
                        Text("\(subject.name)（\(subject.count)）")
                            .tag(Optional(subject.id))
                    }
                }
                .labelsHidden()
                .pickerStyle(.menu)
                .frame(maxWidth: .infinity, alignment: .leading)

                Menu {
                    Toggle("仅看重点", isOn: $importantOnly)
                    Toggle("仅看收藏", isOn: $favoritesOnly)
                    Toggle("仅看错题", isOn: $mistakesOnly)

                    Divider()

                    Button("清除筛选") {
                        importantOnly = false
                        favoritesOnly = false
                        mistakesOnly = false
                        selectedSubjectID = nil
                    }
                    .disabled(activeFilterCount == 0)
                } label: {
                    Label(
                        toggleFilterCount == 0 ? "筛选" : "筛选 \(toggleFilterCount)",
                        systemImage: toggleFilterCount == 0
                            ? "line.3.horizontal.decrease"
                            : "line.3.horizontal.decrease.circle.fill"
                    )
                    .font(.subheadline.weight(.semibold))
                }
                .menuStyle(.button)
                .fixedSize()
            }

            Text("显示 \(filteredTerms.count) / \(library.terms.count) 个词汇")
                .font(.caption.monospacedDigit().weight(.medium))
                .foregroundStyle(AcademicTheme.inkNavy.opacity(0.62))
        }
        .padding(14)
        .background(AcademicTheme.paperWhite.opacity(0.96))
    }

    private var activeFilterCount: Int {
        [importantOnly, favoritesOnly, mistakesOnly].filter { $0 }.count
            + (selectedSubjectID == nil ? 0 : 1)
    }

    private var toggleFilterCount: Int {
        [importantOnly, favoritesOnly, mistakesOnly].filter { $0 }.count
    }

    private func termList(selection: Binding<Int?>) -> some View {
        List(selection: selection) {
            learningHeader

            if filteredTerms.isEmpty {
                ContentUnavailableView(
                    "没有符合条件的词汇",
                    systemImage: "text.magnifyingglass",
                    description: Text("请更换搜索内容、科目或筛选条件。")
                )
            } else {
                ForEach(library.subjects) { subject in
                    let subjectTerms = filteredTerms.filter { $0.subjectID == subject.id }
                    if !subjectTerms.isEmpty {
                        Section(subject.name) {
                            ForEach(subjectTerms) { term in
                                TermRow(
                                    term: term,
                                    isSelected: selectedTermID == term.id
                                )
                                    .tag(term.id)
                            }
                        }
                    }
                }
            }
        }
        .listStyle(.sidebar)
    }

    private var learningHeader: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .firstTextBaseline) {
                VStack(alignment: .leading, spacing: 3) {
                    Text("知识坐标")
                        .font(.headline)
                        .foregroundStyle(AcademicTheme.inkNavy)
                    Text("已掌握 \(progress.masteredIDs.count) 个词汇")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer()
                Text("\(Int(masteryProgress * 100))%")
                    .font(.title3.monospacedDigit().weight(.semibold))
                    .foregroundStyle(AcademicTheme.formulaBlue)
            }

            KnowledgeAxis(progress: masteryProgress)
        }
        .academicCard(contentPadding: 16)
        .listRowInsets(EdgeInsets(top: 12, leading: 14, bottom: 12, trailing: 14))
        .listRowBackground(Color.clear)
    }

    private var masteryProgress: Double {
        guard !library.terms.isEmpty else { return 0 }
        return Double(progress.masteredIDs.count) / Double(library.terms.count)
    }
}

private struct TermRow: View {
    @Environment(ProgressStore.self) private var progress
    let term: Term
    let isSelected: Bool

    var body: some View {
        HStack(spacing: 12) {
            Text("\(term.id)")
                .font(.caption.monospacedDigit().weight(.semibold))
                .foregroundStyle(
                    isSelected ? Color.white.opacity(0.90) : AcademicTheme.formulaBlue
                )
                .frame(width: 30, alignment: .trailing)

            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 6) {
                    Text(term.chinese)
                        .font(.body.weight(term.important ? .bold : .medium))
                        .foregroundStyle(isSelected ? Color.white : AcademicTheme.inkNavy)

                    if term.important {
                        Text("重点")
                            .font(.caption2.weight(.bold))
                            .foregroundStyle(
                                isSelected ? Color.white.opacity(0.86) : AcademicTheme.formulaBlue
                            )
                    }
                }

                Text(MathNotation.display(term.english))
                    .font(.subheadline)
                    .foregroundStyle(
                        isSelected ? Color.white.opacity(0.82) : Color.secondary
                    )
                    .lineLimit(2)
                    .environment(\.locale, Locale(identifier: "en"))
            }

            Spacer(minLength: 4)

            HStack(spacing: 6) {
                if progress.isFavorite(term.id) {
                    Image(systemName: "star.fill")
                        .foregroundStyle(AcademicTheme.formulaBlue)
                        .accessibilityLabel("已收藏")
                }

                if progress.isMastered(term.id) {
                    Image(systemName: "checkmark.seal.fill")
                        .foregroundStyle(AcademicTheme.successGreen)
                        .accessibilityLabel("已掌握")
                } else if progress.isWrong(term.id) {
                    Image(systemName: "arrow.counterclockwise.circle.fill")
                        .foregroundStyle(AcademicTheme.errorRed)
                        .accessibilityLabel("待复习")
                }
            }
        }
        .padding(.vertical, 4)
        .contentShape(Rectangle())
    }
}

private struct GlossaryWelcomeView: View {
    @Environment(TermLibrary.self) private var library
    @Environment(ProgressStore.self) private var progress

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 26) {
                VStack(alignment: .leading, spacing: 8) {
                    Text("数学专业面试")
                        .font(.system(.largeTitle, design: .serif, weight: .bold))
                        .foregroundStyle(AcademicTheme.inkNavy)
                    Text("英文专业词汇汇总")
                        .font(.system(.title, design: .serif, weight: .semibold))
                        .foregroundStyle(AcademicTheme.formulaBlue)
                    Text("从左侧选择一个词汇，查看规范英文、中文释义并播放发音。")
                        .foregroundStyle(.secondary)
                }

                HStack(spacing: 12) {
                    WelcomeMetric(value: "\(library.terms.count)", label: "全部词汇")
                    WelcomeMetric(value: "\(library.terms.filter(\.important).count)", label: "重点词汇")
                    WelcomeMetric(value: "\(progress.masteredIDs.count)", label: "已掌握")
                }

                VStack(alignment: .leading, spacing: 14) {
                    Text("五个学习板块")
                        .font(.headline)
                        .foregroundStyle(AcademicTheme.inkNavy)
                    ForEach(library.subjects) { subject in
                        HStack {
                            SectionBadge(subject.name)
                            Spacer()
                            Text("\(subject.count) 词")
                                .font(.subheadline.monospacedDigit())
                                .foregroundStyle(.secondary)
                        }
                    }
                }
                .academicCard()
            }
            .academicContentColumn()
        }
        .background(AcademicTheme.gridBlue.opacity(0.16))
    }
}

private struct WelcomeMetric: View {
    let value: String
    let label: String

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(value)
                .font(.title.monospacedDigit().weight(.bold))
                .foregroundStyle(AcademicTheme.formulaBlue)
            Text(label)
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .academicCard(contentPadding: 14)
    }
}

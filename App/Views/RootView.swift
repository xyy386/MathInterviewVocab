import SwiftUI

enum AppDestination: String, CaseIterable, Identifiable {
    case glossary
    case quiz
    case progress

    var id: String { rawValue }

    var title: String {
        switch self {
        case .glossary: "词汇"
        case .quiz: "检测"
        case .progress: "学习记录"
        }
    }

    var icon: String {
        switch self {
        case .glossary: "text.book.closed"
        case .quiz: "checkmark.circle"
        case .progress: "chart.bar.xaxis"
        }
    }

    var shortcut: KeyEquivalent {
        KeyEquivalent(Character(shortcutLabel))
    }

    var shortcutLabel: String {
        switch self {
        case .glossary: "1"
        case .quiz: "2"
        case .progress: "3"
        }
    }
}

struct RootView: View {
    @State private var selection = AppDestination.glossary

    var body: some View {
        destinationView(selection)
            .toolbar {
                ToolbarItem(placement: .principal) {
                    AppSectionPicker(selection: $selection)
                }

                ToolbarItem(placement: .primaryAction) {
                    SettingsLink {
                        Label("设置", systemImage: "gearshape")
                    }
                    .labelStyle(.iconOnly)
                    .help("设置发音、软件更新与本地数据")
                }
            }
            .tint(AcademicTheme.formulaBlue)
    }

    @ViewBuilder
    private func destinationView(_ destination: AppDestination) -> some View {
        switch destination {
        case .glossary:
            GlossaryView()
        case .quiz:
            QuizView()
        case .progress:
            ProgressDashboardView()
        }
    }
}

private struct AppSectionPicker: View {
    @Binding var selection: AppDestination

    var body: some View {
        HStack(spacing: 2) {
            ForEach(AppDestination.allCases) { destination in
                Button {
                    selection = destination
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: destination.icon)
                            .accessibilityHidden(true)
                        Text(destination.title)
                    }
                        .font(.subheadline.weight(.semibold))
                        .frame(minWidth: 92)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 6)
                        .foregroundStyle(
                            selection == destination
                                ? Color.white
                                : AcademicTheme.inkNavy.opacity(0.78)
                        )
                        .background {
                            RoundedRectangle(cornerRadius: 7, style: .continuous)
                                .fill(
                                    selection == destination
                                        ? AcademicTheme.formulaBlue
                                        : Color.clear
                                )
                        }
                }
                .buttonStyle(.plain)
                .keyboardShortcut(destination.shortcut, modifiers: .command)
                .help("\(destination.title)（⌘\(destination.shortcutLabel)）")
                .accessibilityAddTraits(
                    selection == destination ? .isSelected : []
                )
            }
        }
        .padding(3)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .stroke(AcademicTheme.inkNavy.opacity(0.12), lineWidth: 1)
        }
        .accessibilityElement(children: .contain)
        .accessibilityLabel("主要功能")
    }
}

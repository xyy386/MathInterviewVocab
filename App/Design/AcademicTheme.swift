import SwiftUI

/// Shared visual tokens for the app's academic-note aesthetic.
enum AcademicTheme {
    /// 深墨蓝 · #14213D
    static let inkNavy = Color(
        red: 20.0 / 255.0,
        green: 33.0 / 255.0,
        blue: 61.0 / 255.0
    )

    /// 公式蓝 · #2E5AAC
    static let formulaBlue = Color(
        red: 46.0 / 255.0,
        green: 90.0 / 255.0,
        blue: 172.0 / 255.0
    )

    /// 纸白 · #F7F9FC
    static let paperWhite = Color(
        red: 247.0 / 255.0,
        green: 249.0 / 255.0,
        blue: 252.0 / 255.0
    )

    /// 网格蓝 · #D9E4F2
    static let gridBlue = Color(
        red: 217.0 / 255.0,
        green: 228.0 / 255.0,
        blue: 242.0 / 255.0
    )

    /// 正确绿 · #1C7C54
    static let successGreen = Color(
        red: 28.0 / 255.0,
        green: 124.0 / 255.0,
        blue: 84.0 / 255.0
    )

    /// 错误红 · #B54343
    static let errorRed = Color(
        red: 181.0 / 255.0,
        green: 67.0 / 255.0,
        blue: 67.0 / 255.0
    )

    static let cardCornerRadius: CGFloat = 18
    static let cardPadding: CGFloat = 20
}

/// A quiet, paper-like surface for vocabulary and assessment content.
struct CardModifier: ViewModifier {
    var emphasized = false
    var contentPadding = AcademicTheme.cardPadding

    func body(content: Content) -> some View {
        content
            .padding(contentPadding)
            .background {
                RoundedRectangle(
                    cornerRadius: AcademicTheme.cardCornerRadius,
                    style: .continuous
                )
                .fill(AcademicTheme.paperWhite)
            }
            .overlay {
                RoundedRectangle(
                    cornerRadius: AcademicTheme.cardCornerRadius,
                    style: .continuous
                )
                .stroke(
                    emphasized
                        ? AcademicTheme.formulaBlue
                        : AcademicTheme.gridBlue,
                    lineWidth: emphasized ? 1.5 : 1
                )
            }
            .shadow(
                color: AcademicTheme.inkNavy.opacity(emphasized ? 0.12 : 0.07),
                radius: emphasized ? 12 : 8,
                x: 0,
                y: emphasized ? 5 : 3
            )
    }
}

extension View {
    func academicCard(
        emphasized: Bool = false,
        contentPadding: CGFloat = AcademicTheme.cardPadding
    ) -> some View {
        modifier(
            CardModifier(
                emphasized: emphasized,
                contentPadding: contentPadding
            )
        )
    }

    /// Keeps long-form app content centered and readable at every window size.
    func academicContentColumn(
        maxWidth: CGFloat = 820,
        horizontalPadding: CGFloat = 30,
        verticalPadding: CGFloat = 28
    ) -> some View {
        frame(maxWidth: maxWidth, alignment: .leading)
            .frame(maxWidth: .infinity, alignment: .center)
            .padding(.horizontal, horizontalPadding)
            .padding(.vertical, verticalPadding)
    }
}

/// A compact chapter or subject marker that remains legible without color.
struct SectionBadge: View {
    let title: String
    var systemImage: String?
    var tint = AcademicTheme.formulaBlue

    init(
        _ title: String,
        systemImage: String? = nil,
        tint: Color = AcademicTheme.formulaBlue
    ) {
        self.title = title
        self.systemImage = systemImage
        self.tint = tint
    }

    var body: some View {
        HStack(spacing: 6) {
            if let systemImage {
                Image(systemName: systemImage)
                    .accessibilityHidden(true)
            }

            Text(title)
                .lineLimit(1)
        }
        .font(.caption.weight(.semibold))
        .foregroundStyle(tint)
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .background {
            Capsule()
                .fill(tint.opacity(0.10))
        }
        .overlay {
            Capsule()
                .stroke(tint.opacity(0.28), lineWidth: 1)
        }
        .accessibilityElement(children: .combine)
    }
}

/// A reusable mastery axis from unfamiliar to mastered.
struct KnowledgeAxis: View {
    var progress: Double
    var startLabel = "陌生"
    var midpointLabel = "学习中"
    var endLabel = "已掌握"
    var tint = AcademicTheme.formulaBlue

    private var clampedProgress: Double {
        min(max(progress, 0), 1)
    }

    private var progressPercentage: Int {
        Int((clampedProgress * 100).rounded())
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            GeometryReader { proxy in
                let markerDiameter = 14.0
                let usableWidth = max(proxy.size.width - markerDiameter, 0)
                let progressWidth = usableWidth * clampedProgress

                ZStack(alignment: .leading) {
                    Capsule()
                        .fill(AcademicTheme.gridBlue)
                        .frame(height: 4)
                        .padding(.horizontal, markerDiameter / 2)

                    Capsule()
                        .fill(tint)
                        .frame(width: progressWidth, height: 4)
                        .offset(x: markerDiameter / 2)

                    ForEach([0.0, 0.5, 1.0], id: \.self) { position in
                        Circle()
                            .fill(
                                position <= clampedProgress
                                    ? tint
                                    : AcademicTheme.paperWhite
                            )
                            .overlay {
                                Circle()
                                    .stroke(
                                        position <= clampedProgress
                                            ? tint
                                            : AcademicTheme.gridBlue,
                                        lineWidth: 2
                                    )
                            }
                            .frame(width: 10, height: 10)
                            .position(
                                x: markerDiameter / 2 + usableWidth * position,
                                y: markerDiameter / 2
                            )
                    }

                    Circle()
                        .fill(tint)
                        .overlay {
                            Circle()
                                .stroke(AcademicTheme.paperWhite, lineWidth: 3)
                        }
                        .frame(width: markerDiameter, height: markerDiameter)
                        .shadow(
                            color: AcademicTheme.inkNavy.opacity(0.16),
                            radius: 3,
                            y: 1
                        )
                        .position(
                            x: markerDiameter / 2 + progressWidth,
                            y: markerDiameter / 2
                        )
                }
            }
            .frame(height: 14)

            HStack {
                Text(startLabel)
                Spacer()
                Text(midpointLabel)
                Spacer()
                Text(endLabel)
            }
            .font(.caption2.weight(.medium))
            .foregroundStyle(AcademicTheme.inkNavy.opacity(0.72))
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("知识掌握进度")
        .accessibilityValue("\(progressPercentage)%")
    }
}

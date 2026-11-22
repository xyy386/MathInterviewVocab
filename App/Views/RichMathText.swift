import AppKit
import SwiftUI

/// 普通文字仍由原生文本系统绘制，公式使用随包提供的矢量附件。
struct RichMathText: View {
    let source: String
    var size: CGFloat = NSFont.systemFontSize
    var weight: NSFont.Weight = .regular
    var color: NSColor = .labelColor
    var selectable = true
    var serif = false
    private var nativeFont: NSFont {
        let base = NSFont.systemFont(ofSize: size, weight: weight)
        return serif ? base.fontDescriptor.withDesign(.serif).flatMap { NSFont(descriptor: $0, size: size) } ?? base : base
    }

    var body: some View {
        if MathTextDocument(source).containsMath {
            SelectablePronunciationText(text: source, selectedText: .constant(""),
                font: nativeFont, color: color, selectable: selectable, pronunciationEnabled: false) { _ in }
                .accessibilityLabel(source)
                .allowsHitTesting(selectable)
        } else {
            if selectable {
                Text(source.replacingOccurrences(of: "`", with: ""))
                    .font(Font(nativeFont))
                    .foregroundStyle(Color(nsColor: color)).textSelection(.enabled)
            } else {
                Text(source.replacingOccurrences(of: "`", with: ""))
                    .font(Font(nativeFont)).foregroundStyle(Color(nsColor: color))
            }
        }
    }
}

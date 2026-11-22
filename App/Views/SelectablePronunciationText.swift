import AppKit
import SwiftUI

/// A read-only AppKit text view that supports native selection and contextual
/// pronunciation while sizing itself to its wrapped contents.
struct SelectablePronunciationText: NSViewRepresentable {
    let text: String
    @Binding var selectedText: String
    var font: NSFont? = nil
    var color: NSColor? = nil
    var selectable = true
    var pronunciationEnabled = true
    let onSpeakSelection: (String) -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(parent: self)
    }

    func makeNSView(context: Context) -> PronunciationTextView {
        let textView = PronunciationTextView()
        textView.delegate = context.coordinator
        textView.speechMenuTarget = context.coordinator
        textView.speechMenuAction = pronunciationEnabled ? #selector(Coordinator.speakSelected(_:)) : nil

        textView.isEditable = false
        textView.isSelectable = selectable
        textView.isRichText = true
        textView.importsGraphics = false
        textView.allowsUndo = false
        textView.drawsBackground = false
        textView.backgroundColor = .clear
        textView.textContainerInset = .zero
        textView.textContainer?.lineFragmentPadding = 0
        textView.textContainer?.widthTracksTextView = true
        textView.isHorizontallyResizable = false
        textView.isVerticallyResizable = true
        textView.setAccessibilityLabel(pronunciationEnabled ? "英文术语，可选择部分文本后朗读" : "数学文本，可选择和复制 LaTeX")

        applyAppearance(to: textView)
        textView.installMathSource(text, font: textView.font!, color: textView.textColor!)
        context.coordinator.textView = textView
        return textView
    }

    func updateNSView(_ textView: PronunciationTextView, context: Context) {
        context.coordinator.parent = self
        applyAppearance(to: textView)

        if textView.mathSource != text {
            context.coordinator.isUpdatingText = true
            textView.installMathSource(text, font: textView.font!, color: textView.textColor!)
            textView.setSelectedRange(NSRange(location: 0, length: 0))
            context.coordinator.isUpdatingText = false

            if !selectedText.isEmpty {
                DispatchQueue.main.async {
                    selectedText = ""
                }
            }
        }
    }

    func sizeThatFits(
        _ proposal: ProposedViewSize,
        nsView textView: PronunciationTextView,
        context: Context
    ) -> CGSize? {
        guard let proposedWidth = proposal.width, proposedWidth > 0 else {
            return nil
        }

        let font = textView.font ?? .systemFont(ofSize: 22, weight: .semibold)
        textView.updateMathLayout(width: proposedWidth)
        textView.textContainer?.containerSize = NSSize(width: proposedWidth, height: .greatestFiniteMagnitude)
        if let container = textView.textContainer { textView.layoutManager?.ensureLayout(for: container) }
        let bounds = textView.textContainer.flatMap { textView.layoutManager?.usedRect(for: $0) } ?? .zero
        let minimumHeight = ceil(textView.layoutManager?.defaultLineHeight(for: font) ?? font.pointSize * 1.25)
        return CGSize(
            width: proposedWidth,
            height: max(minimumHeight, ceil(bounds.height))
        )
    }

    private func applyAppearance(to textView: PronunciationTextView) {
        let baseFont = font ?? NSFont.systemFont(ofSize: 22, weight: .semibold)
        let selectedFont = font ?? baseFont.fontDescriptor.withDesign(.rounded)
            .flatMap { NSFont(descriptor: $0, size: 22) }
            ?? baseFont
        let selectedColor = color ?? NSColor(
            red: 46.0 / 255.0,
            green: 90.0 / 255.0,
            blue: 172.0 / 255.0,
            alpha: 1
        )
        textView.mathFont = selectedFont; textView.mathColor = selectedColor
        if textView.mathSource == nil { textView.font = selectedFont; textView.textColor = selectedColor }
        textView.selectedTextAttributes = [
            .backgroundColor: NSColor.selectedTextBackgroundColor,
            .foregroundColor: NSColor.selectedTextColor,
        ]
    }

    @MainActor
    final class Coordinator: NSObject, NSTextViewDelegate {
        var parent: SelectablePronunciationText
        weak var textView: NSTextView?
        var isUpdatingText = false

        init(parent: SelectablePronunciationText) {
            self.parent = parent
        }

        func textViewDidChangeSelection(_ notification: Notification) {
            guard !isUpdatingText,
                  let textView = notification.object as? PronunciationTextView, !textView.isRebuildingMath else {
                return
            }
            updateSelection(from: textView)
        }

        @objc func speakSelected(_ sender: Any?) {
            guard let textView,
                  let selection = (textView as? PronunciationTextView)?.mathSelection(textView.selectedRange()), !selection.isEmpty else {
                return
            }

            if parent.selectedText != selection {
                parent.selectedText = selection
            }
            parent.onSpeakSelection(selection)
        }

        private func updateSelection(from textView: NSTextView) {
            let selection = (textView as? PronunciationTextView)?.mathSelection(textView.selectedRange()) ?? ""

            if parent.selectedText != selection {
                parent.selectedText = selection
            }
        }
    }
}

final class PronunciationTextView: NSTextView {
    var mathSource: String?
    private(set) var isRebuildingMath = false
    var mathFont = NSFont.systemFont(ofSize: 22)
    var mathColor = NSColor.labelColor
    private var mathWidth: CGFloat = 0
    private var installedFont: NSFont?
    private var installedColor: NSColor?
    var mathAssets = MathAssets.shared
    var copyPasteboard = NSPasteboard.general
    private var mathOverlays: [Int: NSScrollView] = [:]
    static let formulaSource = NSAttributedString.Key("MathInterviewVocab.formulaSource")
    static let syntheticBreak = NSAttributedString.Key("MathInterviewVocab.syntheticBreak")

    func installMathSource(_ source: String, font: NSFont, color: NSColor) {
        mathSource = source; mathFont = font; mathColor = color
        rebuildMathText(width: max(1, textContainer?.containerSize.width ?? 820))
    }
    func updateMathLayout(width: CGFloat) {
        guard width != mathWidth || mathFont != installedFont || mathColor != installedColor else { return }
        isRebuildingMath = true; defer { isRebuildingMath = false }
        let selection = selectedRange()
        rebuildMathText(width: width)
        if NSMaxRange(selection) <= (string as NSString).length { setSelectedRange(selection) }
    }
    private func rebuildMathText(width: CGFloat) {
        guard let mathSource else { return }
        mathOverlays.values.forEach { $0.removeFromSuperview() }; mathOverlays.removeAll()
        mathWidth = width; installedFont = mathFont; installedColor = mathColor
        let result = NSMutableAttributedString(string: "")
        let paragraph = NSMutableParagraphStyle(); paragraph.lineSpacing = 4
        let attributes: [NSAttributedString.Key: Any] = [.font: mathFont, .foregroundColor: mathColor, .paragraphStyle: paragraph]
        for segment in MathTextDocument(mathSource).segments {
            if let latex = segment.latex {
                if let asset = mathAssets.asset(latex: latex, display: segment.display), let page = mathAssets.page(for: asset) {
                    if segment.display && result.length > 0 { result.append(NSAttributedString(string: "\n", attributes: attributes.merging([Self.syntheticBreak: true]) { _, new in new })) }
                    let attachment = NSTextAttachment()
                    attachment.attachmentCell = MathPDFCell(page: page, asset: asset, font: mathFont, color: mathColor, width: width, source: segment.text)
                    let run = NSMutableAttributedString(attachment: attachment)
                    run.addAttributes(attributes.merging([Self.formulaSource: segment.text]) { _, new in new }, range: NSRange(location: 0, length: run.length))
                    result.append(run)
                    if segment.display { result.append(NSAttributedString(string: "\n", attributes: attributes.merging([Self.syntheticBreak: true]) { _, new in new })) }
                } else {
                    result.append(NSAttributedString(string: "[公式显示异常：\(segment.text)]", attributes: attributes.merging([.foregroundColor: NSColor.systemRed, Self.formulaSource: segment.text]) { _, new in new }))
                }
            } else { result.append(NSAttributedString(string: segment.text, attributes: attributes)) }
        }
        textStorage?.setAttributedString(result)
        setAccessibilityHelp("原始内容（公式复制为 LaTeX）：\(mathSource)")
        needsLayout = true
    }
    override func layout() {
        super.layout()
        guard let storage = textStorage, let manager = layoutManager, let container = textContainer else { return }
        manager.ensureLayout(for: container)
        storage.enumerateAttribute(.attachment, in: NSRange(location: 0, length: storage.length)) { value, range, _ in
            guard let attachment = value as? NSTextAttachment, let cell = attachment.attachmentCell as? MathPDFCell, cell.overflows else { return }
            let glyphs = manager.glyphRange(forCharacterRange: range, actualCharacterRange: nil)
            var rect = manager.boundingRect(forGlyphRange: glyphs, in: container)
            rect.origin.x += textContainerOrigin.x; rect.origin.y += textContainerOrigin.y
            rect.size = cell.cellSize()
            if let overlay = mathOverlays[range.location] { overlay.frame = rect; return }
            let canvas = MathFormulaCanvas(frame: NSRect(origin: .zero, size: cell.naturalSize))
            canvas.cell = cell; canvas.owner = self; canvas.characterRange = range
            let scroll = NSScrollView(frame: rect)
            scroll.drawsBackground = false; scroll.borderType = .noBorder
            scroll.hasHorizontalScroller = true; scroll.hasVerticalScroller = false; scroll.scrollerStyle = .legacy
            scroll.documentView = canvas; addSubview(scroll); mathOverlays[range.location] = scroll
        }
    }
    func mathSelection(_ range: NSRange) -> String {
        guard let storage = textStorage, range.location != NSNotFound, NSMaxRange(range) <= storage.length else { return "" }
        var result = ""
        storage.enumerateAttributes(in: range) { attributes, part, _ in
            guard attributes[Self.syntheticBreak] as? Bool != true else { return }
            result += (attributes[Self.formulaSource] as? String) ?? (storage.string as NSString).substring(with: part)
        }
        return result
    }
    override func copy(_ sender: Any?) {
        let value = mathSelection(selectedRange()); guard !value.isEmpty else { return }
        copyPasteboard.clearContents(); copyPasteboard.setString(value, forType: .string)
    }
    weak var speechMenuTarget: AnyObject?
    var speechMenuAction: Selector?

    private static let separatorIdentifier = NSUserInterfaceItemIdentifier(
        "SelectablePronunciationText.separator"
    )
    private static let itemIdentifier = NSUserInterfaceItemIdentifier(
        "SelectablePronunciationText.speakSelection"
    )

    override func menu(for event: NSEvent) -> NSMenu? {
        guard let menu = super.menu(for: event) else {
            return nil
        }
        guard speechMenuAction != nil else { return menu }

        for item in menu.items.reversed() where
            item.identifier == Self.separatorIdentifier || item.identifier == Self.itemIdentifier {
            menu.removeItem(item)
        }

        let separator = NSMenuItem.separator()
        separator.identifier = Self.separatorIdentifier
        menu.addItem(separator)

        let speakItem = NSMenuItem(
            title: "朗读所选",
            action: speechMenuAction,
            keyEquivalent: ""
        )
        speakItem.identifier = Self.itemIdentifier
        speakItem.target = speechMenuTarget
        speakItem.image = NSImage(systemSymbolName: "speaker.wave.2", accessibilityDescription: nil)
        speakItem.isEnabled = SpeechSelectionSanitizer.sanitize(mathSelection(selectedRange())) != nil
        menu.addItem(speakItem)

        return menu
    }
}

@MainActor
private final class MathFormulaCanvas: NSView {
    weak var owner: PronunciationTextView?
    var cell: MathPDFCell?
    var characterRange = NSRange(location: 0, length: 0)
    override var isFlipped: Bool { true }
    override func draw(_ dirtyRect: NSRect) { cell?.drawPDF(in: bounds, color: cell?.color ?? .labelColor) }
    override func mouseDown(with event: NSEvent) {
        guard let owner else { return }
        owner.window?.makeFirstResponder(owner); owner.setSelectedRange(characterRange)
    }
}

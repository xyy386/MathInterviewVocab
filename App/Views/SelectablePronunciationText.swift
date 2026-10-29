import AppKit
import SwiftUI

/// A read-only AppKit text view that supports native selection and contextual
/// pronunciation while sizing itself to its wrapped contents.
struct SelectablePronunciationText: NSViewRepresentable {
    let text: String
    @Binding var selectedText: String
    let onSpeakSelection: (String) -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(parent: self)
    }

    func makeNSView(context: Context) -> PronunciationTextView {
        let textView = PronunciationTextView()
        textView.delegate = context.coordinator
        textView.speechMenuTarget = context.coordinator
        textView.speechMenuAction = #selector(Coordinator.speakSelected(_:))

        textView.isEditable = false
        textView.isSelectable = true
        textView.isRichText = false
        textView.importsGraphics = false
        textView.allowsUndo = false
        textView.drawsBackground = false
        textView.backgroundColor = .clear
        textView.textContainerInset = .zero
        textView.textContainer?.lineFragmentPadding = 0
        textView.textContainer?.widthTracksTextView = true
        textView.isHorizontallyResizable = false
        textView.isVerticallyResizable = true
        textView.setAccessibilityLabel("英文术语，可选择部分文本后朗读")

        applyAppearance(to: textView)
        textView.string = text
        context.coordinator.textView = textView
        return textView
    }

    func updateNSView(_ textView: PronunciationTextView, context: Context) {
        context.coordinator.parent = self
        applyAppearance(to: textView)

        if textView.string != text {
            context.coordinator.isUpdatingText = true
            textView.string = text
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
        let bounds = (textView.string as NSString).boundingRect(
            with: NSSize(width: proposedWidth, height: .greatestFiniteMagnitude),
            options: [.usesLineFragmentOrigin, .usesFontLeading],
            attributes: [.font: font]
        )
        let minimumHeight = ceil(textView.layoutManager?.defaultLineHeight(for: font) ?? font.pointSize * 1.25)
        return CGSize(
            width: proposedWidth,
            height: max(minimumHeight, ceil(bounds.height))
        )
    }

    private func applyAppearance(to textView: NSTextView) {
        let baseFont = NSFont.systemFont(ofSize: 22, weight: .semibold)
        textView.font = baseFont.fontDescriptor.withDesign(.rounded)
            .flatMap { NSFont(descriptor: $0, size: 22) }
            ?? baseFont
        textView.textColor = NSColor(
            red: 46.0 / 255.0,
            green: 90.0 / 255.0,
            blue: 172.0 / 255.0,
            alpha: 1
        )
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
                  let textView = notification.object as? NSTextView else {
                return
            }
            updateSelection(from: textView)
        }

        @objc func speakSelected(_ sender: Any?) {
            guard let textView,
                  let selection = SpeechSelectionSanitizer.selectedText(
                    in: textView.string,
                    range: textView.selectedRange()
                  ) else {
                return
            }

            if parent.selectedText != selection {
                parent.selectedText = selection
            }
            parent.onSpeakSelection(selection)
        }

        private func updateSelection(from textView: NSTextView) {
            let selection = SpeechSelectionSanitizer.selectedText(
                in: textView.string,
                range: textView.selectedRange()
            ) ?? ""

            if parent.selectedText != selection {
                parent.selectedText = selection
            }
        }
    }
}

final class PronunciationTextView: NSTextView {
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
        speakItem.isEnabled = SpeechSelectionSanitizer.selectedText(
            in: string,
            range: selectedRange()
        ) != nil
        menu.addItem(speakItem)

        return menu
    }
}

import AppKit
import SwiftUI

/// 只接管所属前台窗口中的学习按键，不注册系统全局快捷键。
struct WindowStudyShortcuts: NSViewRepresentable {
    var isEnabled = true
    var scope = StudyShortcutScope.glossary
    let handle: @MainActor (StudyShortcut) -> Bool

    func makeCoordinator() -> Coordinator {
        Coordinator(isEnabled: isEnabled, scope: scope, handle: handle)
    }

    func makeNSView(context: Context) -> ShortcutAnchorView {
        let view = ShortcutAnchorView()
        view.windowChanged = { [weak coordinator = context.coordinator] view in
            coordinator?.attach(to: view)
        }
        return view
    }

    func updateNSView(_ view: ShortcutAnchorView, context: Context) {
        context.coordinator.isEnabled = isEnabled
        context.coordinator.scope = scope
        context.coordinator.handle = handle
        context.coordinator.attach(to: view)
    }

    static func dismantleNSView(_ view: ShortcutAnchorView, coordinator: Coordinator) {
        view.windowChanged = nil
        coordinator.stop()
    }

    @MainActor
    final class Coordinator: NSObject {
        var isEnabled: Bool
        var scope: StudyShortcutScope
        var handle: @MainActor (StudyShortcut) -> Bool
        private weak var anchor: NSView?
        private var monitor: Any?
        private var menuTrackingDepth = 0

        init(
            isEnabled: Bool,
            scope: StudyShortcutScope,
            handle: @escaping @MainActor (StudyShortcut) -> Bool
        ) {
            self.isEnabled = isEnabled
            self.scope = scope
            self.handle = handle
        }

        func attach(to view: NSView) {
            anchor = view
            guard view.window != nil else {
                stop()
                return
            }
            guard monitor == nil else { return }
            NotificationCenter.default.addObserver(
                self, selector: #selector(menuBegan), name: NSMenu.didBeginTrackingNotification, object: nil
            )
            NotificationCenter.default.addObserver(
                self, selector: #selector(menuEnded), name: NSMenu.didEndTrackingNotification, object: nil
            )
            monitor = NSEvent.addLocalMonitorForEvents(matching: .keyDown) { [weak self] event in
                let consumed = MainActor.assumeIsolated {
                    guard let self else { return false }
                    return self.process(event) == nil
                }
                return consumed ? nil : event
            }
        }

        func stop() {
            if let monitor { NSEvent.removeMonitor(monitor) }
            monitor = nil
            menuTrackingDepth = 0
            NotificationCenter.default.removeObserver(self)
        }

        @objc private func menuBegan(_ notification: Notification) {
            menuTrackingDepth += 1
        }

        @objc private func menuEnded(_ notification: Notification) {
            menuTrackingDepth = max(0, menuTrackingDepth - 1)
        }

        func process(_ event: NSEvent) -> NSEvent? {
            guard isEnabled, event.type == .keyDown,
                  let anchor, !anchor.isHiddenOrHasHiddenAncestor,
                  let window = anchor.window,
                  NSApp.isActive, window.isKeyWindow, event.window === window,
                  window.attachedSheet == nil, NSApp.modalWindow == nil,
                  menuTrackingDepth == 0,
                  !Self.protectsNativeInput(in: window),
                  let shortcut = Self.shortcut(for: event), scope.accepts(shortcut) else {
                return event
            }

            // 重复键也要消费，避免原生 List 在长按时继续切词。
            if event.isARepeat { return nil }
            return handle(shortcut) ? nil : event
        }

        static func shortcut(for event: NSEvent) -> StudyShortcut? {
            let key: String
            switch event.keyCode {
            case 123: key = "ArrowLeft"
            case 124: key = "ArrowRight"
            case 125: key = "ArrowDown"
            case 126: key = "ArrowUp"
            case 36, 76: key = "Enter"
            default: key = event.charactersIgnoringModifiers ?? ""
            }
            return StudyShortcut.parse(
                key: key,
                shift: event.modifierFlags.contains(.shift),
                command: event.modifierFlags.contains(.command),
                control: event.modifierFlags.contains(.control),
                option: event.modifierFlags.contains(.option)
            )
        }

        static func protectsNativeInput(in window: NSWindow) -> Bool {
            if let client = window.firstResponder as? any NSTextInputClient,
               client.hasMarkedText() || client.selectedRange().length > 0 {
                return true
            }
            if let text = window.firstResponder as? NSTextView,
               text.isEditable || text.isFieldEditor || text.selectedRange().length > 0 {
                return true
            }
            var view = window.firstResponder as? NSView
            while let current = view {
                if current is NSTextField || current is NSPopUpButton || current is NSComboBox
                    || current is NSSlider || current is NSStepper {
                    return true
                }
                if let role = current.accessibilityRole(),
                   [NSAccessibility.Role.splitter, .slider, .popUpButton, .comboBox].contains(role) {
                    return true
                }
                view = current.superview
            }
            return containsSelection(in: window.contentView)
        }

        private static func containsSelection(in view: NSView?) -> Bool {
            guard let view, !view.isHiddenOrHasHiddenAncestor else { return false }
            if let text = view as? NSTextView, text.isSelectable, text.selectedRange().length > 0 {
                return true
            }
            return view.subviews.contains { containsSelection(in: $0) }
        }
    }
}

final class ShortcutAnchorView: NSView {
    var windowChanged: (@MainActor (ShortcutAnchorView) -> Void)?

    override func viewDidMoveToWindow() {
        super.viewDidMoveToWindow()
        windowChanged?(self)
    }
}

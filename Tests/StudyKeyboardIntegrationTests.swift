import AppKit
import Observation
import SwiftUI
import Testing
@testable import MathInterviewVocab

@Suite(.serialized)
@MainActor
struct StudyKeyboardIntegrationTests {
    @Test
    func nativeEventsMapArrowsLettersDigitsAndBothEnterKeys() throws {
        let fixture = KeyboardWindowFixture()
        defer { fixture.close() }
        let cases: [(UInt16, String, StudyShortcut)] = [
            (123, "", .previous), (126, "", .previous),
            (124, "", .next), (125, "", .next),
            (0, "a", .answer(0)), (11, "B", .answer(1)),
            (8, "c", .answer(2)), (2, "D", .answer(3)),
            (18, "1", .answer(0)), (19, "2", .answer(1)),
            (20, "3", .answer(2)), (21, "4", .answer(3)),
            (83, "1", .answer(0)), (84, "2", .answer(1)),
            (85, "3", .answer(2)), (86, "4", .answer(3)),
            (36, "\r", .advance), (76, "\u{3}", .advance),
        ]
        for (code, characters, expected) in cases {
            let event = try fixture.event(code: code, characters: characters)
            #expect(WindowStudyShortcuts.Coordinator.shortcut(for: event) == expected)
        }
        #expect(WindowStudyShortcuts.Coordinator.shortcut(
            for: try fixture.event(code: 0, characters: "A", modifiers: .shift)
        ) == .answer(0))
        for modifiers: NSEvent.ModifierFlags in [.command, .control, .option, .shift] {
            #expect(WindowStudyShortcuts.Coordinator.shortcut(
                for: try fixture.event(code: 125, modifiers: modifiers)
            ) == nil)
        }
    }

    @Test
    func scopedMonitorConsumesOnceAndStopsOnDisableHideAndDismantle() async throws {
        let fixture = KeyboardWindowFixture()
        defer { fixture.close() }
        var actions: [StudyShortcut] = []
        let coordinator = WindowStudyShortcuts.Coordinator(isEnabled: true, scope: .glossary) {
            actions.append($0)
            return true
        }
        coordinator.attach(to: fixture.anchor)
        defer { coordinator.stop() }
        try await fixture.requireForeground()

        let down = try fixture.event(code: 125)
        #expect(coordinator.process(down) == nil)
        #expect(actions == [.next])
        #expect(coordinator.process(try fixture.event(code: 125, repeatKey: true)) == nil)
        #expect(actions == [.next])
        #expect(coordinator.process(try fixture.event(code: 0, characters: "a")) != nil)
        coordinator.scope = .quiz
        #expect(coordinator.process(try fixture.event(code: 0, characters: "a")) == nil)
        #expect(actions == [.next, .answer(0)])
        #expect(coordinator.process(try fixture.event(code: 123)) != nil)

        coordinator.isEnabled = false
        #expect(coordinator.process(down) != nil)
        coordinator.isEnabled = true
        fixture.anchor.isHidden = true
        #expect(coordinator.process(down) != nil)
        fixture.anchor.isHidden = false
        let countBeforeDispatch = actions.count
        NSApp.sendEvent(down)
        #expect(actions.count == countBeforeDispatch + 1)

        WindowStudyShortcuts.dismantleNSView(fixture.anchor, coordinator: coordinator)
        let countAfterDismantle = actions.count
        NSApp.sendEvent(down)
        #expect(actions.count == countAfterDismantle)
    }

    @Test
    func otherWindowsMenusAndSheetsKeepTheirOwnKeys() async throws {
        let fixture = KeyboardWindowFixture()
        defer { fixture.close() }
        var actionCount = 0
        let coordinator = WindowStudyShortcuts.Coordinator(isEnabled: true, scope: .glossary) { _ in
            actionCount += 1
            return true
        }
        coordinator.attach(to: fixture.anchor)
        defer { coordinator.stop() }
        try await fixture.requireForeground()

        let menu = NSMenu(title: "Test menu")
        NotificationCenter.default.post(name: NSMenu.didBeginTrackingNotification, object: menu)
        #expect(coordinator.process(try fixture.event(code: 125)) != nil)
        NotificationCenter.default.post(name: NSMenu.didEndTrackingNotification, object: menu)
        #expect(coordinator.process(try fixture.event(code: 125)) == nil)
        #expect(actionCount == 1)

        let other = NSWindow(contentRect: NSRect(x: 80, y: 80, width: 300, height: 200),
                             styleMask: [.titled], backing: .buffered, defer: false)
        other.isReleasedWhenClosed = false
        defer { other.close() }
        other.makeKeyAndOrderFront(nil)
        KeyboardWindowFixture.pump()
        #expect(coordinator.process(try fixture.event(code: 125)) != nil)
        let otherEvent = try fixture.event(code: 125, windowNumber: other.windowNumber)
        #expect(coordinator.process(otherEvent) != nil)
        fixture.window.makeKeyAndOrderFront(nil)
        KeyboardWindowFixture.pump()
        #expect(coordinator.process(otherEvent) != nil)

        other.orderOut(nil)
        fixture.window.beginSheet(other, completionHandler: nil)
        KeyboardWindowFixture.pump()
        #expect(fixture.window.attachedSheet === other)
        #expect(coordinator.process(try fixture.event(code: 125)) != nil)
        fixture.window.endSheet(other)
        other.orderOut(nil)
        KeyboardWindowFixture.pump()
        #expect(actionCount == 1)
    }

    @Test
    func editingSelectionIMEAndNativeControlsAreProtected() throws {
        let fixture = KeyboardWindowFixture()
        defer { fixture.close() }
        let text = NSTextView(frame: NSRect(x: 0, y: 0, width: 300, height: 50))
        text.string = "unbounded function"
        fixture.container.addSubview(text)
        fixture.window.makeFirstResponder(text)
        #expect(WindowStudyShortcuts.Coordinator.protectsNativeInput(in: fixture.window))

        text.isEditable = false
        text.isSelectable = true
        text.setSelectedRange(NSRange(location: 0, length: 9))
        #expect(WindowStudyShortcuts.Coordinator.protectsNativeInput(in: fixture.window))
        fixture.window.makeFirstResponder(fixture.container)
        #expect(WindowStudyShortcuts.Coordinator.protectsNativeInput(in: fixture.window))
        text.setSelectedRange(NSRange(location: 0, length: 0))
        #expect(!WindowStudyShortcuts.Coordinator.protectsNativeInput(in: fixture.window))

        let markedText = MarkedTextView(frame: NSRect(x: 0, y: 60, width: 300, height: 50))
        markedText.isEditable = false
        markedText.isSelectable = true
        fixture.container.addSubview(markedText)
        fixture.window.makeFirstResponder(markedText)
        #expect(WindowStudyShortcuts.Coordinator.protectsNativeInput(in: fixture.window))
        markedText.removeFromSuperview()

        for control: NSControl in [KeyboardPopUpButton(), KeyboardComboBox(), KeyboardSlider(),
                                   KeyboardStepper(), KeyboardTextField()] {
            fixture.container.addSubview(control)
            // 直接保护控件焦点，避免 test field editor 依赖用户输入。
            try #require(fixture.window.makeFirstResponder(control))
            #expect(WindowStudyShortcuts.Coordinator.protectsNativeInput(in: fixture.window))
            control.removeFromSuperview()
        }
        let splitter = KeyboardSplitterView()
        fixture.container.addSubview(splitter)
        try #require(fixture.window.makeFirstResponder(splitter))
        #expect(WindowStudyShortcuts.Coordinator.protectsNativeInput(in: fixture.window))
        try #require(fixture.window.makeFirstResponder(fixture.container))
        #expect(!WindowStudyShortcuts.Coordinator.protectsNativeInput(in: fixture.window))
    }

    @Test
    func nativeListMovesExactlyOneItemPerDispatchedArrow() async throws {
        let fixture = KeyboardWindowFixture()
        defer { fixture.close() }
        let state = ListKeyboardState()
        fixture.setContent(ListKeyboardHarness(state: state))
        try await fixture.requireForeground()
        let table = try #require(fixture.nativeView(of: NSTableView.self))
        try #require(fixture.window.makeFirstResponder(table))
        NSApp.sendEvent(try fixture.event(code: 125))
        KeyboardWindowFixture.pump()
        #expect(state.selection == 10)
        NSApp.sendEvent(try fixture.event(code: 125))
        KeyboardWindowFixture.pump()
        #expect(state.selection == 30)
        NSApp.sendEvent(try fixture.event(code: 125, repeatKey: true))
        KeyboardWindowFixture.pump()
        #expect(state.selection == 30)
        NSApp.sendEvent(try fixture.event(code: 124))
        KeyboardWindowFixture.pump()
        #expect(state.selection == 80)
        NSApp.sendEvent(try fixture.event(code: 125))
        KeyboardWindowFixture.pump()
        #expect(state.selection == 80)
    }

    @Test
    func glossaryPageDispatchesOnceAndPreservesTheSelectedEnglishText() async throws {
        let fixture = KeyboardWindowFixture()
        let data = try IsolatedLearningData()
        defer { fixture.close(); data.close() }
        fixture.setContent(GlossaryView().environment(data.library).environment(data.progress).environment(data.speech))
        try await KeyboardWindowFixture.settle()
        try await fixture.requireForeground()
        try #require(data.library.loadError == nil)
        try #require(data.library.terms.count == 326)
        try #require(fixture.window.makeFirstResponder(fixture.container))

        NSApp.sendEvent(try fixture.event(code: 125))
        try await KeyboardWindowFixture.settle()
        #expect(fixture.pronunciationText()?.string == MathNotation.display(data.library.terms[0].english))
        NSApp.sendEvent(try fixture.event(code: 125))
        try await KeyboardWindowFixture.settle()
        #expect(fixture.pronunciationText()?.string == MathNotation.display(data.library.terms[1].english))

        let english = try #require(fixture.pronunciationText())
        english.setSelectedRange(NSRange(location: 0, length: min(3, english.string.utf16.count)))
        NSApp.sendEvent(try fixture.event(code: 124))
        try await KeyboardWindowFixture.settle()
        #expect(fixture.pronunciationText()?.string == MathNotation.display(data.library.terms[1].english))
        english.setSelectedRange(NSRange(location: 0, length: 0))

        // 使用缺失片段设置播放状态，不让验收自动播放出声。
        data.speech.speak(text: "test-only-missing-audio-fragment")
        NSApp.sendEvent(try fixture.event(code: 124))
        try await KeyboardWindowFixture.settle()
        #expect(fixture.pronunciationText()?.string == MathNotation.display(data.library.terms[2].english))
        #expect(data.speech.playbackSource == .idle)
        #expect(data.progress.attempts.isEmpty)

        // 大跨度切词后，原生列表的选中行必须仍在可见区，不能只更新右侧详情。
        for _ in 3..<data.library.terms.count {
            NSApp.sendEvent(try fixture.event(code: 124))
        }
        try await KeyboardWindowFixture.settle()
        let lastTerm = try #require(data.library.terms.last)
        #expect(fixture.pronunciationText()?.string == MathNotation.display(lastTerm.english))
        let table = try #require(fixture.nativeView(of: NSTableView.self))
        print("GLOSSARY_SCROLL_PROOF table=\(type(of: table)),rows=\(table.numberOfRows),selected=\(table.selectedRow),visible=\(table.visibleRect),visibleRows=\(table.rows(in: table.visibleRect)),bounds=\(table.bounds),hidden=\(table.isHiddenOrHasHiddenAncestor)")
        try #require(table.numberOfRows >= data.library.terms.count)
        #expect(table.visibleRect.intersects(table.rect(ofRow: table.numberOfRows - 1)))
        #expect(NSMaxRange(table.rows(in: table.visibleRect)) == table.numberOfRows)

        try fixture.captureIfRequested(name: "glossary", widths: [1000, 1220, 1440])
    }

    @Test
    func quizPageRecordsEachAnswerOnlyOnceAndWillNotSkipUnansweredQuestions() async throws {
        let fixture = KeyboardWindowFixture()
        let data = try IsolatedLearningData()
        defer { fixture.close(); data.close() }
        data.progress.updatePreferences(QuizPreferences(direction: .zhToEn, scope: .all,
                                                       questionLength: .ten, includeMastered: true))
        fixture.setContent(QuizView().environment(data.library).environment(data.progress).environment(data.speech))
        try await fixture.requireForeground()
        try #require(data.library.loadError == nil)
        try fixture.startQuizUsingNativeMouse()
        KeyboardWindowFixture.pump()
        try #require(fixture.window.makeFirstResponder(fixture.container))

        NSApp.sendEvent(try fixture.event(code: 125))
        NSApp.sendEvent(try fixture.event(code: 36, characters: "\r"))
        #expect(data.progress.attempts.isEmpty)
        NSApp.sendEvent(try fixture.event(code: 0, characters: "a"))
        NSApp.sendEvent(try fixture.event(code: 11, characters: "b"))
        NSApp.sendEvent(try fixture.event(code: 0, characters: "a", repeatKey: true))
        #expect(data.progress.attempts.count == 1)
        let firstTermID = try #require(data.progress.attempts.first?.termID)

        NSApp.sendEvent(try fixture.event(code: 124))
        NSApp.sendEvent(try fixture.event(code: 125))
        KeyboardWindowFixture.pump()
        NSApp.sendEvent(try fixture.event(code: 84, characters: "2"))
        #expect(data.progress.attempts.count == 2)
        #expect(data.progress.attempts.last?.termID != firstTermID)
        NSApp.sendEvent(try fixture.event(code: 123))
        NSApp.sendEvent(try fixture.event(code: 8, characters: "C", modifiers: .shift))
        #expect(data.progress.attempts.count == 2)
        NSApp.sendEvent(try fixture.event(code: 76, characters: "\u{3}"))
        KeyboardWindowFixture.pump()

        // 每题用不同键位作答；最后一题继续必须进入总结，不能再写入记录。
        let remainingKeys: [(UInt16, String)] = [(8, "C"), (2, "D"), (18, "1"), (19, "2"),
                                               (20, "3"), (21, "4"), (85, "3"), (86, "4")]
        for (offset, key) in remainingKeys.enumerated() {
            NSApp.sendEvent(try fixture.event(code: key.0, characters: key.1))
            #expect(data.progress.attempts.count == offset + 3)
            NSApp.sendEvent(try fixture.event(code: 125))
            KeyboardWindowFixture.pump()
        }
        #expect(data.progress.attempts.count == 10)
        NSApp.sendEvent(try fixture.event(code: 0, characters: "a"))
        NSApp.sendEvent(try fixture.event(code: 36, characters: "\r"))
        #expect(data.progress.attempts.count == 10)
        #expect(fixture.pronunciationText() == nil)
        #expect(ProgressStore(fileURL: data.progress.storageURL).attempts.count == 10)
        try fixture.captureIfRequested(name: "quiz-summary", widths: [1000, 1220, 1440])
    }
}

@MainActor
private final class KeyboardWindowFixture {
    let window: NSWindow
    let container: NSView
    let anchor = ShortcutAnchorView()
    private let previousKeyWindow: NSWindow?

    init() {
        previousKeyWindow = NSApp.keyWindow
        window = NSWindow(contentRect: NSRect(x: 100, y: 100, width: 1000, height: 680),
                          styleMask: [.titled, .closable, .resizable], backing: .buffered, defer: false)
        window.isReleasedWhenClosed = false
        window.title = "Study shortcuts isolated test"
        container = KeyboardContentView(frame: NSRect(x: 0, y: 0, width: 1000, height: 680))
        window.contentView = container
        container.addSubview(anchor)
        NSApp.unhide(nil)
        NSRunningApplication.current.activate(options: [.activateAllWindows])
        window.makeKeyAndOrderFront(nil)
        NSApp.activate()
        Self.pump()
    }

    func setContent(_ content: some View) {
        let host = NSHostingView(rootView: content)
        host.frame = container.bounds
        host.autoresizingMask = [.width, .height]
        container.addSubview(host)
        Self.pump()
    }

    func requireForeground() async throws {
        var deadline = Date(timeIntervalSinceNow: 0.5)
        repeat {
            NSApp.unhide(nil)
            NSRunningApplication.current.activate(options: [.activateAllWindows])
            NSApp.activate()
            window.makeKeyAndOrderFront(nil)
            Self.pump()
            try await Task.sleep(for: .milliseconds(20))
        } while (!NSApp.isActive || !window.isKeyWindow) && Date() < deadline
        if !NSApp.isActive {
            let hostURL = Bundle.main.bundleURL
            try #require(hostURL.pathExtension == "app")
            try #require(Bundle.main.bundleIdentifier == "com.xingyiyao.MathInterviewVocab")
            let activateHost = Process()
            activateHost.executableURL = URL(fileURLWithPath: "/usr/bin/open")
            activateHost.arguments = ["-a", hostURL.path]
            try activateHost.run()
            activateHost.waitUntilExit()
            try #require(activateHost.terminationStatus == 0)
            deadline = Date(timeIntervalSinceNow: 5)
            repeat {
                NSRunningApplication.current.activate(options: [.activateAllWindows])
                NSApp.activate()
                window.makeKeyAndOrderFront(nil)
                Self.pump()
                try await Task.sleep(for: .milliseconds(20))
            } while (!NSApp.isActive || !window.isKeyWindow) && Date() < deadline
        }
        try #require(NSApp.isActive)
        try #require(window.isKeyWindow)
    }

    func event(
        code: UInt16,
        characters: String = "",
        modifiers: NSEvent.ModifierFlags = [],
        repeatKey: Bool = false,
        windowNumber: Int? = nil
    ) throws -> NSEvent {
        let arrowCharacters: [UInt16: String] = [123: "\u{f702}", 124: "\u{f703}",
                                                125: "\u{f701}", 126: "\u{f700}"]
        let nativeCharacters = characters.isEmpty ? arrowCharacters[code] ?? "" : characters
        return try #require(NSEvent.keyEvent(
            with: .keyDown, location: .zero, modifierFlags: modifiers,
            timestamp: ProcessInfo.processInfo.systemUptime,
            windowNumber: windowNumber ?? window.windowNumber, context: nil,
            characters: nativeCharacters, charactersIgnoringModifiers: nativeCharacters,
            isARepeat: repeatKey, keyCode: code
        ))
    }

    func close() {
        window.close()
        previousKeyWindow?.makeKeyAndOrderFront(nil)
        Self.pump()
    }

    func pronunciationText() -> PronunciationTextView? {
        nativeView(of: PronunciationTextView.self)
    }

    func nativeView<T: NSView>(of type: T.Type) -> T? {
        func find(in view: NSView) -> T? {
            if let result = view as? T { return result }
            for child in view.subviews {
                if let result = find(in: child) { return result }
            }
            return nil
        }
        return find(in: container)
    }

    func startQuizUsingNativeMouse() throws {
        let scroll = try #require(nativeView(of: NSScrollView.self))
        let document = try #require(scroll.documentView)
        let clip = scroll.contentView
        let bottom = document.isFlipped ? max(0, document.bounds.maxY - clip.bounds.height) : document.bounds.minY
        clip.scroll(to: NSPoint(x: clip.bounds.minX, y: bottom))
        scroll.reflectScrolledClipView(clip)
        Self.pump()
        try captureIfRequested(name: "quiz-setup-bottom", widths: [container.bounds.width], height: container.bounds.height)

        // 固定1000×680测试窗口中，经截图确认开始按钮中心位于(500,146.5)。
        // 页面内容未铺满窗口，不能把内容底部padding误当成窗口底部间距。
        try #require(container.bounds.size == NSSize(width: 1000, height: 680))
        let location = container.convert(NSPoint(x: 500, y: 146.5), to: nil)
        func mouseEvent(_ type: NSEvent.EventType) throws -> NSEvent {
            try #require(NSEvent.mouseEvent(
                with: type, location: location, modifierFlags: [],
                timestamp: ProcessInfo.processInfo.systemUptime,
                windowNumber: window.windowNumber, context: nil,
                eventNumber: 1, clickCount: 1, pressure: type == .leftMouseDown ? 1 : 0
            ))
        }
        // 原生控件可能同步跟踪mouseUp，先排队释放事件，避免测试卡在mouseDown。
        NSApp.postEvent(try mouseEvent(.leftMouseUp), atStart: true)
        NSApp.sendEvent(try mouseEvent(.leftMouseDown))
        if let release = NSApp.nextEvent(matching: .leftMouseUp, until: Date(), inMode: .default, dequeue: true) {
            if release.window === window { NSApp.sendEvent(release) }
            else { NSApp.postEvent(release, atStart: true) }
        }
        Self.pump()
    }

    func captureIfRequested(name: String, widths: [CGFloat], height: CGFloat = 780) throws {
        guard let directory = ProcessInfo.processInfo.environment["MATH_VOCAB_SHORTCUT_SCREENSHOTS"] else { return }
        let destination = URL(fileURLWithPath: directory, isDirectory: true)
        for width in widths {
            window.setContentSize(NSSize(width: width, height: height))
            Self.pump()
            guard let bitmap = container.bitmapImageRepForCachingDisplay(in: container.bounds) else {
                Issue.record("无法生成测试窗口截图")
                continue
            }
            container.cacheDisplay(in: container.bounds, to: bitmap)
            let png = try #require(bitmap.representation(using: .png, properties: [:]))
            try png.write(to: destination.appendingPathComponent("mac-\(name)-\(Int(width)).png"), options: .atomic)
        }
    }

    static func pump() {
        RunLoop.current.run(until: Date(timeIntervalSinceNow: 0.06))
    }

    static func settle() async throws {
        await Task.yield()
        try await Task.sleep(for: .milliseconds(100))
        pump()
    }
}

@MainActor
private final class IsolatedLearningData {
    let library = TermLibrary()
    let progress: ProgressStore
    let speech: SpeechService
    private let directory: URL
    private let suiteName = "MathInterviewVocab.ShortcutTests.\(UUID().uuidString)"
    private let defaults: UserDefaults

    init() throws {
        directory = FileManager.default.temporaryDirectory.appendingPathComponent(
            "MathInterviewVocabShortcutTests-\(UUID().uuidString)", isDirectory: true
        )
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        progress = ProgressStore(fileURL: directory.appendingPathComponent("progress.json"))
        defaults = try #require(UserDefaults(suiteName: suiteName))
        speech = SpeechService(defaults: defaults)
    }

    func close() {
        speech.stop()
        defaults.removePersistentDomain(forName: suiteName)
        try? FileManager.default.removeItem(at: directory)
    }
}

@MainActor
private final class MarkedTextView: NSTextView {
    override func hasMarkedText() -> Bool { true }
}

@MainActor
private final class KeyboardContentView: NSView {
    override var acceptsFirstResponder: Bool { true }
}

@MainActor
private final class KeyboardSplitterView: NSView {
    override var acceptsFirstResponder: Bool { true }
    override func accessibilityRole() -> NSAccessibility.Role? { .splitter }
}

@MainActor
private final class KeyboardPopUpButton: NSPopUpButton {
    override var acceptsFirstResponder: Bool { true }
}

@MainActor
private final class KeyboardComboBox: NSComboBox {
    override var acceptsFirstResponder: Bool { true }
}

@MainActor
private final class KeyboardSlider: NSSlider {
    override var acceptsFirstResponder: Bool { true }
}

@MainActor
private final class KeyboardStepper: NSStepper {
    override var acceptsFirstResponder: Bool { true }
}

@MainActor
private final class KeyboardTextField: NSTextField {
    override var acceptsFirstResponder: Bool { true }
}

@MainActor
@Observable
private final class ListKeyboardState {
    let ids = [10, 30, 80]
    var selection: Int?
}

private struct ListKeyboardHarness: View {
    @Bindable var state: ListKeyboardState

    var body: some View {
        List(selection: $state.selection) {
            ForEach(state.ids, id: \.self) { id in
                Text("Term \(id)").tag(id)
            }
        }
        .background {
            WindowStudyShortcuts { action in
                guard action == .previous || action == .next else { return false }
                state.selection = StudyShortcut.adjacentID(
                    in: state.ids, selectedID: state.selection, direction: action
                )
                return true
            }
        }
    }
}

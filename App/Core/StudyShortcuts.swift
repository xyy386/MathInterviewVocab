import Foundation

enum StudyShortcutScope: Sendable {
    case glossary
    case quiz

    func accepts(_ shortcut: StudyShortcut) -> Bool {
        switch (self, shortcut) {
        case (.glossary, .previous), (.glossary, .next),
             (.quiz, .answer), (.quiz, .next), (.quiz, .advance): true
        default: false
        }
    }
}

enum StudyShortcut: Equatable, Sendable {
    case previous
    case next
    case answer(Int)
    case advance

    static func parse(
        key: String,
        shift: Bool = false,
        command: Bool = false,
        control: Bool = false,
        option: Bool = false
    ) -> StudyShortcut? {
        guard !command, !control, !option else { return nil }

        let letters = ["a", "b", "c", "d"]
        if let index = letters.firstIndex(of: key.lowercased()) {
            return .answer(index)
        }
        guard !shift else { return nil }

        switch key {
        case "ArrowLeft", "ArrowUp": return .previous
        case "ArrowRight", "ArrowDown": return .next
        case "Enter": return .advance
        case "1", "2", "3", "4":
            guard let number = Int(key) else { return nil }
            return .answer(number - 1)
        default: return nil
        }
    }

    static func adjacentID(
        in visibleIDs: [Int],
        selectedID: Int?,
        direction: StudyShortcut
    ) -> Int? {
        guard !visibleIDs.isEmpty, direction == .previous || direction == .next else {
            return nil
        }
        guard let selectedID, let index = visibleIDs.firstIndex(of: selectedID) else {
            return direction == .next ? visibleIDs.first : visibleIDs.last
        }
        let nextIndex = direction == .next
            ? min(index + 1, visibleIDs.count - 1)
            : max(index - 1, 0)
        return visibleIDs[nextIndex]
    }
}

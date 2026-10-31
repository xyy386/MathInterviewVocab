import Testing
@testable import MathInterviewVocab

struct StudyShortcutsTests {
    @Test(arguments: ["ArrowLeft", "ArrowUp"])
    func previousKeys(key: String) {
        #expect(StudyShortcut.parse(key: key) == .previous)
    }

    @Test(arguments: ["ArrowRight", "ArrowDown"])
    func nextKeys(key: String) {
        #expect(StudyShortcut.parse(key: key) == .next)
    }

    @Test(arguments: Array("abcdABCD1234").map(String.init))
    func answerKeysMatchVisibleOptionOrder(key: String) {
        let expected = ["a": 0, "b": 1, "c": 2, "d": 3, "1": 0, "2": 1, "3": 2, "4": 3]
        #expect(StudyShortcut.parse(key: key) == expected[key.lowercased()].map(StudyShortcut.answer))
    }

    @Test
    func enterAdvancesAndUnknownKeysRemainNative() {
        #expect(StudyShortcut.parse(key: "Enter") == .advance)
        for key in ["", "0", "5", "ab", "Space", "Escape", "Home", "End", "Tab"] {
            #expect(StudyShortcut.parse(key: key) == nil)
        }
    }

    @Test(arguments: ["a", "D", "1", "Enter", "ArrowDown", "ArrowLeft"])
    func commandControlAndOptionNeverBecomeStudyActions(key: String) {
        #expect(StudyShortcut.parse(key: key, command: true) == nil)
        #expect(StudyShortcut.parse(key: key, control: true) == nil)
        #expect(StudyShortcut.parse(key: key, option: true) == nil)
    }

    @Test
    func shiftOnlyPermitsLetterCase() {
        #expect(StudyShortcut.parse(key: "A", shift: true) == .answer(0))
        #expect(StudyShortcut.parse(key: "d", shift: true) == .answer(3))
        for key in ["ArrowLeft", "ArrowRight", "ArrowUp", "ArrowDown", "Enter", "1"] {
            #expect(StudyShortcut.parse(key: key, shift: true) == nil)
        }
    }

    @Test
    func scopesRejectActionsFromOtherPages() {
        #expect(StudyShortcutScope.glossary.accepts(.previous))
        #expect(StudyShortcutScope.glossary.accepts(.next))
        #expect(!StudyShortcutScope.glossary.accepts(.answer(0)))
        #expect(!StudyShortcutScope.glossary.accepts(.advance))
        #expect(StudyShortcutScope.quiz.accepts(.answer(3)))
        #expect(StudyShortcutScope.quiz.accepts(.next))
        #expect(StudyShortcutScope.quiz.accepts(.advance))
        #expect(!StudyShortcutScope.quiz.accepts(.previous))
    }

    @Test
    func adjacentSelectionUsesFilteredDisplayOrderRatherThanNumericIDs() {
        let ids = [91, 4, 260, 12]
        #expect(StudyShortcut.adjacentID(in: ids, selectedID: 4, direction: .next) == 260)
        #expect(StudyShortcut.adjacentID(in: ids, selectedID: 260, direction: .previous) == 4)
        #expect(StudyShortcut.adjacentID(in: ids, selectedID: 91, direction: .previous) == 91)
        #expect(StudyShortcut.adjacentID(in: ids, selectedID: 12, direction: .next) == 12)
    }

    @Test
    func noSelectionAndSelectionOutsideFilterStartAtTheRelevantBoundary() {
        for selectedID: Int? in [nil, 999] {
            #expect(StudyShortcut.adjacentID(in: [8, 30, 54], selectedID: selectedID, direction: .next) == 8)
            #expect(StudyShortcut.adjacentID(in: [8, 30, 54], selectedID: selectedID, direction: .previous) == 54)
        }
    }

    @Test
    func emptySingleItemAndNonNavigationActionsAreSafe() {
        #expect(StudyShortcut.adjacentID(in: [], selectedID: nil, direction: .next) == nil)
        #expect(StudyShortcut.adjacentID(in: [], selectedID: 1, direction: .previous) == nil)
        #expect(StudyShortcut.adjacentID(in: [42], selectedID: nil, direction: .next) == 42)
        #expect(StudyShortcut.adjacentID(in: [42], selectedID: 42, direction: .previous) == 42)
        #expect(StudyShortcut.adjacentID(in: [42], selectedID: 42, direction: .next) == 42)
        #expect(StudyShortcut.adjacentID(in: [42], selectedID: 42, direction: .advance) == nil)
        #expect(StudyShortcut.adjacentID(in: [42], selectedID: 42, direction: .answer(0)) == nil)
    }
}

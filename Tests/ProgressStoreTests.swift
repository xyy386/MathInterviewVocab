import Foundation
import XCTest
@testable import MathInterviewVocab

@MainActor
final class ProgressStoreTests: XCTestCase {
    func testDefaultPreferencesStartWithImportantTerms() {
        let preferences = QuizPreferences()
        XCTAssertEqual(preferences.scope, .important)
        XCTAssertEqual(preferences.questionLength, .twenty)
        XCTAssertEqual(preferences.selectedSubjectID, "analysis")
    }

    func testLegacyPreferencesMigrateWithoutLosingScopeOrLength() throws {
        let legacyJSON = #"""
        {
          "direction": "enToZh",
          "questionCount": 326,
          "selectedSubjectIDs": ["algebra"],
          "importantOnly": false,
          "includeMastered": false
        }
        """#

        let preferences = try JSONDecoder().decode(
            QuizPreferences.self,
            from: Data(legacyJSON.utf8)
        )

        XCTAssertEqual(preferences.direction, .enToZh)
        XCTAssertEqual(preferences.scope, .subject)
        XCTAssertEqual(preferences.questionLength, .all)
        XCTAssertEqual(preferences.selectedSubjectID, "algebra")
        XCTAssertFalse(preferences.includeMastered)
    }

    func testNewPreferencesRoundTripPreservesFavoritesScope() throws {
        let preferences = QuizPreferences(
            direction: .mixed,
            scope: .favorites,
            questionLength: .fifty,
            selectedSubjectID: "statistics",
            includeMastered: false
        )

        let data = try JSONEncoder().encode(preferences)
        let decoded = try JSONDecoder().decode(QuizPreferences.self, from: data)

        XCTAssertEqual(decoded, preferences)
        XCTAssertEqual(QuizLength.all.resolvedCount(availableCount: 73), 73)
        XCTAssertEqual(QuizLength.fifty.resolvedCount(availableCount: 21), 21)
    }

    func testWrongMasteryAndFavoriteStatePersistsAcrossReloads() throws {
        let directoryURL = FileManager.default.temporaryDirectory
            .appendingPathComponent(
                "MathInterviewVocabTests-\(UUID().uuidString)",
                isDirectory: true
            )
        let fileURL = directoryURL.appendingPathComponent("progress.json")
        defer {
            try? FileManager.default.removeItem(at: directoryURL)
        }

        let termID = 17
        let store = ProgressStore(fileURL: fileURL)

        XCTAssertTrue(store.toggleFavorite(termID))
        store.recordAttempt(
            makeAttempt(termID: termID, isCorrect: false)
        )

        XCTAssertTrue(store.isFavorite(termID))
        XCTAssertTrue(store.isWrong(termID))
        XCTAssertFalse(store.isMastered(termID))
        XCTAssertEqual(store.correctStreak[termID], 0)

        let afterWrongReload = ProgressStore(fileURL: fileURL)
        XCTAssertTrue(afterWrongReload.isFavorite(termID))
        XCTAssertTrue(afterWrongReload.isWrong(termID))
        XCTAssertFalse(afterWrongReload.isMastered(termID))
        XCTAssertEqual(afterWrongReload.attempts.count, 1)

        afterWrongReload.recordAttempt(
            makeAttempt(termID: termID, isCorrect: true)
        )
        XCTAssertTrue(afterWrongReload.isWrong(termID))
        XCTAssertFalse(afterWrongReload.isMastered(termID))

        afterWrongReload.recordAttempt(
            makeAttempt(termID: termID, isCorrect: true)
        )
        XCTAssertFalse(afterWrongReload.isWrong(termID))
        XCTAssertFalse(afterWrongReload.isMastered(termID))

        afterWrongReload.recordAttempt(
            makeAttempt(termID: termID, isCorrect: true)
        )
        XCTAssertFalse(afterWrongReload.isWrong(termID))
        XCTAssertTrue(afterWrongReload.isMastered(termID))

        let finalReload = ProgressStore(fileURL: fileURL)
        XCTAssertTrue(finalReload.isFavorite(termID))
        XCTAssertFalse(finalReload.isWrong(termID))
        XCTAssertTrue(finalReload.isMastered(termID))
        XCTAssertEqual(finalReload.correctStreak[termID], 3)
        XCTAssertEqual(finalReload.attempts.count, 4)
        XCTAssertNil(finalReload.lastPersistenceError)
    }

    private func makeAttempt(
        termID: Int,
        isCorrect: Bool
    ) -> QuizAttempt {
        QuizAttempt(
            termID: termID,
            direction: .zhToEn,
            selectedOptionID: isCorrect ? termID : termID + 1_000,
            correctOptionID: termID,
            answeredAt: Date(timeIntervalSince1970: 1_700_000_000)
        )
    }
}

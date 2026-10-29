import XCTest
@testable import MathInterviewVocab

final class QuizEngineTests: XCTestCase {
    func testSeedProducesDeterministicQuestions() {
        let terms = makeTerms(count: 12)

        let first = QuizEngine(seed: 20260730).makeQuestions(
            from: terms,
            count: 8,
            direction: .mixed
        )
        let second = QuizEngine(seed: 20260730).makeQuestions(
            from: terms,
            count: 8,
            direction: .mixed
        )

        XCTAssertEqual(first, second)
        XCTAssertEqual(first.count, 8)
    }

    func testEveryQuestionHasFourUniqueOptionsIncludingCorrectAnswer() {
        let questions = QuizEngine(seed: 42).makeQuestions(
            from: makeTerms(count: 10),
            count: 10,
            direction: .zhToEn
        )

        XCTAssertEqual(questions.count, 10)

        for question in questions {
            let optionIDs = question.options.map(\.id)
            XCTAssertEqual(question.options.count, 4)
            XCTAssertEqual(Set(optionIDs).count, 4)
            XCTAssertTrue(optionIDs.contains(question.correctOptionID))
            XCTAssertEqual(question.correctOptionID, question.term.id)
        }
    }

    func testSingleQuestionPoolUsesLargerDistractorPool() {
        let allTerms = makeTerms(count: 6)
        let onlyQuestion = [allTerms[2]]

        let questions = QuizEngine(seed: 7).makeQuestions(
            from: onlyQuestion,
            distractorsFrom: allTerms,
            count: 20,
            direction: .enToZh
        )

        XCTAssertEqual(questions.count, 1)
        XCTAssertEqual(questions.first?.term.id, allTerms[2].id)
        XCTAssertEqual(Set(questions[0].options.map(\.id)).count, 4)
    }

    func testPoolWithFewerThanFourUniqueTermsProducesNoQuestions() {
        let terms = makeTerms(count: 3)

        let questions = QuizEngine(seed: 99).makeQuestions(
            from: terms,
            count: 3,
            direction: .zhToEn
        )

        XCTAssertTrue(questions.isEmpty)
    }

    func testPoolCountsExcludeMasteredTermsAcrossScopes() {
        let terms = makeTerms(count: 12)
        let masteredIDs: Set<Int> = [2, 4, 7, 9]

        let importantPool = QuizPoolBuilder.makePool(
            from: terms,
            scope: .important,
            selectedSubjectID: "analysis",
            wrongIDs: [],
            favoriteIDs: [],
            masteredIDs: masteredIDs,
            includeMastered: false
        )
        XCTAssertEqual(importantPool.map(\.id), [6, 8, 10, 12])

        let subjectPool = QuizPoolBuilder.makePool(
            from: terms,
            scope: .subject,
            selectedSubjectID: "analysis",
            wrongIDs: [],
            favoriteIDs: [],
            masteredIDs: masteredIDs,
            includeMastered: false
        )
        XCTAssertEqual(subjectPool.map(\.id), [1, 3, 5, 6])

        let favoritePool = QuizPoolBuilder.makePool(
            from: terms,
            scope: .favorites,
            selectedSubjectID: "analysis",
            wrongIDs: [],
            favoriteIDs: [1, 2, 7, 8],
            masteredIDs: masteredIDs,
            includeMastered: false
        )
        XCTAssertEqual(favoritePool.map(\.id), [1, 8])
    }

    private func makeTerms(count: Int) -> [Term] {
        (1...count).map { id in
            Term(
                id: id,
                subjectID: id <= 6 ? "analysis" : "algebra",
                subjectName: id <= 6 ? "数学分析" : "高等代数",
                subjectNameEn: id <= 6
                    ? "Mathematical Analysis"
                    : "Advanced Algebra",
                chinese: "术语\(id)",
                english: "Term \(id)",
                definition: "Definition \(id)",
                important: id.isMultiple(of: 2)
            )
        }
    }
}

import Foundation
import Observation

struct QuizAttempt: Codable, Identifiable, Hashable, Sendable {
    let id: UUID
    let termID: Int
    let direction: QuizDirection
    let selectedOptionID: Int
    let correctOptionID: Int
    let answeredAt: Date

    var isCorrect: Bool {
        selectedOptionID == correctOptionID
    }

    init(
        id: UUID = UUID(),
        termID: Int,
        direction: QuizDirection,
        selectedOptionID: Int,
        correctOptionID: Int,
        answeredAt: Date = Date()
    ) {
        self.id = id
        self.termID = termID
        self.direction = direction
        self.selectedOptionID = selectedOptionID
        self.correctOptionID = correctOptionID
        self.answeredAt = answeredAt
    }
}

enum QuizScope: String, Codable, CaseIterable, Identifiable, Hashable, Sendable {
    case important
    case all
    case subject
    case mistakes
    case favorites

    var id: String { rawValue }
}

enum QuizLength: String, Codable, CaseIterable, Identifiable, Hashable, Sendable {
    case ten
    case twenty
    case fifty
    case all

    var id: String { rawValue }

    func resolvedCount(availableCount: Int) -> Int {
        let fixedCount: Int? = switch self {
        case .ten: 10
        case .twenty: 20
        case .fifty: 50
        case .all: nil
        }
        return min(fixedCount ?? availableCount, availableCount)
    }

    fileprivate init(legacyQuestionCount: Int) {
        self = switch legacyQuestionCount {
        case 10: .ten
        case 50: .fifty
        case 326: .all
        default: .twenty
        }
    }
}

struct QuizPreferences: Codable, Hashable, Sendable {
    var direction: QuizDirection
    var scope: QuizScope
    var questionLength: QuizLength
    var selectedSubjectID: String
    var includeMastered: Bool

    init(
        direction: QuizDirection = .mixed,
        scope: QuizScope = .important,
        questionLength: QuizLength = .twenty,
        selectedSubjectID: String = "analysis",
        includeMastered: Bool = true
    ) {
        self.direction = direction
        self.scope = scope
        self.questionLength = questionLength
        self.selectedSubjectID = selectedSubjectID
        self.includeMastered = includeMastered
    }

    private enum CodingKeys: String, CodingKey {
        case direction
        case scope
        case questionLength
        case selectedSubjectID
        case includeMastered

        // Legacy keys retained for read compatibility with version 1.0.
        case questionCount
        case selectedSubjectIDs
        case importantOnly
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)

        direction = try container.decodeIfPresent(
            QuizDirection.self,
            forKey: .direction
        ) ?? .mixed
        includeMastered = try container.decodeIfPresent(
            Bool.self,
            forKey: .includeMastered
        ) ?? true

        let legacySubjects = try container.decodeIfPresent(
            Set<String>.self,
            forKey: .selectedSubjectIDs
        ) ?? []
        selectedSubjectID = try container.decodeIfPresent(
            String.self,
            forKey: .selectedSubjectID
        ) ?? legacySubjects.sorted().first ?? "analysis"

        if let storedScope = try container.decodeIfPresent(
            QuizScope.self,
            forKey: .scope
        ) {
            scope = storedScope
        } else if !legacySubjects.isEmpty {
            scope = .subject
        } else {
            let legacyImportantOnly = try container.decodeIfPresent(
                Bool.self,
                forKey: .importantOnly
            ) ?? true
            scope = legacyImportantOnly ? .important : .all
        }

        if let storedLength = try container.decodeIfPresent(
            QuizLength.self,
            forKey: .questionLength
        ) {
            questionLength = storedLength
        } else {
            let legacyCount = try container.decodeIfPresent(
                Int.self,
                forKey: .questionCount
            ) ?? 20
            questionLength = QuizLength(legacyQuestionCount: legacyCount)
        }
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(direction, forKey: .direction)
        try container.encode(scope, forKey: .scope)
        try container.encode(questionLength, forKey: .questionLength)
        try container.encode(selectedSubjectID, forKey: .selectedSubjectID)
        try container.encode(includeMastered, forKey: .includeMastered)
    }
}

@MainActor
@Observable
final class ProgressStore {
    private(set) var attempts: [QuizAttempt]
    private(set) var favoriteIDs: Set<Int>
    private(set) var correctStreak: [Int: Int]
    private(set) var wrongIDs: Set<Int>
    private(set) var masteredIDs: Set<Int>
    private(set) var lastPersistenceError: String?

    var quizPreferences: QuizPreferences {
        didSet {
            persist()
        }
    }

    @ObservationIgnored
    private let fileURL: URL

    @ObservationIgnored
    private let fileManager: FileManager

    init(
        fileURL: URL? = nil,
        fileManager: FileManager = .default
    ) {
        self.fileManager = fileManager
        self.fileURL = fileURL ?? Self.defaultFileURL(using: fileManager)

        do {
            let payload = try Self.load(
                from: self.fileURL,
                using: fileManager
            )
            attempts = payload.attempts
            favoriteIDs = payload.favoriteIDs
            correctStreak = payload.correctStreak
            wrongIDs = payload.wrongIDs
            masteredIDs = payload.masteredIDs
            quizPreferences = payload.quizPreferences
            lastPersistenceError = nil
        } catch {
            let payload = PersistedProgress()
            attempts = payload.attempts
            favoriteIDs = payload.favoriteIDs
            correctStreak = payload.correctStreak
            wrongIDs = payload.wrongIDs
            masteredIDs = payload.masteredIDs
            quizPreferences = payload.quizPreferences
            lastPersistenceError = error.localizedDescription
        }
    }

    var storageURL: URL {
        fileURL
    }

    func isFavorite(_ termID: Int) -> Bool {
        favoriteIDs.contains(termID)
    }

    func isWrong(_ termID: Int) -> Bool {
        wrongIDs.contains(termID)
    }

    func isMastered(_ termID: Int) -> Bool {
        masteredIDs.contains(termID)
    }

    @discardableResult
    func toggleFavorite(_ termID: Int) -> Bool {
        let isNowFavorite: Bool

        if favoriteIDs.remove(termID) != nil {
            isNowFavorite = false
        } else {
            favoriteIDs.insert(termID)
            isNowFavorite = true
        }

        persist()
        return isNowFavorite
    }

    func setFavorite(_ isFavorite: Bool, for termID: Int) {
        if isFavorite {
            favoriteIDs.insert(termID)
        } else {
            favoriteIDs.remove(termID)
        }
        persist()
    }

    func updatePreferences(_ preferences: QuizPreferences) {
        quizPreferences = preferences
    }

    @discardableResult
    func recordAnswer(
        for question: QuizQuestion,
        selectedOptionID: Int,
        answeredAt: Date = Date()
    ) -> Bool {
        let attempt = QuizAttempt(
            termID: question.term.id,
            direction: question.direction,
            selectedOptionID: selectedOptionID,
            correctOptionID: question.correctOptionID,
            answeredAt: answeredAt
        )
        recordAttempt(attempt)
        return attempt.isCorrect
    }

    func recordAttempt(_ attempt: QuizAttempt) {
        attempts.append(attempt)

        if attempt.isCorrect {
            let previousStreak = correctStreak[attempt.termID] ?? 0
            let newStreak = previousStreak == Int.max
                ? Int.max
                : previousStreak + 1
            correctStreak[attempt.termID] = newStreak

            if newStreak >= 2 {
                wrongIDs.remove(attempt.termID)
            }
            if newStreak >= 3 {
                masteredIDs.insert(attempt.termID)
            }
        } else {
            correctStreak[attempt.termID] = 0
            wrongIDs.insert(attempt.termID)
            masteredIDs.remove(attempt.termID)
        }

        persist()
    }

    func reset(keepingPreferences: Bool = false) {
        attempts.removeAll()
        favoriteIDs.removeAll()
        correctStreak.removeAll()
        wrongIDs.removeAll()
        masteredIDs.removeAll()

        if keepingPreferences {
            persist()
        } else {
            quizPreferences = QuizPreferences()
        }
    }

    func save() throws {
        let payload = PersistedProgress(
            attempts: attempts,
            favoriteIDs: favoriteIDs,
            correctStreak: correctStreak,
            wrongIDs: wrongIDs,
            masteredIDs: masteredIDs,
            quizPreferences: quizPreferences
        )
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]

        let parentDirectory = fileURL.deletingLastPathComponent()
        try fileManager.createDirectory(
            at: parentDirectory,
            withIntermediateDirectories: true
        )
        let data = try encoder.encode(payload)
        try data.write(to: fileURL, options: .atomic)
        lastPersistenceError = nil
    }

    private func persist() {
        do {
            try save()
        } catch {
            lastPersistenceError = error.localizedDescription
        }
    }

    private static func defaultFileURL(using fileManager: FileManager) -> URL {
        let fallbackDirectory = URL(
            fileURLWithPath: NSTemporaryDirectory(),
            isDirectory: true
        )
        let baseDirectory = (
            try? fileManager.url(
                for: .applicationSupportDirectory,
                in: .userDomainMask,
                appropriateFor: nil,
                create: true
            )
        ) ?? fallbackDirectory

        return baseDirectory
            .appendingPathComponent("MathInterviewVocab", isDirectory: true)
            .appendingPathComponent("progress.json", isDirectory: false)
    }

    private static func load(
        from fileURL: URL,
        using fileManager: FileManager
    ) throws -> PersistedProgress {
        guard fileManager.fileExists(atPath: fileURL.path) else {
            return PersistedProgress()
        }

        let data = try Data(contentsOf: fileURL)
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return try decoder.decode(PersistedProgress.self, from: data)
    }
}

private struct PersistedProgress: Codable {
    var attempts: [QuizAttempt]
    var favoriteIDs: Set<Int>
    var correctStreak: [Int: Int]
    var wrongIDs: Set<Int>
    var masteredIDs: Set<Int>
    var quizPreferences: QuizPreferences

    init(
        attempts: [QuizAttempt] = [],
        favoriteIDs: Set<Int> = [],
        correctStreak: [Int: Int] = [:],
        wrongIDs: Set<Int> = [],
        masteredIDs: Set<Int> = [],
        quizPreferences: QuizPreferences = QuizPreferences()
    ) {
        self.attempts = attempts
        self.favoriteIDs = favoriteIDs
        self.correctStreak = correctStreak
        self.wrongIDs = wrongIDs
        self.masteredIDs = masteredIDs
        self.quizPreferences = quizPreferences
    }

    private enum CodingKeys: String, CodingKey {
        case attempts
        case favoriteIDs
        case correctStreak
        case wrongIDs
        case masteredIDs
        case quizPreferences
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)

        attempts = try container.decodeIfPresent(
            [QuizAttempt].self,
            forKey: .attempts
        ) ?? []
        favoriteIDs = try container.decodeIfPresent(
            Set<Int>.self,
            forKey: .favoriteIDs
        ) ?? []
        correctStreak = try container.decodeIfPresent(
            [Int: Int].self,
            forKey: .correctStreak
        ) ?? [:]
        wrongIDs = try container.decodeIfPresent(
            Set<Int>.self,
            forKey: .wrongIDs
        ) ?? []
        masteredIDs = try container.decodeIfPresent(
            Set<Int>.self,
            forKey: .masteredIDs
        ) ?? []
        quizPreferences = try container.decodeIfPresent(
            QuizPreferences.self,
            forKey: .quizPreferences
        ) ?? QuizPreferences()
    }
}

import Foundation

enum QuizDirection: String, Codable, CaseIterable, Hashable, Sendable {
    case zhToEn
    case enToZh
    case mixed
}

struct QuizQuestion: Codable, Identifiable, Hashable, Sendable {
    let term: Term
    let direction: QuizDirection
    let options: [Term]
    let correctOptionID: Int

    var id: String {
        "\(term.id)-\(direction.rawValue)"
    }
}

struct QuizPoolBuilder: Sendable {
    static func makePool(
        from terms: [Term],
        scope: QuizScope,
        selectedSubjectID: String,
        wrongIDs: Set<Int>,
        favoriteIDs: Set<Int>,
        masteredIDs: Set<Int>,
        includeMastered: Bool
    ) -> [Term] {
        var pool = switch scope {
        case .important:
            terms.filter(\.important)
        case .all:
            terms
        case .subject:
            terms.filter { $0.subjectID == selectedSubjectID }
        case .mistakes:
            terms.filter { wrongIDs.contains($0.id) }
        case .favorites:
            terms.filter { favoriteIDs.contains($0.id) }
        }

        if !includeMastered {
            pool.removeAll { masteredIDs.contains($0.id) }
        }
        return pool
    }
}

struct QuizEngine: Sendable {
    private let seed: UInt64?

    init(seed: UInt64? = nil) {
        self.seed = seed
    }

    /// Builds at most `count` questions from `pool`.
    ///
    /// Every question has exactly four options with distinct term IDs. If fewer
    /// than four unique terms are available, no valid four-option quiz can be
    /// produced and this method returns an empty array.
    func makeQuestions(
        from pool: [Term],
        count: Int,
        direction: QuizDirection
    ) -> [QuizQuestion] {
        makeQuestions(
            from: pool,
            distractorsFrom: pool,
            count: count,
            direction: direction
        )
    }

    /// Builds questions exclusively from `questionPool`, while allowing the
    /// three distractors to come from a larger pool. This keeps favorites and
    /// wrong-answer quizzes usable even when their question pool has 1–3 terms.
    func makeQuestions(
        from questionPool: [Term],
        distractorsFrom distractorPool: [Term],
        count: Int,
        direction: QuizDirection
    ) -> [QuizQuestion] {
        if let seed {
            var generator = SeededRandomNumberGenerator(seed: seed)
            return makeQuestions(
                from: questionPool,
                distractorsFrom: distractorPool,
                count: count,
                direction: direction,
                using: &generator
            )
        }

        var generator = SystemRandomNumberGenerator()
        return makeQuestions(
            from: questionPool,
            distractorsFrom: distractorPool,
            count: count,
            direction: direction,
            using: &generator
        )
    }

    static func makeQuestions(
        from pool: [Term],
        count: Int,
        direction: QuizDirection,
        seed: UInt64? = nil
    ) -> [QuizQuestion] {
        QuizEngine(seed: seed).makeQuestions(
            from: pool,
            count: count,
            direction: direction
        )
    }

    static func makeQuestions(
        from questionPool: [Term],
        distractorsFrom distractorPool: [Term],
        count: Int,
        direction: QuizDirection,
        seed: UInt64? = nil
    ) -> [QuizQuestion] {
        QuizEngine(seed: seed).makeQuestions(
            from: questionPool,
            distractorsFrom: distractorPool,
            count: count,
            direction: direction
        )
    }

    private func makeQuestions<R: RandomNumberGenerator>(
        from questionPool: [Term],
        distractorsFrom distractorPool: [Term],
        count: Int,
        direction requestedDirection: QuizDirection,
        using generator: inout R
    ) -> [QuizQuestion] {
        var seenQuestionIDs = Set<Int>()
        let uniqueQuestionPool = questionPool.filter {
            seenQuestionIDs.insert($0.id).inserted
        }
        var seenDistractorIDs = Set<Int>()
        let uniqueDistractorPool = distractorPool.filter {
            seenDistractorIDs.insert($0.id).inserted
        }

        guard count > 0, !uniqueQuestionPool.isEmpty else {
            return []
        }

        var questionTerms = uniqueQuestionPool.filter { question in
            uniqueDistractorPool.lazy.filter { $0.id != question.id }.prefix(3).count == 3
        }
        questionTerms.shuffle(using: &generator)

        let questionCount = min(count, questionTerms.count)
        var questions: [QuizQuestion] = []
        questions.reserveCapacity(questionCount)

        for term in questionTerms.prefix(questionCount) {
            let direction = resolvedDirection(
                requestedDirection,
                using: &generator
            )
            let distractors = makeDistractors(
                for: term,
                from: uniqueDistractorPool,
                direction: direction,
                using: &generator
            )

            guard distractors.count == 3 else {
                continue
            }

            var options = [term] + distractors
            options.shuffle(using: &generator)

            questions.append(
                QuizQuestion(
                    term: term,
                    direction: direction,
                    options: options,
                    correctOptionID: term.id
                )
            )
        }

        return questions
    }

    private func resolvedDirection<R: RandomNumberGenerator>(
        _ requestedDirection: QuizDirection,
        using generator: inout R
    ) -> QuizDirection {
        guard requestedDirection == .mixed else {
            return requestedDirection
        }

        return generator.next().isMultiple(of: 2) ? .zhToEn : .enToZh
    }

    private func makeDistractors<R: RandomNumberGenerator>(
        for correctTerm: Term,
        from pool: [Term],
        direction: QuizDirection,
        using generator: inout R
    ) -> [Term] {
        var sameSubject = pool.filter {
            $0.id != correctTerm.id && $0.subjectID == correctTerm.subjectID
        }
        var otherSubjects = pool.filter {
            $0.id != correctTerm.id && $0.subjectID != correctTerm.subjectID
        }
        sameSubject.shuffle(using: &generator)
        otherSubjects.shuffle(using: &generator)

        let candidates = sameSubject + otherSubjects
        var selected: [Term] = []
        var selectedIDs: Set<Int> = [correctTerm.id]
        var selectedLabels: Set<String> = [
            normalizedAnswer(for: correctTerm, direction: direction)
        ]

        // Prefer choices whose displayed answers are distinct as well as their
        // IDs. This avoids visually duplicated options for synonymous entries.
        for candidate in candidates where selected.count < 3 {
            let label = normalizedAnswer(for: candidate, direction: direction)
            guard selectedIDs.insert(candidate.id).inserted,
                  selectedLabels.insert(label).inserted else {
                continue
            }
            selected.append(candidate)
        }

        // A small or synonym-heavy pool may not have four distinct labels.
        // Fall back to distinct term IDs so the four-option contract still
        // holds whenever the pool contains at least four unique terms.
        if selected.count < 3 {
            for candidate in candidates where selected.count < 3 {
                guard selectedIDs.insert(candidate.id).inserted else {
                    continue
                }
                selected.append(candidate)
            }
        }

        return selected
    }

    private func normalizedAnswer(
        for term: Term,
        direction: QuizDirection
    ) -> String {
        let answer = direction == .enToZh ? term.chinese : term.english
        return answer
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
    }
}

private struct SeededRandomNumberGenerator: RandomNumberGenerator {
    private var state: UInt64

    init(seed: UInt64) {
        state = seed
    }

    mutating func next() -> UInt64 {
        state &+= 0x9E3779B97F4A7C15
        var value = state
        value = (value ^ (value >> 30)) &* 0xBF58476D1CE4E5B9
        value = (value ^ (value >> 27)) &* 0x94D049BB133111EB
        return value ^ (value >> 31)
    }
}

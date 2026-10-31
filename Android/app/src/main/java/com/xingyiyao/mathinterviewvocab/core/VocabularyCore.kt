package com.xingyiyao.mathinterviewvocab.core

import java.text.Normalizer
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.random.Random

object VocabularyCore {
    val subjectOrder = listOf("subjects", "analysis", "algebra", "probability", "statistics")

    fun subjects(terms: List<Term>): List<SubjectSummary> = subjectOrder.mapNotNull { id ->
        val matching = terms.filter { it.subjectID == id }
        matching.firstOrNull()?.let {
            SubjectSummary(id, it.subjectName, it.subjectNameEn, matching.size)
        }
    }

    fun filterTerms(terms: List<Term>, filters: TermFilters, progress: ProgressData): List<Term> {
        val query = trimWhitespace(filters.query).lowercase(Locale.ROOT)
        return terms.filter { term ->
            (filters.subjectID.isNullOrEmpty() || term.subjectID == filters.subjectID) &&
                (!filters.importantOnly || term.important) &&
                (!filters.favoritesOnly || term.id in progress.favoriteIDs) &&
                (!filters.mistakesOnly || term.id in progress.wrongIDs) &&
                (query.isEmpty() || listOf(term.chinese, term.english, term.definition).any {
                    it.lowercase(Locale.ROOT).contains(query)
                })
        }
    }

    fun makePool(terms: List<Term>, preferences: QuizPreferences, progress: ProgressData): List<Term> {
        VocabularyJson.validatePreferences(preferences)
        return terms.filter { term ->
            val inScope = when (preferences.scope) {
                "all" -> true
                "important" -> term.important
                "subject" -> term.subjectID == preferences.selectedSubjectID
                "mistakes" -> term.id in progress.wrongIDs
                "favorites" -> term.id in progress.favoriteIDs
                else -> false
            }
            inScope && (preferences.includeMastered || term.id !in progress.masteredIDs)
        }
    }

    fun resolveQuestionCount(length: String, available: Int): Int {
        val requested = when (length) {
            "ten" -> 10
            "twenty" -> 20
            "fifty" -> 50
            "all" -> available
            else -> throw IllegalArgumentException("无效题目数量。")
        }
        return requested.coerceAtMost(available).coerceAtLeast(0)
    }

    fun makeQuestions(
        pool: List<Term>,
        allTerms: List<Term>,
        count: Int,
        requestedDirection: String,
        seed: Long? = null,
    ): List<QuizQuestion> {
        require(requestedDirection in setOf("mixed", "zhToEn", "enToZh")) { "无效出题方向。" }
        if (count <= 0) return emptyList()
        val random = seed?.let(::seededRandom) ?: { Random.Default.nextDouble() }
        val distractors = uniqueTerms(allTerms)
        val questions = shuffled(uniqueTerms(pool).filter { term ->
            distractors.count { it.id != term.id } >= 3
        }, random).take(count)

        return questions.mapIndexed { index, term ->
            val direction = if (requestedDirection == "mixed") {
                if (random() < 0.5) "zhToEn" else "enToZh"
            } else requestedDirection
            val candidates = shuffled(distractors.filter {
                it.id != term.id && it.subjectID == term.subjectID
            }, random) + shuffled(distractors.filter {
                it.id != term.id && it.subjectID != term.subjectID
            }, random)
            fun label(value: Term) = trimWhitespace(if (direction == "zhToEn") value.english else value.chinese)
                .lowercase(Locale.ROOT)
            val labels = mutableSetOf(label(term))
            val selected = mutableListOf<Term>()
            for (candidate in candidates) {
                if (selected.size == 3) break
                if (labels.add(label(candidate))) selected += candidate
            }
            // 极小自定义词库仍维持四个不同编号，与桌面版降级规则一致。
            for (candidate in candidates) {
                if (selected.size == 3) break
                if (selected.none { it.id == candidate.id }) selected += candidate
            }
            QuizQuestion(
                id = "${term.id}-$direction-$index",
                term = term,
                direction = direction,
                options = shuffled(listOf(term) + selected, random),
                correctOptionID = term.id,
            )
        }
    }

    fun recordAnswer(progress: ProgressData, attempt: QuizAttempt): ProgressData {
        VocabularyJson.validateAttempt(attempt)
        if (progress.attempts.any { it.id.equals(attempt.id, ignoreCase = true) }) return progress
        val correct = attempt.selectedOptionID == attempt.correctOptionID
        val previous = progress.correctStreak[attempt.termID.toString()] ?: 0
        val streak = if (correct) previous.toLong().plus(1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() else 0
        val wrong = progress.wrongIDs.toMutableSet()
        val mastered = progress.masteredIDs.toMutableSet()
        if (!correct) {
            wrong += attempt.termID
            mastered -= attempt.termID
        } else {
            if (streak >= 2) wrong -= attempt.termID
            if (streak >= 3) mastered += attempt.termID
        }
        return progress.copy(
            attempts = progress.attempts + attempt,
            correctStreak = progress.correctStreak + (attempt.termID.toString() to streak),
            wrongIDs = wrong.toSet(),
            masteredIDs = mastered.toSet(),
        )
    }

    fun toggleFavorite(progress: ProgressData, id: Int): ProgressData {
        require(id > 0) { "无效词汇编号。" }
        val favorites = progress.favoriteIDs.toMutableSet()
        if (!favorites.remove(id)) favorites += id
        return progress.copy(favoriteIDs = favorites.toSet())
    }

    fun summarizeProgress(terms: List<Term>, progress: ProgressData): ProgressSummary {
        val correct = progress.attempts.count { it.selectedOptionID == it.correctOptionID }
        val total = progress.attempts.size
        return ProgressSummary(
            attemptedCount = progress.attempts.map { it.termID }.distinct().size,
            masteredCount = progress.masteredIDs.size,
            wrongCount = progress.wrongIDs.size,
            correctAttemptCount = correct,
            totalAttemptCount = total,
            accuracyPercentage = if (total == 0) 0 else (correct.toDouble() / total * 100).roundToInt(),
            overallMastery = if (terms.isEmpty()) 0.0 else progress.masteredIDs.size.toDouble() / terms.size,
            subjects = subjects(terms).map { subject ->
                val mastered = terms.count { it.subjectID == subject.id && it.id in progress.masteredIDs }
                SubjectProgress(subject.id, subject.name, subject.englishName, subject.count, mastered,
                    if (subject.count == 0) 0.0 else mastered.toDouble() / subject.count)
            },
            wrongTerms = terms.filter { it.id in progress.wrongIDs },
        )
    }

    fun selectedText(source: String, start: Int, end: Int): String? {
        if (start < 0 || end <= start || end > source.length) return null
        return sanitizeSelection(source.substring(start, end))
    }

    fun sanitizeSelection(text: String): String? {
        if (hasUnpairedSurrogate(text)) return null
        var start = 0
        var end = text.length
        while (start < end) {
            val codePoint = text.codePointAt(start)
            if (!isBoundary(codePoint)) break
            start += Character.charCount(codePoint)
        }
        while (end > start) {
            val codePoint = text.codePointBefore(end)
            if (!isBoundary(codePoint)) break
            end -= Character.charCount(codePoint)
        }
        val selected = text.substring(start, end)
        val hasContent = selected.codePoints().anyMatch { codePoint ->
            Character.isLetter(codePoint) || Character.getType(codePoint) in numberTypes
        }
        return selected.takeIf { it.isNotEmpty() && hasContent }
    }

    fun normalizeAudioText(text: String): String {
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFKC)
            .replace(Regex("[‘’ʼ＇]"), "'")
            .replace(Regex("[‐‑‒–—−]"), "-")
        val output = StringBuilder()
        var pendingSpace = false
        normalized.codePoints().forEachOrdered { codePoint ->
            if (isWhitespace(codePoint)) pendingSpace = output.isNotEmpty()
            else {
                if (pendingSpace) output.append(' ')
                output.appendCodePoint(codePoint)
                pendingSpace = false
            }
        }
        return output.toString()
    }

    fun displayMathNotation(source: String): String {
        var text = source.replace("`", "").replace("\\(", "").replace("\\)", "")
            .replace("\\[", "").replace("\\]", "")
        val replacements = listOf(
            "\\operatorname{adj}" to "adj", "^\\mathsf{T}" to "ᵀ", "\\mathsf{T}" to "ᵀ",
            "\\mathrm{d}" to "d", "\\mathcal F" to "𝓕", "\\varepsilon" to "ε", "\\epsilon" to "ε",
            "\\lambda" to "λ", "\\delta" to "δ", "\\xi" to "ξ", "\\alpha" to "α", "\\Omega" to "Ω",
            "\\sigma" to "σ", "\\infty" to "∞", "\\times" to "×", "\\le" to "≤", "\\ge" to "≥",
            "\\mid" to "∣", "\\cap" to "∩", "\\oplus" to "⊕", "\\perp" to "⊥", "\\subseteq" to "⊆",
            "\\det" to "det", "\\dim" to "dim", "\\sum" to "∑", "\\int" to "∫", "\\in" to "∈",
            "\\ldots" to "…", "\\," to " ", "\\!" to "", "^2" to "²", "^3" to "³", "^{-1}" to "⁻¹",
            "\\{" to "{", "\\}" to "}", "\\" to "",
        )
        for ((from, to) in replacements) text = text.replace(from, to)
        return text
    }

    private fun uniqueTerms(terms: List<Term>): List<Term> = linkedMapOf<Int, Term>().apply {
        terms.forEach { put(it.id, it) }
    }.values.toList()

    private fun <T> shuffled(values: List<T>, random: () -> Double): List<T> {
        val output = values.toMutableList()
        for (i in output.lastIndex downTo 1) {
            val j = (random() * (i + 1)).toInt()
            val previous = output[i]
            output[i] = output[j]
            output[j] = previous
        }
        return output
    }

    private fun seededRandom(seed: Long): () -> Double {
        var state = seed.toInt()
        return {
            state += 0x6D2B79F5
            var value = (state xor (state ushr 15)) * (state or 1)
            value = value xor (value + ((value xor (value ushr 7)) * (value or 61)))
            ((value xor (value ushr 14)).toLong() and 0xffffffffL).toDouble() / 4_294_967_296.0
        }
    }

    private val punctuationTypes = setOf(
        Character.CONNECTOR_PUNCTUATION.toInt(), Character.DASH_PUNCTUATION.toInt(),
        Character.START_PUNCTUATION.toInt(), Character.END_PUNCTUATION.toInt(),
        Character.INITIAL_QUOTE_PUNCTUATION.toInt(), Character.FINAL_QUOTE_PUNCTUATION.toInt(),
        Character.OTHER_PUNCTUATION.toInt(),
    )
    private val numberTypes = setOf(
        Character.DECIMAL_DIGIT_NUMBER.toInt(), Character.LETTER_NUMBER.toInt(), Character.OTHER_NUMBER.toInt(),
    )
    private fun isWhitespace(codePoint: Int) = Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint) || codePoint == 0xfeff
    private fun isBoundary(codePoint: Int) = isWhitespace(codePoint) || Character.getType(codePoint) in punctuationTypes
    private fun trimWhitespace(text: String): String = text.trim { isWhitespace(it.code) }

    private fun hasUnpairedSurrogate(text: String): Boolean {
        var index = 0
        while (index < text.length) {
            val char = text[index]
            if (Character.isHighSurrogate(char)) {
                if (index + 1 >= text.length || !Character.isLowSurrogate(text[index + 1])) return true
                index += 2
            } else {
                if (Character.isLowSurrogate(char)) return true
                index += 1
            }
        }
        return false
    }
}

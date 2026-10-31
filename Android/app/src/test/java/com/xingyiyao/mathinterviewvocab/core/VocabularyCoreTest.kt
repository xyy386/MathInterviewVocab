package com.xingyiyao.mathinterviewvocab.core

import org.junit.Assert.*
import org.junit.Test

class VocabularyCoreTest {
    private val terms = canonicalTerms()

    @Test fun canonicalCorpusKeepsStableIDsAndSubjectOrder() {
        assertEquals(326, terms.size)
        assertEquals(130, terms.count { it.important })
        assertEquals((1..326).toList(), terms.map { it.id })
        val subjects = VocabularyCore.subjects(terms)
        assertEquals(VocabularyCore.subjectOrder, subjects.map { it.id })
        assertEquals(listOf(11, 163, 110, 27, 15), subjects.map { it.count })
        assertTrue(terms.all { it.speechText.isNotBlank() && it.pronunciation.americanIPA.isNotBlank() && it.pronunciation.britishIPA.isNotBlank() })
    }

    @Test fun searchIsCaseInsensitiveAndIncludesDefinitionsNotIPA() {
        val values = listOf(term(1, english = "Unbounded Function").copy(definition = "研究极限", pronunciation = Pronunciation("ONLYIPA", "other")))
        assertEquals(values, VocabularyCore.filterTerms(values, TermFilters(query = " \u00a0UNBOUNDED\uFEFF"), ProgressData()))
        assertEquals(values, VocabularyCore.filterTerms(values, TermFilters(query = "极限"), ProgressData()))
        assertTrue(VocabularyCore.filterTerms(values, TermFilters(query = "ONLYIPA"), ProgressData()).isEmpty())
    }

    @Test fun filtersIntersectRatherThanUnion() {
        val progress = ProgressData(favoriteIDs = setOf(2, 4), wrongIDs = setOf(2, 3))
        val values = listOf(term(1), term(2), term(3), term(4, "algebra"))
        val filters = TermFilters(subjectID = "analysis", importantOnly = true, favoritesOnly = true, mistakesOnly = true)
        assertEquals(listOf(2), VocabularyCore.filterTerms(values, filters, progress).map { it.id })
    }

    @Test fun allFiveScopesAndMasteryExclusionUseActualPool() {
        val progress = ProgressData(favoriteIDs = setOf(1, 2), wrongIDs = setOf(2, 3), masteredIDs = setOf(2))
        val values = listOf(term(1), term(2), term(3, "algebra"), term(4, "algebra"))
        val expected = mapOf("important" to listOf(4), "all" to listOf(1, 3, 4), "subject" to listOf(1),
            "mistakes" to listOf(3), "favorites" to listOf(1))
        for ((scope, ids) in expected) {
            val preferences = QuizPreferences(scope = scope, includeMastered = false)
            assertEquals(ids, VocabularyCore.makePool(values, preferences, progress).map { it.id })
        }
        assertEquals(4, VocabularyCore.makePool(values, QuizPreferences(scope = "all"), progress).size)
    }

    @Test fun questionLengthsClampToAvailableAndAllHasNoHardcodedLimit() {
        assertEquals(10, VocabularyCore.resolveQuestionCount("ten", 100))
        assertEquals(20, VocabularyCore.resolveQuestionCount("twenty", 100))
        assertEquals(50, VocabularyCore.resolveQuestionCount("fifty", 100))
        assertEquals(301, VocabularyCore.resolveQuestionCount("all", 301))
        assertEquals(3, VocabularyCore.resolveQuestionCount("fifty", 3))
        assertEquals(0, VocabularyCore.resolveQuestionCount("all", 0))
        assertEquals(0, VocabularyCore.resolveQuestionCount("ten", -1))
        assertThrows(IllegalArgumentException::class.java) { VocabularyCore.resolveQuestionCount("326", 326) }
    }

    @Test fun allQuestionsAreDeterministicAndHaveFourDifferentLabelsAndIDs() {
        val questions = VocabularyCore.makeQuestions(terms, terms, 326, "mixed", 123)
        assertEquals(questions, VocabularyCore.makeQuestions(terms, terms, 326, "mixed", 123))
        assertNotEquals(questions, VocabularyCore.makeQuestions(terms, terms, 326, "mixed", 124))
        assertEquals(326, questions.size)
        assertEquals(326, questions.map { it.term.id }.toSet().size)
        assertEquals(setOf("zhToEn", "enToZh"), questions.map { it.direction }.toSet())
        questions.forEach { question ->
            assertEquals(question.term.id, question.correctOptionID)
            assertEquals(4, question.options.map { it.id }.toSet().size)
            assertEquals(4, question.options.map { if (question.direction == "zhToEn") it.english.lowercase() else it.chinese }.toSet().size)
            assertTrue(question.options.contains(question.term))
        }
    }

    @Test fun narrowQuestionPoolStillUsesFullCorpusForDistractors() {
        val question = VocabularyCore.makeQuestions(listOf(terms.first()), terms, 10, "enToZh", 7).single()
        assertEquals(4, question.options.size)
        assertTrue(question.options.all { it.subjectID == "subjects" })
        assertEquals("enToZh", question.direction)
    }

    @Test fun seededQuestionsMatchExistingWindowsAlgorithmAndRandomCallOrder() {
        val questions = VocabularyCore.makeQuestions(terms, terms, 3, "mixed", 123)
        assertEquals(listOf("52-zhToEn-0", "118-enToZh-1", "153-zhToEn-2"), questions.map { it.id })
        assertEquals(listOf(listOf(57, 52, 73, 103), listOf(118, 137, 98, 85), listOf(90, 153, 165, 16)),
            questions.map { question -> question.options.map { it.id } })
    }

    @Test fun tinyCorpusDoesNotInventOptionsAndDuplicateIDsAreDeduplicated() {
        val small = listOf(term(1), term(2), term(3))
        assertTrue(VocabularyCore.makeQuestions(small, small, 10, "mixed", 0).isEmpty())
        val values = small + term(4)
        val questions = VocabularyCore.makeQuestions(values + values, values + values, 10, "zhToEn", 0)
        assertEquals(4, questions.size)
        assertTrue(questions.all { it.options.map { option -> option.id }.toSet().size == 4 })
    }

    @Test fun twoCorrectAnswersClearMistakeAndThreeMasterTermWithoutChangingFavorites() {
        var progress = ProgressData(favoriteIDs = setOf(1))
        progress = VocabularyCore.recordAnswer(progress, attempt(1, correct = false))
        assertEquals(setOf(1), progress.wrongIDs)
        progress = VocabularyCore.recordAnswer(progress, attempt(2))
        assertTrue(1 in progress.wrongIDs)
        progress = VocabularyCore.recordAnswer(progress, attempt(3))
        assertTrue(progress.wrongIDs.isEmpty())
        assertTrue(progress.masteredIDs.isEmpty())
        progress = VocabularyCore.recordAnswer(progress, attempt(4))
        assertEquals(setOf(1), progress.masteredIDs)
        assertEquals(setOf(1), progress.favoriteIDs)
        progress = VocabularyCore.recordAnswer(progress, attempt(5, correct = false))
        assertTrue(progress.masteredIDs.isEmpty())
        assertEquals(setOf(1), progress.wrongIDs)
        assertEquals(0, progress.correctStreak["1"])
    }

    @Test fun retryingSameAttemptIsIdempotentIncludingUUIDCase() {
        val first = attempt(1).copy(id = "abcdefab-cdef-4abc-8def-abcdefabcdef")
        val progress = VocabularyCore.recordAnswer(ProgressData(), first)
        assertSame(progress, VocabularyCore.recordAnswer(progress, first.copy(id = first.id.uppercase())))
        val saturated = VocabularyCore.recordAnswer(ProgressData(correctStreak = mapOf("1" to Int.MAX_VALUE)), attempt(2))
        assertEquals(Int.MAX_VALUE, saturated.correctStreak["1"])
    }

    @Test fun favoritesRemainIndependentFromMistakesAndMastery() {
        val progress = ProgressData(wrongIDs = setOf(1), masteredIDs = setOf(2))
        val favorite = VocabularyCore.toggleFavorite(progress, 1)
        assertEquals(setOf(1), favorite.favoriteIDs)
        assertEquals(progress.wrongIDs, favorite.wrongIDs)
        assertEquals(progress.masteredIDs, favorite.masteredIDs)
        assertEquals(progress, VocabularyCore.toggleFavorite(favorite, 1))
    }

    @Test fun summarySeparatesUniqueStudiedTermsFromAnswerCount() {
        val progress = ProgressData(attempts = listOf(attempt(1), attempt(2), attempt(3, 2, false)),
            masteredIDs = setOf(1), wrongIDs = setOf(2))
        val summary = VocabularyCore.summarizeProgress(listOf(term(1), term(2)), progress)
        assertEquals(2, summary.attemptedCount)
        assertEquals(3, summary.totalAttemptCount)
        assertEquals(67, summary.accuracyPercentage)
        assertEquals(0.5, summary.overallMastery, 0.0)
        assertEquals(listOf(2), summary.wrongTerms.map { it.id })
        assertEquals(0, VocabularyCore.summarizeProgress(emptyList(), ProgressData()).accuracyPercentage)
        assertEquals(0.0, VocabularyCore.summarizeProgress(emptyList(), ProgressData()).overallMastery, 0.0)
    }

    @Test fun selectionRetainsSingleAndMultipleWordsAndInternalPunctuation() {
        mapOf("unbounded" to "unbounded", "  unbounded function " to "unbounded function",
            "（well-defined），" to "well-defined", "‘Euler’s formula.’" to "Euler’s formula",
            "...αβ 变量..." to "αβ 变量", "\u00a0１½\uFEFF" to "１½").forEach { (input, expected) ->
            assertEquals(expected, VocabularyCore.sanitizeSelection(input))
        }
        listOf("", " \t\n", "—…！？", "\u00a0\uFEFF", "😀 + =").forEach {
            assertNull(VocabularyCore.sanitizeSelection(it))
        }
    }

    @Test fun selectionUsesUTF16BoundsWithoutSplittingSurrogatePairs() {
        val value = "😀 unbounded function"
        assertEquals("unbounded", VocabularyCore.selectedText(value, 3, 12))
        assertEquals("𐐀x", VocabularyCore.selectedText("𐐀x", 0, 3))
        assertNull(VocabularyCore.selectedText("𐐀x", 1, 3))
        assertNull(VocabularyCore.selectedText(value, -1, 5))
        assertNull(VocabularyCore.selectedText(value, 5, 4))
        assertNull(VocabularyCore.selectedText(value, 0, 100))
        assertNull(VocabularyCore.sanitizeSelection("x\uD800"))
    }

    @Test fun audioNormalizationMatchesBundledManifestWithoutLowercasing() {
        assertEquals("Euler's well-defined Function", VocabularyCore.normalizeAudioText("\uFEFF Ｅｕｌｅｒ’s\u00a0well‑defined \n Function  "))
        assertEquals("a-b-c-d-e-f-g", VocabularyCore.normalizeAudioText("a‐b‑c‒d–e—f−g"))
        assertEquals("", VocabularyCore.normalizeAudioText("\u00a0\n\uFEFF"))
    }

    @Test fun mathDisplayMatchesDesktopNotation() {
        assertEquals("AᵀA⁻¹ λ² + ξ³ ≤ ∞", VocabularyCore.displayMathNotation("`\\(A^\\mathsf{T}A^{-1} \\lambda^2 + \\xi^3 \\le \\infty\\)`"))
        assertEquals("𝓕 ∫ f d x ∈ Ω", VocabularyCore.displayMathNotation("\\mathcal F \\int f \\mathrm{d} x \\in \\Omega"))
        assertFalse(VocabularyCore.displayMathNotation(terms.joinToString { it.definition }).contains('\\'))
    }
}

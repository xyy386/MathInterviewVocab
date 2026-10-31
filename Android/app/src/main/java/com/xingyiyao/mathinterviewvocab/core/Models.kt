package com.xingyiyao.mathinterviewvocab.core

import kotlinx.serialization.Serializable

@Serializable
data class Pronunciation(
    val americanIPA: String,
    val britishIPA: String,
)

@Serializable
data class Term(
    val id: Int,
    val subjectID: String,
    val subjectName: String,
    val subjectNameEn: String,
    val chinese: String,
    val english: String,
    val definition: String,
    val important: Boolean,
    val speechText: String,
    val pronunciation: Pronunciation,
)

@Serializable
data class QuizPreferences(
    val direction: String = "mixed",
    val scope: String = "important",
    val questionLength: String = "twenty",
    val selectedSubjectID: String = "analysis",
    val includeMastered: Boolean = true,
)

@Serializable
data class SpeechSettings(
    val accent: String = "en-US",
    val speed: Double = 1.0,
)

@Serializable
data class QuizAttempt(
    val id: String,
    val termID: Int,
    val direction: String,
    val selectedOptionID: Int,
    val correctOptionID: Int,
    val answeredAt: String,
)

@Serializable
data class ProgressData(
    val attempts: List<QuizAttempt> = emptyList(),
    val favoriteIDs: Set<Int> = emptySet(),
    val correctStreak: Map<String, Int> = emptyMap(),
    val wrongIDs: Set<Int> = emptySet(),
    val masteredIDs: Set<Int> = emptySet(),
    val quizPreferences: QuizPreferences = QuizPreferences(),
)

data class QuizQuestion(
    val id: String,
    val term: Term,
    val direction: String,
    val options: List<Term>,
    val correctOptionID: Int,
)

data class TermFilters(
    val query: String = "",
    val subjectID: String? = null,
    val importantOnly: Boolean = false,
    val favoritesOnly: Boolean = false,
    val mistakesOnly: Boolean = false,
)

data class SubjectSummary(
    val id: String,
    val name: String,
    val englishName: String,
    val count: Int,
)

data class SubjectProgress(
    val id: String,
    val name: String,
    val englishName: String,
    val count: Int,
    val masteredCount: Int,
    val fraction: Double,
)

data class ProgressSummary(
    val attemptedCount: Int,
    val masteredCount: Int,
    val wrongCount: Int,
    val correctAttemptCount: Int,
    val totalAttemptCount: Int,
    val accuracyPercentage: Int,
    val overallMastery: Double,
    val subjects: List<SubjectProgress>,
    val wrongTerms: List<Term>,
)

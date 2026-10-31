package com.xingyiyao.mathinterviewvocab.core

import java.io.File

internal fun canonicalTerms(): List<Term> {
    val root = System.getProperty("math.repositoryRoot")?.let(::File)
        ?: generateSequence(File(System.getProperty("user.dir") ?: ".")) { it.parentFile }
            .first { File(it, "Resources/terms.json").isFile }
    return VocabularyJson.decodeTerms(File(root, "Resources/terms.json").readText())
}

internal fun term(id: Int, subject: String = "analysis", english: String = "term $id", chinese: String = "术语$id") = Term(
    id, subject, "科目", "Subject", chinese, english, "定义$id", id % 2 == 0,
    english, Pronunciation("US$id", "UK$id"),
)

internal fun attempt(number: Int, termID: Int = 1, correct: Boolean = true) = QuizAttempt(
    "00000000-0000-4000-8000-${number.toString().padStart(12, '0')}",
    termID, "zhToEn", if (correct) termID else termID + 1, termID, "2026-10-30T12:34:56.123Z",
)

package com.xingyiyao.mathinterviewvocab.data

import com.xingyiyao.mathinterviewvocab.core.VocabularyJson
import com.xingyiyao.mathinterviewvocab.core.canonicalTerms
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class TermLibraryTest {
    private val terms = canonicalTerms()

    @Test fun canonicalLibraryPassesCompleteValidation() {
        assertEquals(terms, TermLibrary.decodeAndValidate(VocabularyJson.json.encodeToString(terms)))
    }

    @Test fun missingTermDuplicateIDAndWrongImportantCountAreRejected() {
        listOf(terms.dropLast(1), terms.dropLast(1) + terms.first(),
            terms.map { if (it.id == 1) it.copy(important = false) else it }).forEach {
            assertThrows(IllegalArgumentException::class.java) {
                TermLibrary.decodeAndValidate(VocabularyJson.json.encodeToString(it))
            }
        }
    }

    @Test fun alteredSubjectsMissingIPAAndInconsistentMetadataAreRejected() {
        val first = terms.first()
        listOf(first.copy(subjectID = "unknown"), first.copy(subjectName = "另一个科目"),
            first.copy(pronunciation = first.pronunciation.copy(britishIPA = " ")),
            first.copy(speechText = ""), first.copy(definition = "")).forEach { changed ->
            assertThrows(IllegalArgumentException::class.java) {
                TermLibrary.decodeAndValidate(VocabularyJson.json.encodeToString(listOf(changed) + terms.drop(1)))
            }
        }
    }
}

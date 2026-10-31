package com.xingyiyao.mathinterviewvocab.core

import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class VocabularyJsonTest {
    @Test fun absentLegacyFieldsReceiveCompatibleDefaults() {
        assertEquals(ProgressData(), VocabularyJson.decodeProgress("{}"))
        assertEquals(QuizPreferences(), VocabularyJson.decodePreferences("{}"))
        assertEquals(SpeechSettings(), VocabularyJson.decodeSettings("{}"))
        assertEquals(ProgressData(), VocabularyJson.decodeProgress("{\"futureField\":true}"))
    }

    @Test fun legacyPreferencesMigrateDeterministically() {
        val migrated = VocabularyJson.decodePreferences("""{
            "direction":"enToZh", "selectedSubjectIDs":["statistics","analysis","analysis"],
            "importantOnly":false,"questionCount":326,"includeMastered":false
        }""")
        assertEquals(QuizPreferences("enToZh", "subject", "all", "analysis", false), migrated)
        assertEquals("all", VocabularyJson.decodePreferences("""{"importantOnly":false}""").scope)
        assertEquals("ten", VocabularyJson.decodePreferences("""{"questionCount":10}""").questionLength)
        assertEquals("fifty", VocabularyJson.decodePreferences("""{"questionCount":50}""").questionLength)
        assertEquals("twenty", VocabularyJson.decodePreferences("""{"questionCount":21}""").questionLength)
    }

    @Test fun explicitNewPreferencesOverrideLegacyFallbacks() {
        val preferences = VocabularyJson.decodePreferences("""{
            "direction":"zhToEn", "scope":"favorites", "questionLength":"fifty",
            "selectedSubjectID":"algebra", "includeMastered":false,
            "selectedSubjectIDs":["analysis"], "importantOnly":true,"questionCount":10
        }""")
        assertEquals(QuizPreferences("zhToEn", "favorites", "fifty", "algebra", false), preferences)
        val progress = ProgressData(quizPreferences = preferences, attempts = listOf(attempt(1)),
            correctStreak = mapOf("1" to 1), favoriteIDs = setOf(1, 2), wrongIDs = setOf(3), masteredIDs = setOf(4))
        assertEquals(progress, VocabularyJson.decodeProgress(VocabularyJson.encodeProgress(progress), (1..326).toSet()))
    }

    @Test fun allPreferenceCombinationsRoundTrip() {
        for (direction in listOf("mixed", "zhToEn", "enToZh")) {
            for (scope in listOf("important", "all", "subject", "mistakes", "favorites")) {
                for (length in listOf("ten", "twenty", "fifty", "all")) {
                    val preferences = QuizPreferences(direction, scope, length, "statistics", false)
                    val encoded = VocabularyJson.encodeProgress(ProgressData(quizPreferences = preferences))
                    assertEquals(preferences, VocabularyJson.decodeProgress(encoded).quizPreferences)
                }
            }
        }
    }

    @Test fun malformedPreferencesAreNotSilentlyReset() {
        listOf("null", "[]", """{"scope":"unknown"}""", """{"direction":null}""",
            """{"questionLength":20}""", """{"selectedSubjectID":"missing"}""",
            """{"selectedSubjectIDs":null}""", """{"selectedSubjectIDs":[1]}""",
            """{"importantOnly":"true"}""", """{"includeMastered":null}""",
            """{"questionCount":10.5}""", """{"questionCount":"10"}""",
            """{"questionCount":9007199254740992}""").forEach { source ->
            assertThrows("Must reject $source", IllegalArgumentException::class.java) {
                VocabularyJson.decodePreferences(source)
            }
        }
    }

    @Test fun legacySpeechAliasesAndAllSixVoiceCombinationsRoundTrip() {
        assertEquals(SpeechSettings("en-GB", 0.75), VocabularyJson.decodeSettings("""{"voiceLocale":"en-GB","speechSpeed":0.75}"""))
        assertEquals(SpeechSettings("en-GB", 1.25), VocabularyJson.decodeSettings("""{"accent":null,"speed":null,"voiceLocale":"en-GB","speechSpeed":1.25}"""))
        for (accent in listOf("en-US", "en-GB")) for (speed in listOf(0.75, 1.0, 1.25)) {
            val settings = SpeechSettings(accent, speed)
            assertEquals(settings, VocabularyJson.decodeSettings(VocabularyJson.encodeSettings(settings)))
        }
    }

    @Test fun invalidSpeechSettingsAreNotCoerced() {
        listOf("[]", "null", """{"accent":"zh-CN"}""", """{"speed":0.5}""",
            """{"speed":"1"}""", """{"speed":true}""").forEach { source ->
            assertThrows(IllegalArgumentException::class.java) { VocabularyJson.decodeSettings(source) }
        }
        assertThrows(IllegalArgumentException::class.java) { VocabularyJson.validateSettings(SpeechSettings(speed = Double.NaN)) }
    }

    @Test fun invalidJSONStructuresAndIDsAreNotTreatedAsEmptyProgress() {
        listOf("null", "[]", """{"attempts":null}""", """{"favoriteIDs":{}}""",
            """{"favoriteIDs":[0]}""", """{"wrongIDs":[1.5]}""", """{"masteredIDs":["1"]}""",
            """{"correctStreak":null}""", """{"correctStreak":{"01":1}}""",
            """{"correctStreak":{"1":-1}}""", """{"correctStreak":{"1":1.5}}""",
            """{"quizPreferences":null}""").forEach { source ->
            assertThrows("Must reject $source", IllegalArgumentException::class.java) {
                VocabularyJson.decodeProgress(source)
            }
        }
    }

    @Test fun IDCollectionsDeduplicateAndRejectReferencesOutsideCorpus() {
        assertEquals(setOf(1, 2), VocabularyJson.decodeProgress("""{"favoriteIDs":[1,1,2]}""").favoriteIDs)
        listOf(ProgressData(favoriteIDs = setOf(999)), ProgressData(wrongIDs = setOf(999)),
            ProgressData(masteredIDs = setOf(999)), ProgressData(correctStreak = mapOf("999" to 0)),
            ProgressData(attempts = listOf(attempt(1, termID = 999))),
            ProgressData(attempts = listOf(attempt(1).copy(selectedOptionID = 999)))).forEach { progress ->
            assertThrows(IllegalArgumentException::class.java) {
                VocabularyJson.decodeProgress(VocabularyJson.encodeProgress(progress), (1..326).toSet())
            }
        }
    }

    @Test fun attemptValidationRejectsInvalidUUIDDirectionDateAndCorrectOption() {
        listOf(attempt(1).copy(id = "1"), attempt(1).copy(direction = "mixed"),
            attempt(1).copy(answeredAt = "2026-10-30"), attempt(1).copy(answeredAt = "2026-02-30T12:00:00Z"),
            attempt(1).copy(answeredAt = "2026-10-30T25:00:00Z"), attempt(1).copy(correctOptionID = 2),
            attempt(1).copy(selectedOptionID = 0)).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { VocabularyJson.validateAttempt(value) }
        }
        assertEquals(attempt(1), VocabularyJson.validateAttempt(attempt(1)))
    }

    @Test fun repeatedUUIDsAreRejectedBeforePersistence() {
        val attempt = attempt(1).copy(id = "abcdefab-cdef-4abc-8def-abcdefabcdef")
        val source = VocabularyJson.json.encodeToString(ProgressData(attempts = listOf(attempt, attempt.copy(id = attempt.id.uppercase()))))
        assertThrows(IllegalArgumentException::class.java) { VocabularyJson.decodeProgress(source) }
    }

    @Test fun additiveUnknownFieldsDoNotBreakFutureProgressFiles() {
        val source = """{"favoriteIDs":[1],"future":{"data":2},"quizPreferences":{"scope":"mistakes","futureOption":true}}"""
        val data = VocabularyJson.decodeProgress(source, (1..326).toSet())
        assertEquals(setOf(1), data.favoriteIDs)
        assertEquals("mistakes", data.quizPreferences.scope)
    }
}

package com.xingyiyao.mathinterviewvocab.core

import java.time.OffsetDateTime
import java.util.Locale
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/** 保持桌面端 JSON 字段与旧检测偏好兼容，不把损坏数据当作空记录。 */
object VocabularyJson {
    val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        prettyPrint = true
        isLenient = false
    }

    fun decodeTerms(source: String): List<Term> = json.decodeFromString<List<Term>>(source)

    fun encodeProgress(progress: ProgressData): String {
        validateProgress(progress)
        return json.encodeToString(progress) + "\n"
    }

    fun decodeProgress(source: String, validIDs: Set<Int>? = null): ProgressData {
        val value = objectValue(json.parseToJsonElement(source))
        val attempts = optionalArray(value, "attempts").map { element ->
            val attempt = objectValue(element)
            QuizAttempt(
                id = stringValue(attempt["id"], "答题 UUID"),
                termID = positiveID(attempt["termID"]),
                direction = enumValue(attempt["direction"], setOf("zhToEn", "enToZh"), "zhToEn"),
                selectedOptionID = positiveID(attempt["selectedOptionID"]),
                correctOptionID = positiveID(attempt["correctOptionID"]),
                answeredAt = stringValue(attempt["answeredAt"], "答题时间"),
            ).also(::validateAttempt)
        }
        val correctStreak = linkedMapOf<String, Int>()
        if (value.containsKey("correctStreak")) {
            for ((id, count) in objectValue(value["correctStreak"])) {
                require(positiveKey.matches(id) && id.toIntOrNull() != null) { "无效连续答对编号。" }
                correctStreak[id] = integerValue(count, 0, Int.MAX_VALUE.toLong()).toInt()
            }
        }
        val progress = ProgressData(
            attempts = attempts,
            favoriteIDs = idSet(value, "favoriteIDs"),
            correctStreak = correctStreak.toMap(),
            wrongIDs = idSet(value, "wrongIDs"),
            masteredIDs = idSet(value, "masteredIDs"),
            quizPreferences = value["quizPreferences"]?.let(::decodePreferences) ?: QuizPreferences(),
        )
        validateProgress(progress, validIDs)
        return progress
    }

    fun encodeSettings(settings: SpeechSettings): String {
        validateSettings(settings)
        return json.encodeToString(settings) + "\n"
    }

    fun decodeSettings(source: String): SpeechSettings {
        val value = objectValue(json.parseToJsonElement(source))
        val accent = nonNull(value["accent"]) ?: nonNull(value["voiceLocale"])
        val speedElement = nonNull(value["speed"]) ?: nonNull(value["speechSpeed"])
        val speed = speedElement?.let { numberValue(it) } ?: 1.0
        return validateSettings(SpeechSettings(
            accent = enumValue(accent, setOf("en-US", "en-GB"), "en-US"),
            speed = speed,
        ))
    }

    fun decodePreferences(source: String): QuizPreferences = decodePreferences(json.parseToJsonElement(source))

    fun validatePreferences(preferences: QuizPreferences): QuizPreferences {
        require(preferences.direction in directions && preferences.scope in scopes &&
            preferences.questionLength in lengths && preferences.selectedSubjectID in VocabularyCore.subjectOrder) {
            "无效检测偏好。"
        }
        return preferences
    }

    fun validateSettings(settings: SpeechSettings): SpeechSettings {
        require(settings.accent in setOf("en-US", "en-GB")) { "无效发音口音。" }
        require(settings.speed in setOf(0.75, 1.0, 1.25)) { "无效语速。" }
        return settings
    }

    fun validateAttempt(attempt: QuizAttempt): QuizAttempt {
        require(uuidPattern.matches(attempt.id)) { "无效答题 UUID。" }
        require(attempt.termID > 0 && attempt.selectedOptionID > 0 &&
            attempt.correctOptionID == attempt.termID) { "无效答题词汇编号。" }
        require(attempt.direction in setOf("zhToEn", "enToZh")) { "无效答题方向。" }
        require(datePrefix.containsMatchIn(attempt.answeredAt) &&
            runCatching { OffsetDateTime.parse(attempt.answeredAt) }.isSuccess) { "无效答题时间。" }
        return attempt
    }

    fun validateProgress(progress: ProgressData, validIDs: Set<Int>? = null): ProgressData {
        progress.attempts.forEach(::validateAttempt)
        require(progress.attempts.map { it.id.lowercase(Locale.ROOT) }.distinct().size == progress.attempts.size) {
            "答题记录编号重复。"
        }
        validatePreferences(progress.quizPreferences)
        val referenced = progress.favoriteIDs + progress.wrongIDs + progress.masteredIDs +
            progress.correctStreak.map { (key, count) ->
                require(positiveKey.matches(key) && count >= 0) { "无效连续答对计数。" }
                key.toIntOrNull() ?: throw IllegalArgumentException("无效连续答对编号。")
            } + progress.attempts.flatMap { listOf(it.termID, it.selectedOptionID, it.correctOptionID) }
        require(referenced.all { it > 0 }) { "词汇编号必须为正整数。" }
        if (validIDs != null) require(referenced.all { it in validIDs }) { "记录包含词库中不存在的编号。" }
        return progress
    }

    private fun decodePreferences(element: JsonElement): QuizPreferences {
        val value = objectValue(element)
        val defaults = QuizPreferences()
        val legacySubjects = optionalArray(value, "selectedSubjectIDs").map {
            stringValue(it, "旧版科目").also { id ->
                require(id in VocabularyCore.subjectOrder) { "无效旧版科目列表。" }
            }
        }.distinct().sorted()
        val selectedSubject = nonNull(value["selectedSubjectID"])?.let { stringValue(it, "科目") }
            ?: legacySubjects.firstOrNull() ?: defaults.selectedSubjectID
        val important = booleanValue(value["importantOnly"], true)
        val fallbackScope = if (legacySubjects.isNotEmpty()) "subject" else if (important) "important" else "all"
        val legacyCount = value["questionCount"]?.let { integerValue(it, -MAX_SAFE_INTEGER, MAX_SAFE_INTEGER) }
        val fallbackLength = when (legacyCount) {
            10L -> "ten"
            50L -> "fifty"
            326L -> "all"
            else -> "twenty"
        }
        return validatePreferences(QuizPreferences(
            direction = enumValue(value["direction"], directions, defaults.direction),
            scope = enumValue(value["scope"], scopes, fallbackScope),
            questionLength = enumValue(value["questionLength"], lengths, fallbackLength),
            selectedSubjectID = selectedSubject,
            includeMastered = booleanValue(value["includeMastered"], true),
        ))
    }

    private fun objectValue(element: JsonElement?): JsonObject = element as? JsonObject
        ?: throw IllegalArgumentException("数据必须是 JSON 对象。")

    private fun optionalArray(value: JsonObject, field: String): List<JsonElement> =
        if (!value.containsKey(field)) emptyList() else value[field] as? JsonArray
            ?: throw IllegalArgumentException("$field 必须为数组。")

    private fun stringValue(element: JsonElement?, label: String): String {
        val primitive = element as? JsonPrimitive
        require(primitive != null && primitive.isString) { "$label 必须为文本。" }
        return primitive.content
    }

    private fun numberValue(element: JsonElement?): Double {
        val primitive = element as? JsonPrimitive
        require(primitive != null && !primitive.isString && primitive !== JsonNull) { "无效数值。" }
        return primitive.doubleOrNull?.takeIf { it.isFinite() }
            ?: throw IllegalArgumentException("无效数值。")
    }

    private fun integerValue(element: JsonElement?, minimum: Long, maximum: Long): Long {
        val number = numberValue(element)
        require(number >= minimum.toDouble() && number <= maximum.toDouble() && number == number.toLong().toDouble()) {
            "数值必须为有效整数。"
        }
        return number.toLong()
    }

    private fun positiveID(element: JsonElement?): Int = integerValue(element, 1, Int.MAX_VALUE.toLong()).toInt()

    private fun idSet(value: JsonObject, field: String): Set<Int> = optionalArray(value, field).map(::positiveID).toSet()

    private fun booleanValue(element: JsonElement?, fallback: Boolean): Boolean {
        if (element == null) return fallback
        val primitive = element as? JsonPrimitive
        require(primitive != null && !primitive.isString) { "开关值必须为布尔值。" }
        return primitive.booleanOrNull ?: throw IllegalArgumentException("开关值必须为布尔值。")
    }

    private fun enumValue(element: JsonElement?, values: Set<String>, fallback: String): String {
        if (element == null) return fallback
        val value = stringValue(element, "枚举值")
        require(value in values) { "无效枚举值：$value" }
        return value
    }

    private fun nonNull(element: JsonElement?): JsonElement? = element?.takeUnless { it === JsonNull }
    private val directions = setOf("mixed", "zhToEn", "enToZh")
    private val scopes = setOf("important", "all", "subject", "mistakes", "favorites")
    private val lengths = setOf("ten", "twenty", "fifty", "all")
    private val positiveKey = Regex("^[1-9][0-9]*$")
    private val uuidPattern = Regex("^[0-9a-fA-F]{8}-(?:[0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}$")
    private val datePrefix = Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}T")
    private const val MAX_SAFE_INTEGER = 9_007_199_254_740_991L
}

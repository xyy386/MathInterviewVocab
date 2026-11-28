package com.xingyiyao.mathinterviewvocab.data

import android.content.Context
import com.xingyiyao.mathinterviewvocab.core.VocabularyCore

data class UiPreferences(
    val darkTheme: Boolean = false,
    val subjectOrder: List<String> = emptyList(),
    val quizPracticeMode: String = "choice",
)

/**
 * 在应用私有 SharedPreferences 中持久化界面偏好（主题、学科顺序、检测模式），
 * 与跨端共享的 `progress.json` / `settings.json` 契约保持隔离。
 */
class UiPreferencesStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): UiPreferences {
        val darkTheme = prefs.getBoolean(KEY_DARK_THEME, false)
        val rawOrder = prefs.getString(KEY_SUBJECT_ORDER, null).orEmpty()
        val order = rawOrder.split(',')
            .map { it.trim() }
            .filter { it in VocabularyCore.subjectOrder }
            .distinct()
        val rawMode = prefs.getString(KEY_QUIZ_PRACTICE_MODE, "choice")
        val mode = rawMode.takeIf { it in setOf("choice", "flashcard") } ?: "choice"
        return UiPreferences(
            darkTheme = darkTheme,
            subjectOrder = order,
            quizPracticeMode = mode,
        )
    }

    fun saveDarkTheme(dark: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_THEME, dark).apply()
    }

    fun saveSubjectOrder(order: List<String>) {
        val sanitized = order.filter { it in VocabularyCore.subjectOrder }.distinct()
        prefs.edit().putString(KEY_SUBJECT_ORDER, sanitized.joinToString(",")).apply()
    }

    fun saveQuizPracticeMode(mode: String) {
        if (mode !in setOf("choice", "flashcard")) return
        prefs.edit().putString(KEY_QUIZ_PRACTICE_MODE, mode).apply()
    }

    private companion object {
        const val PREFS_NAME = "ui-preferences"
        const val KEY_DARK_THEME = "dark_theme"
        const val KEY_SUBJECT_ORDER = "subject_order"
        const val KEY_QUIZ_PRACTICE_MODE = "quiz_practice_mode"
    }
}

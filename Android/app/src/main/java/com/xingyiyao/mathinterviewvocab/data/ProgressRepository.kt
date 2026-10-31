package com.xingyiyao.mathinterviewvocab.data

import com.xingyiyao.mathinterviewvocab.core.ProgressData
import com.xingyiyao.mathinterviewvocab.core.QuizAttempt
import com.xingyiyao.mathinterviewvocab.core.QuizPreferences
import com.xingyiyao.mathinterviewvocab.core.SpeechSettings
import com.xingyiyao.mathinterviewvocab.core.VocabularyCore
import com.xingyiyao.mathinterviewvocab.core.VocabularyJson
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** 仅由 Application.filesDir 构造，不读桌面端或用户其他目录的记录。 */
class ProgressRepository internal constructor(
    directory: File,
    private val validIDs: Set<Int>,
    storageFactory: (File) -> AtomicDocument,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    constructor(directory: File, validIDs: Set<Int>) : this(directory, validIDs, ::AndroidAtomicDocument)

    private val progressDocument = storageFactory(File(directory, "progress.json"))
    private val settingsDocument = storageFactory(File(directory, "settings.json"))
    private val mutex = Mutex()
    private val mutableProgress = MutableStateFlow(ProgressData())
    private val mutableSettings = MutableStateFlow(SpeechSettings())
    private val mutableError = MutableStateFlow<String?>(null)
    val progress: StateFlow<ProgressData> = mutableProgress.asStateFlow()
    val settings: StateFlow<SpeechSettings> = mutableSettings.asStateFlow()
    val error: StateFlow<String?> = mutableError.asStateFlow()
    private var loaded = false
    private var progressBlocked = false
    private var settingsBlocked = false
    private var progressError: String? = null
    private var settingsError: String? = null

    suspend fun load(): Unit = locked {
        // 两份数据分别读取，损坏发音设置不能丢弃有效学习记录，反之亦然。
        try {
            mutableProgress.value = if (progressDocument.exists()) {
                VocabularyJson.decodeProgress(progressDocument.read(), validIDs)
            } else ProgressData()
            progressBlocked = false
            progressError = null
        } catch (failure: Exception) {
            progressBlocked = true
            progressError = "学习记录无法读取，原文件已保留。可确认重置并备份原文件。${detail(failure)}"
        }
        try {
            mutableSettings.value = if (settingsDocument.exists()) {
                VocabularyJson.decodeSettings(settingsDocument.read())
            } else SpeechSettings()
            settingsBlocked = false
            settingsError = null
        } catch (failure: Exception) {
            settingsBlocked = true
            settingsError = "发音设置无法读取，原文件已保留。重新保存设置会先备份。${detail(failure)}"
        }
        loaded = true
        publishError()
        error.value?.let { throw IOException(it) }
    }

    suspend fun toggleFavorite(id: Int): Unit = locked {
        require(id in validIDs) { "无效词汇编号。" }
        saveProgress(VocabularyCore.toggleFavorite(progress.value, id))
    }

    suspend fun updatePreferences(preferences: QuizPreferences): Unit = locked {
        VocabularyJson.validatePreferences(preferences)
        saveProgress(progress.value.copy(quizPreferences = preferences))
    }

    suspend fun recordAnswer(attempt: QuizAttempt): Unit = locked {
        requireLoaded()
        VocabularyJson.validateProgress(ProgressData(attempts = listOf(attempt)), validIDs)
        requireProgressWritable()
        val next = VocabularyCore.recordAnswer(progress.value, attempt)
        if (next != progress.value) saveProgress(next)
    }

    suspend fun saveSettings(settings: SpeechSettings): Unit = locked {
        requireLoaded()
        VocabularyJson.validateSettings(settings)
        try {
            if (settingsBlocked && settingsDocument.exists()) settingsDocument.backup()
            settingsDocument.write(VocabularyJson.encodeSettings(settings))
            mutableSettings.value = settings
            settingsBlocked = false
            settingsError = null
            publishError()
        } catch (failure: Exception) {
            settingsError = "发音设置保存未确认成功，当前设置未改变，请重试。${detail(failure)}"
            publishError()
            throw IOException(error.value, failure)
        }
    }

    suspend fun resetKeepingPreferences(): Unit = locked {
        requireLoaded()
        val next = ProgressData(quizPreferences = progress.value.quizPreferences)
        try {
            if (progressBlocked && progressDocument.exists()) progressDocument.backup()
            progressDocument.write(VocabularyJson.encodeProgress(next))
            mutableProgress.value = next
            progressBlocked = false
            progressError = null
            publishError()
        } catch (failure: Exception) {
            progressError = "学习记录重置未确认成功，尚未在当前会话清空记录，请重试。${detail(failure)}"
            publishError()
            throw IOException(error.value, failure)
        }
    }

    private fun saveProgress(next: ProgressData) {
        requireLoaded()
        requireProgressWritable()
        VocabularyJson.validateProgress(next, validIDs)
        try {
            progressDocument.write(VocabularyJson.encodeProgress(next))
            mutableProgress.value = next
            progressError = null
            publishError()
        } catch (failure: Exception) {
            progressError = "学习记录保存失败，未将失败操作计入记录。请检查存储空间并重试。${detail(failure)}"
            publishError()
            throw IOException(error.value, failure)
        }
    }

    private fun requireLoaded() = check(loaded) { "尚未读取本地记录，请先重试加载。" }
    private fun requireProgressWritable() = check(!progressBlocked) {
        progressError ?: "学习记录无法读取，尚未覆盖原文件。"
    }
    private fun publishError() {
        mutableError.value = listOfNotNull(progressError, settingsError).joinToString("\n").ifEmpty { null }
    }
    private fun detail(failure: Exception) = failure.message?.let { "（$it）" }.orEmpty()
    private suspend fun <T> locked(operation: () -> T): T = withContext(dispatcher) {
        mutex.withLock { operation() }
    }
}

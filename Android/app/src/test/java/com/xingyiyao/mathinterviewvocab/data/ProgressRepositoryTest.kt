package com.xingyiyao.mathinterviewvocab.data

import com.xingyiyao.mathinterviewvocab.core.ProgressData
import com.xingyiyao.mathinterviewvocab.core.QuizPreferences
import com.xingyiyao.mathinterviewvocab.core.SpeechSettings
import com.xingyiyao.mathinterviewvocab.core.VocabularyJson
import com.xingyiyao.mathinterviewvocab.core.attempt
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProgressRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()
    private val ids = (1..326).toSet()
    private val documents = mutableMapOf<String, TestAtomicDocument>()

    private fun repository(directory: File = temporary.root): ProgressRepository = ProgressRepository(
        directory, ids, { file -> documents.getOrPut(file.path) { TestAtomicDocument(file) } }, Dispatchers.IO,
    )

    @Test fun missingFilesStartEmptyAndMutationsRequireLoading() = runBlocking<Unit> {
        val repository = repository()
        expectFailure { repository.toggleFavorite(1) }
        assertFalse(File(temporary.root, "progress.json").exists())
        repository.load()
        assertEquals(ProgressData(), repository.progress.value)
        assertEquals(SpeechSettings(), repository.settings.value)
        assertNull(repository.error.value)
    }

    @Test fun answersFavoritesPreferencesAndSpeechSettingsSurviveReload() = runBlocking<Unit> {
        val repository = repository()
        repository.load()
        repository.toggleFavorite(1)
        val preferences = QuizPreferences("enToZh", "subject", "all", "statistics", false)
        repository.updatePreferences(preferences)
        repository.recordAnswer(attempt(1, correct = false))
        repository.saveSettings(SpeechSettings("en-GB", 0.75))
        val reloaded = repository()
        reloaded.load()
        assertEquals(repository.progress.value, reloaded.progress.value)
        assertEquals(preferences, reloaded.progress.value.quizPreferences)
        assertEquals(SpeechSettings("en-GB", 0.75), reloaded.settings.value)
        assertEquals(setOf(1), reloaded.progress.value.wrongIDs)
        assertEquals(setOf(1), reloaded.progress.value.favoriteIDs)
    }

    @Test fun failedWriteLeavesStateAndExistingFileUnchangedAndCanRetry() = runBlocking<Unit> {
        val repository = repository()
        repository.load()
        repository.toggleFavorite(1)
        val file = File(temporary.root, "progress.json")
        val original = file.readText()
        val document = documents.getValue(file.path)
        document.failWrite = true
        expectFailure { repository.toggleFavorite(2) }
        assertEquals(setOf(1), repository.progress.value.favoriteIDs)
        assertEquals(original, file.readText())
        assertTrue(repository.error.value!!.contains("保存失败"))
        document.failWrite = false
        repository.toggleFavorite(2)
        assertEquals(setOf(1, 2), repository.progress.value.favoriteIDs)
        assertNull(repository.error.value)
    }

    @Test fun failedAnswerSaveIsNotCountedAndRetryCannotDoubleCount() = runBlocking<Unit> {
        val repository = repository()
        repository.load()
        val document = documents.getValue(File(temporary.root, "progress.json").path)
        document.failWrite = true
        val answer = attempt(1)
        expectFailure { repository.recordAnswer(answer) }
        assertTrue(repository.progress.value.attempts.isEmpty())
        document.failWrite = false
        repository.recordAnswer(answer)
        val saves = document.writeCount
        repository.recordAnswer(answer)
        assertEquals(saves, document.writeCount)
        assertEquals(1, repository.progress.value.attempts.size)
        assertEquals(1, repository.progress.value.correctStreak["1"])
    }

    @Test fun corruptedProgressCannotBeOverwrittenByNormalMutations() = runBlocking<Unit> {
        val file = File(temporary.root, "progress.json")
        file.writeText("broken original")
        val repository = repository()
        expectFailure { repository.load() }
        expectFailure { repository.toggleFavorite(1) }
        expectFailure { repository.updatePreferences(QuizPreferences(scope = "all")) }
        expectFailure { repository.recordAnswer(attempt(1)) }
        assertEquals("broken original", file.readText())
        assertNotNull(repository.error.value)
        assertFalse(temporary.root.listFiles()!!.any { it.name.contains("backup") })
    }

    @Test fun explicitCorruptionResetBacksUpOriginalAndRetainsIndependentSettings() = runBlocking<Unit> {
        val file = File(temporary.root, "progress.json")
        file.writeText("broken original")
        File(temporary.root, "settings.json").writeText(VocabularyJson.encodeSettings(SpeechSettings("en-GB", 1.25)))
        val repository = repository()
        expectFailure { repository.load() }
        repository.resetKeepingPreferences()
        val backups = temporary.root.listFiles()!!.filter { it.name.startsWith("progress.json.backup-") }
        assertEquals(1, backups.size)
        assertEquals("broken original", backups.single().readText())
        assertEquals(ProgressData(), VocabularyJson.decodeProgress(file.readText()))
        assertEquals(SpeechSettings("en-GB", 1.25), repository.settings.value)
        assertNull(repository.error.value)
    }

    @Test fun backupFailureNeverUnblocksOrOverwritesCorruptedProgress() = runBlocking<Unit> {
        val file = File(temporary.root, "progress.json")
        file.writeText("broken original")
        val repository = repository()
        expectFailure { repository.load() }
        documents.getValue(file.path).failBackup = true
        expectFailure { repository.resetKeepingPreferences() }
        expectFailure { repository.toggleFavorite(1) }
        assertEquals("broken original", file.readText())
        assertTrue(repository.progress.value.favoriteIDs.isEmpty())
    }

    @Test fun corruptSettingsDoNotDiscardProgressAndOnlyExplicitSaveCanRecoverThem() = runBlocking<Unit> {
        val progress = ProgressData(favoriteIDs = setOf(3))
        File(temporary.root, "progress.json").writeText(VocabularyJson.encodeProgress(progress))
        val settingsFile = File(temporary.root, "settings.json")
        settingsFile.writeText("broken voice")
        val repository = repository()
        expectFailure { repository.load() }
        assertEquals(progress, repository.progress.value)
        repository.toggleFavorite(4)
        assertNotNull(repository.error.value)
        assertEquals("broken voice", settingsFile.readText())
        repository.saveSettings(SpeechSettings("en-GB", 0.75))
        assertEquals(setOf(3, 4), repository.progress.value.favoriteIDs)
        assertEquals("broken voice", temporary.root.listFiles()!!.single { it.name.startsWith("settings.json.backup-") }.readText())
        assertNull(repository.error.value)
    }

    @Test fun recoveringOneFileDoesNotClearOtherFileErrorOrProtection() = runBlocking<Unit> {
        val progressFile = File(temporary.root, "progress.json")
        progressFile.writeText("broken progress")
        File(temporary.root, "settings.json").writeText("broken settings")
        val repository = repository()
        expectFailure { repository.load() }
        repository.saveSettings(SpeechSettings())
        assertTrue(repository.error.value!!.contains("学习记录"))
        expectFailure { repository.toggleFavorite(1) }
        assertEquals("broken progress", progressFile.readText())
        repository.resetKeepingPreferences()
        assertNull(repository.error.value)
    }

    @Test fun failedSettingsWriteRetainsVoiceAndIndependentProgressError() = runBlocking<Unit> {
        File(temporary.root, "progress.json").writeText("broken progress")
        val settingsFile = File(temporary.root, "settings.json")
        val original = VocabularyJson.encodeSettings(SpeechSettings("en-GB", 0.75))
        settingsFile.writeText(original)
        val repository = repository()
        expectFailure { repository.load() }
        val document = documents.getValue(settingsFile.path)
        document.failWrite = true
        expectFailure { repository.saveSettings(SpeechSettings("en-US", 1.25)) }
        assertEquals(SpeechSettings("en-GB", 0.75), repository.settings.value)
        assertEquals(original, settingsFile.readText())
        assertTrue(repository.error.value!!.contains("学习记录"))
        document.failWrite = false
        repository.saveSettings(SpeechSettings())
        assertTrue(repository.error.value!!.contains("学习记录"))
        expectFailure { repository.toggleFavorite(1) }
    }

    @Test fun failedResetAfterBackupRetainsProtectionAndRetryMakesUniqueBackup() = runBlocking<Unit> {
        val file = File(temporary.root, "progress.json")
        file.writeText("broken original")
        val repository = repository()
        expectFailure { repository.load() }
        val document = documents.getValue(file.path)
        document.failWrite = true
        expectFailure { repository.resetKeepingPreferences() }
        expectFailure { repository.toggleFavorite(1) }
        assertEquals("broken original", file.readText())
        document.failWrite = false
        repository.resetKeepingPreferences()
        val backups = temporary.root.listFiles()!!.filter { it.name.startsWith("progress.json.backup-") }
        assertEquals(2, backups.size)
        assertTrue(backups.all { it.readText() == "broken original" })
        assertNull(repository.error.value)
        repository.toggleFavorite(1)
        assertEquals(setOf(1), repository.progress.value.favoriteIDs)
    }

    @Test fun normalResetKeepsQuizPreferencesAndVoiceButClearsLearningState() = runBlocking<Unit> {
        val repository = repository()
        repository.load()
        val preferences = QuizPreferences("zhToEn", "favorites", "fifty", "algebra", false)
        repository.updatePreferences(preferences)
        repository.toggleFavorite(1)
        repository.recordAnswer(attempt(1, correct = false))
        repository.saveSettings(SpeechSettings("en-GB", 1.25))
        repository.resetKeepingPreferences()
        assertEquals(ProgressData(quizPreferences = preferences), repository.progress.value)
        assertEquals(SpeechSettings("en-GB", 1.25), repository.settings.value)
        val reloaded = repository()
        reloaded.load()
        assertEquals(repository.progress.value, reloaded.progress.value)
    }

    @Test fun invalidReferencesAndPreferencesNeverReachStorage() = runBlocking<Unit> {
        val repository = repository()
        repository.load()
        expectFailure { repository.toggleFavorite(999) }
        expectFailure { repository.recordAnswer(attempt(1).copy(selectedOptionID = 999)) }
        expectFailure { repository.updatePreferences(QuizPreferences(scope = "invalid")) }
        expectFailure { repository.saveSettings(SpeechSettings(speed = 2.0)) }
        assertTrue(documents.values.all { it.writeCount == 0 })
        assertEquals(ProgressData(), repository.progress.value)
    }

    @Test fun concurrentWritesSerializeWithoutLosingFavoritesOrAnswers() = runBlocking<Unit> {
        val repository = repository()
        repository.load()
        coroutineScope {
            repeat(50) { index -> launch(Dispatchers.Default) {
                repository.toggleFavorite(index + 1)
                repository.recordAnswer(attempt(index + 1, termID = index + 1))
            } }
        }
        assertEquals((1..50).toSet(), repository.progress.value.favoriteIDs)
        assertEquals(50, repository.progress.value.attempts.size)
        val reloaded = repository()
        reloaded.load()
        assertEquals(repository.progress.value, reloaded.progress.value)
    }

    @Test fun legacyProgressPreferencesMigrateWithoutDroppingRecords() = runBlocking<Unit> {
        File(temporary.root, "progress.json").writeText("""{
            "favoriteIDs":[2], "quizPreferences":{"importantOnly":false,"questionCount":326}
        }""")
        val repository = repository()
        repository.load()
        assertEquals(setOf(2), repository.progress.value.favoriteIDs)
        assertEquals("all", repository.progress.value.quizPreferences.scope)
        assertEquals("all", repository.progress.value.quizPreferences.questionLength)
        repository.toggleFavorite(3)
        val reloaded = repository()
        reloaded.load()
        assertEquals(repository.progress.value, reloaded.progress.value)
    }

    @Test fun sameRepositoryCanRetryLoadingWithoutResetAfterExternalRepair() = runBlocking<Unit> {
        val file = File(temporary.root, "progress.json")
        file.writeText("broken")
        val repository = repository()
        expectFailure { repository.load() }
        val recovered = ProgressData(favoriteIDs = setOf(5))
        file.writeText(VocabularyJson.encodeProgress(recovered))
        repository.load()
        assertEquals(recovered, repository.progress.value)
        assertNull(repository.error.value)
    }

    @Test fun IDsOutsideCanonicalLibraryBlockLoadAndPreserveFile() = runBlocking<Unit> {
        val file = File(temporary.root, "progress.json")
        val source = VocabularyJson.encodeProgress(ProgressData(favoriteIDs = setOf(999)))
        file.writeText(source)
        val repository = repository()
        expectFailure { repository.load() }
        expectFailure { repository.toggleFavorite(1) }
        assertEquals(source, file.readText())
    }

    private suspend fun expectFailure(operation: suspend () -> Unit): Throwable {
        try { operation() } catch (failure: Exception) { return failure }
        throw AssertionError("Expected operation to fail.")
    }
}

/** 独立临时目录文件，不调用 android.jar 的存根方法。 */
internal class TestAtomicDocument(private val file: File) : AtomicDocument {
    var failWrite = false
    var failBackup = false
    var writeCount = 0
    override fun exists() = file.isFile
    override fun read() = file.readText()
    override fun write(source: String) {
        if (failWrite) throw IOException("模拟磁盘写入失败")
        file.parentFile!!.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.${UUID.randomUUID()}.tmp")
        temporary.writeText(source)
        Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        writeCount++
    }
    override fun backup(): File {
        if (failBackup) throw IOException("模拟备份失败")
        return file.copyTo(File(file.parentFile, "${file.name}.backup-${UUID.randomUUID()}"), overwrite = false)
    }
}

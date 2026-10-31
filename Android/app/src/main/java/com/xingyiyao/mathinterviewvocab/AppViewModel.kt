package com.xingyiyao.mathinterviewvocab

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.xingyiyao.mathinterviewvocab.core.ProgressData
import com.xingyiyao.mathinterviewvocab.core.QuizAttempt
import com.xingyiyao.mathinterviewvocab.core.QuizPreferences
import com.xingyiyao.mathinterviewvocab.core.QuizQuestion
import com.xingyiyao.mathinterviewvocab.core.SpeechSettings
import com.xingyiyao.mathinterviewvocab.core.Term
import com.xingyiyao.mathinterviewvocab.core.TermFilters
import com.xingyiyao.mathinterviewvocab.core.VocabularyCore
import com.xingyiyao.mathinterviewvocab.data.ProgressRepository
import com.xingyiyao.mathinterviewvocab.data.TermLibrary
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class SavedQuestion(
    val id: String,
    val termID: Int,
    val direction: String,
    val optionIDs: List<Int>,
    val correctOptionID: Int,
)

@Serializable
data class SavedQuiz(
    val stage: String = "active",
    val questions: List<SavedQuestion> = emptyList(),
    val index: Int = 0,
    val selectedOptionID: Int? = null,
    val pendingAttempt: QuizAttempt? = null,
    val answerSaved: Boolean = false,
    val attemptIDs: List<String> = emptyList(),
)

@Serializable
data class SavedScreen(
    val tab: String = "glossary",
    val showSettings: Boolean = false,
    val query: String = "",
    val subjectID: String? = null,
    val importantOnly: Boolean = false,
    val favoritesOnly: Boolean = false,
    val mistakesOnly: Boolean = false,
    val selectedTermID: Int? = null,
    val quiz: SavedQuiz? = null,
    val pendingQuizPreferences: QuizPreferences? = null,
)

data class AppUiState(
    val loading: Boolean = true,
    val loadError: String? = null,
    val operationError: String? = null,
    val busy: Boolean = false,
    val terms: List<Term> = emptyList(),
    val progress: ProgressData = ProgressData(),
    val settings: SpeechSettings = SpeechSettings(),
    val screen: SavedScreen = SavedScreen(),
) {
    val filters: TermFilters
        get() = TermFilters(
            screen.query, screen.subjectID, screen.importantOnly,
            screen.favoritesOnly, screen.mistakesOnly,
        )
    val filteredTerms: List<Term>
        get() = VocabularyCore.filterTerms(terms, filters, progress)
    val selectedTerm: Term?
        get() = terms.find { it.id == screen.selectedTermID }
    val currentQuestion: QuizQuestion?
        get() {
            val quiz = screen.quiz ?: return null
            val saved = quiz.questions.getOrNull(quiz.index) ?: return null
            val term = terms.find { it.id == saved.termID } ?: return null
            val options = saved.optionIDs.mapNotNull { id -> terms.find { it.id == id } }
            if (options.size != 4) return null
            return QuizQuestion(saved.id, term, saved.direction, options, saved.correctOptionID)
        }
}

class AppViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val services = (application as MathVocabApplication).services
    val speechState = services.speech.state
    val updateState = services.updater.state
    val updater get() = services.updater
    val canRecoverData get() = repository != null
    private var repository: ProgressRepository? = null
    private var progressCollector: Job? = null
    private var settingsCollector: Job? = null
    private var errorCollector: Job? = null
    private var loading = false
    private val mutableState = MutableStateFlow(
        AppUiState(screen = restoreScreen(savedStateHandle.get<String>("screen"))),
    )
    val state: StateFlow<AppUiState> = mutableState.asStateFlow()

    init { load() }

    fun load() {
        if (loading) return
        loading = true
        mutableState.value = mutableState.value.copy(loading = true, loadError = null)
        viewModelScope.launch {
            try {
                val terms = withContext(Dispatchers.IO) {
                    TermLibrary.load(getApplication<Application>().assets)
                }
                val store = repository ?: ProgressRepository(
                    getApplication<Application>().filesDir, terms.map { it.id }.toSet(),
                ).also { repository = it }
                store.load()
                var screen = mutableState.value.screen
                if (screen.selectedTermID !in terms.map { it.id }.toSet()) {
                    screen = screen.copy(selectedTermID = null)
                }
                screen = restoreSavedAnswer(screen, store.progress.value, terms)
                mutableState.value = AppUiState(
                    loading = false, terms = terms, progress = store.progress.value,
                    settings = store.settings.value, screen = screen,
                )
                persistScreen(screen)
                progressCollector?.cancel()
                settingsCollector?.cancel()
                errorCollector?.cancel()
                progressCollector = launch {
                    store.progress.collect { progress ->
                        val current = mutableState.value.copy(progress = progress)
                        val selected = current.screen.selectedTermID
                        val visible = current.filteredTerms.any { it.id == selected }
                        val screen = if (selected != null && !visible)
                            current.screen.copy(selectedTermID = null) else current.screen
                        mutableState.value = current.copy(screen = screen)
                        if (screen != current.screen) { stopSpeech(); persistScreen(screen) }
                    }
                }
                settingsCollector = launch {
                    store.settings.collect { settings ->
                        mutableState.value = mutableState.value.copy(settings = settings)
                    }
                }
                errorCollector = launch {
                    store.error.collect { error ->
                        if (error != null) mutableState.value = mutableState.value.copy(operationError = error)
                    }
                }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                mutableState.value = mutableState.value.copy(
                    loading = false,
                    loadError = failure.message ?: "无法读取本地数据，请重试。原有记录未被覆盖。",
                )
            } finally { loading = false }
        }
    }

    fun recoverData(resetProgress: Boolean) {
        val store = repository ?: return
        if (loading || state.value.busy) return
        mutableState.value = mutableState.value.copy(busy = true)
        viewModelScope.launch {
            try {
                if (resetProgress) {
                    store.resetKeepingPreferences()
                    updateScreen { it.copy(quiz = null) }
                } else store.saveSettings(SpeechSettings())
                mutableState.value = mutableState.value.copy(busy = false)
                load()
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                mutableState.value = mutableState.value.copy(
                    busy = false, loadError = failure.message ?: "恢复失败，原文件仍已保留。",
                )
            }
        }
    }

    fun selectTab(tab: String) {
        if (tab !in listOf("glossary", "quiz", "progress")) return
        stopSpeech()
        updateScreen { it.copy(tab = tab, showSettings = false) }
    }

    fun showSettings(show: Boolean) {
        stopSpeech()
        updateScreen { it.copy(showSettings = show) }
    }

    fun setQuery(query: String) = changeFilters { it.copy(query = query) }
    fun setSubject(subjectID: String?) = changeFilters { it.copy(subjectID = subjectID) }
    fun setImportantOnly(value: Boolean) = changeFilters { it.copy(importantOnly = value) }
    fun setFavoritesOnly(value: Boolean) = changeFilters { it.copy(favoritesOnly = value) }
    fun setMistakesOnly(value: Boolean) = changeFilters { it.copy(mistakesOnly = value) }
    fun clearFilters() = changeFilters {
        it.copy(subjectID = null, importantOnly = false, favoritesOnly = false, mistakesOnly = false)
    }

    fun selectTerm(id: Int?) {
        if (id != null && state.value.terms.none { it.id == id }) return
        stopSpeech()
        updateScreen { it.copy(tab = "glossary", selectedTermID = id, showSettings = false) }
    }

    fun toggleFavorite(id: Int) = mutate { it.toggleFavorite(id) }

    fun saveSettings(settings: SpeechSettings) = mutate { it.saveSettings(settings) }

    fun setPreferences(preferences: QuizPreferences) = mutate { it.updatePreferences(preferences) }

    fun resetProgress() = mutate { store ->
        store.resetKeepingPreferences()
        stopSpeech()
        updateScreen { it.copy(quiz = null, pendingQuizPreferences = null) }
    }

    fun startQuiz(
        preferences: QuizPreferences = state.value.progress.quizPreferences,
        replaceExisting: Boolean = false,
    ) {
        if (state.value.busy) return
        val current = state.value
        if (current.screen.quiz?.stage == "active" && !replaceExisting) {
            stopSpeech()
            updateScreen { it.copy(tab = "quiz", showSettings = false, pendingQuizPreferences = preferences) }
            return
        }
        val pool = VocabularyCore.makePool(current.terms, preferences, current.progress)
        val count = VocabularyCore.resolveQuestionCount(preferences.questionLength, pool.size)
        val questions = VocabularyCore.makeQuestions(pool, current.terms, count, preferences.direction)
        if (questions.isEmpty()) return
        mutate { store ->
            store.updatePreferences(preferences)
            stopSpeech()
            updateScreen { screen ->
                screen.copy(
                    tab = "quiz",
                    pendingQuizPreferences = null,
                    quiz = SavedQuiz(questions = questions.map {
                        SavedQuestion(it.id, it.term.id, it.direction, it.options.map(Term::id), it.correctOptionID)
                    }),
                )
            }
        }
    }

    fun confirmQuizReplacement() {
        val preferences = state.value.screen.pendingQuizPreferences ?: return
        startQuiz(preferences, replaceExisting = true)
    }

    fun cancelQuizReplacement() = updateScreen { it.copy(pendingQuizPreferences = null) }

    fun answer(optionID: Int) {
        val current = state.value
        val quiz = current.screen.quiz ?: return
        val question = current.currentQuestion ?: return
        if (current.busy || quiz.stage != "active" || quiz.selectedOptionID != null ||
            question.options.none { it.id == optionID }) return
        // 同一个 UUID 贯穿保存、失败重试和状态恢复，不能重复计分。
        val attempt = QuizAttempt(
            UUID.randomUUID().toString(), question.term.id, question.direction,
            optionID, question.correctOptionID, Instant.now().toString(),
        )
        updateScreen { it.copy(quiz = quiz.copy(selectedOptionID = optionID, pendingAttempt = attempt)) }
        savePendingAnswer()
    }

    fun retryAnswerSave() { savePendingAnswer() }

    private fun savePendingAnswer() {
        val quiz = state.value.screen.quiz ?: return
        val attempt = quiz.pendingAttempt ?: return
        if (state.value.busy || quiz.answerSaved) return
        mutate { store ->
            if (store.progress.value.attempts.none { it.id == attempt.id }) store.recordAnswer(attempt)
            updateScreen { screen ->
                val active = screen.quiz ?: return@updateScreen screen
                screen.copy(quiz = active.copy(
                    answerSaved = true,
                    attemptIDs = (active.attemptIDs + attempt.id).distinct(),
                ))
            }
        }
    }

    fun nextQuestion() {
        val quiz = state.value.screen.quiz ?: return
        if (state.value.busy || !quiz.answerSaved) return
        stopSpeech()
        updateScreen {
            it.copy(quiz = if (quiz.index + 1 >= quiz.questions.size) quiz.copy(stage = "summary")
            else quiz.copy(index = quiz.index + 1, selectedOptionID = null, pendingAttempt = null, answerSaved = false))
        }
    }

    fun finishQuiz() {
        if (state.value.busy) return
        stopSpeech()
        updateScreen { it.copy(quiz = null, pendingQuizPreferences = null) }
    }

    fun reviewMistakes() {
        val preferences = state.value.progress.quizPreferences.copy(scope = "mistakes", includeMastered = true)
        startQuiz(preferences)
    }

    fun speak(text: String, accent: String = state.value.settings.accent) {
        services.speech.speak(text, accent, state.value.settings.speed)
    }

    fun stopSpeech() { services.speech.stop() }

    fun dismissError() {
        mutableState.value = mutableState.value.copy(operationError = null)
    }

    private fun mutate(operation: suspend (ProgressRepository) -> Unit) {
        val store = repository ?: return
        if (state.value.busy || state.value.loading || state.value.loadError != null) return
        mutableState.value = mutableState.value.copy(busy = true, operationError = null)
        viewModelScope.launch {
            try { operation(store) }
            catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                mutableState.value = mutableState.value.copy(
                    operationError = failure.message ?: "本地记录保存失败，请检查磁盘空间后重试。",
                )
            } finally { mutableState.value = mutableState.value.copy(busy = false) }
        }
    }

    private fun changeFilters(transform: (SavedScreen) -> SavedScreen) {
        val screen = transform(state.value.screen)
        val filters = TermFilters(
            screen.query, screen.subjectID, screen.importantOnly, screen.favoritesOnly, screen.mistakesOnly,
        )
        val selected = screen.selectedTermID
        val visible = VocabularyCore.filterTerms(state.value.terms, filters, state.value.progress)
        updateScreen { screen.copy(selectedTermID = selected?.takeIf { id -> visible.any { it.id == id } }) }
        if (state.value.screen.selectedTermID != selected) stopSpeech()
    }

    private fun updateScreen(transform: (SavedScreen) -> SavedScreen) {
        val screen = transform(state.value.screen)
        mutableState.value = mutableState.value.copy(screen = screen)
        persistScreen(screen)
    }

    private fun persistScreen(screen: SavedScreen) {
        savedStateHandle["screen"] = json.encodeToString(screen)
    }

    private fun restoreScreen(encoded: String?): SavedScreen {
        if (encoded == null || encoded.length > 250_000) return SavedScreen()
        val screen = runCatching { json.decodeFromString<SavedScreen>(encoded) }.getOrDefault(SavedScreen())
        return screen.copy(tab = screen.tab.takeIf { it in setOf("glossary", "quiz", "progress") } ?: "glossary")
    }

    private fun restoreSavedAnswer(screen: SavedScreen, progress: ProgressData, terms: List<Term>): SavedScreen {
        val quiz = screen.quiz ?: return screen
        val ids = terms.map(Term::id).toSet()
        val valid = quiz.questions.isNotEmpty() && quiz.index in quiz.questions.indices &&
            quiz.stage in setOf("active", "summary") && quiz.questions.all {
                it.termID in ids && it.correctOptionID == it.termID && it.optionIDs.size == 4 &&
                    it.optionIDs.toSet().size == 4 && it.optionIDs.all(ids::contains) && it.termID in it.optionIDs &&
                    it.direction in setOf("zhToEn", "enToZh")
            }
        if (!valid) return screen.copy(quiz = null)
        val saved = quiz.pendingAttempt?.let { pending -> progress.attempts.any { it.id == pending.id } } == true
        val restored = quiz.copy(
            answerSaved = saved,
            attemptIDs = (quiz.attemptIDs + if (saved) listOf(quiz.pendingAttempt.id) else emptyList()).distinct(),
        )
        return screen.copy(quiz = restored)
    }

    override fun onCleared() { stopSpeech(); super.onCleared() }
}

package com.rewordly.app.feature.learn

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rewordly.app.core.common.AppResult
import com.rewordly.app.core.common.TimeProvider
import com.rewordly.app.domain.model.LearningSession
import com.rewordly.app.domain.model.ReviewRating
import com.rewordly.app.domain.model.WordWithProgress
import com.rewordly.app.domain.model.isUntouched
import com.rewordly.app.domain.repository.SettingsRepository
import com.rewordly.app.domain.repository.VocabularyRepository
import com.rewordly.app.domain.usecase.MarkWordKnownUseCase
import com.rewordly.app.domain.usecase.StartLearningSessionUseCase
import com.rewordly.app.domain.usecase.StartStudyingWordUseCase
import com.rewordly.app.domain.usecase.SubmitReviewAnswerUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface LearnUiState {
    data object Loading : LearnUiState

    data object Empty : LearnUiState

    data class Content(
        val session: LearningSession,
        val items: List<WordWithProgress>,
        val current: WordWithProgress,
        val revealed: Boolean,
        val isSubmitting: Boolean,
        val submitFailed: Boolean,
    ) : LearnUiState {
        val index: Int get() = session.currentPosition
        val total: Int get() = session.total
        val completedCount: Int get() = session.completedCount

        /**
         * A word the user has never answered is triaged ("do you already know it?") instead of reviewed
         * ("did you remember it?"), so the two answers need different wording.
         */
        val isFirstEncounter: Boolean get() = current.progress.isUntouched
    }

    /** Every word in the session was answered. */
    data class Finished(val completedCount: Int, val total: Int) : LearnUiState
}

sealed interface LearnUiEvent {
    /** The swipe (or the matching half of the answer bar): true when the word was recognised. */
    data class Answer(val remembered: Boolean) : LearnUiEvent

    /** Shows the translation and the examples before answering. */
    data object Reveal : LearnUiEvent

    data object ToggleSaved : LearnUiEvent

    data object Restart : LearnUiEvent
}

/** Transient state of the single card on screen, kept out of [LearnUiState] to keep it a plain snapshot. */
private data class CardFlags(
    val revealed: Boolean = false,
    val isSubmitting: Boolean = false,
    val submitFailed: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LearnViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val settingsRepository: SettingsRepository,
    private val vocabularyRepository: VocabularyRepository,
    private val startLearningSession: StartLearningSessionUseCase,
    private val startStudyingWord: StartStudyingWordUseCase,
    private val markWordKnown: MarkWordKnownUseCase,
    private val submitReviewAnswer: SubmitReviewAnswerUseCase,
    private val timeProvider: TimeProvider,
) : ViewModel() {
    private val session = MutableStateFlow(restoreSession())
    private val flags = MutableStateFlow(CardFlags())
    private var shownAt: Long = 0L

    /** Live progress (saved/learned/views) is merged in from Room; the session order is fixed. */
    private val words = settingsRepository.settings
        .map { it.learningLanguage }
        .distinctUntilChanged()
        .flatMapLatest { vocabularyRepository.observeWords(it) }

    val uiState: StateFlow<LearnUiState> = combine(session, words, flags) { current, list, cardFlags ->
        toState(current, list, cardFlags)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), LearnUiState.Loading)

    init {
        viewModelScope.launch {
            if (session.value == null) startNewSession()
        }
        // Every time a different card is shown, remember that the user saw it and start the timer.
        viewModelScope.launch {
            uiState
                .map { (it as? LearnUiState.Content)?.current?.word?.id }
                .distinctUntilChanged()
                .collect { wordId ->
                    if (wordId != null) {
                        shownAt = timeProvider.nowMillis()
                        vocabularyRepository.recordView(wordId)
                    }
                }
        }
    }

    fun onEvent(event: LearnUiEvent) {
        when (event) {
            is LearnUiEvent.Answer -> answer(event.remembered)
            LearnUiEvent.Reveal -> flags.value = flags.value.copy(revealed = true)
            LearnUiEvent.ToggleSaved -> toggleSaved()
            LearnUiEvent.Restart -> viewModelScope.launch { startNewSession() }
        }
    }

    /**
     * Answers the card on screen. A word the user has never met is only triaged: "I know it" graduates it
     * right away (tomorrow's review still checks the claim), "study it" puts it into the rotation. Every
     * other answer is a normal review answer, and the word graduates once it collects enough correct
     * answers in a row.
     */
    private fun answer(remembered: Boolean) {
        val state = uiState.value as? LearnUiState.Content ?: return
        if (flags.value.isSubmitting) return
        val word = state.current
        val sessionId = session.value?.sessionId ?: return
        val duration = (timeProvider.nowMillis() - shownAt).coerceIn(0, MAX_CARD_DURATION_MILLIS)
        flags.value = flags.value.copy(isSubmitting = true, submitFailed = false)
        viewModelScope.launch {
            val stored = when {
                state.isFirstEncounter && remembered -> markWordKnown(word.word.id, sessionId)
                state.isFirstEncounter -> startStudyingWord(word.word.id, sessionId)
                else -> submitReviewAnswer(
                    wordId = word.word.id,
                    sessionId = sessionId,
                    rating = if (remembered) ReviewRating.GOOD else ReviewRating.AGAIN,
                    durationMs = duration,
                )
            }
            if (stored is AppResult.Success) {
                complete(word.word.id)
                flags.value = CardFlags()
            } else {
                flags.value = flags.value.copy(isSubmitting = false, submitFailed = true)
            }
        }
    }

    private fun toggleSaved() {
        val state = uiState.value as? LearnUiState.Content ?: return
        viewModelScope.launch {
            vocabularyRepository.setSaved(state.current.word.id, !state.current.progress.isSaved)
        }
    }

    private fun complete(wordId: String) {
        update { current ->
            val completed = current.completedWordIds + wordId
            val finished = completed.size >= current.total
            current.copy(
                completedWordIds = completed,
                completedAt = if (finished) current.completedAt ?: now() else current.completedAt,
                currentPosition = if (finished) current.currentPosition else current.currentPosition + 1,
            )
        }
        flags.value = CardFlags()
    }

    private fun now(): Long = timeProvider.nowMillis()

    private suspend fun startNewSession() {
        val settings = settingsRepository.settings.first()
        session.value = startLearningSession(settings.learningLanguage, settings.dailyGoal)
            .also(::persist)
    }

    private fun update(transform: (LearningSession) -> LearningSession) {
        session.value = session.value?.let(transform)?.also(::persist)
    }

    private fun toState(current: LearningSession?, words: List<WordWithProgress>, flags: CardFlags): LearnUiState {
        if (current == null) return LearnUiState.Loading
        val ordered = orderOf(current, words)
        if (ordered.isEmpty()) return LearnUiState.Empty
        if (current.isFinished) {
            return LearnUiState.Finished(
                completedCount = current.completedCount,
                total = current.total,
            )
        }
        val position = current.currentPosition.coerceIn(0, ordered.lastIndex)
        return LearnUiState.Content(
            session = current.copy(currentPosition = position),
            items = ordered,
            current = ordered[position],
            revealed = flags.revealed,
            isSubmitting = flags.isSubmitting,
            submitFailed = flags.submitFailed,
        )
    }

    private fun orderOf(session: LearningSession, words: List<WordWithProgress>): List<WordWithProgress> {
        val byId = words.associateBy { it.word.id }
        return session.wordIds.mapNotNull(byId::get)
    }

    // Session state is mirrored into SavedStateHandle so it survives configuration changes and process death.
    private fun persist(value: LearningSession) {
        savedStateHandle[KEY_SESSION_ID] = value.sessionId
        savedStateHandle[KEY_WORD_IDS] = ArrayList(value.wordIds)
        savedStateHandle[KEY_STARTED_AT] = value.startedAt
        savedStateHandle[KEY_POSITION] = value.currentPosition
        savedStateHandle[KEY_COMPLETED] = ArrayList(value.completedWordIds.toList())
        value.completedAt?.let { savedStateHandle[KEY_COMPLETED_AT] = it }
    }

    private fun restoreSession(): LearningSession? {
        val wordIds = savedStateHandle.get<ArrayList<String>>(KEY_WORD_IDS)?.takeIf { it.isNotEmpty() } ?: return null
        return LearningSession(
            sessionId = savedStateHandle[KEY_SESSION_ID] ?: return null,
            wordIds = wordIds,
            currentPosition = savedStateHandle[KEY_POSITION] ?: 0,
            startedAt = savedStateHandle[KEY_STARTED_AT] ?: 0L,
            completedAt = savedStateHandle[KEY_COMPLETED_AT],
            completedWordIds = savedStateHandle.get<ArrayList<String>>(KEY_COMPLETED)?.toSet() ?: emptySet(),
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val MAX_CARD_DURATION_MILLIS = 120_000L
        const val KEY_SESSION_ID = "session_id"
        const val KEY_WORD_IDS = "session_word_ids"
        const val KEY_STARTED_AT = "session_started_at"
        const val KEY_POSITION = "session_position"
        const val KEY_COMPLETED = "session_completed"
        const val KEY_COMPLETED_AT = "session_completed_at"
    }
}

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
import kotlin.random.Random
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
        val variants: List<String>,
        val answerMode: StudyMode?,
        val typed: String,
        val typeMismatch: Boolean,
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
}

/**
 * How the user verifies the card: the Russian translation is always face up, so the answer to produce or
 * check is the English word. See shows it, Type asks for it (with a letter hint), Options offers choices.
 */
enum class StudyMode { TYPE, SEE, OPTIONS }

sealed interface LearnUiEvent {
    /** The swipe (or the matching half of the answer bar): true when the word was recognised. */
    data class Answer(val remembered: Boolean) : LearnUiEvent

    /** Chooses the verification panel shown under the card; tapping it again folds it away. */
    data class SelectMode(val mode: StudyMode) : LearnUiEvent

    /** The typed answer changes with every keystroke. */
    data class TypeText(val text: String) : LearnUiEvent

    /** Checks the typed answer against the word: a match answers the card as remembered. */
    data object TypeCheck : LearnUiEvent

    /** Reveals the next letter of the English word; the first letter when the field is empty. */
    data object TypeHint : LearnUiEvent

    /** One of the offered options was picked. */
    data class PickVariant(val text: String) : LearnUiEvent

    /** Shows the English word, pronunciation and examples before answering. */
    data object Reveal : LearnUiEvent

    data object ToggleSaved : LearnUiEvent
}

/** Transient state of the single card on screen, kept out of [LearnUiState] to keep it a plain snapshot. */
private data class CardFlags(
    val answerMode: StudyMode? = null,
    val typed: String = "",
    val typeMismatch: Boolean = false,
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

    /**
     * Only the words the session actually uses. Observing the whole vocabulary meant every recorded view
     * (once per card shown) re-hydrated and re-mapped every imported word, for a queue that never holds
     * more than a day's worth of cards. The ids only change when a new session starts, so answering a card
     * does not re-run the query at all.
     */
    private val words = session
        .map { it?.wordIds.orEmpty() }
        .distinctUntilChanged()
        .flatMapLatest { vocabularyRepository.observeWordsByIds(it) }

    /**
     * The queue is a batch of [settings.dailyGoal] cards; finishing one chains straight into the
     * next batch, so the loop runs with no end screen until the vocabulary itself runs out.
     */
    val uiState: StateFlow<LearnUiState> = combine(session, words, flags) { current, list, cardFlags ->
        toState(current, list, cardFlags)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), LearnUiState.Loading)

    init {
        viewModelScope.launch {
            val restored = session.value
            if (restored == null || restored.isFinished) startNewSession()
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
            is LearnUiEvent.SelectMode -> {
                val current = flags.value
                flags.value = when {
                    // The eye folds the panel away without un-revealing what the user already saw.
                    event.mode == StudyMode.SEE && current.answerMode == StudyMode.SEE ->
                        current.copy(answerMode = null)
                    event.mode == StudyMode.SEE -> current.copy(answerMode = StudyMode.SEE, revealed = true)
                    event.mode == current.answerMode -> current.copy(answerMode = null, typeMismatch = false)
                    else -> current.copy(answerMode = event.mode, typeMismatch = false)
                }
            }
            is LearnUiEvent.TypeText -> flags.value = flags.value.copy(typed = event.text, typeMismatch = false)
            LearnUiEvent.TypeCheck -> typeCheck()
            LearnUiEvent.TypeHint -> hintNextLetter()
            is LearnUiEvent.PickVariant -> if (isCorrectAnswer(event.text)) {
                answer(remembered = true)
            } else {
                flags.value = flags.value.copy(typeMismatch = true)
            }
            LearnUiEvent.Reveal -> flags.value = flags.value.copy(revealed = true)
            LearnUiEvent.ToggleSaved -> toggleSaved()
        }
    }

    /** Typing the English word right is the same as answering "remembered" by hand. */
    private fun typeCheck() {
        if (isCorrectAnswer(flags.value.typed)) {
            answer(remembered = true)
        } else {
            flags.value = flags.value.copy(typeMismatch = true)
        }
    }

    private fun isCorrectAnswer(text: String): Boolean {
        val guess = normalizeAnswer(text)
        if (guess.isEmpty()) return false
        val word = (uiState.value as? LearnUiState.Content)?.current?.word?.text.orEmpty()
        return normalizeAnswer(word) == guess
    }

    /**
     * The comparison is deliberately lenient: case and runs of whitespace do not matter, but a different
     * word stays wrong so typing is worth the same credit as a remembered card.
     */
    private fun normalizeAnswer(value: String): String = value.trim().lowercase().replace(WHITESPACE_REGEX, " ")

    private fun hintNextLetter() {
        val state = uiState.value as? LearnUiState.Content ?: return
        // The hint always builds the English word itself, because that is what the user must recall.
        val target = state.current.word.text
        val typed = flags.value.typed
        val next = when {
            typed.length >= target.length -> return
            target.regionMatches(0, typed, 0, typed.length, ignoreCase = true) -> target.take(typed.length + 1)
            else -> return
        }
        flags.value = flags.value.copy(typed = next, typeMismatch = false)
    }

    /**
     * Four English word options for the card, stable for a given card: three distractors from the same
     * batch plus the real word, shuffled with a seed derived from the word id.
     */
    private fun variantsFor(current: WordWithProgress, pool: List<WordWithProgress>, position: Int): List<String> {
        val correct = current.word.text.trim()
        val wrong = pool.asSequence()
            .map { it.word.text.trim() }
            .filter { it.isNotEmpty() && normalizeAnswer(it) != normalizeAnswer(correct) }
            .distinct()
            .toList()
        if (wrong.isEmpty()) return listOf(correct)
        val random = Random(current.word.id.hashCode() + position)
        val picks = wrong.shuffled(random).take(VARIANT_COUNT - 1)
        return (picks + correct).shuffled(random)
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

    /**
     * Finishes the card and in one breath continues the loop: if this was the last card of the batch,
     * the next one starts without an end screen — the user simply never runs out of cards.
     */
    private fun complete(wordId: String) {
        val current = session.value ?: return
        val completed = current.completedWordIds + wordId
        if (completed.size >= current.total) {
            session.value = null
            flags.value = CardFlags()
            viewModelScope.launch { startNewSession() }
            return
        }
        session.value = current.copy(
            completedWordIds = completed,
            completedAt = null,
            currentPosition = current.currentPosition + 1,
        ).also(::persist)
        flags.value = CardFlags()
    }

    private suspend fun startNewSession() {
        val settings = settingsRepository.settings.first()
        session.value = startLearningSession(settings.learningLanguage, settings.dailyGoal)
            .also(::persist)
    }

    private fun toState(current: LearningSession?, words: List<WordWithProgress>, flags: CardFlags): LearnUiState {
        if (current == null) return LearnUiState.Loading
        val ordered = orderOf(current, words)
        if (ordered.isEmpty()) return LearnUiState.Empty
        val position = current.currentPosition.coerceIn(0, ordered.lastIndex)
        return LearnUiState.Content(
            session = current.copy(currentPosition = position),
            items = ordered,
            current = ordered[position],
            variants = variantsFor(ordered[position], ordered, position),
            answerMode = flags.answerMode,
            typed = flags.typed,
            typeMismatch = flags.typeMismatch,
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
        const val VARIANT_COUNT = 4
        val WHITESPACE_REGEX = Regex("\\s+")
        const val KEY_SESSION_ID = "session_id"
        const val KEY_WORD_IDS = "session_word_ids"
        const val KEY_STARTED_AT = "session_started_at"
        const val KEY_POSITION = "session_position"
        const val KEY_COMPLETED = "session_completed"
        const val KEY_COMPLETED_AT = "session_completed_at"
    }
}

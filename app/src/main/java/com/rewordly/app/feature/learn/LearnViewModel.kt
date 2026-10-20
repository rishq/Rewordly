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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
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
        val pickedVariant: String?,
        val correctPick: Boolean,
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
    val pickedVariant: String? = null,
    val correctPick: Boolean = false,
    val revealed: Boolean = false,
    val isSubmitting: Boolean = false,
    val submitFailed: Boolean = false,
)

/** The words of a batch together with the ids they were loaded for. */
private data class WordSet(
    val ids: List<String>,
    val words: List<WordWithProgress>,
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

    /** The vocabulary as of the last card, so the next batch can be built without reading it again. */
    private var preparedVocabulary: List<WordWithProgress>? = null
    private var prepareJob: Job? = null

    /**
     * Only the words the session actually uses. Observing the whole vocabulary meant every recorded view
     * (once per card shown) re-hydrated and re-mapped every imported word, for a queue that never holds
     * more than a day's worth of cards. The ids only change when a new session starts, so answering a card
     * does not re-run the query at all.
     *
     * The ids travel with the words: when a new batch starts, the words on hand still belong to the old
     * one for a moment, and the pair is what tells the two apart.
     */
    private val words = session
        .map { it?.wordIds.orEmpty() }
        .distinctUntilChanged()
        .flatMapLatest { ids -> vocabularyRepository.observeWordsByIds(ids).map { WordSet(ids, it) } }

    /**
     * The queue is a batch of [settings.dailyGoal] cards; finishing one chains straight into the
     * next batch, so the loop runs with no end screen until the vocabulary itself runs out.
     */
    val uiState: StateFlow<LearnUiState> = combine(session, words, flags) { current, wordSet, cardFlags ->
        Triple(current, wordSet, cardFlags)
    }.scan<Triple<LearningSession?, WordSet, CardFlags>, LearnUiState>(
        LearnUiState.Loading,
    ) { previous, (current, wordSet, cardFlags) ->
        toState(current, wordSet, cardFlags, previous)
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
        // The batch after this one is built while the user is still on the last card, so the cards
        // continue without a pause and without a loading screen in between.
        viewModelScope.launch {
            uiState
                .map { it as? LearnUiState.Content }
                .distinctUntilChangedBy { it?.let { content -> content.index to content.total } }
                .collect { content ->
                    if (content != null && content.total > 0 && content.index >= content.total - 1) {
                        prepareNextBatch()
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
            is LearnUiEvent.PickVariant -> pickVariant(event.text)
            LearnUiEvent.Reveal -> flags.value = flags.value.copy(revealed = true)
            LearnUiEvent.ToggleSaved -> toggleSaved()
        }
    }

    /**
     * An option is never answered silently: the picked button colours itself first — green when it is
     * right, red when it is not — and only a correct pick advances, after the flash has been seen.
     */
    private fun pickVariant(text: String) {
        val current = flags.value
        if (current.isSubmitting || current.correctPick) return
        if (isCorrectAnswer(text)) {
            val wordId = (uiState.value as? LearnUiState.Content)?.current?.word?.id
            flags.value = current.copy(pickedVariant = text, correctPick = true, typeMismatch = false)
            viewModelScope.launch {
                delay(VARIANT_FLASH_MILLIS)
                // Let the green fade back out before the card changes, so the two motions never overlap.
                if (flags.value.correctPick) flags.value = flags.value.copy(pickedVariant = null)
                delay(VARIANT_SETTLE_MILLIS)
                // Only this card may be answered: a swipe in the meantime may already have moved on.
                val state = uiState.value as? LearnUiState.Content
                if (state?.current?.word?.id == wordId && flags.value.correctPick) {
                    answer(remembered = true)
                }
            }
        } else {
            flags.value = current.copy(pickedVariant = text, typeMismatch = true)
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

    /**
     * The hint always builds the English word itself, because that is what the user must recall.
     *
     * The button never dead-ends: text that already is the beginning of the word grows by one letter,
     * text whose letters are in the word but in the wrong places gets the missing letter back where it
     * belongs, and text that shares nothing with the word is wiped - a wrong start is better cleared
     * than left to rot.
     */
    private fun hintNextLetter() {
        val state = uiState.value as? LearnUiState.Content ?: return
        val target = state.current.word.text
        val typed = flags.value.typed
        val next = when {
            typed.isEmpty() -> target.take(1)
            target.startsWith(typed, ignoreCase = true) -> target.take(typed.length + 1)
            else -> fillMissingLetters(target, typed) ?: target.take(1)
        }
        flags.value = flags.value.copy(typed = next, typeMismatch = false)
    }

    /**
     * The typed letters in the order the word has them, with the ones the user skipped written back in.
     * Null when the typed text shares nothing with the word.
     */
    private fun fillMissingLetters(target: String, typed: String): String? {
        var index = 0
        var last = -1
        for (char in typed) {
            val found = target.indexOf(char, index, ignoreCase = true)
            if (found < 0) return null
            last = found
            index = found + 1
        }
        return if (last < 0) null else target.take(last + 1)
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
                // The card stays and can be answered again; any half-played pick flash is dropped with it.
                flags.value = flags.value.copy(
                    isSubmitting = false,
                    submitFailed = true,
                    pickedVariant = null,
                    correctPick = false,
                )
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
    private suspend fun complete(wordId: String) {
        val current = session.value ?: return
        val completed = current.completedWordIds + wordId
        if (completed.size >= current.total) {
            val prepared = preparedVocabulary
            preparedVocabulary = null
            prepareJob?.cancel()
            if (prepared != null) {
                // The vocabulary for the next batch is already in hand, so the cards continue without
                // a pause and without a loading screen in between.
                val next = buildNextSession(current, prepared)
                session.value = next.also(::persist)
                flags.value = CardFlags()
            } else {
                // Nothing was prepared: the card stays on screen until the next batch is ready, so the
                // app never shows a loading screen in the middle of a run.
                flags.value = CardFlags().copy(isSubmitting = true)
                viewModelScope.launch {
                    val next = buildSession()
                    session.value = next.also(::persist)
                    flags.value = CardFlags()
                }
            }
            return
        }
        session.value = current.copy(
            completedWordIds = completed,
            completedAt = null,
            currentPosition = current.currentPosition + 1,
        ).also(::persist)
        flags.value = CardFlags()
    }

    /**
     * Builds the batch that comes after the current one.
     *
     * Reading the whole vocabulary is the slow part of it, and doing that while the app shows a
     * loading screen is what used to make the cards stop every ten words. The vocabulary is therefore
     * read while the user is still on the last card, in the background.
     */
    private fun prepareNextBatch() {
        if (prepareJob?.isActive == true) return
        prepareJob = viewModelScope.launch {
            val language = settingsRepository.settings.first().learningLanguage
            // The repository maps the whole vocabulary off the main thread, so the card on screen does
            // not stutter while the next batch is being prepared.
            val vocabulary = vocabularyRepository.observeWords(language).first()
            // A cancelled preparation must not leave a stale vocabulary behind.
            ensureActive()
            preparedVocabulary = vocabulary
        }
    }

    /**
     * The next batch, built from the vocabulary that was read while the user was on the last card.
     *
     * The words of the batch that just ended are read once more: the last answer may have graduated
     * one of them, and a word the user has already mastered must not come back in the next batch.
     */
    private suspend fun buildNextSession(
        current: LearningSession,
        vocabulary: List<WordWithProgress>,
    ): LearningSession {
        val settings = settingsRepository.settings.first()
        val answered = vocabularyRepository.observeWordsByIds(current.wordIds).first().associateBy { it.word.id }
        val merged = vocabulary.map { word -> answered[word.word.id] ?: word }
        return startLearningSession.build(merged, settings.dailyGoal)
    }

    private suspend fun buildSession(): LearningSession {
        val settings = settingsRepository.settings.first()
        return startLearningSession(settings.learningLanguage, settings.dailyGoal)
    }

    private suspend fun startNewSession() {
        session.value = buildSession().also(::persist)
    }

    private fun toState(
        current: LearningSession?,
        wordSet: WordSet,
        flags: CardFlags,
        previous: LearnUiState,
    ): LearnUiState {
        if (current == null) return LearnUiState.Loading
        if (wordSet.ids != current.wordIds) {
            // The words on hand still belong to the previous batch: the card that is on screen stays,
            // so a finished batch does not flash an empty screen while the new words are on their way.
            return (previous as? LearnUiState.Content) ?: LearnUiState.Loading
        }
        val ordered = orderOf(current, wordSet.words)
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
            pickedVariant = flags.pickedVariant,
            correctPick = flags.correctPick,
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
        const val VARIANT_FLASH_MILLIS = 450L
        const val VARIANT_SETTLE_MILLIS = 160L
        val WHITESPACE_REGEX = Regex("\\s+")
        const val KEY_SESSION_ID = "session_id"
        const val KEY_WORD_IDS = "session_word_ids"
        const val KEY_STARTED_AT = "session_started_at"
        const val KEY_POSITION = "session_position"
        const val KEY_COMPLETED = "session_completed"
        const val KEY_COMPLETED_AT = "session_completed_at"
    }
}

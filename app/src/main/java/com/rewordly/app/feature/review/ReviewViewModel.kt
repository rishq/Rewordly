package com.rewordly.app.feature.review

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rewordly.app.core.common.AppError
import com.rewordly.app.core.common.AppResult
import com.rewordly.app.core.common.TimeProvider
import com.rewordly.app.domain.model.ReviewRating
import com.rewordly.app.domain.model.ReviewSubmitResult
import com.rewordly.app.domain.model.WordWithProgress
import com.rewordly.app.domain.repository.SettingsRepository
import com.rewordly.app.domain.repository.VocabularyRepository
import com.rewordly.app.domain.usecase.BuildReviewQueueUseCase
import com.rewordly.app.domain.usecase.SubmitReviewAnswerUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Shown briefly after an answer: which button was pressed and when the word comes back. */
data class ReviewFeedback(val rating: ReviewRating, val intervalDays: Int)

data class ReviewSummary(
    val total: Int,
    val answers: Map<ReviewRating, Int>,
    /** Words that are still due after this batch, so the user can continue with the next one. */
    val remainingDue: Int,
) {
    val remembered: Int get() = total - (answers[ReviewRating.AGAIN] ?: 0)
}

sealed interface ReviewUiState {
    data object Loading : ReviewUiState

    /** Nothing is due: the friendly "all done" state. */
    data object Empty : ReviewUiState

    data class Error(val error: AppError) : ReviewUiState

    data class InProgress(
        val current: WordWithProgress,
        val index: Int,
        val total: Int,
        val dueToday: Int,
        val revealed: Boolean,
        val isSubmitting: Boolean,
        /** Days until the next review for each button, so the choice is informed. */
        val intervals: Map<ReviewRating, Int>,
        val feedback: ReviewFeedback?,
        val submitFailed: Boolean,
    ) : ReviewUiState {
        val fraction: Float get() = if (total == 0) 0f else index.toFloat() / total
    }

    data class Finished(val summary: ReviewSummary) : ReviewUiState
}

sealed interface ReviewUiEvent {
    data object Reveal : ReviewUiEvent

    data class Answer(val rating: ReviewRating) : ReviewUiEvent

    /** Starts a new session: used after finishing a batch and for retrying after an error. */
    data object Restart : ReviewUiEvent

    /** Sent when the screen comes back: rebuilds the queue if the previous result is stale. */
    data object Refresh : ReviewUiEvent
}

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val settingsRepository: SettingsRepository,
    private val vocabularyRepository: VocabularyRepository,
    private val buildReviewQueue: BuildReviewQueueUseCase,
    private val submitReviewAnswer: SubmitReviewAnswerUseCase,
    private val timeProvider: TimeProvider,
) : ViewModel() {
    private val _uiState = MutableStateFlow<ReviewUiState>(ReviewUiState.Loading)
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    private var sessionId: String = ""
    private var words: List<WordWithProgress> = emptyList()
    private var totalDue: Int = 0
    private val answers = LinkedHashMap<String, ReviewRating>()
    private var shownAt: Long = 0L
    private var lastFeedback: ReviewFeedback? = null
    private var finishedOn: java.time.LocalDate? = null

    init {
        viewModelScope.launch { restoreOrStart() }
    }

    fun onEvent(event: ReviewUiEvent) {
        when (event) {
            ReviewUiEvent.Reveal -> reveal()
            is ReviewUiEvent.Answer -> answer(event.rating)
            ReviewUiEvent.Restart -> viewModelScope.launch { start() }
            ReviewUiEvent.Refresh -> refresh()
        }
    }

    private fun refresh() {
        val stale = when (_uiState.value) {
            ReviewUiState.Empty, is ReviewUiState.Error -> true
            is ReviewUiState.Finished -> finishedOn != timeProvider.today()
            else -> false
        }
        if (stale) viewModelScope.launch { start() }
    }

    private fun reveal() {
        val state = _uiState.value as? ReviewUiState.InProgress ?: return
        if (!state.revealed) _uiState.value = state.copy(revealed = true)
    }

    private fun answer(rating: ReviewRating) {
        val state = _uiState.value as? ReviewUiState.InProgress ?: return
        // Answering is allowed straight away (a swipe is a decision on its own); while one submission is in
        // flight further taps are dropped.
        if (state.isSubmitting) return
        _uiState.value = state.copy(isSubmitting = true, submitFailed = false)
        val wordId = state.current.word.id
        val duration = (timeProvider.nowMillis() - shownAt).coerceIn(0, MAX_CARD_DURATION_MILLIS)
        viewModelScope.launch {
            when (val result = submitReviewAnswer(wordId, sessionId, rating, duration)) {
                is AppResult.Success -> {
                    val days = (result.data as? ReviewSubmitResult.Applied)?.newState?.intervalDays
                        ?: state.intervals[rating] ?: 0
                    lastFeedback = ReviewFeedback(rating, days)
                    answers[wordId] = rating
                    persist()
                    render()
                }
                is AppResult.Failure -> _uiState.value = state.copy(isSubmitting = false, submitFailed = true)
            }
        }
    }

    private suspend fun restoreOrStart() {
        val ids = savedStateHandle.get<ArrayList<String>>(KEY_WORD_IDS)
        val savedSession = savedStateHandle.get<String>(KEY_SESSION_ID)
        if (ids.isNullOrEmpty() || savedSession == null) {
            start()
            return
        }
        val language = settingsRepository.settings.first().learningLanguage
        val loaded = try {
            vocabularyRepository.observeWords(language).first().associateBy { it.word.id }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            _uiState.value = ReviewUiState.Error(AppError.Database(e))
            return
        }
        words = ids.mapNotNull(loaded::get)
        if (words.isEmpty()) {
            start()
            return
        }
        sessionId = savedSession
        totalDue = savedStateHandle[KEY_TOTAL_DUE] ?: words.size
        answers.clear()
        savedStateHandle.get<ArrayList<String>>(KEY_ANSWERS).orEmpty().forEach { entry ->
            val (id, ordinal) = entry.split(SEPARATOR).let { it[0] to it.getOrNull(1)?.toIntOrNull() }
            val rating = ordinal?.let { ReviewRating.entries.getOrNull(it) }
            if (rating != null && loaded.containsKey(id)) answers[id] = rating
        }
        lastFeedback = null
        render()
    }

    private suspend fun start() {
        _uiState.value = ReviewUiState.Loading
        val language = settingsRepository.settings.first().learningLanguage
        when (val result = buildReviewQueue(language)) {
            is AppResult.Failure -> _uiState.value = ReviewUiState.Error(result.error)
            is AppResult.Success -> {
                words = result.data.items
                totalDue = result.data.totalDue
                answers.clear()
                lastFeedback = null
                sessionId = "review-${timeProvider.nowMillis()}"
                persist()
                render()
            }
        }
    }

    private fun render() {
        if (words.isEmpty()) {
            _uiState.value = ReviewUiState.Empty
            return
        }
        val position = answers.size
        if (position >= words.size) {
            finishedOn = timeProvider.today()
            val counts = ReviewRating.entries.associateWith { rating -> answers.values.count { it == rating } }
            _uiState.value = ReviewUiState.Finished(
                ReviewSummary(
                    total = words.size,
                    answers = counts,
                    remainingDue = (totalDue - words.size).coerceAtLeast(0),
                ),
            )
            return
        }
        val current = words[position]
        shownAt = timeProvider.nowMillis()
        _uiState.value = ReviewUiState.InProgress(
            current = current,
            index = position,
            total = words.size,
            dueToday = totalDue,
            revealed = false,
            isSubmitting = false,
            intervals = submitReviewAnswer.previewIntervals(current.progress),
            feedback = lastFeedback,
            submitFailed = false,
        )
    }

    // Mirrored into SavedStateHandle so an interrupted session resumes where it stopped after a restart.
    private fun persist() {
        savedStateHandle[KEY_SESSION_ID] = sessionId
        savedStateHandle[KEY_WORD_IDS] = ArrayList(words.map { it.word.id })
        savedStateHandle[KEY_TOTAL_DUE] = totalDue
        savedStateHandle[KEY_ANSWERS] = ArrayList(answers.map { (id, rating) -> "$id$SEPARATOR${rating.ordinal}" })
    }

    private companion object {
        const val MAX_CARD_DURATION_MILLIS = 120_000L
        const val SEPARATOR = "|"
        const val KEY_SESSION_ID = "review_session_id"
        const val KEY_WORD_IDS = "review_word_ids"
        const val KEY_TOTAL_DUE = "review_total_due"
        const val KEY_ANSWERS = "review_answers"
    }
}

package com.rewordly.app.feature.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rewordly.app.domain.model.WordWithProgress
import com.rewordly.app.domain.repository.SettingsRepository
import com.rewordly.app.domain.usecase.BuildReviewQueueUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ReviewUiState {
    data object Loading : ReviewUiState

    data object Empty : ReviewUiState

    data class InProgress(
        val current: WordWithProgress,
        val index: Int,
        val total: Int,
        val revealed: Boolean,
        val known: Int,
        val unknown: Int,
    ) : ReviewUiState

    data class Finished(val known: Int, val unknown: Int) : ReviewUiState
}

sealed interface ReviewUiEvent {
    data object Reveal : ReviewUiEvent

    data object Known : ReviewUiEvent

    data object Unknown : ReviewUiEvent

    data object Restart : ReviewUiEvent
}

/** Placeholder review: answers only update in-memory state (no spaced repetition in STEP 1). */
@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val buildReviewQueue: BuildReviewQueueUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow<ReviewUiState>(ReviewUiState.Loading)
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    private var queue: List<WordWithProgress> = emptyList()

    init {
        start()
    }

    fun onEvent(event: ReviewUiEvent) {
        when (event) {
            ReviewUiEvent.Reveal -> _uiState.update { state ->
                if (state is ReviewUiState.InProgress) state.copy(revealed = true) else state
            }
            ReviewUiEvent.Known -> answer(known = true)
            ReviewUiEvent.Unknown -> answer(known = false)
            ReviewUiEvent.Restart -> start()
        }
    }

    private fun start() {
        _uiState.value = ReviewUiState.Loading
        viewModelScope.launch {
            val language = settingsRepository.settings.first().learningLanguage
            queue = buildReviewQueue(language)
            _uiState.value = if (queue.isEmpty()) ReviewUiState.Empty else stateAt(0, known = 0, unknown = 0)
        }
    }

    private fun answer(known: Boolean) {
        val state = _uiState.value as? ReviewUiState.InProgress ?: return
        val knownCount = state.known + if (known) 1 else 0
        val unknownCount = state.unknown + if (known) 0 else 1
        val next = state.index + 1
        _uiState.value = if (next < queue.size) {
            stateAt(next, knownCount, unknownCount)
        } else {
            ReviewUiState.Finished(knownCount, unknownCount)
        }
    }

    private fun stateAt(index: Int, known: Int, unknown: Int) = ReviewUiState.InProgress(
        current = queue[index],
        index = index,
        total = queue.size,
        revealed = false,
        known = known,
        unknown = unknown,
    )
}

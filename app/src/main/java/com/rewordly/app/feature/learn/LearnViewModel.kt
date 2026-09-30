package com.rewordly.app.feature.learn

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rewordly.app.domain.model.WordStatus
import com.rewordly.app.domain.model.WordWithProgress
import com.rewordly.app.domain.repository.SettingsRepository
import com.rewordly.app.domain.repository.VocabularyRepository
import com.rewordly.app.domain.usecase.StartLearningSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface LearnUiState {
    data object Loading : LearnUiState

    data object Empty : LearnUiState

    data class Content(
        val current: WordWithProgress,
        val index: Int,
        val total: Int,
    ) : LearnUiState {
        val canGoBack: Boolean get() = index > 0
        val canGoForward: Boolean get() = index < total - 1
        val isLearned: Boolean get() = current.progress.status == WordStatus.LEARNED
    }
}

sealed interface LearnUiEvent {
    data object Next : LearnUiEvent

    data object Previous : LearnUiEvent

    data object ToggleSaved : LearnUiEvent

    data object ToggleLearned : LearnUiEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LearnViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    private val vocabularyRepository: VocabularyRepository,
    startLearningSession: StartLearningSessionUseCase,
) : ViewModel() {
    private val index = MutableStateFlow(0)

    /** Session order is fixed on start; live progress (saved/learned) is merged in from Room. */
    val uiState: StateFlow<LearnUiState> = flow {
        val language = settingsRepository.settings.first().learningLanguage
        val order = startLearningSession(language).words.map { it.word.id }
        emit(language to order)
    }.flatMapLatest { (language, order) ->
        combine(vocabularyRepository.observeWords(language), index) { words, i ->
            val byId = words.associateBy { it.word.id }
            val session = order.mapNotNull(byId::get)
            if (session.isEmpty()) {
                LearnUiState.Empty
            } else {
                val safeIndex = i.coerceIn(0, session.lastIndex)
                LearnUiState.Content(current = session[safeIndex], index = safeIndex, total = session.size)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), LearnUiState.Loading)

    fun onEvent(event: LearnUiEvent) {
        val state = uiState.value as? LearnUiState.Content ?: return
        when (event) {
            LearnUiEvent.Next -> if (state.canGoForward) index.value = state.index + 1
            LearnUiEvent.Previous -> if (state.canGoBack) index.value = state.index - 1
            LearnUiEvent.ToggleSaved -> viewModelScope.launch {
                vocabularyRepository.setSaved(state.current.word.id, !state.current.progress.isSaved)
            }
            LearnUiEvent.ToggleLearned -> viewModelScope.launch {
                val newStatus = if (state.isLearned) WordStatus.LEARNING else WordStatus.LEARNED
                vocabularyRepository.setStatus(state.current.word.id, newStatus)
                if (newStatus == WordStatus.LEARNED && state.canGoForward) index.value = state.index + 1
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

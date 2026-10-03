package com.rewordly.app.feature.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rewordly.app.core.common.AppError
import com.rewordly.app.domain.model.AiSettings
import com.rewordly.app.domain.model.GenerationHistoryEntry
import com.rewordly.app.domain.model.UsageInfo
import com.rewordly.app.domain.repository.AiSettingsRepository
import com.rewordly.app.domain.repository.GenerationHistoryRepository
import com.rewordly.app.domain.repository.VocabularyGenerationRepository
import com.rewordly.app.domain.service.GenerationSessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Landing screen of the AI feature; only shows usage the backend has reported. */
@HiltViewModel
class AiHomeViewModel @Inject constructor(
    generationRepository: VocabularyGenerationRepository,
    aiSettingsRepository: AiSettingsRepository,
) : ViewModel() {
    val usage: StateFlow<UsageInfo?> = generationRepository.usage

    /** Drives the provider notice: the user's own provider, or the project's backend. */
    val ai: StateFlow<AiSettings> = aiSettingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AiSettings())
}

sealed interface AiHistoryUiState {
    data object Loading : AiHistoryUiState

    data object Empty : AiHistoryUiState

    data class Content(val entries: List<GenerationHistoryEntry>) : AiHistoryUiState

    data class Error(val error: AppError) : AiHistoryUiState
}

@HiltViewModel
class AiHistoryViewModel @Inject constructor(
    private val historyRepository: GenerationHistoryRepository,
    private val sessionStore: GenerationSessionStore,
) : ViewModel() {
    val uiState: StateFlow<AiHistoryUiState> = historyRepository.observeHistory()
        .map { entries -> if (entries.isEmpty()) AiHistoryUiState.Empty else AiHistoryUiState.Content(entries) }
        .map<AiHistoryUiState, AiHistoryUiState> { it }
        .catch { emit(AiHistoryUiState.Error(AppError.Database(it))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AiHistoryUiState.Loading)

    private val _confirmClear = MutableStateFlow(false)
    val confirmClear: StateFlow<Boolean> = _confirmClear

    fun delete(id: String) {
        sessionStore.remove(id)
        viewModelScope.launch { historyRepository.delete(id) }
    }

    fun requestClear() {
        _confirmClear.value = true
    }

    fun dismissClear() {
        _confirmClear.value = false
    }

    /** Wipes stored results and the in-memory requests, which may still hold pasted text. */
    fun confirmClear() {
        _confirmClear.value = false
        sessionStore.clear()
        viewModelScope.launch { historyRepository.clear() }
    }
}

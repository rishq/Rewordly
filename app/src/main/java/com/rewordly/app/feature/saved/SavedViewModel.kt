package com.rewordly.app.feature.saved

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rewordly.app.domain.model.WordWithProgress
import com.rewordly.app.domain.repository.SettingsRepository
import com.rewordly.app.domain.repository.VocabularyRepository
import com.rewordly.app.domain.usecase.SearchWordsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface SavedUiState {
    data object Loading : SavedUiState

    data object NoSavedWords : SavedUiState

    data object NoMatches : SavedUiState

    data class Content(val query: String, val words: List<WordWithProgress>) : SavedUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SavedViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    vocabularyRepository: VocabularyRepository,
) : ViewModel() {
    private val query = MutableStateFlow("")

    private val saved = settingsRepository.settings
        .map { it.learningLanguage }
        .distinctUntilChanged()
        .flatMapLatest { vocabularyRepository.observeSavedWords(it) }

    val uiState: StateFlow<SavedUiState> = combine(query, saved) { text, words ->
        // A blank query shows the whole list, so only run the matcher when something was typed.
        val filtered = if (text.isBlank()) words else SearchWordsUseCase.match(words, text)
        when {
            words.isEmpty() -> SavedUiState.NoSavedWords
            text.isNotBlank() && filtered.isEmpty() -> SavedUiState.NoMatches
            else -> SavedUiState.Content(text, filtered)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SavedUiState.Loading)

    fun onQueryChange(value: String) {
        query.value = value
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

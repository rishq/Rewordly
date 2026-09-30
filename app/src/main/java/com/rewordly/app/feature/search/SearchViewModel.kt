package com.rewordly.app.feature.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.rewordly.app.core.navigation.SearchRoute
import com.rewordly.app.domain.model.WordWithProgress
import com.rewordly.app.domain.repository.SettingsRepository
import com.rewordly.app.domain.usecase.SearchWordsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SearchUiState {
    data object Loading : SearchUiState

    data class Idle(val recentSearches: List<String>) : SearchUiState

    data class Results(val words: List<WordWithProgress>) : SearchUiState

    data class NoResults(val query: String) : SearchUiState
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val settingsRepository: SettingsRepository,
    searchWords: SearchWordsUseCase,
) : ViewModel() {
    /** Held as Compose state so the text field stays in sync without async lag. */
    var query by mutableStateOf(savedStateHandle.toRoute<SearchRoute>().query)
        private set

    private val normalizedQuery = snapshotFlow { query }
        .map(SearchWordsUseCase::normalize)
        .distinctUntilChanged()
        .debounce { if (it.isEmpty()) 0L else DEBOUNCE_MILLIS }

    private val language = settingsRepository.settings.map { it.learningLanguage }.distinctUntilChanged()

    val uiState: StateFlow<SearchUiState> = combine(normalizedQuery, language, ::Pair)
        .flatMapLatest { (q, lang) ->
            if (q.isEmpty()) {
                settingsRepository.recentSearches.map { SearchUiState.Idle(it) }
            } else {
                searchWords(lang, q).map { words ->
                    if (words.isEmpty()) SearchUiState.NoResults(q) else SearchUiState.Results(words)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState.Loading)

    fun onQueryChange(value: String) {
        query = value
    }

    fun onSubmit() {
        saveRecent(query)
    }

    fun onRecentSelected(value: String) {
        query = value
    }

    fun onResultOpened() {
        saveRecent(query)
    }

    fun clearRecent() {
        viewModelScope.launch { settingsRepository.clearRecentSearches() }
    }

    private fun saveRecent(value: String) {
        val normalized = SearchWordsUseCase.normalize(value)
        if (normalized.isNotEmpty()) {
            viewModelScope.launch { settingsRepository.addRecentSearch(normalized) }
        }
    }

    private companion object {
        const val DEBOUNCE_MILLIS = 250L
    }
}

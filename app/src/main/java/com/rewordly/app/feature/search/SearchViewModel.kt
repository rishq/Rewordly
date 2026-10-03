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
import com.rewordly.app.domain.model.VocabularyFilters
import com.rewordly.app.domain.model.WordWithProgress
import com.rewordly.app.domain.repository.SettingsRepository
import com.rewordly.app.domain.repository.VocabularyRepository
import com.rewordly.app.domain.usecase.FilterVocabularyUseCase
import com.rewordly.app.domain.usecase.SearchWordsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val vocabularyRepository: VocabularyRepository,
    private val filterVocabulary: FilterVocabularyUseCase,
    private val searchWords: SearchWordsUseCase,
) : ViewModel() {
    /** Held as Compose state so the text field stays in sync without async lag. */
    var query by mutableStateOf(savedStateHandle.toRoute<SearchRoute>().query)
        private set

    private val filters = MutableStateFlow(VocabularyFilters())
    val activeFilters: StateFlow<VocabularyFilters> = filters

    private val normalizedQuery = snapshotFlow { query }
        .map(SearchWordsUseCase::normalize)
        .distinctUntilChanged()
        .debounce { if (it.isEmpty()) 0L else DEBOUNCE_MILLIS }

    private val language = settingsRepository.settings.map { it.learningLanguage }.distinctUntilChanged()

    val uiState: StateFlow<SearchUiState> = combine(normalizedQuery, language, filters) { q, lang, active ->
        Triple(q, lang, active)
    }.flatMapLatest { (q, lang, active) ->
        if (q.isEmpty() && !active.isActive) {
            settingsRepository.recentSearches.map { SearchUiState.Idle(it) }
        } else {
            val source = if (q.isEmpty()) {
                vocabularyRepository.observeWords(lang)
            } else {
                searchWords(lang, q)
            }
            source.map { words ->
                val filtered = filterVocabulary(words, active)
                if (filtered.isEmpty()) SearchUiState.NoResults(q) else SearchUiState.Results(filtered)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState.Loading)

    fun onQueryChange(value: String) {
        query = value
    }

    fun onFiltersChange(value: VocabularyFilters) {
        filters.value = value
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

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

sealed interface SearchUiState {
    data object Loading : SearchUiState

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
        // An empty query lists the whole vocabulary, the same way every other section does; a typed
        // query narrows it. This replaced the recent-searches idle state, which had left the default
        // "Все" screen without any word list at all.
        val source = if (q.isEmpty()) vocabularyRepository.observeWords(lang) else searchWords(lang, q)
        source.map { words ->
            val filtered = filterVocabulary(words, active)
            if (filtered.isEmpty()) SearchUiState.NoResults(q) else SearchUiState.Results(filtered)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState.Loading)

    fun onQueryChange(value: String) {
        query = value
    }

    fun onFiltersChange(value: VocabularyFilters) {
        filters.value = value
    }

    private companion object {
        const val DEBOUNCE_MILLIS = 250L
    }
}

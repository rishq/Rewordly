package com.rewordly.app.feature.word

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.rewordly.app.core.navigation.WordDetailsRoute
import com.rewordly.app.domain.model.WordWithProgress
import com.rewordly.app.domain.repository.VocabularyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface WordDetailsUiState {
    data object Loading : WordDetailsUiState

    data object NotFound : WordDetailsUiState

    data class Content(val item: WordWithProgress) : WordDetailsUiState
}

@HiltViewModel
class WordDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vocabularyRepository: VocabularyRepository,
) : ViewModel() {
    private val wordId = savedStateHandle.toRoute<WordDetailsRoute>().wordId

    val uiState: StateFlow<WordDetailsUiState> = vocabularyRepository.observeWord(wordId)
        .map { item -> item?.let(WordDetailsUiState::Content) ?: WordDetailsUiState.NotFound }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WordDetailsUiState.Loading)

    fun toggleSaved() {
        val state = uiState.value as? WordDetailsUiState.Content ?: return
        viewModelScope.launch { vocabularyRepository.setSaved(wordId, !state.item.progress.isSaved) }
    }
}

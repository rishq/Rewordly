package com.rewordly.app.feature.ai.preview

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rewordly.app.core.common.AppError
import com.rewordly.app.core.common.AppResult
import com.rewordly.app.domain.model.GenerationHistoryEntry
import com.rewordly.app.domain.model.PreviewItem
import com.rewordly.app.domain.repository.GenerationHistoryRepository
import com.rewordly.app.domain.usecase.DetectDuplicatesUseCase
import com.rewordly.app.domain.usecase.RegenerateVocabularyUseCase
import com.rewordly.app.domain.usecase.SaveGeneratedWordsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the preview is busy with. Only one operation runs at a time. */
sealed interface PreviewBusy {
    data object None : PreviewBusy

    data object Saving : PreviewBusy

    data object RegeneratingAll : PreviewBusy

    data class RegeneratingWord(val index: Int) : PreviewBusy
}

sealed interface PreviewMessage {
    data class Saved(val count: Int) : PreviewMessage

    data object NothingToSave : PreviewMessage
}

sealed interface AiPreviewUiState {
    data object Loading : AiPreviewUiState

    /** The generation was deleted, or its result was never stored. */
    data object NotFound : AiPreviewUiState

    data class Content(
        val entry: GenerationHistoryEntry,
        val items: List<PreviewItem>,
        val canRegenerate: Boolean,
        val busy: PreviewBusy = PreviewBusy.None,
        val message: PreviewMessage? = null,
        val error: AppError? = null,
        /** Item whose full card is open in the details sheet. */
        val detailIndex: Int? = null,
    ) : AiPreviewUiState {
        val selectedCount: Int get() = items.count { it.selected && !it.isDuplicate }
        val isBusy: Boolean get() = busy != PreviewBusy.None
        val canSave: Boolean get() = selectedCount > 0 && !isBusy
    }
}

sealed interface AiPreviewEvent {
    data class Toggle(val index: Int) : AiPreviewEvent

    data class SetAllSelected(val selected: Boolean) : AiPreviewEvent

    data class RegenerateWord(val index: Int) : AiPreviewEvent

    data object RegenerateAll : AiPreviewEvent

    data object Save : AiPreviewEvent

    data class OpenDetail(val index: Int) : AiPreviewEvent

    data object CloseDetail : AiPreviewEvent

    data object DismissMessage : AiPreviewEvent

    data object DismissError : AiPreviewEvent
}

@HiltViewModel
class AiPreviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val historyRepository: GenerationHistoryRepository,
    private val detectDuplicates: DetectDuplicatesUseCase,
    private val saveGeneratedWords: SaveGeneratedWordsUseCase,
    private val regenerate: RegenerateVocabularyUseCase,
) : ViewModel() {
    private val historyId = savedStateHandle.get<String>("historyId").orEmpty()

    private val _uiState = MutableStateFlow<AiPreviewUiState>(AiPreviewUiState.Loading)
    val uiState: StateFlow<AiPreviewUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    fun onEvent(event: AiPreviewEvent) {
        when (event) {
            is AiPreviewEvent.Toggle -> content { copy(items = items.toggled(event.index), message = null) }
            is AiPreviewEvent.SetAllSelected ->
                content { copy(items = items.map { it.copy(selected = event.selected && !it.isDuplicate) }) }
            is AiPreviewEvent.RegenerateWord -> regenerateWord(event.index)
            AiPreviewEvent.RegenerateAll -> regenerateAll()
            AiPreviewEvent.Save -> save()
            is AiPreviewEvent.OpenDetail -> content { copy(detailIndex = event.index) }
            AiPreviewEvent.CloseDetail -> content { copy(detailIndex = null) }
            AiPreviewEvent.DismissMessage -> content { copy(message = null) }
            AiPreviewEvent.DismissError -> content { copy(error = null) }
        }
    }

    private suspend fun load() {
        val entry = (historyRepository.getEntry(historyId) as? AppResult.Success)?.data
        val words = (historyRepository.getItems(historyId) as? AppResult.Success)?.data.orEmpty()
        if (entry == null || words.isEmpty()) {
            _uiState.value = AiPreviewUiState.NotFound
            return
        }
        when (val marked = detectDuplicates(words)) {
            is AppResult.Failure -> _uiState.value = AiPreviewUiState.NotFound
            is AppResult.Success -> _uiState.value = AiPreviewUiState.Content(
                entry = entry,
                items = marked.data,
                canRegenerate = regenerate.canRegenerate(historyId),
            )
        }
    }

    private fun regenerateWord(index: Int) {
        val state = current() ?: return
        if (state.isBusy || index !in state.items.indices) return
        content { copy(busy = PreviewBusy.RegeneratingWord(index), error = null, message = null) }
        viewModelScope.launch {
            when (val result = regenerate.word(historyId, state.items.map { it.word }, index)) {
                is AppResult.Success -> content {
                    copy(items = result.data.keepingSelectionFrom(items), busy = PreviewBusy.None, detailIndex = null)
                }
                is AppResult.Failure -> content { copy(busy = PreviewBusy.None, error = result.error) }
            }
        }
    }

    private fun regenerateAll() {
        val state = current() ?: return
        if (state.isBusy) return
        content { copy(busy = PreviewBusy.RegeneratingAll, error = null, message = null) }
        viewModelScope.launch {
            when (val result = regenerate.all(historyId, state.items.map { it.word })) {
                is AppResult.Success -> {
                    if (result.data.items.isEmpty()) {
                        content { copy(busy = PreviewBusy.None, error = AppError.EmptyResponse) }
                    } else {
                        content { copy(items = result.data.items, busy = PreviewBusy.None, detailIndex = null) }
                    }
                }
                is AppResult.Failure -> content { copy(busy = PreviewBusy.None, error = result.error) }
            }
        }
    }

    private fun save() {
        val state = current() ?: return
        if (!state.canSave) return
        content { copy(busy = PreviewBusy.Saving, error = null, message = null) }
        viewModelScope.launch {
            val chosen = state.items.filter { it.selected && !it.isDuplicate }.map { it.word }
            when (val result = saveGeneratedWords(chosen)) {
                is AppResult.Failure -> content { copy(busy = PreviewBusy.None, error = result.error) }
                is AppResult.Success -> {
                    // Saved words now exist locally, so a fresh duplicate check marks them as such.
                    val refreshed = (detectDuplicates(state.items.map { it.word }) as? AppResult.Success)?.data
                    content {
                        copy(
                            items = refreshed ?: items,
                            busy = PreviewBusy.None,
                            message = if (result.data.savedIds.isEmpty()) {
                                PreviewMessage.NothingToSave
                            } else {
                                PreviewMessage.Saved(result.data.savedIds.size)
                            },
                        )
                    }
                }
            }
        }
    }

    private fun current(): AiPreviewUiState.Content? = _uiState.value as? AiPreviewUiState.Content

    private fun content(transform: AiPreviewUiState.Content.() -> AiPreviewUiState.Content) {
        _uiState.update { if (it is AiPreviewUiState.Content) it.transform() else it }
    }

    private fun List<PreviewItem>.toggled(index: Int): List<PreviewItem> = mapIndexed { i, item ->
        if (i == index && !item.isDuplicate) item.copy(selected = !item.selected) else item
    }

    /** After a replacement the user's choices for the untouched words stay as they were. */
    private fun List<PreviewItem>.keepingSelectionFrom(old: List<PreviewItem>): List<PreviewItem> {
        val before = old.associate { it.word.key to it.selected }
        return map { item -> before[item.word.key]?.let { item.copy(selected = it && !item.isDuplicate) } ?: item }
    }
}

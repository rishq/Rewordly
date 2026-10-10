package com.rewordly.app.feature.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rewordly.app.core.common.AppError
import com.rewordly.app.core.common.AppResult
import com.rewordly.app.core.network.ConnectivityObserver
import com.rewordly.app.domain.model.Word
import com.rewordly.app.domain.usecase.AddWordOutcome
import com.rewordly.app.domain.usecase.AddWordUseCase
import com.rewordly.app.domain.usecase.GenerationInputRules
import com.rewordly.app.domain.usecase.InputError
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where the current lookup stands. */
sealed interface AddWordStatus {
    data object Idle : AddWordStatus

    /** The dictionaries are being read. Indeterminate: there is no progress to report. */
    data object Looking : AddWordStatus

    /** The word is in the vocabulary now. */
    data class Added(val word: Word, val hasTranscription: Boolean) : AddWordStatus

    /** The word was already there; nothing was written. */
    data class AlreadyExists(val wordId: String, val text: String) : AddWordStatus

    /** No source knew the word, or none of them had a translation for it. Usually a typo. */
    data class NotFound(val word: String) : AddWordStatus

    data class Failed(val error: AppError) : AddWordStatus
}

data class AddWordUiState(
    val word: String = "",
    val status: AddWordStatus = AddWordStatus.Idle,
    val inputError: InputError? = null,
    /** False when the device has no connection, so a lookup is known to be impossible. */
    val isOnline: Boolean = true,
) {
    val isLoading: Boolean get() = status == AddWordStatus.Looking

    /** A finished lookup hides the form: the sheet shows its result instead. */
    val isSettled: Boolean get() = status is AddWordStatus.Added || status is AddWordStatus.AlreadyExists

    val canSubmit: Boolean get() = word.isNotBlank() && !isLoading
}

sealed interface AddWordEvent {
    /** The sheet was opened, optionally with the word the user was already searching for. */
    data class Started(val word: String) : AddWordEvent

    data class WordChanged(val value: String) : AddWordEvent

    data object Submit : AddWordEvent

    /** Clears the result and the field so another word can be added without reopening the sheet. */
    data object Reset : AddWordEvent
}

@HiltViewModel
class AddWordViewModel @Inject constructor(
    private val addWord: AddWordUseCase,
    private val connectivity: ConnectivityObserver,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AddWordUiState())
    val uiState: StateFlow<AddWordUiState> = _uiState.asStateFlow()

    private var job: Job? = null

    init {
        viewModelScope.launch {
            connectivity.isOnline.collect { online -> update { copy(isOnline = online) } }
        }
    }

    fun onEvent(event: AddWordEvent) {
        when (event) {
            is AddWordEvent.Started -> {
                job?.cancel()
                update { AddWordUiState(word = event.word.trim(), isOnline = isOnline) }
            }
            is AddWordEvent.WordChanged -> update { copy(word = event.value, inputError = null) }
            AddWordEvent.Submit -> submit()
            AddWordEvent.Reset -> {
                job?.cancel()
                update { AddWordUiState(isOnline = isOnline) }
            }
        }
    }

    private fun submit() {
        val state = _uiState.value
        if (state.isLoading) return
        val error = GenerationInputRules.wordError(state.word)
        if (error != null) {
            update { copy(inputError = error) }
            return
        }
        if (!state.isOnline) {
            // Nothing is attempted while the device is offline, and the typed word stays in place so it
            // can be submitted again once the connection is back.
            update { copy(status = AddWordStatus.Failed(AppError.Offline)) }
            return
        }
        val word = state.word.trim()
        update { copy(status = AddWordStatus.Looking, inputError = null) }
        job = viewModelScope.launch {
            // The lookup is awaited outside `update`: its lambda is not a coroutine body.
            val status = statusFor(addWord(word), word)
            update { copy(status = status) }
        }
    }

    private fun statusFor(result: AppResult<AddWordOutcome>, word: String): AddWordStatus = when (result) {
        is AppResult.Failure -> if (result.error == AppError.EmptyResponse) {
            AddWordStatus.NotFound(word)
        } else {
            AddWordStatus.Failed(result.error)
        }
        is AppResult.Success -> when (val outcome = result.data) {
            is AddWordOutcome.Added -> AddWordStatus.Added(outcome.word, outcome.hasTranscription)
            is AddWordOutcome.AlreadyExists -> AddWordStatus.AlreadyExists(outcome.wordId, outcome.text)
        }
    }

    private fun update(transform: AddWordUiState.() -> AddWordUiState) {
        _uiState.update(transform)
    }
}

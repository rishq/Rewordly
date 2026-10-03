package com.rewordly.app.feature.placement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rewordly.app.domain.model.PlacementQuestion
import com.rewordly.app.domain.model.PlacementResult
import com.rewordly.app.domain.repository.SettingsRepository
import com.rewordly.app.domain.repository.VocabularyRepository
import com.rewordly.app.domain.service.PlacementAssessor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface PlacementUiState {
    data object Loading : PlacementUiState

    /** Explains what the check is (and is not) before anything is asked. */
    data object Intro : PlacementUiState

    /** The vocabulary is too small to build questions. */
    data object Empty : PlacementUiState

    data class InProgress(
        val question: PlacementQuestion,
        val index: Int,
        val total: Int,
    ) : PlacementUiState {
        val fraction: Float get() = if (total == 0) 0f else index.toFloat() / total
    }

    data class Result(val result: PlacementResult) : PlacementUiState

    /** The estimated level was stored; the screen should close. */
    data object Applied : PlacementUiState
}

sealed interface PlacementEvent {
    data object Start : PlacementEvent

    data class Answer(val optionIndex: Int) : PlacementEvent

    /** Leaves this question unanswered; skipped questions never influence the estimate. */
    data object SkipQuestion : PlacementEvent

    data object Restart : PlacementEvent

    data object Apply : PlacementEvent
}

/**
 * Drives the optional placement check. Questions are built from the vocabulary already on the device,
 * so the check works offline and never leaves the phone. The result is only a hint the user can apply
 * or ignore.
 */
@HiltViewModel
class PlacementViewModel @Inject constructor(
    private val vocabularyRepository: VocabularyRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<PlacementUiState>(PlacementUiState.Loading)
    val uiState: StateFlow<PlacementUiState> = _uiState.asStateFlow()

    private var questions: List<PlacementQuestion> = emptyList()
    private val answers = LinkedHashMap<String, Boolean>()

    init {
        viewModelScope.launch { load() }
    }

    fun onEvent(event: PlacementEvent) {
        when (event) {
            PlacementEvent.Start -> start()
            is PlacementEvent.Answer -> answer(event.optionIndex)
            PlacementEvent.SkipQuestion -> advance()
            PlacementEvent.Restart -> viewModelScope.launch { load() }
            PlacementEvent.Apply -> viewModelScope.launch { applyLevel() }
        }
    }

    private suspend fun load() {
        _uiState.value = PlacementUiState.Loading
        val language = settingsRepository.settings.first().learningLanguage
        questions = try {
            PlacementAssessor.questions(vocabularyRepository.observeWords(language).first().map { it.word })
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
        answers.clear()
        _uiState.value = if (questions.isEmpty()) PlacementUiState.Empty else PlacementUiState.Intro
    }

    private fun start() {
        if (questions.isEmpty()) return
        answers.clear()
        show(0)
    }

    private fun answer(optionIndex: Int) {
        val state = _uiState.value as? PlacementUiState.InProgress ?: return
        val question = state.question
        if (optionIndex !in question.options.indices) return
        answers[question.id] = optionIndex == question.correctIndex
        advance()
    }

    private fun advance() {
        val state = _uiState.value as? PlacementUiState.InProgress ?: return
        val next = state.index + 1
        if (next >= questions.size) {
            _uiState.value = PlacementUiState.Result(PlacementAssessor.assess(questions, answers))
        } else {
            show(next)
        }
    }

    private fun show(index: Int) {
        _uiState.value = PlacementUiState.InProgress(
            question = questions[index],
            index = index,
            total = questions.size,
        )
    }

    private suspend fun applyLevel() {
        val result = (_uiState.value as? PlacementUiState.Result)?.result ?: return
        settingsRepository.setLevel(result.level)
        settingsRepository.setProfileConfigured()
        _uiState.value = PlacementUiState.Applied
    }
}

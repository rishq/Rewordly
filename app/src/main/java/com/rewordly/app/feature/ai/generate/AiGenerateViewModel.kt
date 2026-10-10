package com.rewordly.app.feature.ai.generate

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rewordly.app.core.common.AppError
import com.rewordly.app.core.common.AppResult
import com.rewordly.app.core.network.ConnectivityObserver
import com.rewordly.app.domain.model.Difficulty
import com.rewordly.app.domain.model.GenerationInput
import com.rewordly.app.domain.model.GenerationMode
import com.rewordly.app.domain.model.GenerationRequest
import com.rewordly.app.domain.model.GenerationSettings
import com.rewordly.app.domain.model.TopicPreset
import com.rewordly.app.domain.model.UsageInfo
import com.rewordly.app.domain.repository.GenerationSettingsRepository
import com.rewordly.app.domain.repository.VocabularyGenerationRepository
import com.rewordly.app.domain.usecase.GenerateVocabularyUseCase
import com.rewordly.app.domain.usecase.GenerationInputRules
import com.rewordly.app.domain.usecase.InputError
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where the current generation stands. Failures keep the [AppError] so the UI can tell rate limits from outages. */
sealed interface GenerationStatus {
    data object Idle : GenerationStatus

    /** Indeterminate on purpose: the backend gives no progress, so the UI never shows a percentage. */
    data object Loading : GenerationStatus

    /** The result is stored; the screen should open the preview of [historyId] once. */
    data class Done(val historyId: String) : GenerationStatus

    data object Empty : GenerationStatus

    data class Failed(val error: AppError) : GenerationStatus
}

data class AiGenerateUiState(
    val mode: GenerationMode,
    /** Selected preset, or null when [customTopic] is used. */
    val preset: TopicPreset? = TopicPreset.TECHNOLOGY,
    val customTopic: String = "",
    val text: String = "",
    val word: String = "",
    val settings: GenerationSettings = GenerationSettings(),
    val status: GenerationStatus = GenerationStatus.Idle,
    val usage: UsageInfo? = null,
    val showPrivacyDialog: Boolean = false,
    val suggestion: String? = null,
    val inputError: InputError? = null,
    /** False when the device has no usable connection, so generation is known to be impossible. */
    val isOnline: Boolean = true,
) {
    val topic: String get() = preset?.apiName ?: customTopic.trim()
    val isLoading: Boolean get() = status == GenerationStatus.Loading

    val input: GenerationInput
        get() = when (mode) {
            GenerationMode.TOPIC -> GenerationInput.Topic(topic)
            GenerationMode.TEXT -> GenerationInput.Text(text)
            GenerationMode.WORD -> GenerationInput.SingleWord(word.trim())
        }
}

sealed interface AiGenerateEvent {
    data class SelectPreset(val preset: TopicPreset?) : AiGenerateEvent

    data class CustomTopicChanged(val value: String) : AiGenerateEvent

    data class TextChanged(val value: String) : AiGenerateEvent

    data class WordChanged(val value: String) : AiGenerateEvent

    data class SettingsChanged(val settings: GenerationSettings) : AiGenerateEvent

    data object Submit : AiGenerateEvent

    data object ConfirmPrivacy : AiGenerateEvent

    data object DismissPrivacy : AiGenerateEvent

    data object Cancel : AiGenerateEvent

    data object ApplySuggestion : AiGenerateEvent

    /** The screen handled [GenerationStatus.Done]; reset so coming back shows the form again. */
    data object ResultConsumed : AiGenerateEvent
}

@HiltViewModel
class AiGenerateViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val settingsRepository: GenerationSettingsRepository,
    private val generationRepository: VocabularyGenerationRepository,
    private val generate: GenerateVocabularyUseCase,
    private val connectivity: ConnectivityObserver,
) : ViewModel() {
    private val mode = savedStateHandle.get<String>("mode")
        ?.let { name -> GenerationMode.entries.firstOrNull { it.name == name } }
        ?: GenerationMode.TOPIC

    private val _uiState = MutableStateFlow(AiGenerateUiState(mode = mode))
    val uiState: StateFlow<AiGenerateUiState> = _uiState.asStateFlow()

    private var job: Job? = null

    /** What the repository already holds, so submitting a request never rewrites identical preferences. */
    private var storedSettings: GenerationSettings? = null
    private var storedTopic: String? = null

    init {
        viewModelScope.launch {
            val saved = settingsRepository.settings.first()
            val lastTopic = settingsRepository.lastTopic.first()
            storedSettings = saved
            storedTopic = lastTopic
            val preset = TopicPreset.entries.firstOrNull { it.apiName == lastTopic }
            _uiState.update {
                it.copy(
                    settings = saved,
                    preset = if (lastTopic.isBlank()) it.preset else preset,
                    customTopic = if (lastTopic.isNotBlank() && preset == null) lastTopic else it.customTopic,
                )
            }
        }
        viewModelScope.launch {
            generationRepository.usage.collect { usage -> _uiState.update { it.copy(usage = usage) } }
        }
        // Only this screen listens to connectivity: everything else in the app works offline.
        viewModelScope.launch {
            connectivity.isOnline.collect { online -> _uiState.update { it.copy(isOnline = online) } }
        }
    }

    fun onEvent(event: AiGenerateEvent) {
        when (event) {
            is AiGenerateEvent.SelectPreset -> update { copy(preset = event.preset, inputError = null) }
            is AiGenerateEvent.CustomTopicChanged ->
                update { copy(customTopic = event.value, preset = null, inputError = null) }
            is AiGenerateEvent.TextChanged -> update { copy(text = event.value, inputError = null) }
            is AiGenerateEvent.WordChanged -> update { copy(word = event.value, inputError = null, suggestion = null) }
            is AiGenerateEvent.SettingsChanged -> update { copy(settings = event.settings) }
            AiGenerateEvent.Submit -> submit()
            AiGenerateEvent.ConfirmPrivacy -> confirmPrivacy()
            AiGenerateEvent.DismissPrivacy -> update { copy(showPrivacyDialog = false) }
            AiGenerateEvent.Cancel -> cancel()
            AiGenerateEvent.ApplySuggestion -> applySuggestion()
            AiGenerateEvent.ResultConsumed -> update { copy(status = GenerationStatus.Idle) }
        }
    }

    private fun submit() {
        val state = _uiState.value
        if (state.isLoading) return
        val error = GenerationInputRules.error(state.input)
        if (error != null) {
            update { copy(inputError = error) }
            return
        }
        if (state.mode == GenerationMode.TEXT) {
            viewModelScope.launch {
                if (settingsRepository.textNoticeAccepted.first()) {
                    start()
                } else {
                    update {
                        copy(
                            showPrivacyDialog = true,
                        )
                    }
                }
            }
        } else {
            start()
        }
    }

    private fun confirmPrivacy() {
        update { copy(showPrivacyDialog = false) }
        viewModelScope.launch {
            settingsRepository.acceptTextNotice()
            start()
        }
    }

    private fun start() {
        if (job?.isActive == true) return
        val state = _uiState.value
        if (!state.isOnline) {
            // Nothing is sent while the device is offline: no request is attempted, and everything the
            // user typed stays in place so it can be submitted again once the connection is back.
            update { copy(status = GenerationStatus.Failed(AppError.Offline)) }
            return
        }
        update { copy(status = GenerationStatus.Loading, suggestion = null) }
        job = viewModelScope.launch {
            persistInput(state)
            val result = generate(GenerationRequest(state.input, state.settings))
            update { copy(status = statusFor(result)).withSuggestion(result) }
        }
    }

    /** Only writes what actually changed: the settings were already read once, so an unchanged form is free. */
    private suspend fun persistInput(state: AiGenerateUiState) {
        if (state.settings != storedSettings) {
            settingsRepository.save(state.settings)
            storedSettings = state.settings
        }
        if (state.mode == GenerationMode.TOPIC && state.topic != storedTopic) {
            settingsRepository.saveLastTopic(state.topic)
            storedTopic = state.topic
        }
    }

    private fun statusFor(result: AppResult<com.rewordly.app.domain.usecase.GenerationOutcome>): GenerationStatus =
        when (result) {
            is AppResult.Failure ->
                if (result.error == AppError.EmptyResponse) {
                    GenerationStatus.Empty
                } else {
                    GenerationStatus.Failed(
                        result.error,
                    )
                }
            is AppResult.Success -> when {
                result.data.historyId != null -> GenerationStatus.Done(result.data.historyId)
                result.data.suggestedCorrection != null -> GenerationStatus.Idle
                else -> GenerationStatus.Empty
            }
        }

    private fun AiGenerateUiState.withSuggestion(
        result: AppResult<com.rewordly.app.domain.usecase.GenerationOutcome>,
    ): AiGenerateUiState = copy(suggestion = (result as? AppResult.Success)?.data?.suggestedCorrection)

    private fun cancel() {
        job?.cancel()
        job = null
        update { copy(status = GenerationStatus.Idle) }
    }

    private fun applySuggestion() {
        val suggestion = _uiState.value.suggestion ?: return
        update { copy(word = suggestion, suggestion = null, inputError = null) }
    }

    private fun update(transform: AiGenerateUiState.() -> AiGenerateUiState) {
        _uiState.update(transform)
    }

    companion object {
        val LEVELS: List<Difficulty> = Difficulty.entries
    }
}

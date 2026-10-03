package com.rewordly.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rewordly.app.domain.model.AiProvider
import com.rewordly.app.domain.model.AiSettings
import com.rewordly.app.domain.model.Difficulty
import com.rewordly.app.domain.model.InterfaceLanguage
import com.rewordly.app.domain.model.LearningGoal
import com.rewordly.app.domain.model.LearningLanguage
import com.rewordly.app.domain.model.ThemeMode
import com.rewordly.app.domain.model.TopicPreset
import com.rewordly.app.domain.model.UserSettings
import com.rewordly.app.domain.repository.AiSettingsRepository
import com.rewordly.app.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SettingsUiState {
    data object Loading : SettingsUiState

    data class Content(val settings: UserSettings, val ai: AiSettings) : SettingsUiState
}

sealed interface SettingsUiEvent {
    data class SetInterfaceLanguage(val language: InterfaceLanguage) : SettingsUiEvent

    data class SetLearningLanguage(val language: LearningLanguage) : SettingsUiEvent

    data class SetTheme(val mode: ThemeMode) : SettingsUiEvent

    data class SetDailyGoal(val goal: Int) : SettingsUiEvent

    data class SetNotifications(val enabled: Boolean) : SettingsUiEvent

    data class SetReminderTime(val hour: Int, val minute: Int) : SettingsUiEvent

    /** Null clears the estimated level, so recommendations fall back to neutral rules. */
    data class SetLevel(val level: Difficulty?) : SettingsUiEvent

    data class SetGoal(val goal: LearningGoal) : SettingsUiEvent

    data class SetInterests(val interests: Set<TopicPreset>) : SettingsUiEvent

    data class SetSessionLength(val length: Int) : SettingsUiEvent

    /** Null turns the user's own provider off, leaving generation to the backend path. */
    data class SetAiProvider(val provider: AiProvider?) : SettingsUiEvent

    data class SetAiModel(val model: String) : SettingsUiEvent

    /** Null or blank removes the stored key. */
    data class SetAiApiKey(val apiKey: String?) : SettingsUiEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val aiSettingsRepository: AiSettingsRepository,
) : ViewModel() {
    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        aiSettingsRepository.settings,
    ) { settings, ai ->
        SettingsUiState.Content(settings, ai)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState.Loading)

    fun onEvent(event: SettingsUiEvent) {
        viewModelScope.launch {
            when (event) {
                is SettingsUiEvent.SetInterfaceLanguage -> settingsRepository.setInterfaceLanguage(event.language)
                is SettingsUiEvent.SetLearningLanguage -> settingsRepository.setLearningLanguage(event.language)
                is SettingsUiEvent.SetTheme -> settingsRepository.setThemeMode(event.mode)
                is SettingsUiEvent.SetDailyGoal -> settingsRepository.setDailyGoal(event.goal)
                is SettingsUiEvent.SetNotifications -> settingsRepository.setNotificationsEnabled(event.enabled)
                is SettingsUiEvent.SetReminderTime -> settingsRepository.setReminderTime(event.hour, event.minute)
                is SettingsUiEvent.SetLevel -> settingsRepository.setLevel(event.level)
                is SettingsUiEvent.SetGoal -> settingsRepository.setLearningGoal(event.goal)
                is SettingsUiEvent.SetInterests -> settingsRepository.setInterests(event.interests)
                is SettingsUiEvent.SetSessionLength -> settingsRepository.setSessionLength(event.length)
                is SettingsUiEvent.SetAiProvider -> aiSettingsRepository.setProvider(event.provider)
                is SettingsUiEvent.SetAiModel -> aiSettingsRepository.setModel(event.model)
                is SettingsUiEvent.SetAiApiKey -> aiSettingsRepository.setApiKey(event.apiKey)
            }
        }
    }
}

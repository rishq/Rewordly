package com.rewordly.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rewordly.app.domain.model.InterfaceLanguage
import com.rewordly.app.domain.model.LearningLanguage
import com.rewordly.app.domain.model.ThemeMode
import com.rewordly.app.domain.model.UserSettings
import com.rewordly.app.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SettingsUiState {
    data object Loading : SettingsUiState

    data class Content(val settings: UserSettings) : SettingsUiState
}

sealed interface SettingsUiEvent {
    data class SetInterfaceLanguage(val language: InterfaceLanguage) : SettingsUiEvent

    data class SetLearningLanguage(val language: LearningLanguage) : SettingsUiEvent

    data class SetTheme(val mode: ThemeMode) : SettingsUiEvent

    data class SetDailyGoal(val goal: Int) : SettingsUiEvent

    data class SetNotifications(val enabled: Boolean) : SettingsUiEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val uiState: StateFlow<SettingsUiState> = settingsRepository.settings
        .map<UserSettings, SettingsUiState>(SettingsUiState::Content)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState.Loading)

    fun onEvent(event: SettingsUiEvent) {
        viewModelScope.launch {
            when (event) {
                is SettingsUiEvent.SetInterfaceLanguage -> settingsRepository.setInterfaceLanguage(event.language)
                is SettingsUiEvent.SetLearningLanguage -> settingsRepository.setLearningLanguage(event.language)
                is SettingsUiEvent.SetTheme -> settingsRepository.setThemeMode(event.mode)
                is SettingsUiEvent.SetDailyGoal -> settingsRepository.setDailyGoal(event.goal)
                is SettingsUiEvent.SetNotifications -> settingsRepository.setNotificationsEnabled(event.enabled)
            }
        }
    }
}

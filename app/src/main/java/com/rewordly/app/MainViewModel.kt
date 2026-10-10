package com.rewordly.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rewordly.app.domain.model.UserSettings
import com.rewordly.app.domain.repository.SettingsRepository
import com.rewordly.app.domain.usecase.InitializeAppUseCase
import com.rewordly.app.domain.usecase.StartDestination
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

sealed interface MainUiState {
    data object Loading : MainUiState

    /**
     * Everything the host needs for its first real frame: the stored settings and where to start.
     *
     * Resolving the destination here is what removes the second splash. The system splash already stays up
     * until the settings are loaded; waiting for the destination in the same state means the app is drawn
     * once, already on the right screen, instead of drawing a Compose splash and navigating away from it.
     */
    data class Ready(val settings: UserSettings, val startDestination: StartDestination) : MainUiState
}

@HiltViewModel
class MainViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    initializeApp: InitializeAppUseCase,
) : ViewModel() {
    val uiState: StateFlow<MainUiState> = combine(
        settingsRepository.settings,
        flow { emit(initializeApp()) },
    ) { settings, startDestination -> MainUiState.Ready(settings, startDestination) as MainUiState }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MainUiState.Loading)
}

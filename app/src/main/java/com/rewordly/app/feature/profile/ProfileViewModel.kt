package com.rewordly.app.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rewordly.app.domain.model.ProgressOverview
import com.rewordly.app.domain.repository.ProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface ProfileUiState {
    data object Loading : ProfileUiState

    data class Content(val overview: ProgressOverview) : ProfileUiState
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    progressRepository: ProgressRepository,
) : ViewModel() {
    val uiState: StateFlow<ProfileUiState> = progressRepository.observeOverview()
        .map<ProgressOverview, ProfileUiState>(ProfileUiState::Content)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState.Loading)
}

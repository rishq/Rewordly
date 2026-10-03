package com.rewordly.app.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rewordly.app.core.common.AppError
import com.rewordly.app.domain.model.LearningInsight
import com.rewordly.app.domain.model.ProgressOverview
import com.rewordly.app.domain.model.ReviewStatistics
import com.rewordly.app.domain.repository.ProgressRepository
import com.rewordly.app.domain.repository.SettingsRepository
import com.rewordly.app.domain.usecase.ObserveLearningInsightUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface ProgressUiState {
    data object Loading : ProgressUiState

    data class Content(
        val overview: ProgressOverview,
        val statistics: ReviewStatistics,
        /** Introduced / mastered / in review, weak words and recent accuracy. */
        val insight: LearningInsight,
    ) : ProgressUiState

    data class Error(val error: AppError) : ProgressUiState
}

/** State of the Progress screen: learning totals, review statistics and the learning insights. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProgressViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    progressRepository: ProgressRepository,
    observeInsight: ObserveLearningInsightUseCase,
) : ViewModel() {
    private val retry = MutableStateFlow(0)

    val uiState: StateFlow<ProgressUiState> = retry.flatMapLatest {
        val insight = settingsRepository.settings
            .map { it.learningLanguage }
            .distinctUntilChanged()
            .flatMapLatest { observeInsight(it) }
        combine(
            progressRepository.observeOverview(),
            progressRepository.observeStatistics(),
            insight,
        ) { overview, statistics, learningInsight ->
            ProgressUiState.Content(overview, statistics, learningInsight) as ProgressUiState
        }.catch { emit(ProgressUiState.Error(AppError.Database(it))) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ProgressUiState.Loading)

    fun retry() {
        retry.value++
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

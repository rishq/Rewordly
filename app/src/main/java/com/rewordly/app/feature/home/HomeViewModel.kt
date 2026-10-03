package com.rewordly.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rewordly.app.core.common.AppError
import com.rewordly.app.domain.model.DailyLearningPlan
import com.rewordly.app.domain.model.LearningInsight
import com.rewordly.app.domain.model.ProgressOverview
import com.rewordly.app.domain.model.WordWithProgress
import com.rewordly.app.domain.repository.ProgressRepository
import com.rewordly.app.domain.repository.SettingsRepository
import com.rewordly.app.domain.repository.VocabularyRepository
import com.rewordly.app.domain.usecase.BuildDailyLearningPlanUseCase
import com.rewordly.app.domain.usecase.GetGreetingUseCase
import com.rewordly.app.domain.usecase.Greeting
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

sealed interface HomeUiState {
    data object Loading : HomeUiState

    data class Success(
        val greeting: Greeting,
        val overview: ProgressOverview,
        val recentWords: List<WordWithProgress>,
        /** Today's recommended session: what to review and which new words to introduce. */
        val plan: DailyLearningPlan,
        val insight: LearningInsight,
        /** True while the user has no estimated level and has barely started. */
        val showPlacementPrompt: Boolean,
    ) : HomeUiState

    data class Error(val error: AppError) : HomeUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    vocabularyRepository: VocabularyRepository,
    progressRepository: ProgressRepository,
    getGreeting: GetGreetingUseCase,
    buildDailyPlan: BuildDailyLearningPlanUseCase,
    observeInsight: ObserveLearningInsightUseCase,
) : ViewModel() {
    private val retry = MutableStateFlow(0)

    val uiState: StateFlow<HomeUiState> = retry.flatMapLatest {
        val settings = settingsRepository.settings
        val language = settings.map { it.learningLanguage }.distinctUntilChanged()
        val recentWords = language.flatMapLatest {
            vocabularyRepository.observeRecentWords(it, RECENT_WORDS_LIMIT)
        }
        val plan = language.flatMapLatest { buildDailyPlan(it) }
        val insight = language.flatMapLatest { observeInsight(it) }
        combine(
            progressRepository.observeOverview(),
            recentWords,
            plan,
            insight,
            settings,
        ) { overview, words, dailyPlan, learningInsight, userSettings ->
            HomeUiState.Success(
                greeting = getGreeting(),
                overview = overview,
                recentWords = words,
                plan = dailyPlan,
                insight = learningInsight,
                // The profile setup is optional: the prompt disappears once a level is known or the
                // user has clearly started learning, and never blocks anything.
                showPlacementPrompt = userSettings.level == null &&
                    learningInsight.wordsIntroduced < PLACEMENT_PROMPT_MAX_INTRODUCED,
            ) as HomeUiState
        }.catch { emit(HomeUiState.Error(AppError.Database(it))) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), HomeUiState.Loading)

    fun retry() {
        retry.value++
    }

    private companion object {
        const val RECENT_WORDS_LIMIT = 5
        const val PLACEMENT_PROMPT_MAX_INTRODUCED = 5
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

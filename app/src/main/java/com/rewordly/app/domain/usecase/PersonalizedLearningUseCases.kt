package com.rewordly.app.domain.usecase

import com.rewordly.app.core.common.TimeProvider
import com.rewordly.app.domain.model.DailyLearningPlan
import com.rewordly.app.domain.model.LearningInsight
import com.rewordly.app.domain.model.LearningLanguage
import com.rewordly.app.domain.model.toLearningProfile
import com.rewordly.app.domain.repository.ProgressRepository
import com.rewordly.app.domain.repository.SettingsRepository
import com.rewordly.app.domain.repository.VocabularyRepository
import com.rewordly.app.domain.service.LearningInsightCalculator
import com.rewordly.app.domain.service.RecommendationEngine
import com.rewordly.app.domain.service.RecommendationInput
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Builds today's personalized plan from local data only. It is a flow, so the plan is recomputed
 * whenever the vocabulary, the profile or the progress changes - the Home screen never shows a stale
 * plan right after a word was learned.
 */
class BuildDailyLearningPlanUseCase @Inject constructor(
    private val vocabularyRepository: VocabularyRepository,
    private val settingsRepository: SettingsRepository,
    private val progressRepository: ProgressRepository,
    private val engine: RecommendationEngine,
    private val timeProvider: TimeProvider,
) {
    operator fun invoke(language: LearningLanguage): Flow<DailyLearningPlan> = combine(
        vocabularyRepository.observeWords(language),
        settingsRepository.settings,
        progressRepository.observeOverview(),
    ) { words, settings, overview ->
        engine.recommend(
            RecommendationInput(
                words = words,
                profile = settings.toLearningProfile(),
                now = timeProvider.nowMillis(),
                zone = timeProvider.zone(),
                learnedToday = overview.today.wordsLearned,
            ),
        )
    }
}

/** Observable learning insights, reused by the Home summary and the insights section. */
class ObserveLearningInsightUseCase @Inject constructor(
    private val vocabularyRepository: VocabularyRepository,
    private val progressRepository: ProgressRepository,
) {
    operator fun invoke(language: LearningLanguage): Flow<LearningInsight> = combine(
        vocabularyRepository.observeWords(language),
        progressRepository.observeOverview(),
        progressRepository.observeHistory(),
    ) { words, overview, history -> LearningInsightCalculator.calculate(words, overview, history) }
}

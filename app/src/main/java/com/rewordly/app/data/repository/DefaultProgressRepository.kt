package com.rewordly.app.data.repository

import com.rewordly.app.core.common.TimeProvider
import com.rewordly.app.core.database.dao.WordProgressDao
import com.rewordly.app.data.local.MockActivity
import com.rewordly.app.domain.model.DailyProgress
import com.rewordly.app.domain.model.ProgressOverview
import com.rewordly.app.domain.repository.ProgressRepository
import com.rewordly.app.domain.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Combines real per-word counts from Room with placeholder streak/activity data. */
@Singleton
class DefaultProgressRepository @Inject constructor(
    private val progressDao: WordProgressDao,
    private val settingsRepository: SettingsRepository,
    private val timeProvider: TimeProvider,
) : ProgressRepository {

    override fun observeOverview(): Flow<ProgressOverview> = combine(
        progressDao.observeCounts(sinceMillis = timeProvider.startOfTodayMillis()),
        settingsRepository.settings.map { it.dailyGoal }.distinctUntilChanged(),
    ) { counts, goal ->
        val todayDate = timeProvider.today()
        val today = DailyProgress(
            date = todayDate,
            wordsLearned = counts.learnedSince,
            wordsReviewed = counts.reviewed,
            goal = goal,
        )
        val history = MockActivity.wordsLearnedLastWeek.dropLast(1).mapIndexed { index, learned ->
            DailyProgress(
                date = todayDate.minusDays((MockActivity.wordsLearnedLastWeek.size - 1 - index).toLong()),
                wordsLearned = learned,
                wordsReviewed = learned,
                goal = goal,
            )
        }
        ProgressOverview(
            today = today,
            wordsLearned = counts.learned,
            wordsReviewed = MockActivity.REVIEWED_BASELINE + counts.reviewed,
            wordsToReview = counts.toReview,
            currentStreak = MockActivity.CURRENT_STREAK + if (counts.learnedSince > 0) 1 else 0,
            longestStreak = MockActivity.LONGEST_STREAK,
            activity = history + today,
        )
    }
}

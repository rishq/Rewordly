package com.rewordly.app.data.repository

import com.rewordly.app.core.common.TimeProvider
import com.rewordly.app.core.database.dao.DailyActivityDao
import com.rewordly.app.core.database.dao.ReviewLogDao
import com.rewordly.app.core.database.dao.WordDao
import com.rewordly.app.core.database.dao.WordProgressDao
import com.rewordly.app.data.local.toDomain
import com.rewordly.app.data.local.toStorageKey
import com.rewordly.app.domain.model.DailyActivity
import com.rewordly.app.domain.model.DailyProgress
import com.rewordly.app.domain.model.ProgressOverview
import com.rewordly.app.domain.model.ReviewHistory
import com.rewordly.app.domain.model.ReviewLogEntry
import com.rewordly.app.domain.model.ReviewStatistics
import com.rewordly.app.domain.repository.ProgressRepository
import com.rewordly.app.domain.repository.SettingsRepository
import com.rewordly.app.domain.usecase.ComputeStreakUseCase
import com.rewordly.app.domain.usecase.ReviewHistoryCalculator
import com.rewordly.app.domain.usecase.ReviewQueueBuilder
import com.rewordly.app.domain.usecase.ReviewStatisticsCalculator
import com.rewordly.app.domain.usecase.ReviewTotals
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/** Daily goal, totals, streak, statistics and history computed from local Room data only. */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class DefaultProgressRepository @Inject constructor(
    private val progressDao: WordProgressDao,
    private val activityDao: DailyActivityDao,
    private val reviewLogDao: ReviewLogDao,
    private val wordDao: WordDao,
    private val settingsRepository: SettingsRepository,
    private val timeProvider: TimeProvider,
    private val computeStreak: ComputeStreakUseCase,
) : ProgressRepository {

    override fun observeOverview(): Flow<ProgressOverview> {
        val today = timeProvider.today()
        val goalAndTotal = settingsRepository.settings
            .map { it.learningLanguage.tag to it.dailyGoal }
            .distinctUntilChanged()
            .flatMapLatest { (language, goal) -> wordDao.observeCount(language).map { goal to it } }

        return combine(
            progressDao.observeCounts(dueBoundary(), invalidBoundary()),
            goalAndTotal,
            activityDao.observeRange(
                fromDay = today.minusDays((ACTIVITY_WINDOW_DAYS - 1).toLong()).toStorageKey(),
                toDay = today.toStorageKey(),
            ),
            observeLog(),
        ) { counts, totals, legacy, log ->
            val (goal, totalWords) = totals
            val zone = timeProvider.zone()
            val byDay = ReviewHistoryCalculator.activity(
                log,
                legacy.map { it.toDomain() },
                zone,
            ).associateBy { it.date }
            val reviewedToday = ReviewHistoryCalculator.byDay(log, zone).firstOrNull { it.date == today }
                ?.reviewedWords ?: 0
            ProgressOverview(
                today = progressOf(byDay[today], today, goal),
                wordsLearned = counts.learned,
                wordsReviewed = counts.reviewed,
                wordsDueToday = counts.due,
                newWordsAvailable = (totalWords - counts.started).coerceAtLeast(0),
                reviewedToday = reviewedToday,
                savedWords = counts.saved,
                totalWords = totalWords,
                streak = computeStreak(byDay.values.toList(), today),
                activity = (CHART_WINDOW_DAYS - 1 downTo 0).map { offset ->
                    val date = today.minusDays(offset.toLong())
                    progressOf(byDay[date], date, goal)
                },
            )
        }
    }

    override fun observeStatistics(): Flow<ReviewStatistics> {
        val today = timeProvider.today()
        return combine(
            progressDao.observeCounts(dueBoundary(), invalidBoundary()),
            reviewLogDao.observeTotals(),
            observeLog(),
        ) { counts, totals, log ->
            val reviewedToday = ReviewHistoryCalculator.byDay(log, timeProvider.zone())
                .firstOrNull { it.date == today }?.reviewedWords ?: 0
            ReviewStatisticsCalculator.calculate(
                totals = ReviewTotals(totals.total, totals.correct, totals.qualitySum),
                wordsRemembered = counts.remembered,
                wordsForgotten = counts.forgotten,
                wordsReviewedToday = reviewedToday,
                wordsStillDue = counts.due,
            )
        }
    }

    override fun observeHistory(): Flow<ReviewHistory> = observeLog().map {
        ReviewHistory(ReviewHistoryCalculator.byDay(it, timeProvider.zone()))
    }

    private fun observeLog(): Flow<List<ReviewLogEntry>> {
        val since = timeProvider.today().minusDays((ACTIVITY_WINDOW_DAYS - 1).toLong())
            .atStartOfDay(timeProvider.zone()).toInstant().toEpochMilli()
        return reviewLogDao.observeSince(since).map { rows -> rows.map { it.toDomain() } }
    }

    private fun dueBoundary(): Long = timeProvider.startOfTomorrowMillis()

    private fun invalidBoundary(): Long = timeProvider.nowMillis() + ReviewQueueBuilder.MAX_VALID_FUTURE_MILLIS

    private fun progressOf(activity: DailyActivity?, date: LocalDate, goal: Int) = DailyProgress(
        date = date,
        wordsLearned = activity?.wordsLearned ?: 0,
        wordsReviewed = activity?.wordsReviewed ?: 0,
        goal = goal,
    )

    private companion object {
        const val CHART_WINDOW_DAYS = 7
        const val ACTIVITY_WINDOW_DAYS = 365
    }
}

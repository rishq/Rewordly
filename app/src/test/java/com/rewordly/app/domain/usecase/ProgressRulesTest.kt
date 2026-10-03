package com.rewordly.app.domain.usecase

import com.rewordly.app.domain.model.DailyActivity
import com.rewordly.app.domain.model.DailyProgress
import com.rewordly.app.domain.model.UserSettings
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComputeStreakUseCaseTest {
    private val today = LocalDate.of(2024, 5, 10)
    private val useCase = ComputeStreakUseCase()

    private fun activity(vararg offsets: Long, learned: Int = 1, reviewed: Int = 0) =
        offsets.map { DailyActivity(today.minusDays(it), learned, reviewed) }

    @Test
    fun noActivity_meansNoStreak() {
        val streak = useCase(emptyList(), today)
        assertEquals(0, streak.current)
        assertEquals(0, streak.longest)
        assertNull(streak.lastActiveDate)
    }

    @Test
    fun consecutiveDaysEndingToday_countAsCurrentStreak() {
        val streak = useCase(activity(0, 1, 2, 5), today)
        assertEquals(3, streak.current)
        assertEquals(3, streak.longest)
        assertEquals(today, streak.lastActiveDate)
    }

    @Test
    fun yesterdayStillCounts_butTwoDaysAgoDoesNot() {
        assertEquals(2, useCase(activity(1, 2), today).current)
        assertEquals(0, useCase(activity(2, 3), today).current)
    }

    @Test
    fun longestStreak_survivesGaps() {
        val streak = useCase(activity(0, 1, 5, 6, 7, 8), today)
        assertEquals(2, streak.current)
        assertEquals(4, streak.longest)
    }

    @Test
    fun daysWithoutLearningActionsAreIgnored() {
        val viewedOnly = listOf(DailyActivity(today, wordsLearned = 0, wordsReviewed = 0))
        assertEquals(0, useCase(viewedOnly, today).current)
    }
}

class DailyGoalTest {
    @Test
    fun defaultGoal_isTenWords() {
        assertEquals(10, UserSettings().dailyGoal)
        assertEquals(listOf(5, 10, 15, 20, 30), UserSettings.DAILY_GOAL_OPTIONS)
    }

    @Test
    fun fraction_isClampedAndReachesGoal() {
        val today = LocalDate.of(2024, 5, 10)
        val half = DailyProgress(today, wordsLearned = 5, wordsReviewed = 3, goal = 10)
        assertEquals(0.5f, half.goalFraction, 0.001f)
        assertEquals(3, half.wordsReviewed)
        assertFalse(half.isGoalReached)

        val done = half.copy(wordsLearned = 12)
        assertEquals(1f, done.goalFraction, 0.001f)
        assertTrue(done.isGoalReached)
    }

    @Test
    fun zeroGoal_neverCrashes() {
        val today = LocalDate.of(2024, 5, 10)
        val progress = DailyProgress(today, wordsLearned = 3, wordsReviewed = 0, goal = 0)
        assertEquals(0f, progress.goalFraction, 0.001f)
        assertFalse(progress.isGoalReached)
    }
}

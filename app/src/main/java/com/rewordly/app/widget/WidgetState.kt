package com.rewordly.app.widget

import com.rewordly.app.domain.model.ProgressOverview

/**
 * The small amount of information the widget shows.
 *
 * Deliberately not a copy of [ProgressOverview]: the widget renders four numbers, and reducing them
 * here keeps the Glance code free of business logic and makes the mapping unit-testable.
 */
data class WidgetState(
    val wordsLearnedToday: Int,
    val dailyGoal: Int,
    val dueWords: Int,
    val streakDays: Int,
    /** Whether the user has ever learned or reviewed anything. */
    val hasHistory: Boolean,
) {
    /** Share of today's goal completed, clamped so an over-achieved day never overflows the bar. */
    val goalFraction: Float
        get() = if (dailyGoal <= 0) 0f else (wordsLearnedToday.toFloat() / dailyGoal).coerceIn(0f, 1f)

    val isGoalReached: Boolean get() = dailyGoal > 0 && wordsLearnedToday >= dailyGoal

    companion object {
        /** Shown before the first progress arrives, so the widget is never blank. */
        val EMPTY = WidgetState(
            wordsLearnedToday = 0,
            dailyGoal = 0,
            dueWords = 0,
            streakDays = 0,
            hasHistory = false,
        )
    }
}

/** Maps the local progress overview onto the widget state. No database work happens here. */
object WidgetStateMapper {
    fun from(overview: ProgressOverview): WidgetState = WidgetState(
        wordsLearnedToday = overview.today.wordsLearned,
        dailyGoal = overview.today.goal,
        dueWords = overview.wordsDueToday,
        streakDays = overview.streak.current,
        hasHistory = overview.wordsLearned > 0 || overview.wordsReviewed > 0,
    )
}

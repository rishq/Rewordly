package com.rewordly.app.domain.model

import java.time.LocalDate

data class DailyProgress(
    val date: LocalDate,
    val wordsLearned: Int,
    val wordsReviewed: Int,
    val goal: Int,
) {
    val goalFraction: Float
        get() = if (goal <= 0) 0f else (wordsLearned.toFloat() / goal).coerceIn(0f, 1f)
}

data class ProgressOverview(
    val today: DailyProgress,
    val wordsLearned: Int,
    val wordsReviewed: Int,
    val wordsToReview: Int,
    val currentStreak: Int,
    val longestStreak: Int,
    /** Oldest first, ending with today. */
    val activity: List<DailyProgress>,
)

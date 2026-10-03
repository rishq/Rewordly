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

    val isGoalReached: Boolean get() = goal > 0 && wordsLearned >= goal
}

/** One row per active calendar day, used for the streak and the weekly chart. */
data class DailyActivity(
    val date: LocalDate,
    val wordsLearned: Int,
    val wordsReviewed: Int,
) {
    val actions: Int get() = wordsLearned + wordsReviewed
}

data class Streak(
    val current: Int = 0,
    val longest: Int = 0,
    /** Last day with at least one learning action, or null when there is none. */
    val lastActiveDate: LocalDate? = null,
)

data class ProgressOverview(
    val today: DailyProgress,
    val wordsLearned: Int,
    val wordsReviewed: Int,
    /** Words whose review is due before the end of today. */
    val wordsDueToday: Int,
    val newWordsAvailable: Int,
    /** Distinct words answered in review today. */
    val reviewedToday: Int,
    val savedWords: Int,
    val totalWords: Int,
    val streak: Streak,
    /** Oldest first, ending with today. */
    val activity: List<DailyProgress>,
) {
    /** Share of today's review workload completed, or null when nothing was due. */
    val reviewCompletion: Float?
        get() = (reviewedToday + wordsDueToday).takeIf { it > 0 }?.let { reviewedToday.toFloat() / it }
}

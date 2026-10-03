package com.rewordly.app.domain.model

/**
 * A readable summary of the user's learning, derived from local data only.
 *
 * The three word counters are deliberately disjoint - introduced = mastered + in review - so the
 * numbers can never overlap or look better than they are.
 */
data class LearningInsight(
    /** Words the user has started learning: everything that is no longer NEW. */
    val wordsIntroduced: Int,
    /** Words that are learned and have survived enough successful reviews to stick. */
    val wordsMastered: Int,
    /** Introduced but not mastered yet: still being reviewed. */
    val wordsInReview: Int,
    val totalWords: Int,
    /** Words the user keeps getting wrong, worst first. */
    val weakWords: List<WordWithProgress>,
    /** Share of correct answers over the most recent reviews; null without any review history. */
    val recentAccuracy: Float?,
    /** How many reviews the accuracy figure is based on. */
    val reviewsConsidered: Int,
    val learnedToday: Int,
    val dailyGoal: Int,
    val dueToday: Int,
    val reviewedToday: Int,
    /** Oldest first, ending with today. */
    val activity: List<DailyProgress>,
) {
    val goalFraction: Float
        get() = if (dailyGoal <= 0) 0f else (learnedToday.toFloat() / dailyGoal).coerceIn(0f, 1f)

    val isGoalReached: Boolean get() = dailyGoal > 0 && learnedToday >= dailyGoal

    /** True once there is anything meaningful to show; new users get a friendly empty state instead. */
    val hasHistory: Boolean get() = wordsIntroduced > 0

    val hasWeakWords: Boolean get() = weakWords.isNotEmpty()
}

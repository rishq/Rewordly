package com.rewordly.app.domain.model

/**
 * Why the engine put a word into today's plan. Every reason is derived from real learning data,
 * so the UI can show it to the user as an explanation instead of an opaque score.
 */
enum class RecommendationReason {
    /** Scheduled for today or already overdue. */
    DUE_FOR_REVIEW,

    /** The user gets this word wrong again and again. */
    OFTEN_MISTAKEN,

    /** The word keeps coming back after lapses; it needs extra practice. */
    DIFFICULT,

    /** A new word that fits the estimated level. */
    MATCHES_LEVEL,

    /** A new word from one of the topics the user is interested in. */
    MATCHES_INTERESTS,

    /** A new word recommended without level or topic data. */
    NEW_FOR_YOU,
}

data class Recommendation(
    val item: WordWithProgress,
    val reason: RecommendationReason,
) {
    val word: Word get() = item.word
}

/**
 * The recommended work for one day: reviews first, then a strictly limited number of new words.
 * Nothing here schedules anything - the spaced-repetition service still owns every interval.
 */
data class DailyLearningPlan(
    val reviews: List<Recommendation>,
    val newWords: List<Recommendation>,
    /** Words actually due today (overdue or due before tomorrow), before the session cap. */
    val totalDue: Int,
    /** How many new words the engine was willing to introduce today. */
    val newWordBudget: Int,
    val sessionLength: Int,
    /** True when the new-word budget was lowered because the user is struggling with many words. */
    val easedOff: Boolean = false,
) {
    val isEmpty: Boolean get() = reviews.isEmpty() && newWords.isEmpty()

    val plannedCount: Int get() = reviews.size + newWords.size

    val reviewCount: Int get() = reviews.size

    val newWordCount: Int get() = newWords.size
}

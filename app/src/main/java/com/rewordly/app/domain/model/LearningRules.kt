package com.rewordly.app.domain.model

/**
 * Thresholds used to interpret learning data. Kept in one place so the recommendation engine,
 * the insight summary and the UI all agree on what "weak" or "mastered" means.
 */
object LearningRules {
    /** A word counts as repeatedly missed from this many wrong answers onwards. */
    const val WEAK_MIN_MISTAKES = 2

    /** From this many struggling words onwards the daily plan eases off new words. */
    const val MANY_WEAK_WORDS = 5

    /** Successful reviews needed before a word is considered mastered rather than in review. */
    const val MASTERED_REPETITIONS = 2

    /**
     * Correct answers in a row a word needs before it graduates to [WordStatus.LEARNED]. Until then it
     * keeps coming back in the study session; afterwards only the spaced-repetition queue can bring it
     * back. Change this one constant to make the app more or less demanding.
     */
    const val GRADUATION_REPETITIONS = 5

    /** How many of the most recent reviews the accuracy figure is based on. */
    const val RECENT_ACCURACY_REVIEWS = 20

    /** Weak words are a nudge, not a second review queue, so only a few are shown. */
    const val MAX_WEAK_WORDS = 5
}

/** True when the user repeatedly fails this word, rather than having seen it once. */
val WordProgress.isWeak: Boolean
    get() = consecutiveIncorrect >= LearningRules.WEAK_MIN_MISTAKES ||
        (incorrectAnswers >= LearningRules.WEAK_MIN_MISTAKES && incorrectAnswers > correctAnswers)

/** True when the word is learned and has survived enough reviews to be considered solid. */
val WordProgress.isMastered: Boolean
    get() = status == WordStatus.LEARNED && repetitionCount >= LearningRules.MASTERED_REPETITIONS

/**
 * True when the word finished the study session for good: it reached [LearningRules.GRADUATION_REPETITIONS]
 * correct answers in a row. Graduated words never appear in the study session again, only in review.
 */
val WordProgress.isGraduated: Boolean
    get() = status == WordStatus.LEARNED

/**
 * True while the word is still untouched: the user has neither answered it nor decided to study it, so the
 * card offers the triage pair "I know it / study it" instead of "I remembered it / I did not".
 */
val WordProgress.isUntouched: Boolean
    get() = status == WordStatus.NEW && reviewCount == 0

/** How a weak word is explained to the user: consistently difficult, or often missed. */
val WordProgress.weakReason: RecommendationReason
    get() = if (consecutiveIncorrect >= LearningRules.WEAK_MIN_MISTAKES) {
        RecommendationReason.DIFFICULT
    } else {
        RecommendationReason.OFTEN_MISTAKEN
    }

package com.rewordly.app.domain.model

import java.time.LocalDate

/** SM-2 style answer quality, 0 (blackout) to 5 (perfect). Anything below [PASS_THRESHOLD] is a lapse. */
enum class AnswerQuality(val value: Int) {
    FORGOT(0),
    HINT(1),
    DIFFICULT(2),
    EFFORT(3),
    CORRECT(4),
    EASY(5),
    ;

    val isCorrect: Boolean get() = value >= PASS_THRESHOLD

    companion object {
        const val PASS_THRESHOLD = 3

        fun fromValue(value: Int): AnswerQuality? = entries.firstOrNull { it.value == value }
    }
}

/** The four buttons shown to the user, mapped onto [AnswerQuality]. */
enum class ReviewRating(val quality: AnswerQuality) {
    AGAIN(AnswerQuality.FORGOT),
    HARD(AnswerQuality.EFFORT),
    GOOD(AnswerQuality.CORRECT),
    EASY(AnswerQuality.EASY),
}

/** Everything the scheduler needs to know about one word. Stored on [WordProgress]. */
data class ReviewState(
    val wordId: String,
    val repetitionCount: Int = 0,
    val easeFactor: Double = DEFAULT_EASE_FACTOR,
    val intervalDays: Int = 0,
    val nextReviewAt: Long? = null,
    val lastReviewedAt: Long? = null,
    val consecutiveCorrectAnswers: Int = 0,
    val consecutiveIncorrectAnswers: Int = 0,
    val learningStatus: WordStatus = WordStatus.NEW,
) {
    companion object {
        const val DEFAULT_EASE_FACTOR = 2.5
    }
}

enum class ReviewKind { LEARN, REVIEW }

/** One immutable entry of the review history. Timestamps are UTC epoch millis; days are derived on read. */
data class ReviewLogEntry(
    val wordId: String,
    val kind: ReviewKind,
    val quality: Int,
    val reviewedAt: Long,
    val durationMs: Long?,
)

data class ReviewSubmission(
    val wordId: String,
    val sessionId: String,
    val quality: AnswerQuality,
    val reviewedAt: Long,
    val durationMs: Long?,
    val previousIntervalDays: Int,
    val newState: ReviewState,
)

sealed interface ReviewSubmitResult {
    data class Applied(val newState: ReviewState) : ReviewSubmitResult

    /** The same word was already answered in this session; nothing was scheduled twice. */
    data object Duplicate : ReviewSubmitResult
}

/** Words waiting for review. [items] is capped per session, [totalDue] is the real workload. */
data class ReviewQueue(val items: List<WordWithProgress>, val totalDue: Int)

data class ReviewStatistics(
    val totalReviews: Int = 0,
    val correctAnswers: Int = 0,
    val incorrectAnswers: Int = 0,
    val wordsRemembered: Int = 0,
    val wordsForgotten: Int = 0,
    /** 0 (always effortless) to 5 (always forgotten); null without any review. */
    val averageDifficulty: Double? = null,
    /** Share of today's workload already done, null when there was nothing to do today. */
    val completionRate: Float? = null,
)

data class ReviewDay(
    val date: LocalDate,
    val reviewedWords: Int,
    val reviews: Int,
    val correct: Int,
    val incorrect: Int,
    val wordsLearned: Int,
    /** Null when no answer of the day carried a measured duration. */
    val studyTimeMs: Long?,
) {
    val activity: Int get() = reviews + wordsLearned
}

data class ReviewHistory(val days: List<ReviewDay>)

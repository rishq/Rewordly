package com.rewordly.app.domain.model

enum class WordStatus { NEW, LEARNING, LEARNED }

/** Per-word learning state, including the spaced-repetition schedule. */
data class WordProgress(
    val wordId: String,
    val status: WordStatus = WordStatus.NEW,
    val isSaved: Boolean = false,
    val views: Int = 0,
    val correctAnswers: Int = 0,
    val incorrectAnswers: Int = 0,
    val lastViewedAt: Long? = null,
    val lastReviewedAt: Long? = null,
    val nextReviewAt: Long? = null,
    val repetitionCount: Int = 0,
    val easeFactor: Double = ReviewState.DEFAULT_EASE_FACTOR,
    val intervalDays: Int = 0,
    val consecutiveCorrect: Int = 0,
    val consecutiveIncorrect: Int = 0,
) {
    val reviewCount: Int get() = correctAnswers + incorrectAnswers

    val reviewState: ReviewState
        get() = ReviewState(
            wordId = wordId,
            repetitionCount = repetitionCount,
            easeFactor = easeFactor,
            intervalDays = intervalDays,
            nextReviewAt = nextReviewAt,
            lastReviewedAt = lastReviewedAt,
            consecutiveCorrectAnswers = consecutiveCorrect,
            consecutiveIncorrectAnswers = consecutiveIncorrect,
            learningStatus = status,
        )

    fun withReviewState(state: ReviewState): WordProgress = copy(
        status = state.learningStatus,
        repetitionCount = state.repetitionCount,
        easeFactor = state.easeFactor,
        intervalDays = state.intervalDays,
        nextReviewAt = state.nextReviewAt,
        lastReviewedAt = state.lastReviewedAt,
        consecutiveCorrect = state.consecutiveCorrectAnswers,
        consecutiveIncorrect = state.consecutiveIncorrectAnswers,
    )
}

data class WordWithProgress(
    val word: Word,
    val progress: WordProgress,
)

package com.rewordly.app.domain.usecase

import com.rewordly.app.core.common.AppResult
import com.rewordly.app.core.common.TimeProvider
import com.rewordly.app.domain.model.ReviewRating
import com.rewordly.app.domain.model.ReviewState
import com.rewordly.app.domain.model.ReviewSubmission
import com.rewordly.app.domain.model.ReviewSubmitResult
import com.rewordly.app.domain.repository.ReviewRepository
import com.rewordly.app.domain.service.SpacedRepetitionService
import javax.inject.Inject

/** Schedules the next review for an answer and persists both. Repeating a submission is harmless. */
class SubmitReviewAnswerUseCase @Inject constructor(
    private val reviewRepository: ReviewRepository,
    private val spacedRepetition: SpacedRepetitionService,
    private val timeProvider: TimeProvider,
) {
    suspend operator fun invoke(
        wordId: String,
        sessionId: String,
        rating: ReviewRating,
        durationMs: Long?,
    ): AppResult<ReviewSubmitResult> {
        val progress = when (val result = reviewRepository.getProgress(wordId)) {
            is AppResult.Success -> result.data
            is AppResult.Failure -> return result
        }
        val now = timeProvider.nowMillis()
        val newState = spacedRepetition.review(progress.reviewState, rating.quality, now)
        return reviewRepository.submitReview(
            ReviewSubmission(
                wordId = wordId,
                sessionId = sessionId,
                quality = rating.quality,
                reviewedAt = now,
                durationMs = durationMs?.coerceAtLeast(0),
                previousIntervalDays = progress.intervalDays,
                newState = newState,
            ),
        )
    }

    /** Days until the next review for each button, shown as a hint under the buttons. */
    fun previewIntervals(progress: com.rewordly.app.domain.model.WordProgress): Map<ReviewRating, Int> {
        val now = timeProvider.nowMillis()
        return ReviewRating.entries.associateWith {
            spacedRepetition.review(progress.reviewState, it.quality, now).intervalDays
        }
    }
}

/**
 * Puts a word the user decided to study into the rotation. The word stays in the study session, but its
 * first review is already scheduled for tomorrow.
 */
class StartStudyingWordUseCase @Inject constructor(
    private val reviewRepository: ReviewRepository,
    private val spacedRepetition: SpacedRepetitionService,
    private val timeProvider: TimeProvider,
) {
    suspend operator fun invoke(wordId: String, sessionId: String): AppResult<Unit> =
        applyWordState(reviewRepository, timeProvider, wordId, sessionId, spacedRepetition::scheduleFirstReview)
}

/**
 * Takes a word the user says they already know out of the study session for good, while still scheduling
 * a review tomorrow: a wrong claim is caught there and puts the word back into the rotation.
 */
class MarkWordKnownUseCase @Inject constructor(
    private val reviewRepository: ReviewRepository,
    private val spacedRepetition: SpacedRepetitionService,
    private val timeProvider: TimeProvider,
) {
    suspend operator fun invoke(wordId: String, sessionId: String): AppResult<Unit> =
        applyWordState(reviewRepository, timeProvider, wordId, sessionId, spacedRepetition::scheduleKnownWord)
}

class UnmarkWordLearnedUseCase @Inject constructor(
    private val reviewRepository: ReviewRepository,
) {
    suspend operator fun invoke(wordId: String): AppResult<Unit> = reviewRepository.markNotLearned(wordId)
}

/** Shared plumbing of the two "the user decided something about a brand new word" use cases. */
private suspend fun applyWordState(
    reviewRepository: ReviewRepository,
    timeProvider: TimeProvider,
    wordId: String,
    sessionId: String,
    transform: (ReviewState, Long) -> ReviewState,
): AppResult<Unit> {
    val progress = when (val result = reviewRepository.getProgress(wordId)) {
        is AppResult.Success -> result.data
        is AppResult.Failure -> return result
    }
    val now = timeProvider.nowMillis()
    return reviewRepository.markLearned(
        wordId = wordId,
        sessionId = sessionId,
        state = transform(progress.reviewState, now),
        at = now,
    )
}

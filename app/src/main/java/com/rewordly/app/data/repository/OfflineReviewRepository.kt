package com.rewordly.app.data.repository

import com.rewordly.app.core.common.AppResult
import com.rewordly.app.core.common.TimeProvider
import com.rewordly.app.core.database.dao.ReviewLogDao
import com.rewordly.app.core.database.dao.WordProgressDao
import com.rewordly.app.core.database.entity.ReviewLogEntity
import com.rewordly.app.core.database.safeDbCall
import com.rewordly.app.data.local.toDomain
import com.rewordly.app.data.local.toEntity
import com.rewordly.app.domain.model.ReviewKind
import com.rewordly.app.domain.model.ReviewState
import com.rewordly.app.domain.model.ReviewSubmission
import com.rewordly.app.domain.model.ReviewSubmitResult
import com.rewordly.app.domain.model.WordProgress
import com.rewordly.app.domain.model.WordStatus
import com.rewordly.app.domain.repository.ReviewRepository
import com.rewordly.app.domain.usecase.ReviewQueueBuilder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineReviewRepository @Inject constructor(
    private val progressDao: WordProgressDao,
    private val reviewLogDao: ReviewLogDao,
    private val timeProvider: TimeProvider,
) : ReviewRepository {

    override suspend fun getProgress(wordId: String): AppResult<WordProgress> = safeDbCall { progress(wordId) }

    override suspend fun submitReview(submission: ReviewSubmission): AppResult<ReviewSubmitResult> = safeDbCall {
        val current = progress(submission.wordId)
        val correct = submission.quality.isCorrect
        val updated = current.withReviewState(submission.newState).copy(
            correctAnswers = current.correctAnswers + if (correct) 1 else 0,
            incorrectAnswers = current.incorrectAnswers + if (correct) 0 else 1,
        )
        val log = ReviewLogEntity(
            wordId = submission.wordId,
            sessionId = submission.sessionId,
            kind = ReviewKind.REVIEW.name,
            quality = submission.quality.value,
            reviewedAt = submission.reviewedAt,
            durationMs = submission.durationMs,
            intervalBefore = submission.previousIntervalDays,
            intervalAfter = submission.newState.intervalDays,
        )
        val applied = reviewLogDao.record(log, updated.toEntity(updatedAt = timeProvider.nowMillis()))
        if (applied) ReviewSubmitResult.Applied(submission.newState) else ReviewSubmitResult.Duplicate
    }

    override suspend fun markLearned(wordId: String, sessionId: String, state: ReviewState, at: Long): AppResult<Unit> =
        safeDbCall {
            val current = progress(wordId)
            val log = ReviewLogEntity(
                wordId = wordId,
                sessionId = sessionId,
                kind = ReviewKind.LEARN.name,
                quality = LEARN_QUALITY,
                reviewedAt = at,
                durationMs = null,
                intervalBefore = current.intervalDays,
                intervalAfter = state.intervalDays,
            )
            // A repeated mark inside one session keeps the log single but still restores the schedule.
            if (!reviewLogDao.record(log, current.withReviewState(state).toEntity(timeProvider.nowMillis()))) {
                progressDao.upsert(current.withReviewState(state).toEntity(timeProvider.nowMillis()))
            }
        }

    override suspend fun markNotLearned(wordId: String): AppResult<Unit> = safeDbCall {
        val reset = progress(wordId).withReviewState(
            ReviewState(wordId = wordId, learningStatus = WordStatus.NEW),
        )
        progressDao.upsert(reset.toEntity(timeProvider.nowMillis()))
    }

    override suspend fun countDue(): AppResult<Int> = safeDbCall {
        progressDao.countDue(
            endOfTodayMillis = timeProvider.startOfTomorrowMillis(),
            invalidAfterMillis = timeProvider.nowMillis() + ReviewQueueBuilder.MAX_VALID_FUTURE_MILLIS,
        )
    }

    private suspend fun progress(wordId: String): WordProgress =
        progressDao.get(wordId)?.toDomain() ?: WordProgress(wordId = wordId)

    private companion object {
        const val LEARN_QUALITY = -1
    }
}

package com.rewordly.app.domain.repository

import com.rewordly.app.core.common.AppResult
import com.rewordly.app.domain.model.ReviewState
import com.rewordly.app.domain.model.ReviewSubmission
import com.rewordly.app.domain.model.ReviewSubmitResult
import com.rewordly.app.domain.model.WordProgress

/** Persists review answers and the spaced-repetition schedule. */
interface ReviewRepository {
    suspend fun getProgress(wordId: String): AppResult<WordProgress>

    /**
     * Stores the answer and the new schedule atomically. A second submission for the same word in the same
     * session is reported as [ReviewSubmitResult.Duplicate] and changes nothing.
     */
    suspend fun submitReview(submission: ReviewSubmission): AppResult<ReviewSubmitResult>

    /** Marks the word learned with its first scheduled review. Safe to repeat inside one session. */
    suspend fun markLearned(wordId: String, sessionId: String, state: ReviewState, at: Long): AppResult<Unit>

    /** Takes the word back to NEW and clears its schedule. */
    suspend fun markNotLearned(wordId: String): AppResult<Unit>

    /** Number of words due before the end of the current local day. */
    suspend fun countDue(): AppResult<Int>
}

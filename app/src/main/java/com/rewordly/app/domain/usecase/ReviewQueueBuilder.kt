package com.rewordly.app.domain.usecase

import com.rewordly.app.domain.model.WordProgress

object ReviewQueueBuilder {
    /** Anything scheduled further than this from now is treated as a corrupted timestamp. */
    const val MAX_VALID_FUTURE_MILLIS = 10L * 366 * 24 * 60 * 60 * 1000

    /**
     * The timestamp a word is really scheduled for. Missing or corrupted schedules count as "due now"
     * so a word can never get stuck unreviewable. Shared with the recommendation engine so both agree
     * on what "due" means.
     */
    fun effectiveSchedule(progress: WordProgress, now: Long): Long {
        val next = progress.nextReviewAt
        return if (next == null || next < 0 || next > now + MAX_VALID_FUTURE_MILLIS) now else next
    }
}

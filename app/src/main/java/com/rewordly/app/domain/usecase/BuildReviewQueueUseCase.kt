package com.rewordly.app.domain.usecase

import com.rewordly.app.core.common.AppResult
import com.rewordly.app.core.common.TimeProvider
import com.rewordly.app.core.database.safeDbCall
import com.rewordly.app.domain.model.LearningLanguage
import com.rewordly.app.domain.model.ReviewQueue
import com.rewordly.app.domain.model.WordProgress
import com.rewordly.app.domain.model.WordStatus
import com.rewordly.app.domain.model.WordWithProgress
import com.rewordly.app.domain.repository.VocabularyRepository
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** Builds today's review queue from the local vocabulary. See [ReviewQueueBuilder] for the ordering rules. */
class BuildReviewQueueUseCase @Inject constructor(
    private val vocabularyRepository: VocabularyRepository,
    private val timeProvider: TimeProvider,
) {
    suspend operator fun invoke(language: LearningLanguage, limit: Int = DEFAULT_SESSION_SIZE): AppResult<ReviewQueue> =
        safeDbCall {
            val words = vocabularyRepository.observeWords(language).first()
            ReviewQueueBuilder.build(words, timeProvider.nowMillis(), timeProvider.zone(), limit)
        }

    companion object {
        /** Large backlogs are served in batches so a session stays finishable. */
        const val DEFAULT_SESSION_SIZE = 30
    }
}

/**
 * Pure queue generation, deterministic for the same words and timestamp.
 *
 * Order: 1. overdue (due before today), 2. due today, 3. recently forgotten words that are not yet due.
 * Inside a tier the earliest schedule comes first, ties are broken by word id. Each word appears once.
 */
object ReviewQueueBuilder {
    /** Anything scheduled further than this from now is treated as a corrupted timestamp. */
    const val MAX_VALID_FUTURE_MILLIS = 10L * 366 * 24 * 60 * 60 * 1000
    const val RECENTLY_FORGOTTEN_WINDOW_MILLIS = 24L * 60 * 60 * 1000

    fun build(words: List<WordWithProgress>, now: Long, zone: ZoneId, limit: Int): ReviewQueue {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val startOfToday = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val startOfTomorrow = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val overdue = mutableListOf<Pair<Long, WordWithProgress>>()
        val dueToday = mutableListOf<Pair<Long, WordWithProgress>>()
        val forgotten = mutableListOf<WordWithProgress>()
        for (item in words) {
            val progress = item.progress
            if (progress.status == WordStatus.NEW) continue
            val scheduled = effectiveSchedule(progress, now)
            when {
                scheduled < startOfToday -> overdue += scheduled to item
                scheduled < startOfTomorrow -> dueToday += scheduled to item
                isRecentlyForgotten(progress, now) -> forgotten += item
            }
        }
        val byScheduleThenId = compareBy<Pair<Long, WordWithProgress>>({ it.first }, { it.second.word.id })
        val ordered = overdue.sortedWith(byScheduleThenId).map { it.second } +
            dueToday.sortedWith(byScheduleThenId).map { it.second } +
            forgotten.sortedWith(
                compareByDescending<WordWithProgress> { it.progress.lastReviewedAt ?: 0L }.thenBy { it.word.id },
            )
        return ReviewQueue(items = ordered.take(limit.coerceAtLeast(0)), totalDue = overdue.size + dueToday.size)
    }

    /** True when the word belongs to today's workload (overdue or due before tomorrow starts). */
    fun isDue(progress: WordProgress, now: Long, startOfTomorrow: Long): Boolean =
        progress.status != WordStatus.NEW && effectiveSchedule(progress, now) < startOfTomorrow

    /**
     * The timestamp a word is really scheduled for. Missing or corrupted schedules count as "due now"
     * so a word can never get stuck unreviewable. Shared with the recommendation engine so both agree
     * on what "due" means.
     */
    fun effectiveSchedule(progress: WordProgress, now: Long): Long {
        val next = progress.nextReviewAt
        return if (next == null || next < 0 || next > now + MAX_VALID_FUTURE_MILLIS) now else next
    }

    private fun isRecentlyForgotten(progress: WordProgress, now: Long): Boolean {
        val last = progress.lastReviewedAt ?: return false
        return progress.consecutiveIncorrect > 0 && last <= now && now - last <= RECENTLY_FORGOTTEN_WINDOW_MILLIS
    }
}

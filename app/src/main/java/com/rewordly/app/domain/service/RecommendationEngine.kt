package com.rewordly.app.domain.service

import com.rewordly.app.domain.model.DailyLearningPlan
import com.rewordly.app.domain.model.Difficulty
import com.rewordly.app.domain.model.LearningProfile
import com.rewordly.app.domain.model.LearningRules
import com.rewordly.app.domain.model.Recommendation
import com.rewordly.app.domain.model.RecommendationReason
import com.rewordly.app.domain.model.WordStatus
import com.rewordly.app.domain.model.WordWithProgress
import com.rewordly.app.domain.model.isWeak
import com.rewordly.app.domain.model.weakReason
import com.rewordly.app.domain.usecase.ReviewQueueBuilder
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.ceil

/** Everything the engine needs. All of it already exists in the local database; nothing is uploaded. */
data class RecommendationInput(
    val words: List<WordWithProgress>,
    val profile: LearningProfile,
    val now: Long,
    val zone: ZoneId,
    /** Words already learned today, so the daily target is respected across several sessions. */
    val learnedToday: Int = 0,
)

/**
 * Local, rules-based recommendation engine. No AI, no randomness and no hidden state: the result
 * depends only on its arguments, so the same input always produces the same plan.
 *
 * The plan is built in a fixed priority order:
 * 1. overdue reviews, 2. reviews due today, 3. a few words the user keeps failing,
 * 4. new words that fit the estimated level and the chosen interests.
 *
 * The engine only *selects* words. Every interval is still produced by [SpacedRepetitionService],
 * so recommendations can never bypass or duplicate the review schedule.
 */
class RecommendationEngine @Inject constructor() {

    fun recommend(input: RecommendationInput): DailyLearningPlan {
        val zone = input.zone
        val today = Instant.ofEpochMilli(input.now).atZone(zone).toLocalDate()
        val startOfToday = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val startOfTomorrow = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val overdue = mutableListOf<Pair<Long, WordWithProgress>>()
        val dueToday = mutableListOf<Pair<Long, WordWithProgress>>()
        val weak = mutableListOf<WordWithProgress>()
        val fresh = mutableListOf<WordWithProgress>()

        for (item in input.words) {
            val progress = item.progress
            if (progress.status == WordStatus.NEW) {
                fresh += item
                continue
            }
            val scheduled = ReviewQueueBuilder.effectiveSchedule(progress, input.now)
            when {
                scheduled < startOfToday -> overdue += scheduled to item
                scheduled < startOfTomorrow -> dueToday += scheduled to item
                progress.isWeak -> weak += item
            }
        }

        val reviews = buildReviewQueue(overdue, dueToday, weak)
        val sessionLength = input.profile.sessionLength.coerceAtLeast(1)
        val cappedReviews = reviews.take(sessionLength)

        val struggling = weak.size >= LearningRules.MANY_WEAK_WORDS
        val target = (input.profile.dailyNewWordTarget - input.learnedToday).coerceAtLeast(0)
        val easedTarget = if (struggling) ceil(target * STRUGGLE_FACTOR).toInt() else target
        val freeSlots = (sessionLength - cappedReviews.size).coerceAtLeast(0)
        val budget = minOf(easedTarget, freeSlots)

        return DailyLearningPlan(
            reviews = cappedReviews,
            newWords = rankNewWords(fresh, input.profile).take(budget),
            totalDue = overdue.size + dueToday.size,
            newWordBudget = budget,
            sessionLength = sessionLength,
            easedOff = struggling && target > 0,
        )
    }

    /** Overdue first, then due today, then the most-missed words that are not due yet. */
    private fun buildReviewQueue(
        overdue: List<Pair<Long, WordWithProgress>>,
        dueToday: List<Pair<Long, WordWithProgress>>,
        weak: List<WordWithProgress>,
    ): List<Recommendation> {
        val bySchedule = compareBy<Pair<Long, WordWithProgress>>({ it.first }, { it.second.word.id })
        val due = RecommendationReason.DUE_FOR_REVIEW
        return buildList {
            addAll(overdue.sortedWith(bySchedule).map { Recommendation(it.second, due) })
            addAll(dueToday.sortedWith(bySchedule).map { Recommendation(it.second, due) })
            addAll(
                weak.sortedWith(WEAK_ORDER).take(LearningRules.MAX_WEAK_WORDS)
                    .map { Recommendation(it, it.progress.weakReason) },
            )
        }.distinctBy { it.word.id }
    }

    private fun rankNewWords(fresh: List<WordWithProgress>, profile: LearningProfile): List<Recommendation> =
        fresh.mapNotNull { item ->
            val distance = levelDistance(item.word.difficulty, profile.level) ?: return@mapNotNull null
            RankedNewWord(item, distance, TopicTaxonomy.matches(item.word, profile.interests))
        }.sortedWith(
            compareBy<RankedNewWord>(
                { it.absoluteDistance },
                { if (it.matchesInterests) 0 else 1 },
                { it.item.word.id },
            ),
        ).map { Recommendation(it.item, newWordReason(it, profile.level)) }

    private fun newWordReason(ranked: RankedNewWord, level: Difficulty?): RecommendationReason = when {
        ranked.matchesInterests -> RecommendationReason.MATCHES_INTERESTS
        level != null -> RecommendationReason.MATCHES_LEVEL
        else -> RecommendationReason.NEW_FOR_YOU
    }

    private data class RankedNewWord(
        val item: WordWithProgress,
        val distance: Int,
        val matchesInterests: Boolean,
    ) {
        val absoluteDistance: Int get() = abs(distance)
    }

    companion object {
        /** How much of the remaining new-word target survives when the user is struggling. */
        const val STRUGGLE_FACTOR = 0.5

        /** How far above the estimated level a new word may be before it is held back. */
        const val MAX_LEVEL_DISTANCE = 1

        private val WEAK_ORDER = compareByDescending<WordWithProgress> { it.progress.incorrectAnswers }
            .thenBy { it.word.id }

        /**
         * Signed distance from the estimated level, or null when the word is too hard to introduce now.
         * Without a level every word is equally distant, so interests and order decide.
         */
        fun levelDistance(difficulty: Difficulty, level: Difficulty?): Int? {
            if (level == null) return 0
            val distance = difficulty.ordinal - level.ordinal
            return if (distance > MAX_LEVEL_DISTANCE) null else distance
        }
    }
}

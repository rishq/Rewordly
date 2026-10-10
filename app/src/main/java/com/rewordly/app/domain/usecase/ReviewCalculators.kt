package com.rewordly.app.domain.usecase

import com.rewordly.app.domain.model.AnswerQuality
import com.rewordly.app.domain.model.DailyActivity
import com.rewordly.app.domain.model.ReviewDay
import com.rewordly.app.domain.model.ReviewKind
import com.rewordly.app.domain.model.ReviewLogEntry
import com.rewordly.app.domain.model.ReviewStatistics
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Groups the UTC-timestamped review log into the user's local calendar days.
 * The zone is an argument, so a time zone change simply regroups the same data.
 */
object ReviewHistoryCalculator {
    /** Entries with a negative timestamp are corrupted and ignored. */
    fun byDay(entries: List<ReviewLogEntry>, zone: ZoneId): List<ReviewDay> = entries
        .filter { it.reviewedAt >= 0 }
        .groupBy { localDate(it.reviewedAt, zone) }
        .map { (date, items) ->
            val reviews = items.filter { it.kind == ReviewKind.REVIEW }
            val durations = reviews.mapNotNull { it.durationMs }
            ReviewDay(
                date = date,
                reviewedWords = reviews.map { it.wordId }.distinct().size,
                reviews = reviews.size,
                correct = reviews.count { AnswerQuality.fromValue(it.quality)?.isCorrect == true },
                incorrect = reviews.count { AnswerQuality.fromValue(it.quality)?.isCorrect == false },
                wordsLearned = items.count { it.kind == ReviewKind.LEARN },
                studyTimeMs = durations.takeIf { it.isNotEmpty() }?.sum(),
            )
        }
        .sortedByDescending { it.date }

    /**
     * Daily activity for streak and goal. Legacy rows (written before the review log existed) fill days the
     * log knows nothing about; where both exist the larger counter wins so nothing is counted twice.
     */
    fun activity(entries: List<ReviewLogEntry>, legacy: List<DailyActivity>, zone: ZoneId): List<DailyActivity> =
        activity(byDay(entries, zone), legacy)

    /**
     * Same merge as [activity], for callers that already grouped the log and must not pay to group it twice.
     * The day grouping is the expensive half (filter + groupBy + per-group scan + sort), so it is passed in.
     */
    fun activity(days: List<ReviewDay>, legacy: List<DailyActivity>): List<DailyActivity> {
        val fromLog = days.associate {
            it.date to DailyActivity(it.date, wordsLearned = it.wordsLearned, wordsReviewed = it.reviews)
        }
        val merged = (legacy.associateBy { it.date }).toMutableMap()
        fromLog.forEach { (date, log) ->
            val old = merged[date]
            merged[date] = if (old == null) {
                log
            } else {
                DailyActivity(
                    date,
                    wordsLearned = maxOf(old.wordsLearned, log.wordsLearned),
                    wordsReviewed = maxOf(old.wordsReviewed, log.wordsReviewed),
                )
            }
        }
        return merged.values.sortedBy { it.date }
    }

    fun localDate(epochMillis: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(
        epochMillis,
    ).atZone(zone).toLocalDate()
}

/** Totals of the whole review log, aggregated by the database. */
data class ReviewTotals(val total: Int, val correct: Int, val qualitySum: Int)

object ReviewStatisticsCalculator {
    fun calculate(
        totals: ReviewTotals,
        wordsRemembered: Int,
        wordsForgotten: Int,
        wordsReviewedToday: Int,
        wordsStillDue: Int,
    ): ReviewStatistics {
        val workload = wordsReviewedToday + wordsStillDue
        return ReviewStatistics(
            totalReviews = totals.total,
            correctAnswers = totals.correct,
            incorrectAnswers = (totals.total - totals.correct).coerceAtLeast(0),
            wordsRemembered = wordsRemembered,
            wordsForgotten = wordsForgotten,
            averageDifficulty = if (totals.total > 0) 5.0 - totals.qualitySum.toDouble() / totals.total else null,
            completionRate = if (workload > 0) wordsReviewedToday.toFloat() / workload else null,
        )
    }
}

/** One cell of the activity calendar; [level] is 0 (nothing) to 4 (busiest). */
data class CalendarCell(val date: LocalDate, val activity: Int, val level: Int, val isFuture: Boolean)

object ActivityCalendarBuilder {
    const val LEVELS = 4

    /** Columns are weeks (oldest first), rows are weekdays starting at [firstDayOfWeek]. */
    fun build(
        activity: Map<LocalDate, Int>,
        today: LocalDate,
        weeks: Int,
        firstDayOfWeek: java.time.DayOfWeek,
    ): List<List<CalendarCell>> {
        val offset = (today.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
        val start = today.minusDays(offset.toLong()).minusWeeks((weeks - 1).toLong())
        val max = activity.values.maxOrNull() ?: 0
        return (0 until weeks).map { week ->
            (0 until 7).map { day ->
                val date = start.plusWeeks(week.toLong()).plusDays(day.toLong())
                val count = if (date.isAfter(today)) 0 else activity[date] ?: 0
                CalendarCell(date, count, level(count, max), isFuture = date.isAfter(today))
            }
        }
    }

    /** Relative bucket so the heatmap stays readable for both light and heavy learners. */
    fun level(count: Int, max: Int): Int = when {
        count <= 0 || max <= 0 -> 0
        else -> ((count.toDouble() / max) * LEVELS).let { kotlin.math.ceil(it).toInt() }.coerceIn(1, LEVELS)
    }
}

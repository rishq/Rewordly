package com.rewordly.app.domain.usecase

import com.rewordly.app.domain.model.DailyActivity
import com.rewordly.app.domain.model.ReviewKind
import com.rewordly.app.domain.model.ReviewLogEntry
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewCalculatorsTest {
    private val utc = ZoneId.of("UTC")
    private val almaty = ZoneId.of("Asia/Almaty") // UTC+5
    private val newYork = ZoneId.of("America/New_York")

    private fun at(zone: ZoneId, y: Int, m: Int, d: Int, h: Int, min: Int = 0): Long =
        ZonedDateTime.of(y, m, d, h, min, 0, 0, zone).toInstant().toEpochMilli()

    private fun review(word: String, time: Long, quality: Int, duration: Long? = null) =
        ReviewLogEntry(word, ReviewKind.REVIEW, quality, time, duration)

    private fun learn(word: String, time: Long) = ReviewLogEntry(word, ReviewKind.LEARN, -1, time, null)

    // ---- daily statistics ----

    @Test
    fun byDay_countsDistinctWordsCorrectAndIncorrectAnswers() {
        val log = listOf(
            review("a", at(utc, 2024, 5, 10, 9), quality = 4, duration = 4_000),
            review("b", at(utc, 2024, 5, 10, 10), quality = 0, duration = 6_000),
            review("a", at(utc, 2024, 5, 10, 11), quality = 5, duration = 2_000),
            learn("c", at(utc, 2024, 5, 10, 12)),
        )
        val day = ReviewHistoryCalculator.byDay(log, utc).single()
        assertEquals(LocalDate.of(2024, 5, 10), day.date)
        assertEquals(2, day.reviewedWords)
        assertEquals(3, day.reviews)
        assertEquals(2, day.correct)
        assertEquals(1, day.incorrect)
        assertEquals(1, day.wordsLearned)
        assertEquals(12_000L, day.studyTimeMs)
    }

    @Test
    fun byDay_sortsNewestFirst_andReportsMissingStudyTimeAsNull() {
        val log = listOf(
            review("a", at(utc, 2024, 5, 8, 9), 4),
            review("a", at(utc, 2024, 5, 10, 9), 4),
        )
        val days = ReviewHistoryCalculator.byDay(log, utc)
        assertEquals(listOf(LocalDate.of(2024, 5, 10), LocalDate.of(2024, 5, 8)), days.map { it.date })
        assertNull(days.first().studyTimeMs)
    }

    @Test
    fun byDay_ignoresCorruptedNegativeTimestamps() {
        val log = listOf(review("a", -1_000, 4), review("b", at(utc, 2024, 5, 10, 9), 4))
        assertEquals(1, ReviewHistoryCalculator.byDay(log, utc).size)
    }

    // ---- time zone boundaries ----

    @Test
    fun sameInstant_landsOnDifferentLocalDaysInDifferentZones() {
        // 22:30 UTC on May 10 is already May 11, 03:30 in Almaty and still May 10, 18:30 in New York.
        val instant = at(utc, 2024, 5, 10, 22, 30)
        val log = listOf(review("a", instant, 4))
        assertEquals(LocalDate.of(2024, 5, 10), ReviewHistoryCalculator.byDay(log, utc).single().date)
        assertEquals(LocalDate.of(2024, 5, 11), ReviewHistoryCalculator.byDay(log, almaty).single().date)
        assertEquals(LocalDate.of(2024, 5, 10), ReviewHistoryCalculator.byDay(log, newYork).single().date)
    }

    @Test
    fun localMidnight_splitsTwoAnswersOneMinuteApart() {
        val before = at(almaty, 2024, 5, 10, 23, 59)
        val after = at(almaty, 2024, 5, 11, 0, 0)
        val days = ReviewHistoryCalculator.byDay(listOf(review("a", before, 4), review("b", after, 4)), almaty)
        assertEquals(2, days.size)
        assertEquals(setOf(LocalDate.of(2024, 5, 10), LocalDate.of(2024, 5, 11)), days.map { it.date }.toSet())
    }

    @Test
    fun changingTheZone_regroupsTheSameStoredTimestamps() {
        val log = listOf(
            review("a", at(utc, 2024, 5, 10, 18, 30), 4),
            review("b", at(utc, 2024, 5, 10, 19, 30), 4),
        )
        assertEquals(1, ReviewHistoryCalculator.byDay(log, utc).size)
        assertEquals(2, ReviewHistoryCalculator.byDay(log, almaty).size)
    }

    @Test
    fun dstChangeDay_stillGroupsIntoOneLocalDay() {
        // US clocks jump forward on 2024-03-10: that local day is only 23 hours long.
        val log = listOf(
            review("a", at(newYork, 2024, 3, 10, 0, 30), 4),
            review("b", at(newYork, 2024, 3, 10, 23, 30), 4),
            review("c", at(newYork, 2024, 3, 11, 0, 30), 4),
        )
        val days = ReviewHistoryCalculator.byDay(log, newYork)
        assertEquals(2, days.size)
        assertEquals(2, days.first { it.date == LocalDate.of(2024, 3, 10) }.reviews)
    }

    // ---- streaks built from the log ----

    @Test
    fun streak_followsTheLocalCalendar_acrossAZoneBoundary() {
        val today = LocalDate.of(2024, 5, 11)
        // Three consecutive late-evening UTC sessions: in Almaty they fall on May 10, 11 (twice -> 11, 12).
        val log = listOf(
            // Almaty: May 10, 01:00
            review("a", at(utc, 2024, 5, 9, 20), 4),
            // Almaty: May 11, 01:00
            review("a", at(utc, 2024, 5, 10, 20), 4),
        )
        val activity = ReviewHistoryCalculator.activity(log, emptyList(), almaty)
        assertEquals(2, ComputeStreakUseCase.compute(activity, today).current)
        val inUtc = ReviewHistoryCalculator.activity(log, emptyList(), utc)
        assertEquals(setOf(LocalDate.of(2024, 5, 9), LocalDate.of(2024, 5, 10)), inUtc.map { it.date }.toSet())
    }

    @Test
    fun streak_breaksAfterAMissedDay_andSurvivesWhenOnlyTodayIsEmpty() {
        val log = listOf(
            review("a", at(utc, 2024, 5, 7, 9), 4),
            review("a", at(utc, 2024, 5, 9, 9), 4),
            review("a", at(utc, 2024, 5, 10, 9), 4),
        )
        val activity = ReviewHistoryCalculator.activity(log, emptyList(), utc)
        assertEquals(2, ComputeStreakUseCase.compute(activity, LocalDate.of(2024, 5, 10)).current)
        assertEquals(2, ComputeStreakUseCase.compute(activity, LocalDate.of(2024, 5, 11)).current)
        assertEquals(0, ComputeStreakUseCase.compute(activity, LocalDate.of(2024, 5, 12)).current)
    }

    @Test
    fun activity_mergesLegacyRowsWithoutDoubleCounting() {
        val legacy = listOf(
            DailyActivity(LocalDate.of(2024, 5, 9), wordsLearned = 3, wordsReviewed = 1),
            DailyActivity(LocalDate.of(2024, 5, 10), wordsLearned = 1, wordsReviewed = 0),
        )
        val log = listOf(
            learn("a", at(utc, 2024, 5, 10, 8)),
            review("a", at(utc, 2024, 5, 10, 9), 4),
        )
        val merged = ReviewHistoryCalculator.activity(log, legacy, utc).associateBy { it.date }
        assertEquals(3, merged.getValue(LocalDate.of(2024, 5, 9)).wordsLearned)
        assertEquals(1, merged.getValue(LocalDate.of(2024, 5, 10)).wordsLearned)
        assertEquals(1, merged.getValue(LocalDate.of(2024, 5, 10)).wordsReviewed)
    }

    // ---- statistics ----

    @Test
    fun statistics_areDerivedFromRealTotals() {
        val stats = ReviewStatisticsCalculator.calculate(
            totals = ReviewTotals(total = 10, correct = 7, qualitySum = 38),
            wordsRemembered = 5,
            wordsForgotten = 2,
            wordsReviewedToday = 3,
            wordsStillDue = 1,
        )
        assertEquals(10, stats.totalReviews)
        assertEquals(7, stats.correctAnswers)
        assertEquals(3, stats.incorrectAnswers)
        assertEquals(5, stats.wordsRemembered)
        assertEquals(2, stats.wordsForgotten)
        assertEquals(5.0 - 3.8, stats.averageDifficulty!!, 1e-9)
        assertEquals(0.75f, stats.completionRate!!, 1e-6f)
    }

    @Test
    fun statistics_haveNoAverageOrCompletionWithoutData() {
        val stats = ReviewStatisticsCalculator.calculate(ReviewTotals(0, 0, 0), 0, 0, 0, 0)
        assertNull(stats.averageDifficulty)
        assertNull(stats.completionRate)
        assertEquals(0, stats.incorrectAnswers)
    }

    // ---- activity calendar ----

    @Test
    fun calendar_hasTheRequestedWeeks_startsOnTheChosenWeekday_andHidesTheFuture() {
        val today = LocalDate.of(2024, 5, 10) // Friday
        val weeks = ActivityCalendarBuilder.build(emptyMap(), today, weeks = 4, firstDayOfWeek = DayOfWeek.MONDAY)
        assertEquals(4, weeks.size)
        assertTrue(weeks.all { it.size == 7 })
        assertTrue(weeks.all { it.first().date.dayOfWeek == DayOfWeek.MONDAY })
        assertEquals(today, weeks.last()[4].date)
        assertFalse(weeks.last()[4].isFuture)
        assertTrue(weeks.last()[5].isFuture)
        assertEquals(0, weeks.last()[5].level)
    }

    @Test
    fun calendar_levelsScaleWithTheBusiestDay() {
        val today = LocalDate.of(2024, 5, 10)
        val activity = mapOf(today to 20, today.minusDays(1) to 10, today.minusDays(2) to 1)
        val cells = ActivityCalendarBuilder.build(
            activity,
            today,
            2,
            DayOfWeek.MONDAY,
        ).flatten().associateBy { it.date }
        assertEquals(ActivityCalendarBuilder.LEVELS, cells.getValue(today).level)
        assertEquals(2, cells.getValue(today.minusDays(1)).level)
        assertEquals(1, cells.getValue(today.minusDays(2)).level)
        assertEquals(0, cells.getValue(today.minusDays(3)).level)
    }
}

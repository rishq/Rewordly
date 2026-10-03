package com.rewordly.app.domain.usecase

import com.rewordly.app.core.common.AppResult
import com.rewordly.app.data.local.MockVocabulary
import com.rewordly.app.domain.model.LearningLanguage
import com.rewordly.app.domain.model.WordProgress
import com.rewordly.app.domain.model.WordStatus
import com.rewordly.app.domain.model.WordWithProgress
import com.rewordly.app.testing.FakeVocabularyRepository
import com.rewordly.app.testing.TestTimeProvider
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewQueueBuilderTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2024, 5, 10)
    private val startOfToday = today.atStartOfDay(zone).toInstant().toEpochMilli()
    private val now = startOfToday + 10 * HOUR
    private val startOfTomorrow = startOfToday + 24 * HOUR

    private fun item(
        index: Int,
        status: WordStatus = WordStatus.LEARNED,
        next: Long? = null,
        lastReviewed: Long? = null,
        incorrect: Int = 0,
    ): WordWithProgress {
        val word = MockVocabulary.words[index]
        return WordWithProgress(
            word,
            WordProgress(
                wordId = word.id,
                status = status,
                nextReviewAt = next,
                lastReviewedAt = lastReviewed,
                consecutiveIncorrect = incorrect,
            ),
        )
    }

    private fun ids(queue: List<WordWithProgress>) = queue.map { it.word.id }

    private fun build(words: List<WordWithProgress>, limit: Int = 50) = ReviewQueueBuilder.build(
        words,
        now,
        zone,
        limit,
    )

    @Test
    fun overdueWords_comeBeforeWordsDueToday_whichComeBeforeRecentlyForgotten() {
        val overdue = item(0, next = startOfToday - 3 * DAY)
        val dueToday = item(1, next = startOfToday + 2 * HOUR)
        val forgotten = item(2, next = startOfTomorrow + 5 * HOUR, lastReviewed = now - HOUR, incorrect = 1)
        val queue = build(listOf(forgotten, dueToday, overdue))
        assertEquals(listOf(overdue, dueToday, forgotten), queue.items)
        assertEquals(2, queue.totalDue)
    }

    @Test
    fun insideATier_theEarliestScheduleWins_andTiesUseTheWordId() {
        val older = item(0, next = startOfToday - 5 * DAY)
        val newer = item(1, next = startOfToday - DAY)
        val tieB = item(3, next = startOfToday + HOUR)
        val tieA = item(2, next = startOfToday + HOUR)
        val sortedTies = listOf(tieA, tieB).sortedBy { it.word.id }
        val queue = build(listOf(newer, tieB, tieA, older))
        assertEquals(listOf(older, newer) + sortedTies, queue.items)
    }

    @Test
    fun newWords_andWordsDueInTheFuture_areNotQueued() {
        val new = item(0, status = WordStatus.NEW)
        val future = item(1, next = startOfTomorrow + HOUR)
        assertTrue(build(listOf(new, future)).items.isEmpty())
    }

    @Test
    fun wordDueLaterToday_isIncluded_butTomorrowMidnightIsNot() {
        val laterToday = item(0, next = startOfTomorrow - 1)
        val atMidnight = item(1, next = startOfTomorrow)
        assertEquals(listOf(laterToday), build(listOf(laterToday, atMidnight)).items)
    }

    @Test
    fun forgottenWord_isOnlyRecent_andNeedsAFailedLastAnswer() {
        val recent = item(0, next = startOfTomorrow + HOUR, lastReviewed = now - 2 * HOUR, incorrect = 2)
        val stale = item(1, next = startOfTomorrow + HOUR, lastReviewed = now - 3 * DAY, incorrect = 1)
        val passed = item(2, next = startOfTomorrow + HOUR, lastReviewed = now - HOUR, incorrect = 0)
        assertEquals(listOf(recent), build(listOf(recent, stale, passed)).items)
    }

    @Test
    fun missingOrCorruptedSchedules_countAsDueNow() {
        val noSchedule = item(0, next = null)
        val negative = item(1, next = -5)
        val absurd = item(2, next = now + 100L * 365 * DAY)
        val queue = build(listOf(noSchedule, negative, absurd))
        assertEquals(3, queue.items.size)
        assertEquals(3, queue.totalDue)
    }

    @Test
    fun eachWordAppearsOnce_andTheQueueIsDeterministic() {
        val words = MockVocabulary.words.indices.take(12).map { item(it, next = startOfToday - (it % 4) * DAY) }
        val first = build(words)
        val second = build(words.shuffled(java.util.Random(7)))
        assertEquals(ids(first.items), ids(second.items))
        assertEquals(first.items.size, ids(first.items).distinct().size)
    }

    @Test
    fun veryLargeBacklogs_areCappedPerSession_butTotalDueStaysHonest() {
        val words = MockVocabulary.words.indices.map { item(it, next = startOfToday - DAY) }
        val queue = build(words, limit = 5)
        assertEquals(5, queue.items.size)
        assertEquals(words.size, queue.totalDue)
    }

    @Test
    fun dueBoundary_followsTheLocalDayNotUtc() {
        val almaty = ZoneId.of("Asia/Almaty") // UTC+5
        // 20:00 UTC on May 10 is already 01:00 on May 11 in Almaty.
        val instant = LocalDate.of(2024, 5, 10).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli() + 20 * HOUR
        val dueLocalToday = item(0, next = instant + 3 * HOUR) // 04:00 local May 11
        val dueLocalTomorrow = item(1, next = instant + 24 * HOUR) // 01:00 local May 12
        val queue = ReviewQueueBuilder.build(listOf(dueLocalToday, dueLocalTomorrow), instant, almaty, 10)
        assertEquals(listOf(dueLocalToday), queue.items)
    }

    @Test
    fun useCase_buildsFromTheRepository_andWrapsFailures() = runTest {
        val repository = FakeVocabularyRepository(listOf(item(0, next = startOfToday - DAY), item(1, WordStatus.NEW)))
        val time = TestTimeProvider(startMillis = now)
        val result = BuildReviewQueueUseCase(repository, time)(LearningLanguage.ENGLISH)
        assertEquals(1, (result as AppResult.Success).data.items.size)
    }

    private companion object {
        const val HOUR = 60L * 60 * 1000
        const val DAY = 24 * HOUR
    }
}

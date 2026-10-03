package com.rewordly.app.data.repository

import com.rewordly.app.core.common.AppResult
import com.rewordly.app.data.local.MockVocabulary
import com.rewordly.app.domain.model.AnswerQuality
import com.rewordly.app.domain.model.ReviewRating
import com.rewordly.app.domain.model.ReviewSubmitResult
import com.rewordly.app.domain.model.WordStatus
import com.rewordly.app.domain.service.SpacedRepetitionService
import com.rewordly.app.domain.usecase.MarkWordKnownUseCase
import com.rewordly.app.domain.usecase.StartStudyingWordUseCase
import com.rewordly.app.domain.usecase.SubmitReviewAnswerUseCase
import com.rewordly.app.domain.usecase.UnmarkWordLearnedUseCase
import com.rewordly.app.testing.FakeReviewLogDao
import com.rewordly.app.testing.FakeWordDao
import com.rewordly.app.testing.FakeWordProgressDao
import com.rewordly.app.testing.TestTimeProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineReviewRepositoryTest {
    private val wordDao = FakeWordDao(MockVocabulary.words)
    private val progressDao = FakeWordProgressDao(wordDao.progress)
    private val logDao = FakeReviewLogDao(wordDao.progress)
    private val time = TestTimeProvider()
    private val repository = OfflineReviewRepository(progressDao, logDao, time)
    private val service = SpacedRepetitionService()
    private val submit = SubmitReviewAnswerUseCase(repository, service, time)
    private val startStudying = StartStudyingWordUseCase(repository, service, time)
    private val markKnown = MarkWordKnownUseCase(repository, service, time)
    private val id = MockVocabulary.words[0].id
    private val day = 24L * 60 * 60 * 1000

    @Test
    fun startStudying_schedulesTheFirstReview_keepsTheWordInProgress_andLogsTheEventOnce() = runTest {
        startStudying(id, "learn-1")
        startStudying(id, "learn-1")

        val progress = progressDao.get(id)!!
        assertEquals(WordStatus.LEARNING.name, progress.status)
        assertEquals(time.nowMillis() + day, progress.nextReviewAt)
        assertEquals(1, progress.intervalDays)
        assertEquals(1, logDao.logs.value.size)
        assertEquals("LEARN", logDao.logs.value.single().kind)
    }

    @Test
    fun markKnown_graduatesTheWord_andStillSchedulesTheCheckForTomorrow() = runTest {
        markKnown(id, "learn-1")

        val progress = progressDao.get(id)!!
        assertEquals(WordStatus.LEARNED.name, progress.status)
        assertEquals(com.rewordly.app.domain.model.LearningRules.GRADUATION_REPETITIONS, progress.repetitionCount)
        assertEquals(time.nowMillis() + day, progress.nextReviewAt)
        assertEquals(0, progress.correctAnswers)
    }

    @Test
    fun submitReview_storesTheScheduleCountersAndALogEntry() = runTest {
        startStudying(id, "learn-1")
        time.advanceDays(1)
        val result = submit(id, "review-1", ReviewRating.GOOD, durationMs = 4_000)

        assertTrue((result as AppResult.Success).data is ReviewSubmitResult.Applied)
        val progress = progressDao.get(id)!!
        assertEquals(1, progress.repetitionCount)
        assertEquals(3, progress.intervalDays)
        assertEquals(1, progress.correctAnswers)
        assertEquals(time.nowMillis() + 3 * day, progress.nextReviewAt)
        val log = logDao.logs.value.last()
        assertEquals("REVIEW", log.kind)
        assertEquals(AnswerQuality.CORRECT.value, log.quality)
        assertEquals(4_000L, log.durationMs)
        assertEquals(1, log.intervalBefore)
        assertEquals(3, log.intervalAfter)
    }

    @Test
    fun duplicateSubmission_doesNotScheduleTwice() = runTest {
        startStudying(id, "learn-1")
        submit(id, "review-1", ReviewRating.GOOD, null)
        val afterFirst = progressDao.get(id)!!

        time.advanceMillis(1_000)
        val second = submit(id, "review-1", ReviewRating.EASY, null)

        assertEquals(ReviewSubmitResult.Duplicate, (second as AppResult.Success).data)
        assertEquals(afterFirst, progressDao.get(id))
        assertEquals(2, logDao.logs.value.size)
    }

    @Test
    fun sameWord_inALaterSession_isAnotherReview() = runTest {
        submit(id, "review-1", ReviewRating.GOOD, null)
        submit(id, "review-2", ReviewRating.GOOD, null)
        assertEquals(2, progressDao.get(id)!!.repetitionCount)
    }

    @Test
    fun markNotLearned_clearsTheSchedule() = runTest {
        startStudying(id, "learn-1")
        UnmarkWordLearnedUseCase(repository)(id)
        val progress = progressDao.get(id)!!
        assertEquals(WordStatus.NEW.name, progress.status)
        assertNull(progress.nextReviewAt)
        assertEquals(0, progress.intervalDays)
    }

    @Test
    fun countDue_usesTheEndOfTheLocalDay() = runTest {
        startStudying(id, "learn-1") // due in 24h
        assertEquals(0, (repository.countDue() as AppResult.Success).data)
        time.advanceMillis(day + 1_000)
        assertEquals(1, (repository.countDue() as AppResult.Success).data)
    }

    @Test
    fun learnedWordWithoutASchedule_countsAsDue() = runTest {
        wordDao.addProgress(
            com.rewordly.app.testing.progressOf(id, updatedAt = 1) { copy(status = WordStatus.LEARNED) },
        )
        assertEquals(1, (repository.countDue() as AppResult.Success).data)
    }
}

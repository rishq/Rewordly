package com.rewordly.app.data.repository

import com.rewordly.app.core.database.entity.DailyActivityEntity
import com.rewordly.app.data.local.MockVocabulary
import com.rewordly.app.domain.model.WordStatus
import com.rewordly.app.domain.usecase.ComputeStreakUseCase
import com.rewordly.app.testing.FakeDailyActivityDao
import com.rewordly.app.testing.FakeReviewLogDao
import com.rewordly.app.testing.FakeSettingsRepository
import com.rewordly.app.testing.FakeWordDao
import com.rewordly.app.testing.FakeWordProgressDao
import com.rewordly.app.testing.TestTimeProvider
import com.rewordly.app.testing.progressOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultProgressRepositoryTest {
    private val wordDao = FakeWordDao(MockVocabulary.words)
    private val progressDao = FakeWordProgressDao(wordDao.progress)
    private val activityDao = FakeDailyActivityDao()
    private val logDao = FakeReviewLogDao(wordDao.progress)
    private val settings = FakeSettingsRepository()
    private val time = TestTimeProvider()
    private val repository = DefaultProgressRepository(
        progressDao,
        activityDao,
        logDao,
        wordDao,
        settings,
        time,
        ComputeStreakUseCase(),
    )

    @Test
    fun overview_usesTheStoredDailyGoalAndRealCounts() = runTest {
        settings.setDailyGoal(15)
        wordDao.addProgress(progressOf("en-beautiful", updatedAt = 1) { copy(status = WordStatus.LEARNED) })
        wordDao.addProgress(progressOf("en-improve", updatedAt = 1) { copy(isSaved = true) })
        activityDao.rows.value =
            listOf(DailyActivityEntity(time.today().toString(), wordsLearned = 4, wordsReviewed = 2))

        val overview = repository.observeOverview().first()
        assertEquals(15, overview.today.goal)
        assertEquals(4, overview.today.wordsLearned)
        assertEquals(2, overview.today.wordsReviewed)
        assertEquals(1, overview.wordsLearned)
        assertEquals(1, overview.savedWords)
        // A learned word without a schedule is due, a merely saved word is not.
        assertEquals(1, overview.wordsDueToday)
        assertEquals(MockVocabulary.words.size - 1, overview.newWordsAvailable)
        assertEquals(MockVocabulary.words.size, overview.totalWords)
        assertEquals(1, overview.streak.current)
    }

    @Test
    fun activityChart_alwaysHasSevenDaysEndingToday() = runTest {
        val overview = repository.observeOverview().first()
        assertEquals(7, overview.activity.size)
        assertEquals(time.today(), overview.activity.last().date)
        assertEquals(0, overview.activity.sumOf { it.wordsLearned })
        assertEquals(0, overview.streak.current)
    }
}

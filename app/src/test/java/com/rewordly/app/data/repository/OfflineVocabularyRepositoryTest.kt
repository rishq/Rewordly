package com.rewordly.app.data.repository

import com.rewordly.app.data.local.MockVocabulary
import com.rewordly.app.domain.model.LearningLanguage
import com.rewordly.app.testing.FakeWordDao
import com.rewordly.app.testing.FakeWordProgressDao
import com.rewordly.app.testing.TestTimeProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineVocabularyRepositoryTest {
    private val wordDao = FakeWordDao(MockVocabulary.words)
    private val progressDao = FakeWordProgressDao(wordDao.progress)
    private val time = TestTimeProvider()
    private val repository = OfflineVocabularyRepository(wordDao, progressDao, time)

    @Test
    fun ensureSeeded_isIdempotent() = runTest {
        repository.ensureSeeded()
        val afterFirst = wordDao.count()
        repository.ensureSeeded()
        assertEquals(afterFirst, wordDao.count())
        assertTrue(wordDao.examples.value.isNotEmpty())
    }

    @Test
    fun savedWords_arePersistedAndRemovable() = runTest {
        val id = MockVocabulary.words.first().id
        assertTrue(repository.observeSavedWords(LearningLanguage.ENGLISH).first().isEmpty())

        repository.setSaved(id, true)
        assertEquals(listOf(id), repository.observeSavedWords(LearningLanguage.ENGLISH).first().map { it.word.id })

        repository.setSaved(id, false)
        assertTrue(repository.observeSavedWords(LearningLanguage.ENGLISH).first().isEmpty())
    }

    @Test
    fun recordView_countsViewsWithoutTouchingLearningCounters() = runTest {
        val id = MockVocabulary.words[2].id
        repository.recordView(id)
        repository.recordView(id)
        val progress = repository.observeWord(id).first()!!.progress
        assertEquals(2, progress.views)
        assertEquals(0, progress.reviewCount)
        assertEquals(time.nowMillis(), progress.lastViewedAt)
    }

    @Test
    fun progressRow_isCreatedForAWordWithoutOneYet() = runTest {
        val id = MockVocabulary.words[4].id
        assertTrue(progressDao.get(id) == null)
        repository.setSaved(id, true)
        assertTrue(repository.observeWord(id).first()!!.progress.isSaved)
    }
}

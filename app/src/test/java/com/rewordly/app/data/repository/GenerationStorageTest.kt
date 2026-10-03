package com.rewordly.app.data.repository

import com.rewordly.app.core.common.AppResult
import com.rewordly.app.data.local.MockVocabulary
import com.rewordly.app.domain.model.Difficulty
import com.rewordly.app.domain.model.GenerationHistoryEntry
import com.rewordly.app.domain.model.GenerationMode
import com.rewordly.app.domain.model.LearningLanguage
import com.rewordly.app.domain.model.WordProgress
import com.rewordly.app.domain.model.WordSource
import com.rewordly.app.domain.model.WordStatus
import com.rewordly.app.domain.model.WordWithProgress
import com.rewordly.app.domain.model.toWord
import com.rewordly.app.domain.usecase.StudyQueueBuilder
import com.rewordly.app.testing.FakeGenerationHistoryDao
import com.rewordly.app.testing.FakeWordDao
import com.rewordly.app.testing.FakeWordProgressDao
import com.rewordly.app.testing.TestTimeProvider
import com.rewordly.app.testing.generatedWord
import com.rewordly.app.testing.progressOf
import kotlin.random.Random
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationStorageTest {
    private val dao = FakeGenerationHistoryDao()
    private val history = OfflineGenerationHistoryRepository(dao, Json)
    private val entry = GenerationHistoryEntry(
        id = "g1",
        mode = GenerationMode.TOPIC,
        description = "Programming",
        level = Difficulty.B2,
        requestedCount = 10,
        resultCount = 0,
        createdAt = 1_000L,
        hasResult = true,
    )

    // ---- generation history ----

    @Test
    fun recordedGeneration_canBeReadBackWithItsItems() = runTest {
        val items = listOf(generatedWord("deploy"), generatedWord("compile", difficulty = Difficulty.C1))
        history.record(entry, items)

        val stored = (history.getEntry("g1") as AppResult.Success).data!!
        assertEquals("Programming", stored.description)
        assertEquals(Difficulty.B2, stored.level)
        assertEquals(2, stored.resultCount)
        assertTrue(stored.hasResult)
        assertEquals(items, (history.getItems("g1") as AppResult.Success).data)
    }

    @Test
    fun history_isListedNewestFirst() = runTest {
        history.record(entry.copy(id = "old", createdAt = 1L), listOf(generatedWord("a")))
        history.record(entry.copy(id = "new", createdAt = 9L), listOf(generatedWord("b")))
        assertEquals(listOf("new", "old"), history.observeHistory().first().map { it.id })
    }

    @Test
    fun updatingItems_changesTheStoredResultAndCount() = runTest {
        history.record(entry, listOf(generatedWord("deploy")))
        history.updateItems("g1", listOf(generatedWord("ship"), generatedWord("release")))
        assertEquals(listOf("ship", "release"), (history.getItems("g1") as AppResult.Success).data.map { it.word })
        assertEquals(2, history.observeHistory().first().single().resultCount)
    }

    @Test
    fun deleteAndClear_removeTheStoredResults() = runTest {
        history.record(entry, listOf(generatedWord("a")))
        history.record(entry.copy(id = "g2"), listOf(generatedWord("b")))
        history.delete("g1")
        assertEquals(listOf("g2"), history.observeHistory().first().map { it.id })

        history.clear()
        assertTrue(history.observeHistory().first().isEmpty())
        assertNull((history.getEntry("g2") as AppResult.Success).data)
        assertTrue((history.getItems("g2") as AppResult.Success).data.isEmpty())
    }

    @Test
    fun aDamagedStoredResult_yieldsNoItemsInsteadOfCrashing() = runTest {
        history.record(entry, listOf(generatedWord("deploy")))
        dao.rows.value = dao.rows.value.map { it.copy(resultJson = "{ not json") }
        assertTrue((history.getItems("g1") as AppResult.Success).data.isEmpty())
    }

    @Test
    fun anEntryWithoutAResult_isReportedAsSuch() = runTest {
        history.record(entry, listOf(generatedWord("deploy")))
        dao.rows.value = dao.rows.value.map { it.copy(resultJson = null) }
        assertEquals(false, (history.getEntry("g1") as AppResult.Success).data!!.hasResult)
    }

    // ---- saving into the vocabulary ----

    private val wordDao = FakeWordDao(MockVocabulary.words)
    private val progressDao = FakeWordProgressDao(wordDao.progress)
    private val vocabulary = OfflineVocabularyRepository(wordDao, progressDao, TestTimeProvider())

    @Test
    fun addWords_insertsGeneratedWordsWithTheirExamples() = runTest {
        val ids = (vocabulary.addWords(listOf(generatedWord("deploy").toWord())) as AppResult.Success).data
        assertEquals(listOf("ai-deploy"), ids)

        val saved = vocabulary.observeWord("ai-deploy").first()!!
        assertEquals(WordSource.GENERATED, saved.word.source)
        assertEquals("перевод", saved.word.translation.text)
        assertEquals(1, saved.word.examples.size)
        assertEquals(WordStatus.NEW, saved.progress.status)
    }

    @Test
    fun addWords_skipsExistingTexts_andNeverOverwritesProgress() = runTest {
        val existing = MockVocabulary.words[0]
        wordDao.addProgress(
            progressOf(existing.id, updatedAt = 1) { copy(status = WordStatus.LEARNED, correctAnswers = 9) },
        )

        val duplicate = generatedWord("  ${existing.text.uppercase()}  ", translation = "другое").toWord()
        val ids = (vocabulary.addWords(listOf(duplicate)) as AppResult.Success).data

        assertTrue(ids.isEmpty())
        val unchanged = vocabulary.observeWord(existing.id).first()!!
        assertEquals(existing.translation.text, unchanged.word.translation.text)
        assertEquals(9, unchanged.progress.correctAnswers)
        assertEquals(WordStatus.LEARNED, unchanged.progress.status)
    }

    @Test
    fun findExistingIds_normalizesCaseAndWhitespace() = runTest {
        val existing = MockVocabulary.words[1]
        val found = (
            vocabulary.findExistingIds(
                LearningLanguage.ENGLISH,
                listOf("  ${existing.text.uppercase()} ", "zzzz"),
            ) as AppResult.Success
            ).data
        assertEquals(mapOf(existing.text.lowercase() to existing.id), found)
    }

    @Test
    fun addWords_dedupesInsideOneBatch() = runTest {
        val ids = (
            vocabulary.addWords(
                listOf(generatedWord("deploy").toWord(), generatedWord("DEPLOY").toWord()),
            ) as AppResult.Success
            ).data
        assertEquals(1, ids.size)
    }

    // ---- the study queue ----

    @Test
    fun learnSession_offersSavedGeneratedWordsLikeAnyOther() {
        val bundled = MockVocabulary.words.take(2).map { WordWithProgress(it, WordProgress(it.id)) }
        val generated = WordWithProgress(generatedWord("deploy").toWord(), WordProgress("ai-deploy"))
        val queue = StudyQueueBuilder.build(bundled + generated, size = 10, random = Random(1)).map { it.word.id }
        assertEquals((bundled.map { it.word.id } + "ai-deploy").sorted(), queue.sorted())
        assertTrue(queue.contains("ai-deploy"))
    }
}

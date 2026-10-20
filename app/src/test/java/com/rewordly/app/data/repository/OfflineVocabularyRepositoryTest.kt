package com.rewordly.app.data.repository

import com.rewordly.app.core.database.entity.WordEntity
import com.rewordly.app.core.database.entity.WordExampleEntity
import com.rewordly.app.core.database.entity.WordProgressEntity
import com.rewordly.app.data.local.MockVocabulary
import com.rewordly.app.domain.model.Difficulty
import com.rewordly.app.domain.model.LearningLanguage
import com.rewordly.app.domain.model.WordSource
import com.rewordly.app.domain.model.WordStatus
import com.rewordly.app.domain.service.VocabularyImportParser
import com.rewordly.app.testing.FakeSeedPackTracker
import com.rewordly.app.testing.FakeWordDao
import com.rewordly.app.testing.FakeWordProgressDao
import com.rewordly.app.testing.SequentialIdProvider
import com.rewordly.app.testing.TestTimeProvider
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the behavioural contract of pack sync: bundled content is inserted once per pack version,
 * refreshed in place when the version bumps, and never applied to rows owned by another source.
 */
class OfflineVocabularyRepositoryTest {
    private val wordDao = FakeWordDao(MockVocabulary.words)
    private val time = TestTimeProvider()
    private val packTracker = FakeSeedPackTracker()
    private val repository = OfflineVocabularyRepository(
        wordDao = wordDao,
        progressDao = FakeWordProgressDao(wordDao.progress),
        importParser = VocabularyImportParser(Json),
        idProvider = SequentialIdProvider(),
        timeProvider = time,
        packSource = WordPackSource { name -> File("src/main/assets/$name").readText(Charsets.UTF_8) },
        packTracker = packTracker,
    )

    @Test
    fun ensureSeeded_isIdempotent() = runTest {
        repository.ensureSeeded()
        val afterFirst = wordDao.count()
        repository.ensureSeeded()
        assertEquals(afterFirst, wordDao.count())
        assertTrue(wordDao.examples.value.isNotEmpty())
    }

    @Test
    fun ensureSeeded_marksEveryCatalogPackApplied() = runTest {
        repository.ensureSeeded()
        assertEquals(
            WordPackCatalog.packs.associate { it.id to it.version },
            packTracker.applied.value,
        )
    }

    @Test
    fun packSync_refreshesStaleBundledRow_andKeepsProgress() = runTest {
        val stale = staleWaterRow(source = WordSource.BUNDLED.name)
        wordDao.entities.value = listOf(stale)
        wordDao.progress.value = listOf(
            WordProgressEntity(
                wordId = stale.id,
                status = WordStatus.LEARNING.name,
                isSaved = true,
                views = 0,
                correctAnswers = 0,
                incorrectAnswers = 0,
                repetitionCount = 3,
                intervalDays = 6,
                lastViewedAt = 0L,
                lastReviewedAt = 0L,
                nextReviewAt = 0L,
                updatedAt = 0L,
            ),
        )

        repository.ensureSeeded()

        val refreshed = wordDao.entities.value.single { it.text == "water" }
        assertEquals(stale.id, refreshed.id)
        assertEquals("вода", refreshed.translation)
        assertEquals(Difficulty.A1.name, refreshed.difficulty)
        val keptProgress = wordDao.progress.value.single { it.wordId == stale.id }
        assertEquals(3, keptProgress.repetitionCount)
        assertTrue(keptProgress.isSaved)
    }

    @Test
    fun packSync_neverTouchesRowsOwnedByOtherSources() = runTest {
        val foreign = staleWaterRow(source = WordSource.GENERATED.name)
        wordDao.entities.value = listOf(foreign)
        val foreignExample = WordExampleEntity(
            id = "e1",
            wordId = foreign.id,
            text = "my example",
            translation = "мой пример",
            position = 0,
        )
        wordDao.examples.value = listOf(foreignExample)

        repository.ensureSeeded()

        assertEquals(listOf(foreign), wordDao.entities.value.filter { it.text == "water" })
        assertEquals(foreignExample, wordDao.examples.value.single { it.wordId == foreign.id })
    }
}

/** A "water" row with deliberately stale content, so pack refresh has something to change. */
private fun staleWaterRow(source: String): WordEntity = WordEntity(
    id = "test-water-id",
    language = LearningLanguage.ENGLISH.tag,
    text = "water",
    translation = "устаревший вариант",
    translationLanguage = "ru",
    pronunciation = "",
    partOfSpeech = "NOUN",
    difficulty = Difficulty.B1.name,
    definition = "старое описание",
    definitionTranslation = "старый перевод описания",
    forms = emptyList(),
    relatedWords = emptyList(),
    synonyms = emptyList(),
    audioUrl = null,
    createdAt = 0L,
    source = source,
    topic = "everyday",
)

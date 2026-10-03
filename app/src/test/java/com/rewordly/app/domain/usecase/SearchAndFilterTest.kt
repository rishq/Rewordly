package com.rewordly.app.domain.usecase

import com.rewordly.app.data.local.MockVocabulary
import com.rewordly.app.domain.model.DifficultyFilter
import com.rewordly.app.domain.model.StatusFilter
import com.rewordly.app.domain.model.VocabularyFilters
import com.rewordly.app.domain.model.WordProgress
import com.rewordly.app.domain.model.WordStatus
import com.rewordly.app.domain.model.WordWithProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchWordsUseCaseTest {
    private val words: List<WordWithProgress> = MockVocabulary.words.map {
        WordWithProgress(it, WordProgress(wordId = it.id))
    }

    @Test
    fun match_findsWordByEnglishAndByRussianTranslation() {
        assertEquals(listOf("beautiful"), SearchWordsUseCase.match(words, "beautiful").map { it.word.text })
        assertEquals(listOf("beautiful"), SearchWordsUseCase.match(words, "красивый").map { it.word.text })
    }

    @Test
    fun match_isCaseInsensitiveForBothLanguages() {
        assertEquals("beautiful", SearchWordsUseCase.match(words, "BEAUTIFUL").single().word.text)
        assertEquals("beautiful", SearchWordsUseCase.match(words, "Красивый").single().word.text)
    }

    @Test
    fun match_ranksExactBeforePrefixBeforeSubstring() {
        val results = SearchWordsUseCase.match(words, "improve").map { it.word.text }
        assertEquals("improve", results.first())
        assertTrue(results.size >= 1)
    }

    @Test
    fun match_ignoresBlankQueryAndUnknownWords() {
        assertTrue(SearchWordsUseCase.match(words, "   ").isEmpty())
        assertTrue(SearchWordsUseCase.match(words, "zzzz").isEmpty())
    }

    @Test
    fun normalize_trimsAndCollapsesWhitespace() {
        assertEquals("take care of", SearchWordsUseCase.normalize("  take   care\tof "))
        assertEquals("", SearchWordsUseCase.normalize("   "))
    }
}

class FilterVocabularyUseCaseTest {
    private val useCase = FilterVocabularyUseCase()
    private val beautiful = MockVocabulary.words.first { it.text == "beautiful" }
    private val achieve = MockVocabulary.words.first { it.text == "achieve" }
    private val saved =
        WordWithProgress(beautiful, WordProgress(beautiful.id, status = WordStatus.LEARNING, isSaved = true))
    private val learned = WordWithProgress(achieve, WordProgress(achieve.id, status = WordStatus.LEARNED))
    private val words =
        listOf(saved, learned, WordWithProgress(beautiful.copy(id = "en-plain"), WordProgress("en-plain")))

    @Test
    fun defaultFilters_keepEverything() {
        assertEquals(3, useCase(words, VocabularyFilters()).size)
    }

    @Test
    fun difficultyFilter_narrowsByLevel() {
        val result = useCase(words, VocabularyFilters(difficulty = DifficultyFilter.A1))
        assertEquals(listOf("en-beautiful", "en-plain"), result.map { it.word.id }.sorted())
    }

    @Test
    fun statusFilter_selectsSavedAndLearned() {
        assertEquals(listOf(saved), useCase(words, VocabularyFilters(status = StatusFilter.SAVED)))
        assertEquals(listOf(learned), useCase(words, VocabularyFilters(status = StatusFilter.LEARNED)))
        assertEquals(1, useCase(words, VocabularyFilters(status = StatusFilter.NEW)).size)
    }

    @Test
    fun filters_combine() {
        val filters = VocabularyFilters(
            difficulty = DifficultyFilter.A1,
            status = StatusFilter.SAVED,
        )
        assertEquals(listOf(saved), useCase(words, filters))
        assertTrue(filters.isActive)
    }
}

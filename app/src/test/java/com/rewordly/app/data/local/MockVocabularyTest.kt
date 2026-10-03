package com.rewordly.app.data.local

import com.rewordly.app.domain.model.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MockVocabularyTest {
    private val words = MockVocabulary.words

    @Test
    fun dataset_hasAtLeastThirtyWordsAcrossAllTargetLevels() {
        assertTrue(words.size >= 30)
        Difficulty.entries
            .filter { it in TARGET_LEVELS }
            .forEach { level -> assertTrue("$level missing", words.any { it.difficulty == level }) }
    }

    @Test
    fun ids_areUnique() {
        assertEquals(words.size, words.map { it.id }.toSet().size)
        assertEquals(
            words.flatMap { it.examples }.size,
            words.flatMap { it.examples }.map { it.id }.toSet().size,
        )
    }

    @Test
    fun everyWord_hasTranslationPronunciationDefinitionAndSynonyms() {
        words.forEach { word ->
            assertTrue(word.text, word.translation.text.isNotBlank())
            assertTrue(word.text, word.pronunciation.startsWith("/"))
            assertTrue(word.text, word.definition.length > MIN_DEFINITION_LENGTH)
            assertTrue(word.text, word.definitionTranslation.isNotBlank())
            assertTrue(word.text, word.synonyms.isNotEmpty())
        }
    }

    @Test
    fun everyWord_hasTwoOrThreeTranslatedExamples() {
        words.forEach { word ->
            assertTrue(word.text, word.examples.size in 2..3)
            word.examples.forEach { example ->
                assertEquals(word.id, example.wordId)
                assertTrue(example.text.isNotBlank() && example.translation.isNotBlank())
            }
        }
    }

    @Test
    fun atLeastOneExampleContainsTheTargetWord() {
        words.forEach { word ->
            val pattern = Regex("(?<![\\p{L}])${Regex.escape(word.text)}(?![\\p{L}])", RegexOption.IGNORE_CASE)
            assertTrue(word.text, word.examples.any { pattern.containsMatchIn(it.text) })
        }
    }

    @Test
    fun examplesSoundLikeRealEnglish() {
        words.forEach { word ->
            word.examples.forEach { example ->
                assertTrue(example.text, example.text.first().isUpperCase())
                assertTrue(example.text, example.text.trimEnd().last() in SENTENCE_ENDINGS)
            }
        }
    }

    @Test
    fun everyWordIsEnglishWithRussianTranslation() {
        assertTrue(words.all { it.translation.languageCode == "ru" })
        assertTrue(words.all { word -> word.text.all { char -> char.isLetter() || char == ' ' } })
    }

    private companion object {
        val TARGET_LEVELS = setOf(Difficulty.A1, Difficulty.A2, Difficulty.B1, Difficulty.B2)
        val SENTENCE_ENDINGS = setOf('.', '!', '?')
        const val MIN_DEFINITION_LENGTH = 10
    }
}

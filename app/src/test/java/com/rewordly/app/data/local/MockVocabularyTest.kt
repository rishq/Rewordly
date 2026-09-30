package com.rewordly.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MockVocabularyTest {
    private val words = MockVocabulary.words

    @Test
    fun ids_areUnique() {
        assertEquals(words.size, words.map { it.id }.toSet().size)
        assertEquals(
            words.flatMap { it.examples }.size,
            words.flatMap { it.examples }.map { it.id }.toSet().size,
        )
    }

    @Test
    fun everyWord_hasTranslationPronunciationAndTranslatedExample() {
        words.forEach { word ->
            assertTrue(word.text, word.translation.text.isNotBlank())
            assertTrue(word.text, word.pronunciation.isNotBlank())
            assertTrue(word.text, word.examples.isNotEmpty())
            word.examples.forEach { example ->
                assertEquals(word.id, example.wordId)
                assertTrue(example.text.isNotBlank() && example.translation.isNotBlank())
            }
        }
    }
}

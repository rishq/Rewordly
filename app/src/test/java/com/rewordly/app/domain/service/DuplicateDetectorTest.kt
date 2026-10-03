package com.rewordly.app.domain.service

import com.rewordly.app.domain.model.DuplicateKind
import com.rewordly.app.domain.model.ParsedEntry
import com.rewordly.app.domain.model.VocabularyEntry
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Duplicates are only ever *marked*. Two entries that share a spelling can still mean different
 * things, so the detector must never collapse them on its own.
 */
class DuplicateDetectorTest {
    private fun entries(vararg words: String) = words.mapIndexed { index, word ->
        ParsedEntry(row = index + 1, entry = VocabularyEntry(text = word, translation = "перевод $word"))
    }

    @Test
    fun mark_leavesWordsThatAreNewToTheVocabularyAlone() {
        val candidates = DuplicateDetector.mark(entries("apple", "pear"), existingIdsByKey = emptyMap())

        assertEquals(listOf(DuplicateKind.NONE, DuplicateKind.NONE), candidates.map { it.duplicate })
    }

    @Test
    fun mark_flagsWordsThatAreAlreadySaved() {
        val candidates = DuplicateDetector.mark(
            entries("apple", "pear"),
            existingIdsByKey = mapOf("apple" to "word-1"),
        )

        assertEquals(DuplicateKind.EXISTING, candidates.first().duplicate)
        assertEquals("word-1", candidates.first().existingWordId)
        assertEquals(DuplicateKind.NONE, candidates.last().duplicate)
    }

    @Test
    fun mark_comparesCaseAndSurroundingWhitespaceInsensitively() {
        val candidates = DuplicateDetector.mark(
            entries("  Apple ", "APPLE"),
            existingIdsByKey = mapOf("apple" to "word-1"),
        )

        assertEquals(listOf(DuplicateKind.EXISTING, DuplicateKind.WITHIN_FILE), candidates.map { it.duplicate })
    }

    @Test
    fun mark_flagsRepeatsInsideTheFile_andKeepsTheFirstOccurrence() {
        val candidates = DuplicateDetector.mark(entries("apple", "pear", "apple"), existingIdsByKey = emptyMap())

        assertEquals(
            listOf(DuplicateKind.NONE, DuplicateKind.NONE, DuplicateKind.WITHIN_FILE),
            candidates.map { it.duplicate },
        )
    }

    @Test
    fun mark_neverMergesEntriesThatMayMeanDifferentThings() {
        val homonyms = listOf(
            ParsedEntry(1, VocabularyEntry(text = "bank", translation = "банк")),
            ParsedEntry(2, VocabularyEntry(text = "bank", translation = "берег")),
        )

        val candidates = DuplicateDetector.mark(homonyms, existingIdsByKey = emptyMap())

        assertEquals(2, candidates.size)
        assertEquals("банк", candidates.first().entry.translation)
        assertEquals("берег", candidates.last().entry.translation)
    }

    @Test
    fun mark_keepsTheRowOfEveryEntry() {
        val candidates = DuplicateDetector.mark(entries("apple", "pear"), existingIdsByKey = emptyMap())

        assertEquals(listOf(1, 2), candidates.map { it.row })
    }
}

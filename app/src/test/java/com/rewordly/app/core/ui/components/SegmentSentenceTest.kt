package com.rewordly.app.core.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmentSentenceTest {
    @Test
    fun onlyTheTargetWordIsMarked() {
        val segments = segmentSentence("She has a beautiful voice.", "beautiful")
        assertEquals(listOf("She has a ", "beautiful", " voice."), segments.map { it.text })
        assertEquals(listOf(false, true, false), segments.map { it.highlighted })
    }

    @Test
    fun matchIsCaseInsensitiveAndKeepsTheOriginalCasing() {
        val segments = segmentSentence("It was a BEAUTIFUL morning.", "beautiful")
        assertEquals("BEAUTIFUL", segments.single { it.highlighted }.text)
    }

    @Test
    fun partialWordsAreNotHighlighted() {
        val segments = segmentSentence("The beauties of the city.", "beautiful")
        assertTrue(segments.none { it.highlighted })
    }

    @Test
    fun everyOccurrenceIsMarked() {
        val segments = segmentSentence("Improve your English and improve your life.", "improve")
        assertEquals(2, segments.count { it.highlighted })
        assertEquals("Improve your English and improve your life.", segments.joinToString("") { it.text })
    }

    @Test
    fun withoutTargetTheSentenceStaysWhole() {
        assertEquals(listOf(SentenceSegment("Any sentence.", false)), segmentSentence("Any sentence.", null))
    }
}

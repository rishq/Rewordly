package com.rewordly.app.domain.service

import com.rewordly.app.data.local.MockVocabulary
import com.rewordly.app.domain.model.Difficulty
import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the optional placement check. It is built from vocabulary that is already on the device,
 * so it is fully deterministic and needs no network or backend.
 */
class PlacementAssessorTest {
    private val words = MockVocabulary.words

    @Test
    fun questions_areOrderedFromEasyToHard() {
        val questions = PlacementAssessor.questions(words)

        assertTrue(questions.isNotEmpty())
        val levels = questions.map { it.level.ordinal }
        assertEquals(levels.sorted(), levels)
        assertEquals(Difficulty.A1, questions.first().level)
    }

    @Test
    fun questions_takeAtMostTheRequestedNumberOfWordsPerLevel() {
        val questions = PlacementAssessor.questions(words, perLevel = 1)

        Difficulty.entries.forEach { level ->
            assertTrue("too many questions for $level", questions.count { it.level == level } <= 1)
        }
    }

    @Test
    fun everyQuestion_hasFourDistinctOptions_includingTheCorrectTranslation() {
        val questions = PlacementAssessor.questions(words)

        questions.forEach { question ->
            assertEquals(PlacementAssessor.OPTION_COUNT, question.options.size)
            assertEquals(question.options.distinct().size, question.options.size)
            assertTrue(question.correctIndex in question.options.indices)
            val word = words.first { it.text == question.prompt }
            assertEquals(word.translation.text, question.options[question.correctIndex])
        }
    }

    @Test
    fun questions_areDeterministic_andIndependentOfInputOrder() {
        val first = PlacementAssessor.questions(words).map { it.id }
        val second = PlacementAssessor.questions(words.shuffled(Random(3))).map { it.id }

        assertEquals(first, second)
    }

    @Test
    fun assess_walksUpTheLevels_andStopsAtTheFirstFailure() {
        val questions = PlacementAssessor.questions(words)
        val answers = mutableMapOf<String, Boolean>()
        questions.filter { it.level == Difficulty.A1 || it.level == Difficulty.A2 }.forEach { answers[it.id] = true }
        questions.filter { it.level == Difficulty.B1 }.forEach { answers[it.id] = false }

        val result = PlacementAssessor.assess(questions, answers)

        assertEquals(Difficulty.A2, result.level)
        assertEquals(4, result.correctAnswers)
        assertEquals(6, result.answered)
        assertTrue(result.isConfident)
    }

    @Test
    fun assess_failingTheEasiestLevel_keepsTheLowestEstimate() {
        val questions = PlacementAssessor.questions(words)
        val answers = questions.filter { it.level == Difficulty.A1 }.associate { it.id to false }

        val result = PlacementAssessor.assess(questions, answers)

        assertEquals(Difficulty.A1, result.level)
        assertEquals(0, result.correctAnswers)
        assertEquals(2, result.answered)
    }

    @Test
    fun assess_withNoAnswers_atAll_staysAtA1_andIsNotConfident() {
        val questions = PlacementAssessor.questions(words)

        val result = PlacementAssessor.assess(questions, emptyMap())

        assertEquals(Difficulty.A1, result.level)
        assertEquals(0, result.answered)
        assertFalse(result.isConfident)
    }

    @Test
    fun assess_skippedQuestions_doNotCount_andTooFewAnswersStayUncertain() {
        val questions = PlacementAssessor.questions(words)
        val a1 = questions.filter { it.level == Difficulty.A1 }

        val result = PlacementAssessor.assess(questions, mapOf(a1.first().id to true))

        assertEquals(Difficulty.A1, result.level)
        assertEquals(1, result.answered)
        assertEquals(1, result.correctAnswers)
        assertFalse(result.isConfident)
    }
}

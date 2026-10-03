package com.rewordly.app.domain.service

import com.rewordly.app.data.local.MockVocabulary
import com.rewordly.app.domain.model.DailyProgress
import com.rewordly.app.domain.model.LearningRules
import com.rewordly.app.domain.model.ProgressOverview
import com.rewordly.app.domain.model.ReviewDay
import com.rewordly.app.domain.model.ReviewHistory
import com.rewordly.app.domain.model.Streak
import com.rewordly.app.domain.model.WordProgress
import com.rewordly.app.domain.model.WordStatus
import com.rewordly.app.domain.model.WordWithProgress
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the insights summary. The three word counters must stay disjoint and the accuracy window
 * must be honest, so the numbers a user sees can never overlap or look better than they are.
 */
class LearningInsightCalculatorTest {
    private val today = LocalDate.of(2024, 5, 10)

    @Test
    fun introducedMasteredAndInReview_areDisjointAndAddUp() {
        val mastered = learned(0, repetitions = LearningRules.MASTERED_REPETITIONS)
        val inReview = learned(1, repetitions = 1)
        val introduced = learning(2)
        val untouched = new(3)

        val insight = calculate(listOf(mastered, inReview, introduced, untouched))

        assertEquals(3, insight.wordsIntroduced)
        assertEquals(1, insight.wordsMastered)
        assertEquals(2, insight.wordsInReview)
        assertEquals(insight.wordsIntroduced, insight.wordsMastered + insight.wordsInReview)
    }

    @Test
    fun masteredWords_needEnoughSuccessfulRepetitions() {
        val justLearned = learned(0, repetitions = LearningRules.MASTERED_REPETITIONS - 1)
        val solid = learned(1, repetitions = LearningRules.MASTERED_REPETITIONS)

        val insight = calculate(listOf(justLearned, solid))

        assertEquals(1, insight.wordsMastered)
        assertEquals(1, insight.wordsInReview)
    }

    @Test
    fun weakWords_areSortedWorstFirst_andCapped() {
        val words = (0 until 8).map {
            learned(it, repetitions = 1, incorrect = 10 - it, consecutiveIncorrect = 2)
        }

        val insight = calculate(words)

        assertEquals(LearningRules.MAX_WEAK_WORDS, insight.weakWords.size)
        val mistakes = insight.weakWords.map { it.progress.incorrectAnswers }
        assertEquals(listOf(10, 9, 8, 7, 6), mistakes)
        assertTrue(insight.hasWeakWords)
    }

    @Test
    fun recentAccuracy_isNull_withoutAnyHistory() {
        val insight = calculate(listOf(learned(0, repetitions = 1)), history = ReviewHistory(emptyList()))

        assertNull(insight.recentAccuracy)
        assertEquals(0, insight.reviewsConsidered)
    }

    @Test
    fun recentAccuracy_usesTheMostRecentReviews_first() {
        val days = listOf(
            reviewDay(today, reviews = 10, correct = 9),
            reviewDay(today.minusDays(1), reviews = 10, correct = 3),
        )

        val insight = calculate(listOf(learned(0, repetitions = 1)), history = ReviewHistory(days))

        assertEquals(20, insight.reviewsConsidered)
        assertEquals(0.6f, insight.recentAccuracy!!, 0.0001f)
    }

    @Test
    fun goalProgress_comesFromTodaysOverview() {
        val insight = calculate(listOf(learning(0)), overview = overview(learnedToday = 5, goal = 10))

        assertEquals(0.5f, insight.goalFraction, 0.0001f)
        assertFalse(insight.isGoalReached)
    }

    @Test
    fun reachingTheDailyGoal_isReported() {
        val insight = calculate(listOf(learning(0)), overview = overview(learnedToday = 10, goal = 10))

        assertTrue(insight.isGoalReached)
    }

    @Test
    fun aBrandNewUser_hasNothingToShow() {
        val insight = calculate(emptyList(), overview = overview(learnedToday = 0, goal = 10, totalWords = 0))

        assertFalse(insight.hasHistory)
        assertEquals(0, insight.wordsIntroduced)
        assertFalse(insight.hasWeakWords)
    }

    // ------------------------------------------------------------------------- helpers

    private fun calculate(
        words: List<WordWithProgress>,
        overview: ProgressOverview = overview(),
        history: ReviewHistory = ReviewHistory(emptyList()),
    ) = LearningInsightCalculator.calculate(words, overview, history)

    private fun overview(
        learnedToday: Int = 0,
        goal: Int = 10,
        totalWords: Int = 100,
        dueToday: Int = 0,
        reviewedToday: Int = 0,
    ) = ProgressOverview(
        today = DailyProgress(today, learnedToday, reviewedToday, goal),
        wordsLearned = 0,
        wordsReviewed = 0,
        wordsDueToday = dueToday,
        newWordsAvailable = 0,
        reviewedToday = reviewedToday,
        savedWords = 0,
        totalWords = totalWords,
        streak = Streak(),
        activity = listOf(DailyProgress(today, learnedToday, reviewedToday, goal)),
    )

    private fun reviewDay(date: LocalDate, reviews: Int, correct: Int) = ReviewDay(
        date = date,
        reviewedWords = reviews,
        reviews = reviews,
        correct = correct,
        incorrect = reviews - correct,
        wordsLearned = 0,
        studyTimeMs = null,
    )

    private fun new(index: Int) = WordWithProgress(
        MockVocabulary.words[index],
        WordProgress(wordId = MockVocabulary.words[index].id, status = WordStatus.NEW),
    )

    private fun learning(index: Int, incorrect: Int = 0) = WordWithProgress(
        MockVocabulary.words[index],
        WordProgress(
            wordId = MockVocabulary.words[index].id,
            status = WordStatus.LEARNING,
            incorrectAnswers = incorrect,
            consecutiveIncorrect = if (incorrect > 0) LearningRules.WEAK_MIN_MISTAKES else 0,
        ),
    )

    private fun learned(
        index: Int,
        repetitions: Int = 1,
        correct: Int = 0,
        incorrect: Int = 0,
        consecutiveIncorrect: Int = 0,
    ) = WordWithProgress(
        MockVocabulary.words[index],
        WordProgress(
            wordId = MockVocabulary.words[index].id,
            status = WordStatus.LEARNED,
            repetitionCount = repetitions,
            correctAnswers = correct,
            incorrectAnswers = incorrect,
            consecutiveIncorrect = consecutiveIncorrect,
        ),
    )
}

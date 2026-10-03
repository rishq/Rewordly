package com.rewordly.app.domain.service

import com.rewordly.app.domain.model.LearningInsight
import com.rewordly.app.domain.model.LearningRules
import com.rewordly.app.domain.model.ProgressOverview
import com.rewordly.app.domain.model.ReviewHistory
import com.rewordly.app.domain.model.WordStatus
import com.rewordly.app.domain.model.WordWithProgress
import com.rewordly.app.domain.model.isMastered
import com.rewordly.app.domain.model.isWeak

/**
 * Turns the raw local learning data into the numbers shown on the insights screen.
 * Pure and deterministic; the caller passes everything in, including the history.
 */
object LearningInsightCalculator {
    fun calculate(
        words: List<WordWithProgress>,
        overview: ProgressOverview,
        history: ReviewHistory,
        weakLimit: Int = LearningRules.MAX_WEAK_WORDS,
    ): LearningInsight {
        val introduced = words.count { it.progress.status != WordStatus.NEW }
        val mastered = words.count { it.progress.isMastered }
        val (accuracy, considered) = recentAccuracy(history)
        return LearningInsight(
            wordsIntroduced = introduced,
            wordsMastered = mastered,
            wordsInReview = (introduced - mastered).coerceAtLeast(0),
            totalWords = overview.totalWords,
            weakWords = words.filter { it.progress.isWeak }.sortedWith(WEAK_ORDER).take(weakLimit),
            recentAccuracy = accuracy,
            reviewsConsidered = considered,
            learnedToday = overview.today.wordsLearned,
            dailyGoal = overview.today.goal,
            dueToday = overview.wordsDueToday,
            reviewedToday = overview.reviewedToday,
            activity = overview.activity,
        )
    }

    /**
     * Accuracy over the most recent [LearningRules.RECENT_ACCURACY_REVIEWS] reviews.
     * Days arrive newest first, so the window always covers the latest activity.
     */
    private fun recentAccuracy(history: ReviewHistory): Pair<Float?, Int> {
        var reviews = 0
        var correct = 0
        for (day in history.days) {
            reviews += day.reviews
            correct += day.correct
            if (reviews >= LearningRules.RECENT_ACCURACY_REVIEWS) break
        }
        return if (reviews == 0) null to 0 else (correct.toFloat() / reviews) to reviews
    }

    private val WEAK_ORDER = compareByDescending<WordWithProgress> { it.progress.incorrectAnswers }
        .thenByDescending { it.progress.consecutiveIncorrect }
        .thenBy { it.word.id }
}

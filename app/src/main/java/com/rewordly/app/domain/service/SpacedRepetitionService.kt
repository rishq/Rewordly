package com.rewordly.app.domain.service

import com.rewordly.app.domain.model.AnswerQuality
import com.rewordly.app.domain.model.LearningRules
import com.rewordly.app.domain.model.ReviewState
import com.rewordly.app.domain.model.WordStatus
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * SM-2 inspired scheduler. Pure and deterministic: the result depends only on the arguments,
 * never on the wall clock, Android or Room. Tune the constants in [Config] to adjust the curve.
 *
 * - Pass (quality >= 3): repetition grows, the interval grows, ease moves by the SM-2 formula.
 * - Lapse (quality < 3): repetition resets, the word comes back in [Config.LAPSE_INTERVAL_DAYS], ease drops.
 * - Graduation: only [LearningRules.GRADUATION_REPETITIONS] passes in a row make the word
 *   [WordStatus.LEARNED], which is what takes it out of the study session for good.
 */
class SpacedRepetitionService @Inject constructor() {
    object Config {
        const val MIN_EASE = 1.3
        const val MAX_EASE = 3.0
        const val MIN_INTERVAL_DAYS = 1
        const val MAX_INTERVAL_DAYS = 365
        const val LAPSE_INTERVAL_DAYS = 1
        const val FIRST_REVIEW_DAYS = 1
        const val HARD_MULTIPLIER = 1.2
        const val EASY_BONUS = 1.3
        const val DAY_MILLIS = 24L * 60 * 60 * 1000

        /** Intervals after the first and second successful review, by quality 3, 4, 5. */
        val FIRST_PASS_DAYS = mapOf(3 to 2, 4 to 3, 5 to 4)
        val SECOND_PASS_DAYS = mapOf(3 to 4, 4 to 6, 5 to 8)
    }

    /**
     * Puts a word the user decided to study into the rotation: it comes back tomorrow, but it is still
     * [WordStatus.LEARNING] because it has to be recalled [LearningRules.GRADUATION_REPETITIONS] times
     * before it counts as learned.
     */
    fun scheduleFirstReview(state: ReviewState, now: Long): ReviewState {
        val safeNow = now.coerceAtLeast(0)
        val clean = sanitize(state)
        return clean.copy(
            repetitionCount = 0,
            intervalDays = Config.FIRST_REVIEW_DAYS,
            nextReviewAt = safeNow + Config.FIRST_REVIEW_DAYS * Config.DAY_MILLIS,
            consecutiveCorrectAnswers = 0,
            consecutiveIncorrectAnswers = 0,
            learningStatus = WordStatus.LEARNING,
        )
    }

    /**
     * Graduates a word the user says they already know: it leaves the study session right away but is
     * scheduled for review tomorrow, so a wrong claim is caught and drops it back into the rotation.
     */
    fun scheduleKnownWord(state: ReviewState, now: Long): ReviewState = scheduleFirstReview(state, now).copy(
        repetitionCount = LearningRules.GRADUATION_REPETITIONS,
        learningStatus = WordStatus.LEARNED,
    )

    /** Returns the state after answering with [quality] at [now]. */
    fun review(state: ReviewState, quality: AnswerQuality, now: Long): ReviewState {
        val safeNow = now.coerceAtLeast(0)
        val clean = sanitize(state)
        val ease = nextEase(clean.easeFactor, quality)
        return if (quality.isCorrect) {
            val repetition = clean.repetitionCount + 1
            val interval = passInterval(repetition, clean.intervalDays, ease, quality)
            clean.copy(
                repetitionCount = repetition,
                easeFactor = ease,
                intervalDays = interval,
                nextReviewAt = safeNow + interval * Config.DAY_MILLIS,
                lastReviewedAt = safeNow,
                consecutiveCorrectAnswers = clean.consecutiveCorrectAnswers + 1,
                consecutiveIncorrectAnswers = 0,
                learningStatus = graduationStatus(repetition),
            )
        } else {
            clean.copy(
                repetitionCount = 0,
                easeFactor = ease,
                intervalDays = Config.LAPSE_INTERVAL_DAYS,
                nextReviewAt = safeNow + Config.LAPSE_INTERVAL_DAYS * Config.DAY_MILLIS,
                lastReviewedAt = safeNow,
                consecutiveCorrectAnswers = 0,
                consecutiveIncorrectAnswers = clean.consecutiveIncorrectAnswers + 1,
                learningStatus = WordStatus.LEARNING,
            )
        }
    }

    /** Ease factor after an answer, clamped to [Config.MIN_EASE]..[Config.MAX_EASE]. */
    fun nextEase(ease: Double, quality: AnswerQuality): Double {
        val delta = when (quality) {
            AnswerQuality.FORGOT -> -0.30
            AnswerQuality.HINT -> -0.20
            AnswerQuality.DIFFICULT -> -0.15
            else -> {
                val miss = 5 - quality.value
                0.1 - miss * (0.08 + miss * 0.02)
            }
        }
        return (ease + delta).coerceIn(Config.MIN_EASE, Config.MAX_EASE)
    }

    private fun passInterval(repetition: Int, previous: Int, ease: Double, quality: AnswerQuality): Int {
        val raw = when (repetition) {
            1 -> Config.FIRST_PASS_DAYS.getValue(quality.value)
            2 -> Config.SECOND_PASS_DAYS.getValue(quality.value)
            else -> {
                val base = previous.coerceAtLeast(Config.MIN_INTERVAL_DAYS)
                val multiplier = when (quality) {
                    AnswerQuality.EFFORT -> Config.HARD_MULTIPLIER
                    AnswerQuality.EASY -> ease * Config.EASY_BONUS
                    else -> ease
                }
                (base * multiplier).roundToInt().coerceAtLeast(base + 1)
            }
        }
        return raw.coerceIn(Config.MIN_INTERVAL_DAYS, Config.MAX_INTERVAL_DAYS)
    }

    /** A word only graduates to [WordStatus.LEARNED] after enough correct answers in a row. */
    private fun graduationStatus(repetition: Int): WordStatus =
        if (repetition >= LearningRules.GRADUATION_REPETITIONS) {
            WordStatus.LEARNED
        } else {
            WordStatus.LEARNING
        }

    /** Repairs corrupted persisted values so one bad row can never break scheduling. */
    private fun sanitize(state: ReviewState): ReviewState = state.copy(
        repetitionCount = state.repetitionCount.coerceAtLeast(0),
        easeFactor = if (state.easeFactor.isNaN()) {
            ReviewState.DEFAULT_EASE_FACTOR
        } else {
            state.easeFactor.coerceIn(Config.MIN_EASE, Config.MAX_EASE)
        },
        intervalDays = state.intervalDays.coerceIn(0, Config.MAX_INTERVAL_DAYS),
        consecutiveCorrectAnswers = state.consecutiveCorrectAnswers.coerceAtLeast(0),
        consecutiveIncorrectAnswers = state.consecutiveIncorrectAnswers.coerceAtLeast(0),
    )
}

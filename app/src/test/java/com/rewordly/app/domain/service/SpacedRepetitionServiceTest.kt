package com.rewordly.app.domain.service

import com.rewordly.app.domain.model.AnswerQuality
import com.rewordly.app.domain.model.LearningRules
import com.rewordly.app.domain.model.ReviewRating
import com.rewordly.app.domain.model.ReviewState
import com.rewordly.app.domain.model.WordStatus
import com.rewordly.app.domain.service.SpacedRepetitionService.Config
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpacedRepetitionServiceTest {
    private val service = SpacedRepetitionService()
    private val now = 1_700_000_000_000L
    private val day = Config.DAY_MILLIS

    private fun fresh() = ReviewState(wordId = "w")

    private fun answer(state: ReviewState, quality: AnswerQuality, at: Long = now) = service.review(state, quality, at)

    @Test
    fun defaults_matchTheSpecification() {
        val state = fresh()
        assertEquals(2.5, state.easeFactor, 0.0)
        assertEquals(0, state.repetitionCount)
        assertEquals(0, state.intervalDays)
        assertEquals(WordStatus.NEW, state.learningStatus)
    }

    @Test
    fun buttons_mapToTheQualityLevels() {
        assertEquals(0, ReviewRating.AGAIN.quality.value)
        assertEquals(3, ReviewRating.HARD.quality.value)
        assertEquals(4, ReviewRating.GOOD.quality.value)
        assertEquals(5, ReviewRating.EASY.quality.value)
    }

    @Test
    fun firstReviewAfterStartingToStudy_isScheduledOneDayAhead_andStaysInProgress() {
        val scheduled = service.scheduleFirstReview(fresh(), now)
        assertEquals(now + day, scheduled.nextReviewAt)
        assertEquals(1, scheduled.intervalDays)
        assertEquals(0, scheduled.repetitionCount)
        assertEquals(WordStatus.LEARNING, scheduled.learningStatus)
    }

    @Test
    fun wordTheUserSaysTheyKnow_graduatesAtOnce_butIsStillReviewedTomorrow() {
        val scheduled = service.scheduleKnownWord(fresh(), now)
        assertEquals(WordStatus.LEARNED, scheduled.learningStatus)
        assertEquals(LearningRules.GRADUATION_REPETITIONS, scheduled.repetitionCount)
        assertEquals(now + day, scheduled.nextReviewAt)
    }

    @Test
    fun word_graduatesOnlyAfterEnoughCorrectAnswersInARow() {
        var state = service.scheduleFirstReview(fresh(), now)
        repeat(LearningRules.GRADUATION_REPETITIONS - 1) {
            state = answer(state, AnswerQuality.CORRECT)
            assertEquals(WordStatus.LEARNING, state.learningStatus)
        }
        state = answer(state, AnswerQuality.CORRECT)
        assertEquals(LearningRules.GRADUATION_REPETITIONS, state.repetitionCount)
        assertEquals(WordStatus.LEARNED, state.learningStatus)
    }

    @Test
    fun aLapse_putsAGraduatedWordBackIntoTheStudySession() {
        var state = service.scheduleKnownWord(fresh(), now)
        assertEquals(WordStatus.LEARNED, state.learningStatus)
        state = answer(state, AnswerQuality.FORGOT)
        assertEquals(WordStatus.LEARNING, state.learningStatus)
        assertEquals(0, state.repetitionCount)
    }

    // ---- interval calculations ----

    @Test
    fun successfulAnswers_growTheIntervalEveryTime() {
        var state = service.scheduleFirstReview(fresh(), now)
        var previous = state.intervalDays
        repeat(6) {
            state = answer(state, AnswerQuality.CORRECT)
            assertTrue("interval must grow: ${state.intervalDays} <= $previous", state.intervalDays > previous)
            previous = state.intervalDays
        }
    }

    @Test
    fun correctAnswers_followTheDocumentedCurve() {
        var state = service.scheduleFirstReview(fresh(), now)
        state = answer(state, AnswerQuality.CORRECT)
        assertEquals(3, state.intervalDays)
        state = answer(state, AnswerQuality.CORRECT)
        assertEquals(6, state.intervalDays)
        // Third pass multiplies by the ease factor (2.5): 6 * 2.5 = 15.
        state = answer(state, AnswerQuality.CORRECT)
        assertEquals(15, state.intervalDays)
        assertEquals(3, state.repetitionCount)
    }

    @Test
    fun nextReviewAt_isNowPlusTheInterval() {
        val state = answer(fresh(), AnswerQuality.CORRECT)
        assertEquals(now + state.intervalDays * day, state.nextReviewAt)
        assertEquals(now, state.lastReviewedAt)
    }

    @Test
    fun easierAnswers_scheduleLongerThanHarderOnes() {
        val base = answer(answer(fresh(), AnswerQuality.CORRECT), AnswerQuality.CORRECT)
        val hard = answer(base, AnswerQuality.EFFORT).intervalDays
        val good = answer(base, AnswerQuality.CORRECT).intervalDays
        val easy = answer(base, AnswerQuality.EASY).intervalDays
        assertTrue("$hard < $good < $easy", hard < good && good < easy)
    }

    @Test
    fun interval_isNeverShorterThanOneDayOrLongerThanTheCap() {
        var state = fresh()
        repeat(60) {
            state = answer(state, AnswerQuality.EASY)
            assertTrue(state.intervalDays in Config.MIN_INTERVAL_DAYS..Config.MAX_INTERVAL_DAYS)
        }
        assertEquals(Config.MAX_INTERVAL_DAYS, state.intervalDays)
        assertEquals(Config.MIN_INTERVAL_DAYS, answer(state, AnswerQuality.FORGOT).intervalDays)
    }

    // ---- ease factor ----

    @Test
    fun ease_followsTheSm2FormulaForPassingAnswers() {
        assertEquals(2.5 - 0.14, service.nextEase(2.5, AnswerQuality.EFFORT), 1e-9)
        assertEquals(2.5, service.nextEase(2.5, AnswerQuality.CORRECT), 1e-9)
        assertEquals(2.6, service.nextEase(2.5, AnswerQuality.EASY), 1e-9)
    }

    @Test
    fun ease_dropsAfterDifficultOrIncorrectAnswers() {
        for (quality in listOf(
            AnswerQuality.FORGOT,
            AnswerQuality.HINT,
            AnswerQuality.DIFFICULT,
            AnswerQuality.EFFORT,
        )) {
            assertTrue("$quality must lower ease", service.nextEase(2.5, quality) < 2.5)
        }
    }

    @Test
    fun ease_neverDropsBelowTheMinimum() {
        var state = fresh()
        repeat(30) { state = answer(state, AnswerQuality.FORGOT) }
        assertEquals(Config.MIN_EASE, state.easeFactor, 0.0)
        assertEquals(1.3, service.nextEase(1.3, AnswerQuality.FORGOT), 0.0)
    }

    @Test
    fun ease_isCappedAtTheMaximum() {
        var ease = 2.5
        repeat(30) { ease = service.nextEase(ease, AnswerQuality.EASY) }
        assertEquals(Config.MAX_EASE, ease, 0.0)
    }

    // ---- forgotten words ----

    @Test
    fun forgottenWord_resetsRepetitionsAndComesBackTomorrow() {
        var state = service.scheduleFirstReview(fresh(), now)
        repeat(4) { state = answer(state, AnswerQuality.CORRECT) }
        assertTrue(state.intervalDays > 10)

        val lapsed = answer(state, AnswerQuality.FORGOT)
        assertEquals(0, lapsed.repetitionCount)
        assertEquals(Config.LAPSE_INTERVAL_DAYS, lapsed.intervalDays)
        assertEquals(now + Config.LAPSE_INTERVAL_DAYS * day, lapsed.nextReviewAt)
        assertEquals(WordStatus.LEARNING, lapsed.learningStatus)
        assertTrue(lapsed.easeFactor < state.easeFactor)
    }

    @Test
    fun everyBelowPassingQuality_isALapse() {
        val state = service.scheduleFirstReview(fresh(), now)
        for (quality in listOf(AnswerQuality.FORGOT, AnswerQuality.HINT, AnswerQuality.DIFFICULT)) {
            val lapsed = answer(state, quality)
            assertEquals(0, lapsed.repetitionCount)
            assertEquals(1, lapsed.consecutiveIncorrectAnswers)
        }
    }

    @Test
    fun consecutiveCounters_trackStreaksAndResetOnTheOppositeAnswer() {
        var state = fresh()
        state = answer(state, AnswerQuality.CORRECT)
        state = answer(state, AnswerQuality.EASY)
        assertEquals(2, state.consecutiveCorrectAnswers)
        assertEquals(0, state.consecutiveIncorrectAnswers)

        state = answer(state, AnswerQuality.FORGOT)
        state = answer(state, AnswerQuality.HINT)
        assertEquals(0, state.consecutiveCorrectAnswers)
        assertEquals(2, state.consecutiveIncorrectAnswers)

        state = answer(state, AnswerQuality.CORRECT)
        assertEquals(1, state.consecutiveCorrectAnswers)
        assertEquals(0, state.consecutiveIncorrectAnswers)
        assertEquals(WordStatus.LEARNING, state.learningStatus)
    }

    @Test
    fun recoveringAfterALapse_restartsTheCurveFromTheBeginning() {
        val lapsed = answer(service.scheduleFirstReview(fresh(), now), AnswerQuality.FORGOT)
        val recovered = answer(lapsed, AnswerQuality.CORRECT)
        assertEquals(1, recovered.repetitionCount)
        assertEquals(3, recovered.intervalDays)
    }

    // ---- determinism and safeguards ----

    @Test
    fun scheduling_isDeterministic() {
        val state = ReviewState("w", repetitionCount = 4, easeFactor = 2.1, intervalDays = 20)
        assertEquals(answer(state, AnswerQuality.CORRECT), answer(state, AnswerQuality.CORRECT))
    }

    @Test
    fun corruptedState_isRepairedInsteadOfCrashing() {
        val broken = ReviewState(
            wordId = "w",
            repetitionCount = -5,
            easeFactor = Double.NaN,
            intervalDays = -3,
            consecutiveCorrectAnswers = -1,
        )
        val result = answer(broken, AnswerQuality.CORRECT)
        assertEquals(1, result.repetitionCount)
        assertTrue(result.easeFactor in Config.MIN_EASE..Config.MAX_EASE)
        assertTrue(result.intervalDays >= Config.MIN_INTERVAL_DAYS)
    }

    @Test
    fun negativeTimestamp_isTreatedAsEpochStart() {
        val result = answer(fresh(), AnswerQuality.CORRECT, at = -42L)
        assertEquals(result.intervalDays * day, result.nextReviewAt)
        assertEquals(0L, result.lastReviewedAt)
    }
}

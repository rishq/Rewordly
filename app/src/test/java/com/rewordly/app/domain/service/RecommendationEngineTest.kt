package com.rewordly.app.domain.service

import com.rewordly.app.data.local.MockVocabulary
import com.rewordly.app.domain.model.AnswerQuality
import com.rewordly.app.domain.model.DailyLearningPlan
import com.rewordly.app.domain.model.Difficulty
import com.rewordly.app.domain.model.LearningLanguage
import com.rewordly.app.domain.model.LearningProfile
import com.rewordly.app.domain.model.LearningRules
import com.rewordly.app.domain.model.PartOfSpeech
import com.rewordly.app.domain.model.RecommendationReason
import com.rewordly.app.domain.model.TopicPreset
import com.rewordly.app.domain.model.Word
import com.rewordly.app.domain.model.WordProgress
import com.rewordly.app.domain.model.WordStatus
import com.rewordly.app.domain.model.WordTranslation
import com.rewordly.app.domain.model.WordWithProgress
import java.time.LocalDate
import java.time.ZoneId
import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the local, rules-based [RecommendationEngine].
 *
 * The engine has no hidden state and no randomness, so every test fixes the clock at one instant and
 * asserts the plan directly. The listed requirements are covered one by one: overdue priority, extra
 * attention for missed words, level and topic matching, the daily new-word limit, empty history,
 * no duplicate words, determinism, not overloading strong performers, and never rescheduling on its own.
 */
class RecommendationEngineTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2024, 5, 10)
    private val startOfToday = today.atStartOfDay(zone).toInstant().toEpochMilli()
    private val now = startOfToday + 10 * HOUR
    private val startOfTomorrow = startOfToday + DAY

    private val engine = RecommendationEngine()

    // ------------------------------------------------------------- overdue reviews are prioritized

    @Test
    fun overdueReviews_comeBeforeReviewsDueToday_whichComeBeforeWeakWords() {
        val overdue = bundled(0, next = startOfToday - 3 * DAY)
        val dueToday = bundled(1, next = startOfToday + 2 * HOUR)
        val weak = bundled(2, next = startOfTomorrow + 5 * DAY, incorrect = 3, consecutiveIncorrect = 3)

        val plan = plan(listOf(weak, dueToday, overdue), profile(sessionLength = 10))

        assertEquals(listOf(overdue.word.id, dueToday.word.id, weak.word.id), plan.reviews.map { it.word.id })
        assertEquals(RecommendationReason.DUE_FOR_REVIEW, plan.reviews[0].reason)
        assertEquals(RecommendationReason.DUE_FOR_REVIEW, plan.reviews[1].reason)
        assertEquals(RecommendationReason.DIFFICULT, plan.reviews[2].reason)
        assertEquals(2, plan.totalDue)
    }

    @Test
    fun overdueWords_areOrderedByHowLateTheyAre() {
        val veryLate = bundled(0, next = startOfToday - 10 * DAY)
        val slightlyLate = bundled(1, next = startOfToday - DAY)

        val plan = plan(listOf(slightlyLate, veryLate), profile())

        assertEquals(listOf(veryLate.word.id, slightlyLate.word.id), plan.reviews.map { it.word.id })
    }

    // ------------------------------------------------- frequently missed words get extra attention

    @Test
    fun frequentlyMissedWords_arePulledIn_withAnUnderstandableReason() {
        val oftenWrong = bundled(0, next = startOfTomorrow + 10 * DAY, incorrect = 3, correct = 1)
        val consistentlyDifficult = bundled(
            1,
            next = startOfTomorrow + 10 * DAY,
            incorrect = 4,
            consecutiveIncorrect = 3,
        )

        val plan = plan(listOf(oftenWrong, consistentlyDifficult), profile())

        assertEquals(2, plan.reviewCount)
        val reasons = plan.reviews.associate { it.word.id to it.reason }
        assertEquals(RecommendationReason.OFTEN_MISTAKEN, reasons[oftenWrong.word.id])
        assertEquals(RecommendationReason.DIFFICULT, reasons[consistentlyDifficult.word.id])
    }

    @Test
    fun weakWords_areOrderedByMistakes_andCappedPerSession() {
        val words = (0 until 7).map {
            bundled(it, next = startOfTomorrow + 10 * DAY, incorrect = 10 - it, consecutiveIncorrect = 2)
        }

        val plan = plan(words, profile(sessionLength = 20))

        assertEquals(LearningRules.MAX_WEAK_WORDS, plan.reviewCount)
        val mistakes = plan.reviews.map { it.item.progress.incorrectAnswers }
        assertEquals(listOf(10, 9, 8, 7, 6), mistakes)
    }

    // ---------------------------------------------------------------- new words match the level

    @Test
    fun newWords_matchTheUsersLevel_whenLevelDataIsAvailable() {
        val a1 = fresh(custom("w-a1", "alpha", Difficulty.A1))
        val a2 = fresh(custom("w-a2", "bravo", Difficulty.A2))
        val b1 = fresh(custom("w-b1", "charlie", Difficulty.B1))
        val b2 = fresh(custom("w-b2", "delta", Difficulty.B2))
        val c1 = fresh(custom("w-c1", "echo", Difficulty.C1))
        val c2 = fresh(custom("w-c2", "foxtrot", Difficulty.C2))

        val plan = plan(listOf(a1, a2, b1, b2, c1, c2), profile(level = Difficulty.B1))

        val ids = plan.newWords.map { it.word.id }
        assertFalse("too-hard words must be held back", ids.contains(c1.word.id))
        assertFalse("too-hard words must be held back", ids.contains(c2.word.id))
        assertEquals(Difficulty.B1, plan.newWords.first().word.difficulty)
        assertTrue(plan.newWords.all { it.reason == RecommendationReason.MATCHES_LEVEL })
    }

    @Test
    fun withoutLevelOrInterests_newWords_areMarkedNewForYou() {
        val words = (0 until 3).map { fresh(custom("w-$it", "quux$it", Difficulty.B2)) }

        val plan = plan(words, profile())

        assertEquals(3, plan.newWordCount)
        assertTrue(plan.newWords.all { it.reason == RecommendationReason.NEW_FOR_YOU })
    }

    @Test
    fun levelDistance_holdsBackWordsThatAreTooHard_butAlwaysAllowsEasierOnes() {
        assertEquals(0, RecommendationEngine.levelDistance(Difficulty.B1, Difficulty.B1))
        assertEquals(1, RecommendationEngine.levelDistance(Difficulty.B2, Difficulty.B1))
        assertEquals(-2, RecommendationEngine.levelDistance(Difficulty.A1, Difficulty.B1))
        assertNull(RecommendationEngine.levelDistance(Difficulty.C1, Difficulty.B1))
        assertEquals(0, RecommendationEngine.levelDistance(Difficulty.C2, null))
    }

    // ------------------------------------------------------------ topic preferences influence order

    @Test
    fun topicPreferences_influenceTheOrderingOfNewWords() {
        val travelWord = fresh(custom("w-travel", "journey", Difficulty.B1))
        val scienceWord = fresh(custom("w-science", "environment", Difficulty.B1))

        val withoutInterests = plan(listOf(travelWord, scienceWord), profile(level = Difficulty.B1))
        assertEquals(
            listOf(scienceWord.word.id, travelWord.word.id),
            withoutInterests.newWords.map { it.word.id },
        )

        val withInterests = plan(
            listOf(travelWord, scienceWord),
            profile(level = Difficulty.B1, interests = setOf(TopicPreset.TRAVEL)),
        )
        assertEquals(travelWord.word.id, withInterests.newWords.first().word.id)
        assertEquals(RecommendationReason.MATCHES_INTERESTS, withInterests.newWords.first().reason)
    }

    // ------------------------------------------------------------- the daily new-word limit holds

    @Test
    fun dailyNewWordTarget_isRespected() {
        val freshWords = (0 until 20).map { fresh(custom("w-$it", "quux$it", Difficulty.B1)) }

        val plan = plan(freshWords, profile(level = Difficulty.B1, dailyNewWordTarget = 3, sessionLength = 20))

        assertEquals(3, plan.newWordCount)
        assertEquals(3, plan.newWordBudget)
    }

    @Test
    fun wordsAlreadyLearnedToday_reduceTodaysNewWordBudget() {
        val freshWords = (0 until 20).map { fresh(custom("w-$it", "quux$it", Difficulty.B1)) }

        val plan = plan(
            freshWords,
            profile(level = Difficulty.B1, dailyNewWordTarget = 3, sessionLength = 20),
            learnedToday = 2,
        )

        assertEquals(1, plan.newWordCount)
    }

    // ------------------------------------------------------------------ empty history is handled

    @Test
    fun emptyLearningHistory_producesAnEmptyPlan() {
        val plan = plan(emptyList(), profile())

        assertTrue(plan.isEmpty)
        assertEquals(0, plan.reviewCount)
        assertEquals(0, plan.newWordCount)
        assertEquals(0, plan.totalDue)
    }

    @Test
    fun onlyNewWords_produceNewWordsButNoReviews() {
        val words = (0 until 3).map { fresh(custom("w-$it", "quux$it", Difficulty.B1)) }

        val plan = plan(words, profile(level = Difficulty.B1))

        assertEquals(0, plan.reviewCount)
        assertEquals(3, plan.newWordCount)
        assertFalse(plan.isEmpty)
    }

    // -------------------------------------------------------------------- no duplicate words

    @Test
    fun noWord_appearsTwiceInThePlan() {
        val words = listOf(
            bundled(0, next = startOfToday - DAY),
            bundled(1, next = startOfToday + HOUR),
            bundled(2, next = startOfTomorrow + DAY, incorrect = 3, consecutiveIncorrect = 2),
            bundled(3, status = WordStatus.NEW),
            bundled(4, status = WordStatus.NEW),
        )

        val plan = plan(words, profile(level = Difficulty.B1))

        val ids = (plan.reviews + plan.newWords).map { it.word.id }
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun aWordThatIsBothDueAndWeak_isRecommendedOnlyOnce() {
        val word = bundled(0, next = startOfToday - DAY, incorrect = 5, consecutiveIncorrect = 3)

        val plan = plan(listOf(word), profile())

        assertEquals(1, plan.reviewCount)
        assertEquals(RecommendationReason.DUE_FOR_REVIEW, plan.reviews.single().reason)
    }

    // --------------------------------------------------------------------- determinism

    @Test
    fun recommendations_areDeterministic_forIdenticalInputs() {
        val words = listOf(
            bundled(0, next = startOfToday - 2 * DAY),
            bundled(1, next = startOfToday + HOUR),
            bundled(2, next = startOfTomorrow + DAY, incorrect = 4, consecutiveIncorrect = 2),
            bundled(3, status = WordStatus.NEW),
            bundled(4, status = WordStatus.NEW),
            bundled(5, next = startOfToday - 5 * DAY),
        )
        val learningProfile = profile(level = Difficulty.B1, interests = setOf(TopicPreset.BUSINESS))

        val first = plan(words, learningProfile)
        val second = plan(words.shuffled(Random(11)), learningProfile)

        assertEquals(first.reviews.map { it.word.id }, second.reviews.map { it.word.id })
        assertEquals(first.newWords.map { it.word.id }, second.newWords.map { it.word.id })
        assertEquals(first.reviews.map { it.reason }, second.reviews.map { it.reason })
        assertEquals(first.newWords.map { it.reason }, second.newWords.map { it.reason })
    }

    // ------------------------------------------------- strong performers are not overloaded

    @Test
    fun strongPerformers_areNotOverloadedWithReviews() {
        val solid = (0 until 6).map {
            bundled(
                it,
                next = startOfTomorrow + 30 * DAY,
                correct = 12,
                incorrect = 0,
                consecutiveCorrect = 6,
                repetitions = 5,
            )
        }

        val plan = plan(solid, profile())

        assertTrue(plan.reviews.isEmpty())
        assertTrue(plan.isEmpty)
    }

    @Test
    fun strugglingUsers_getFewerNewWords_thanUsersWhoAreKeepingUp() {
        val freshWords = (0 until 20).map { fresh(custom("w-$it", "quux$it", Difficulty.B1)) }
        val learningProfile = profile(level = Difficulty.B1, dailyNewWordTarget = 10, sessionLength = 20)

        val keepingUp = plan(freshWords, learningProfile)
        assertFalse(keepingUp.easedOff)
        assertEquals(10, keepingUp.newWordCount)

        val struggling = (0 until LearningRules.MANY_WEAK_WORDS).map {
            bundled(it, next = startOfTomorrow + 10 * DAY, incorrect = 5, consecutiveIncorrect = 2)
        }
        val eased = plan(freshWords + struggling, learningProfile)
        assertTrue(eased.easedOff)
        assertEquals(5, eased.newWordBudget)
        assertEquals(5, eased.newWordCount)
    }

    @Test
    fun sessionLength_capsTheSession_butTotalDueStaysHonest() {
        val overdue = (0 until 20).map { bundled(it, next = startOfToday - (it + 1) * DAY) }

        val plan = plan(overdue, profile(sessionLength = 5))

        assertEquals(5, plan.reviewCount)
        assertEquals(20, plan.totalDue)
        assertEquals(0, plan.newWordCount)
    }

    // --------------------------------------------- the engine never reschedules on its own

    @Test
    fun aWrongAnswer_reschedulesIntoTheFuture_soItIsNotDueAgainToday() {
        val word = MockVocabulary.words[0]
        val learned = WordProgress(
            wordId = word.id,
            status = WordStatus.LEARNED,
            repetitionCount = 3,
            intervalDays = 3,
            nextReviewAt = now,
        )
        val scheduler = SpacedRepetitionService()
        val lapsed = scheduler.review(learned.reviewState, AnswerQuality.FORGOT, now)
        val afterLapse = WordWithProgress(word, learned.withReviewState(lapsed))

        assertTrue("a lapse must be rescheduled in the future", lapsed.nextReviewAt!! > now)
        val plan = plan(listOf(afterLapse), profile(level = Difficulty.A1))
        assertTrue("a word answered wrong must not be due again the same day", plan.reviews.isEmpty())
    }

    // ------------------------------------------------------------------------- helpers

    private fun plan(
        words: List<WordWithProgress>,
        profile: LearningProfile,
        learnedToday: Int = 0,
    ): DailyLearningPlan = engine.recommend(RecommendationInput(words, profile, now, zone, learnedToday))

    private fun profile(
        level: Difficulty? = null,
        interests: Set<TopicPreset> = emptySet(),
        dailyNewWordTarget: Int = LearningProfile.DEFAULT_DAILY_NEW_WORDS,
        sessionLength: Int = LearningProfile.DEFAULT_SESSION_LENGTH,
    ) = LearningProfile(
        level = level,
        interests = interests,
        dailyNewWordTarget = dailyNewWordTarget,
        sessionLength = sessionLength,
    )

    private fun bundled(
        index: Int,
        status: WordStatus = WordStatus.LEARNED,
        next: Long? = null,
        incorrect: Int = 0,
        correct: Int = 0,
        consecutiveIncorrect: Int = 0,
        consecutiveCorrect: Int = 0,
        repetitions: Int = 0,
    ): WordWithProgress {
        val word = MockVocabulary.words[index]
        return WordWithProgress(
            word,
            WordProgress(
                wordId = word.id,
                status = status,
                nextReviewAt = next,
                incorrectAnswers = incorrect,
                correctAnswers = correct,
                consecutiveIncorrect = consecutiveIncorrect,
                consecutiveCorrect = consecutiveCorrect,
                repetitionCount = repetitions,
            ),
        )
    }

    private fun custom(
        id: String,
        text: String,
        difficulty: Difficulty,
        translation: String = "перевод $id",
        definition: String = "",
        related: List<String> = emptyList(),
        synonyms: List<String> = emptyList(),
    ): Word = Word(
        id = id,
        language = LearningLanguage.ENGLISH,
        text = text,
        translation = WordTranslation("ru", translation),
        pronunciation = "",
        partOfSpeech = PartOfSpeech.NOUN,
        difficulty = difficulty,
        definition = definition,
        relatedWords = related,
        synonyms = synonyms,
    )

    private fun fresh(word: Word): WordWithProgress =
        WordWithProgress(word, WordProgress(wordId = word.id, status = WordStatus.NEW))

    private companion object {
        const val HOUR = 60L * 60 * 1000
        const val DAY = 24 * HOUR
    }
}

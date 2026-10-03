package com.rewordly.app.feature.review

import androidx.lifecycle.SavedStateHandle
import com.rewordly.app.core.common.AppError
import com.rewordly.app.data.local.MockVocabulary
import com.rewordly.app.domain.model.AnswerQuality
import com.rewordly.app.domain.model.ReviewRating
import com.rewordly.app.domain.model.WordProgress
import com.rewordly.app.domain.model.WordStatus
import com.rewordly.app.domain.model.WordWithProgress
import com.rewordly.app.domain.service.SpacedRepetitionService
import com.rewordly.app.domain.usecase.BuildReviewQueueUseCase
import com.rewordly.app.domain.usecase.SubmitReviewAnswerUseCase
import com.rewordly.app.testing.FakeReviewRepository
import com.rewordly.app.testing.FakeSettingsRepository
import com.rewordly.app.testing.FakeVocabularyRepository
import com.rewordly.app.testing.MainDispatcherRule
import com.rewordly.app.testing.TestTimeProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReviewViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val time = TestTimeProvider()
    private val day = 24L * 60 * 60 * 1000

    private fun dueWord(index: Int, nextOffsetDays: Long = -1) = MockVocabulary.words[index].let {
        WordWithProgress(
            it,
            WordProgress(it.id, status = WordStatus.LEARNED, nextReviewAt = time.nowMillis() + nextOffsetDays * day),
        )
    }

    private val vocabulary = FakeVocabularyRepository((0 until 4).map { dueWord(it) })
    private val reviews = FakeReviewRepository(vocabulary)
    private val settings = FakeSettingsRepository()
    private val handle = SavedStateHandle()

    private fun viewModel(handle: SavedStateHandle = this.handle) = ReviewViewModel(
        savedStateHandle = handle,
        settingsRepository = settings,
        vocabularyRepository = vocabulary,
        buildReviewQueue = BuildReviewQueueUseCase(vocabulary, time),
        submitReviewAnswer = SubmitReviewAnswerUseCase(reviews, SpacedRepetitionService(), time),
        timeProvider = time,
    )

    private fun ReviewViewModel.inProgress() = uiState.value as ReviewUiState.InProgress

    private fun ReviewViewModel.answer(rating: ReviewRating) {
        onEvent(ReviewUiEvent.Reveal)
        onEvent(ReviewUiEvent.Answer(rating))
    }

    // ---- progression ----

    @Test
    fun startsWithTheFirstDueWord_hiddenTranslation_andTheDueCount() = runTest {
        val state = viewModel().inProgress()
        assertEquals(0, state.index)
        assertEquals(4, state.total)
        assertEquals(4, state.dueToday)
        assertFalse(state.revealed)
        assertNull(state.feedback)
        assertEquals(0f, state.fraction, 0f)
    }

    @Test
    fun reveal_showsTheTranslationWithoutAdvancing() = runTest {
        val vm = viewModel()
        vm.onEvent(ReviewUiEvent.Reveal)
        assertTrue(vm.inProgress().revealed)
        assertEquals(0, vm.inProgress().index)
    }

    @Test
    fun answering_movesToTheNextCard_hiddenAgain_withFeedback() = runTest {
        val vm = viewModel()
        vm.answer(ReviewRating.GOOD)
        val state = vm.inProgress()
        assertEquals(1, state.index)
        assertFalse(state.revealed)
        assertEquals(ReviewRating.GOOD, state.feedback!!.rating)
        assertEquals(0.25f, state.fraction, 0f)
    }

    @Test
    fun intervalPreview_isOfferedForEveryButton() = runTest {
        val intervals = viewModel().inProgress().intervals
        assertEquals(ReviewRating.entries.toSet(), intervals.keys)
        assertTrue(intervals.getValue(ReviewRating.AGAIN) < intervals.getValue(ReviewRating.EASY))
    }

    // ---- answer submission ----

    @Test
    fun answer_isScheduledAndPersisted_forTheRightWord() = runTest {
        val vm = viewModel()
        val first = vm.inProgress().current.word.id
        vm.answer(ReviewRating.EASY)

        val submission = reviews.submissions.single()
        assertEquals(first, submission.wordId)
        assertEquals(AnswerQuality.EASY, submission.quality)
        val progress = vocabulary.progressOf(first)
        assertEquals(1, progress.repetitionCount)
        assertEquals(time.nowMillis() + progress.intervalDays * day, progress.nextReviewAt)
        assertEquals(1, progress.correctAnswers)
    }

    @Test
    fun againButton_countsAsIncorrect_andResetsTheWord() = runTest {
        val vm = viewModel()
        val first = vm.inProgress().current.word.id
        vm.answer(ReviewRating.AGAIN)
        val progress = vocabulary.progressOf(first)
        assertEquals(1, progress.incorrectAnswers)
        assertEquals(WordStatus.LEARNING, progress.status)
        assertEquals(1, progress.intervalDays)
    }

    /** A swipe is a decision on its own, so checking the translation first stays optional. */
    @Test
    fun answerBeforeReveal_isAccepted() = runTest {
        val vm = viewModel()
        vm.onEvent(ReviewUiEvent.Answer(ReviewRating.GOOD))
        assertEquals(1, reviews.submissions.size)
        assertEquals(1, vm.inProgress().index)
    }

    @Test
    fun repeatedTapsWhileSaving_scheduleTheWordOnlyOnce() = runTest {
        val gate = CompletableDeferred<Unit>()
        reviews.gate = gate
        val vm = viewModel()
        vm.onEvent(ReviewUiEvent.Reveal)
        repeat(5) { vm.onEvent(ReviewUiEvent.Answer(ReviewRating.GOOD)) }
        assertTrue(vm.inProgress().isSubmitting)
        gate.complete(Unit)

        assertEquals(1, reviews.submissions.size)
        assertEquals(1, vm.inProgress().index)
    }

    @Test
    fun resubmittingTheSameWordInTheSameSession_isReportedAsDuplicate() = runTest {
        val useCase = SubmitReviewAnswerUseCase(reviews, SpacedRepetitionService(), time)
        val id = MockVocabulary.words[0].id
        useCase(id, "s1", ReviewRating.GOOD, 1_000)
        useCase(id, "s1", ReviewRating.GOOD, 1_000)
        assertEquals(1, reviews.submissions.size)
        assertEquals(1, vocabulary.progressOf(id).repetitionCount)
    }

    @Test
    fun failedSave_keepsTheCard_showsAnError_andAllowsRetry() = runTest {
        val vm = viewModel()
        vm.onEvent(ReviewUiEvent.Reveal)
        reviews.failNext = true
        vm.onEvent(ReviewUiEvent.Answer(ReviewRating.GOOD))
        assertTrue(vm.inProgress().submitFailed)
        assertFalse(vm.inProgress().isSubmitting)
        assertEquals(0, vm.inProgress().index)

        vm.onEvent(ReviewUiEvent.Answer(ReviewRating.GOOD))
        assertEquals(1, vm.inProgress().index)
        assertFalse(vm.inProgress().submitFailed)
    }

    @Test
    fun studyTime_isMeasuredPerCard_andCapped() = runTest {
        val vm = viewModel()
        time.advanceMillis(5_000)
        vm.answer(ReviewRating.GOOD)
        time.advanceMillis(10 * 60 * 1000)
        vm.answer(ReviewRating.GOOD)
        assertEquals(5_000L, reviews.submissions[0].durationMs)
        assertEquals(120_000L, reviews.submissions[1].durationMs)
    }

    // ---- completion ----

    @Test
    fun answeringEveryCard_finishesWithASummary() = runTest {
        val vm = viewModel()
        vm.answer(ReviewRating.GOOD)
        vm.answer(ReviewRating.AGAIN)
        vm.answer(ReviewRating.EASY)
        vm.answer(ReviewRating.HARD)

        val summary = (vm.uiState.value as ReviewUiState.Finished).summary
        assertEquals(4, summary.total)
        assertEquals(3, summary.remembered)
        assertEquals(1, summary.answers[ReviewRating.AGAIN])
        assertEquals(0, summary.remainingDue)
    }

    @Test
    fun largeBacklog_isServedInBatches_andReportsWhatRemains() = runTest {
        val many = (0 until 35).map { i ->
            val base = MockVocabulary.words.first()
            WordWithProgress(
                base.copy(id = "extra-$i"),
                WordProgress("extra-$i", status = WordStatus.LEARNED, nextReviewAt = time.nowMillis() - day),
            )
        }
        vocabulary.items.value = many
        val vm = viewModel()
        assertEquals(BuildReviewQueueUseCase.DEFAULT_SESSION_SIZE, vm.inProgress().total)
        assertEquals(35, vm.inProgress().dueToday)

        repeat(BuildReviewQueueUseCase.DEFAULT_SESSION_SIZE) { vm.answer(ReviewRating.GOOD) }
        assertEquals(5, (vm.uiState.value as ReviewUiState.Finished).summary.remainingDue)

        vm.onEvent(ReviewUiEvent.Restart)
        assertEquals(5, vm.inProgress().total)
    }

    // ---- empty, error, interruption ----

    @Test
    fun nothingDue_showsTheEmptyState() = runTest {
        vocabulary.items.value = (0 until 3).map { dueWord(it, nextOffsetDays = 5) }
        assertEquals(ReviewUiState.Empty, viewModel().uiState.value)
    }

    @Test
    fun databaseError_showsAnErrorThatRestartCanRecoverFrom() = runTest {
        vocabulary.failure = IllegalStateException("disk")
        val vm = viewModel()
        assertTrue((vm.uiState.value as ReviewUiState.Error).error is AppError.Database)

        vocabulary.failure = null
        vm.onEvent(ReviewUiEvent.Restart)
        assertTrue(vm.uiState.value is ReviewUiState.InProgress)
    }

    @Test
    fun interruptedSession_resumesAtTheSameCard_afterRecreation() = runTest {
        val vm = viewModel()
        vm.answer(ReviewRating.GOOD)
        vm.answer(ReviewRating.GOOD)

        val restored = viewModel(handle).inProgress()
        assertEquals(2, restored.index)
        assertEquals(4, restored.total)
        assertEquals(vm.inProgress().current.word.id, restored.current.word.id)
    }

    @Test
    fun restoredSession_doesNotResubmitAnsweredWords() = runTest {
        val vm = viewModel()
        vm.answer(ReviewRating.GOOD)
        val restored = viewModel(handle)
        restored.answer(ReviewRating.GOOD)
        assertEquals(2, reviews.submissions.size)
        assertEquals(2, reviews.submissions.map { it.wordId }.distinct().size)
    }

    @Test
    fun lostSessionState_rebuildsTheQueueFromWhatIsStillDue() = runTest {
        val vm = viewModel()
        vm.answer(ReviewRating.GOOD)
        vm.answer(ReviewRating.GOOD)

        // Process death without saved state: answered words were already rescheduled in the database.
        val fresh = viewModel(SavedStateHandle()).inProgress()
        assertEquals(2, fresh.total)
        assertEquals(0, fresh.index)
    }

    @Test
    fun refresh_rebuildsAnEmptyQueue_butKeepsASessionInProgress() = runTest {
        vocabulary.items.value = (0 until 2).map { dueWord(it, nextOffsetDays = 3) }
        val vm = viewModel()
        assertEquals(ReviewUiState.Empty, vm.uiState.value)

        vocabulary.items.value = (0 until 2).map { dueWord(it) }
        vm.onEvent(ReviewUiEvent.Refresh)
        assertTrue(vm.uiState.value is ReviewUiState.InProgress)

        vm.answer(ReviewRating.GOOD)
        val before = vm.inProgress()
        vm.onEvent(ReviewUiEvent.Refresh)
        assertEquals(before.current.word.id, vm.inProgress().current.word.id)
        assertNotNull(vm.inProgress().feedback)
    }

    @Test
    fun finishedSummary_isKeptSameDay_butReplacedOnANewDay() = runTest {
        vocabulary.items.value = listOf(dueWord(0))
        val vm = viewModel()
        vm.answer(ReviewRating.GOOD)
        vm.onEvent(ReviewUiEvent.Refresh)
        assertTrue(vm.uiState.value is ReviewUiState.Finished)

        time.advanceDays(4)
        vm.onEvent(ReviewUiEvent.Refresh)
        assertTrue(vm.uiState.value is ReviewUiState.InProgress)
    }
}

package com.rewordly.app.feature.learn

import androidx.lifecycle.SavedStateHandle
import com.rewordly.app.data.local.MockVocabulary
import com.rewordly.app.domain.model.AnswerQuality
import com.rewordly.app.domain.model.LearningRules
import com.rewordly.app.domain.model.UserSettings
import com.rewordly.app.domain.model.WordProgress
import com.rewordly.app.domain.model.WordStatus
import com.rewordly.app.domain.model.WordWithProgress
import com.rewordly.app.domain.service.SpacedRepetitionService
import com.rewordly.app.domain.usecase.MarkWordKnownUseCase
import com.rewordly.app.domain.usecase.StartLearningSessionUseCase
import com.rewordly.app.domain.usecase.StartStudyingWordUseCase
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LearnViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val day = 24L * 60 * 60 * 1000
    private val words = MockVocabulary.words.take(4).map { WordWithProgress(it, WordProgress(it.id)) }
    private val repository = FakeVocabularyRepository(words)
    private val settings = FakeSettingsRepository(UserSettings(dailyGoal = 3))
    private val reviews = FakeReviewRepository(repository)
    private val service = SpacedRepetitionService()
    private val time = TestTimeProvider()
    private val handle = SavedStateHandle()

    private fun viewModel(handle: SavedStateHandle = this.handle) = LearnViewModel(
        savedStateHandle = handle,
        settingsRepository = settings,
        vocabularyRepository = repository,
        startLearningSession = StartLearningSessionUseCase(repository, time),
        startStudyingWord = StartStudyingWordUseCase(reviews, service, time),
        markWordKnown = MarkWordKnownUseCase(reviews, service, time),
        submitReviewAnswer = SubmitReviewAnswerUseCase(reviews, service, time),
        timeProvider = time,
    )

    private fun LearnViewModel.content() = uiState.value as LearnUiState.Content

    private fun LearnViewModel.currentId() = content().current.word.id

    /** Every word already has one answer behind it, so the cards review instead of triaging. */
    private fun wordsAlreadyInProgress() {
        repository.items.value = repository.items.value.map {
            it.copy(progress = it.progress.copy(status = WordStatus.LEARNING, correctAnswers = 1))
        }
    }

    // ---- session ----

    @Test
    fun session_sizeFollowsTheDailyGoal() = runTest {
        val content = viewModel().content()
        assertEquals(3, content.total)
        assertEquals(0, content.index)
        assertEquals(0, content.completedCount)
    }

    @Test
    fun session_survivesRecreation() = runTest {
        viewModel().onEvent(LearnUiEvent.Answer(remembered = true))
        val restored = viewModel().content()
        assertEquals(1, restored.index)
        assertEquals(3, restored.total)
    }

    @Test
    fun restart_startsAFreshSession() = runTest {
        val vm = viewModel()
        vm.onEvent(LearnUiEvent.Answer(remembered = true))
        vm.onEvent(LearnUiEvent.Restart)
        val state = vm.content()
        assertEquals(0, state.completedCount)
        assertEquals(0, state.index)
    }

    @Test
    fun session_onlyContainsWordsThatHaveNotGraduatedYet() = runTest {
        repository.items.value = repository.items.value.mapIndexed { index, item ->
            if (index == 0) {
                item.copy(
                    progress = item.progress.copy(
                        status = WordStatus.LEARNED,
                        repetitionCount = LearningRules.GRADUATION_REPETITIONS,
                    ),
                )
            } else {
                item
            }
        }
        val state = viewModel().content()
        assertEquals(3, state.total)
        assertTrue(state.items.none { it.progress.status == WordStatus.LEARNED })
    }

    @Test
    fun everyWordGraduated_showsTheEmptyState() = runTest {
        repository.items.value = repository.items.value.map {
            it.copy(
                progress = it.progress.copy(
                    status = WordStatus.LEARNED,
                    repetitionCount = LearningRules.GRADUATION_REPETITIONS,
                ),
            )
        }
        assertTrue(viewModel().uiState.value is LearnUiState.Empty)
    }

    // ---- the first encounter: know it or study it ----

    @Test
    fun aWordNeverAnswered_isOfferedAsATriage() = runTest {
        assertTrue(viewModel().content().isFirstEncounter)
    }

    @Test
    fun sayingIAlreadyKnowIt_graduatesTheWordRightAway_andAdvances() = runTest {
        val vm = viewModel()
        val first = vm.currentId()
        vm.onEvent(LearnUiEvent.Answer(remembered = true))

        val progress = repository.progressOf(first)
        assertEquals(WordStatus.LEARNED, progress.status)
        assertEquals(LearningRules.GRADUATION_REPETITIONS, progress.repetitionCount)
        assertEquals(0, progress.correctAnswers)
        assertEquals(1, vm.content().index)
        assertEquals(1, vm.content().completedCount)
    }

    @Test
    fun choosingToStudy_putsTheWordIntoTheRotation_withoutCountingAnAnswer() = runTest {
        val vm = viewModel()
        val first = vm.currentId()
        vm.onEvent(LearnUiEvent.Answer(remembered = false))

        val progress = repository.progressOf(first)
        assertEquals(WordStatus.LEARNING, progress.status)
        assertEquals(0, progress.repetitionCount)
        assertEquals(0, progress.correctAnswers)
        assertEquals(0, progress.incorrectAnswers)
        assertEquals(time.nowMillis() + day, progress.nextReviewAt)
        assertEquals(1, vm.content().index)
    }

    // ---- a word already in the rotation ----

    @Test
    fun aWordAlreadyInProgress_isReviewed_notTriaged() = runTest {
        wordsAlreadyInProgress()
        assertFalse(viewModel().content().isFirstEncounter)
    }

    @Test
    fun rememberingAWordInProgress_schedulesIt() = runTest {
        wordsAlreadyInProgress()
        val vm = viewModel()
        vm.onEvent(LearnUiEvent.Answer(remembered = true))

        val submission = reviews.submissions.single()
        assertEquals(AnswerQuality.CORRECT, submission.quality)
        assertEquals(1, vm.content().index)
    }

    @Test
    fun forgettingAWordInProgress_countsAsALapse_andKeepsItInTheRotation() = runTest {
        wordsAlreadyInProgress()
        val vm = viewModel()
        val first = vm.currentId()
        vm.onEvent(LearnUiEvent.Answer(remembered = false))

        assertEquals(AnswerQuality.FORGOT, reviews.submissions.single().quality)
        val progress = repository.progressOf(first)
        assertEquals(WordStatus.LEARNING, progress.status)
        assertEquals(1, progress.incorrectAnswers)
        assertEquals(1, progress.intervalDays)
    }

    @Test
    fun studyTime_isMeasuredPerCard_andCapped() = runTest {
        wordsAlreadyInProgress()
        val vm = viewModel()
        time.advanceMillis(5_000)
        vm.onEvent(LearnUiEvent.Answer(remembered = true))
        time.advanceMillis(10 * 60 * 1000)
        vm.onEvent(LearnUiEvent.Answer(remembered = true))

        assertEquals(5_000L, reviews.submissions[0].durationMs)
        assertEquals(120_000L, reviews.submissions[1].durationMs)
    }

    // ---- the card itself ----

    @Test
    fun reveal_showsTheAnswerWithoutAnswering() = runTest {
        val vm = viewModel()
        assertFalse(vm.content().revealed)
        vm.onEvent(LearnUiEvent.Reveal)
        assertTrue(vm.content().revealed)
        assertTrue(reviews.submissions.isEmpty())
    }

    @Test
    fun answering_movesToTheNextCard_withTheAnswerHiddenAgain() = runTest {
        val vm = viewModel()
        vm.onEvent(LearnUiEvent.Reveal)
        vm.onEvent(LearnUiEvent.Answer(remembered = true))
        assertFalse(vm.content().revealed)
        assertEquals(1, vm.content().index)
    }

    @Test
    fun togglingSaved_isForwarded() = runTest {
        val vm = viewModel()
        val first = vm.currentId()
        vm.onEvent(LearnUiEvent.ToggleSaved)
        assertTrue(repository.progressOf(first).isSaved)
    }

    @Test
    fun openingACard_isRecordedAsAView() = runTest {
        viewModel()
        assertEquals(1, repository.views.value)
    }

    // ---- completion and failure ----

    @Test
    fun answeringEveryCard_finishesTheSession() = runTest {
        val vm = viewModel()
        repeat(3) { vm.onEvent(LearnUiEvent.Answer(remembered = true)) }
        assertTrue(vm.uiState.value is LearnUiState.Finished)
        assertEquals(3, (vm.uiState.value as LearnUiState.Finished).completedCount)
    }

    @Test
    fun whileSaving_furtherAnswersAreDropped() = runTest {
        wordsAlreadyInProgress()
        val gate = CompletableDeferred<Unit>()
        reviews.gate = gate
        val vm = viewModel()
        repeat(5) { vm.onEvent(LearnUiEvent.Answer(remembered = true)) }
        assertTrue(vm.content().isSubmitting)
        gate.complete(Unit)

        assertEquals(1, reviews.submissions.size)
        assertEquals(1, vm.content().index)
    }

    @Test
    fun failedSave_keepsTheCard_andAllowsRetry() = runTest {
        wordsAlreadyInProgress()
        val vm = viewModel()
        reviews.failNext = true
        vm.onEvent(LearnUiEvent.Answer(remembered = true))
        assertTrue(vm.content().submitFailed)
        assertEquals(0, vm.content().index)

        vm.onEvent(LearnUiEvent.Answer(remembered = true))
        assertFalse(vm.content().submitFailed)
        assertEquals(1, vm.content().index)
    }
}

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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
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
    fun aFinishedBatch_chainsStraightIntoTheNextOne() = runTest {
        val vm = viewModel()
        repeat(3) { vm.onEvent(LearnUiEvent.Answer(remembered = true)) }
        // Three fresh words graduated, so only the fourth is left for the next chunk of the loop.
        assertTrue(vm.uiState.value is LearnUiState.Content)
        assertEquals(1, vm.content().total)
        assertEquals(0, vm.content().index)
    }

    @Test
    fun theNextBatch_isPreparedWhileTheUserIsOnTheLastCard() = runTest {
        val vm = viewModel()
        val readsAtStart = repository.vocabularyReads
        repeat(2) { vm.onEvent(LearnUiEvent.Answer(remembered = true)) }
        // The last card is on screen, so the vocabulary for the next batch is already read. Reading it
        // at the boundary is what used to freeze the app for a second or two every ten words.
        assertTrue(repository.vocabularyReads > readsAtStart)
    }

    @Test
    fun aFinishedBatch_neverBlanksTheScreen() = runTest {
        val vm = viewModel()
        repeat(2) { vm.onEvent(LearnUiEvent.Answer(remembered = true)) }
        val seen = mutableListOf<LearnUiState>()
        val watcher = launch(start = CoroutineStart.UNDISPATCHED) { vm.uiState.collect { seen += it } }
        vm.onEvent(LearnUiEvent.Answer(remembered = true))
        watcher.cancel()

        assertFalse(seen.any { it is LearnUiState.Loading })
        assertTrue(seen.any { it is LearnUiState.Content })
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

    // ---- typed answers, letter hints and variants ----

    /** The card is Russian-first now, so everything the user produces or picks is the English word. */
    private fun LearnViewModel.correctAnswer(): String = content().current.word.text

    @Test
    fun typingTheCorrectWord_answersTheCard() = runTest {
        val vm = viewModel()
        val first = vm.content().current.word.id
        vm.onEvent(LearnUiEvent.TypeText(vm.correctAnswer().uppercase()))
        vm.onEvent(LearnUiEvent.TypeCheck)

        assertEquals(1, vm.content().index)
        assertEquals(WordStatus.LEARNED, repository.progressOf(first).status)
    }

    @Test
    fun aWrongTypedAnswer_showsAMismatch_keepsTheCard() = runTest {
        val vm = viewModel()
        vm.onEvent(LearnUiEvent.TypeText("tralala"))
        vm.onEvent(LearnUiEvent.TypeCheck)

        assertTrue(vm.content().typeMismatch)
        assertEquals(0, vm.content().index)
        assertTrue(reviews.submissions.isEmpty())
        // Editing clears the mismatch again.
        vm.onEvent(LearnUiEvent.TypeText("tralal"))
        assertFalse(vm.content().typeMismatch)
    }

    @Test
    fun aLetterHint_startsFromTheFirstLetter_thenBuildsTheWord() = runTest {
        val vm = viewModel()
        val target = vm.correctAnswer()
        vm.onEvent(LearnUiEvent.TypeHint)
        assertEquals(target.take(1), vm.content().typed)
        vm.onEvent(LearnUiEvent.TypeHint)
        vm.onEvent(LearnUiEvent.TypeHint)
        assertEquals(target.take(3), vm.content().typed)
        assertFalse(vm.content().typeMismatch)
    }

    @Test
    fun aLetterHint_afterAWrongStart_wipesTheText_andWritesTheMissingLetter() = runTest {
        val vm = viewModel()
        val target = vm.correctAnswer()
        vm.onEvent(LearnUiEvent.TypeText("tralala"))
        vm.onEvent(LearnUiEvent.TypeHint)

        assertEquals(target.take(1), vm.content().typed)
        assertFalse(vm.content().typeMismatch)
    }

    @Test
    fun aLetterHint_withASkippedLetter_writesTheMissingLetterBack() = runTest {
        val vm = viewModel()
        val target = vm.correctAnswer()
        // The same letters, one of them left out in the middle.
        vm.onEvent(LearnUiEvent.TypeText(target.removeRange(MIDDLE, MIDDLE + 1)))
        vm.onEvent(LearnUiEvent.TypeHint)

        assertEquals(target, vm.content().typed)
    }

    @Test
    fun typingIsLenientAboutCaseButNotAboutTheWord() = runTest {
        val vm = viewModel()
        vm.onEvent(LearnUiEvent.TypeText(vm.correctAnswer().lowercase().replaceFirstChar { it.uppercase() }))
        vm.onEvent(LearnUiEvent.TypeCheck)
        assertEquals(1, vm.content().index)
    }

    @Test
    fun theCorrectVariant_isAmongTheOfferedOnes() = runTest {
        val vm = viewModel()
        val variants = vm.content().variants
        assertEquals(3, variants.size)
        assertTrue(variants.contains(vm.correctAnswer()))
    }

    @Test
    fun pickingTheCorrectVariant_flashesItGreen_thenAnswersTheCard() = runTest {
        val vm = viewModel()
        vm.onEvent(LearnUiEvent.PickVariant(vm.correctAnswer()))
        // The card holds still while the pick is shown, then advances.
        assertEquals(0, vm.content().index)
        assertTrue(vm.content().correctPick)
        assertEquals(vm.correctAnswer(), vm.content().pickedVariant)

        // The green goes out before the card changes, so the next word never shares the screen with it.
        advanceTimeBy(500)
        assertEquals(0, vm.content().index)
        assertEquals(null, vm.content().pickedVariant)
        assertTrue(vm.content().correctPick)

        advanceUntilIdle()
        assertEquals(1, vm.content().index)
    }

    @Test
    fun whileTheCorrectPickFlashes_furtherPicksAreIgnored() = runTest {
        val vm = viewModel()
        val wrong = vm.content().variants.first { it != vm.correctAnswer() }
        vm.onEvent(LearnUiEvent.PickVariant(vm.correctAnswer()))
        vm.onEvent(LearnUiEvent.PickVariant(wrong))

        assertTrue(vm.content().correctPick)
        assertEquals(vm.correctAnswer(), vm.content().pickedVariant)
        assertEquals(0, vm.content().index)
    }

    @Test
    fun anAnswerGivenDuringTheFlash_neverLeaksOntoTheNextCard() = runTest {
        val vm = viewModel()
        vm.onEvent(LearnUiEvent.PickVariant(vm.correctAnswer()))
        vm.onEvent(LearnUiEvent.Answer(remembered = true))
        advanceUntilIdle()

        assertEquals(1, vm.content().index)
    }

    @Test
    fun pickingAWrongVariant_showsAMismatch_keepsTheCard() = runTest {
        val vm = viewModel()
        val wrong = vm.content().variants.first { it != vm.correctAnswer() }
        vm.onEvent(LearnUiEvent.PickVariant(wrong))

        assertTrue(vm.content().typeMismatch)
        assertEquals(wrong, vm.content().pickedVariant)
        assertEquals(0, vm.content().index)
        assertTrue(reviews.submissions.isEmpty())
    }

    @Test
    fun selectingATypeMode_foldsAndUnfoldsEachTime() = runTest {
        val vm = viewModel()
        vm.onEvent(LearnUiEvent.SelectMode(StudyMode.TYPE))
        assertEquals(StudyMode.TYPE, vm.content().answerMode)
        vm.onEvent(LearnUiEvent.SelectMode(StudyMode.TYPE))
        assertEquals(null, vm.content().answerMode)
    }

    @Test
    fun choosingToSeeTheTranslation_revealsIt_andAnswersStayAvailable() = runTest {
        val vm = viewModel()
        vm.onEvent(LearnUiEvent.SelectMode(StudyMode.SEE))
        assertEquals(StudyMode.SEE, vm.content().answerMode)
        assertTrue(vm.content().revealed)
        assertTrue(reviews.submissions.isEmpty())

        // The reveal never counts as an answer on its own; the answer bar still decides.
        vm.onEvent(LearnUiEvent.Answer(remembered = true))
        assertEquals(1, vm.content().index)
    }

    @Test
    fun openingACard_isRecordedAsAView() = runTest {
        viewModel()
        assertEquals(1, repository.views.value)
    }

    // ---- completion and failure ----

    @Test
    fun whenTheVocabularyRunsOut_theLoopEndsWithTheEmptyState() = runTest {
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

    @Test
    fun theChosenMode_isGoneOnTheNextCard() = runTest {
        val vm = viewModel()
        vm.onEvent(LearnUiEvent.SelectMode(StudyMode.OPTIONS))
        assertEquals(StudyMode.OPTIONS, vm.content().answerMode)

        vm.onEvent(LearnUiEvent.Answer(remembered = true))
        assertEquals(null, vm.content().answerMode)
    }

    private companion object {
        /** The letter the hint test leaves out of the middle of the word. */
        const val MIDDLE = 4
    }
}

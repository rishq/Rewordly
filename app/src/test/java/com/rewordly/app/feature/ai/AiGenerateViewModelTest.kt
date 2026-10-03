package com.rewordly.app.feature.ai

import androidx.lifecycle.SavedStateHandle
import com.rewordly.app.core.common.AppError
import com.rewordly.app.core.common.AppResult
import com.rewordly.app.data.local.MockVocabulary
import com.rewordly.app.domain.model.Difficulty
import com.rewordly.app.domain.model.GenerationInput
import com.rewordly.app.domain.model.GenerationMode
import com.rewordly.app.domain.model.GenerationResult
import com.rewordly.app.domain.model.GenerationSettings
import com.rewordly.app.domain.model.TopicPreset
import com.rewordly.app.domain.model.UsageInfo
import com.rewordly.app.domain.model.WordProgress
import com.rewordly.app.domain.model.WordWithProgress
import com.rewordly.app.domain.service.GenerationSessionStore
import com.rewordly.app.domain.usecase.DetectDuplicatesUseCase
import com.rewordly.app.domain.usecase.GenerateVocabularyUseCase
import com.rewordly.app.domain.usecase.InputError
import com.rewordly.app.feature.ai.generate.AiGenerateEvent
import com.rewordly.app.feature.ai.generate.AiGenerateUiState
import com.rewordly.app.feature.ai.generate.AiGenerateViewModel
import com.rewordly.app.feature.ai.generate.GenerationStatus
import com.rewordly.app.testing.FakeConnectivityObserver
import com.rewordly.app.testing.FakeGenerationHistoryRepository
import com.rewordly.app.testing.FakeGenerationSettingsRepository
import com.rewordly.app.testing.FakeVocabularyGenerationRepository
import com.rewordly.app.testing.FakeVocabularyRepository
import com.rewordly.app.testing.MainDispatcherRule
import com.rewordly.app.testing.SequentialIdProvider
import com.rewordly.app.testing.TestTimeProvider
import com.rewordly.app.testing.generatedWord
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AiGenerateViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val vocabulary = FakeVocabularyRepository(
        MockVocabulary.words.take(2).map { WordWithProgress(it, WordProgress(it.id)) },
    )
    private val generation = FakeVocabularyGenerationRepository()
    private val history = FakeGenerationHistoryRepository()
    private val settings = FakeGenerationSettingsRepository()
    private val connectivity = FakeConnectivityObserver()

    private fun viewModel(mode: GenerationMode = GenerationMode.TOPIC) = AiGenerateViewModel(
        savedStateHandle = SavedStateHandle(mapOf("mode" to mode.name)),
        settingsRepository = settings,
        generationRepository = generation,
        generate = GenerateVocabularyUseCase(
            generation,
            history,
            DetectDuplicatesUseCase(vocabulary),
            GenerationSessionStore(),
            SequentialIdProvider(),
            TestTimeProvider(),
        ),
        connectivity = connectivity,
    )

    private val AiGenerateViewModel.state: AiGenerateUiState get() = uiState.value

    private val longText = "This is a sufficiently long English text that the user pasted for analysis."

    // ---- idle and form state ----

    @Test
    fun startsIdle_withSensibleDefaults() = runTest {
        val state = viewModel().state
        assertEquals(GenerationStatus.Idle, state.status)
        assertEquals(Difficulty.B1, state.settings.level)
        assertEquals(10, state.settings.wordCount)
        assertTrue(
            state.settings.includeExamples && state.settings.includeSynonyms && state.settings.includePronunciation,
        )
    }

    @Test
    fun theModeComesFromTheRoute() = runTest {
        assertEquals(GenerationMode.TEXT, viewModel(GenerationMode.TEXT).state.mode)
        assertEquals(GenerationMode.WORD, viewModel(GenerationMode.WORD).state.mode)
    }

    @Test
    fun lastUsedSettings_areRestored() = runTest {
        settings.state.value = GenerationSettings(Difficulty.C1, 20, includeExamples = false)
        settings.topic.value = TopicPreset.TRAVEL.apiName
        val state = viewModel().state
        assertEquals(Difficulty.C1, state.settings.level)
        assertEquals(20, state.settings.wordCount)
        assertFalse(state.settings.includeExamples)
        assertEquals(TopicPreset.TRAVEL, state.preset)
    }

    @Test
    fun aCustomLastTopic_isRestoredAsCustom() = runTest {
        settings.topic.value = "Software Development"
        val state = viewModel().state
        assertNull(state.preset)
        assertEquals("Software Development", state.topic)
    }

    // ---- topic generation ----

    @Test
    fun submit_sendsTheTopicAndSettings_andEndsInDone() = runTest {
        generation.enqueue(generatedWord("deploy"))
        val vm = viewModel()
        vm.onEvent(AiGenerateEvent.SelectPreset(TopicPreset.PROGRAMMING))
        vm.onEvent(AiGenerateEvent.SettingsChanged(vm.state.settings.copy(level = Difficulty.A2, wordCount = 5)))
        vm.onEvent(AiGenerateEvent.Submit)

        val request = generation.requests.single()
        assertEquals(GenerationInput.Topic("Programming"), request.input)
        assertEquals(Difficulty.A2, request.settings.level)
        assertEquals(5, request.settings.wordCount)
        assertEquals(GenerationStatus.Done("gen-1"), vm.state.status)
    }

    @Test
    fun customTopic_isUsedWhenNoPresetIsSelected() = runTest {
        generation.enqueue(generatedWord("deploy"))
        val vm = viewModel()
        vm.onEvent(AiGenerateEvent.CustomTopicChanged("  Software Development "))
        vm.onEvent(AiGenerateEvent.Submit)
        assertEquals(GenerationInput.Topic("Software Development"), generation.requests.single().input)
    }

    @Test
    fun usedSettingsAndTopic_areRememberedForNextTime() = runTest {
        generation.enqueue(generatedWord("deploy"))
        val vm = viewModel()
        vm.onEvent(AiGenerateEvent.SelectPreset(TopicPreset.SCIENCE))
        vm.onEvent(
            AiGenerateEvent.SettingsChanged(vm.state.settings.copy(level = Difficulty.B2, includeSynonyms = false)),
        )
        vm.onEvent(AiGenerateEvent.Submit)
        assertEquals(Difficulty.B2, settings.state.value.level)
        assertFalse(settings.state.value.includeSynonyms)
        assertEquals("Science", settings.topic.value)
    }

    @Test
    fun emptyTopic_isRejectedBeforeAnythingIsSent() = runTest {
        val vm = viewModel()
        vm.onEvent(AiGenerateEvent.CustomTopicChanged("   "))
        vm.onEvent(AiGenerateEvent.Submit)
        assertEquals(InputError.EMPTY, vm.state.inputError)
        assertTrue(generation.requests.isEmpty())
        assertEquals(GenerationStatus.Idle, vm.state.status)
    }

    @Test
    fun editingTheInput_clearsTheValidationError() = runTest {
        val vm = viewModel()
        vm.onEvent(AiGenerateEvent.CustomTopicChanged(""))
        vm.onEvent(AiGenerateEvent.Submit)
        vm.onEvent(AiGenerateEvent.CustomTopicChanged("Travel"))
        assertNull(vm.state.inputError)
    }

    // ---- loading and cancellation ----

    @Test
    fun whileWaiting_theStateIsLoading_andSecondTapsAreIgnored() = runTest {
        generation.gate = CompletableDeferred()
        generation.enqueue(generatedWord("deploy"))
        val vm = viewModel()
        vm.onEvent(AiGenerateEvent.Submit)
        assertEquals(GenerationStatus.Loading, vm.state.status)
        vm.onEvent(AiGenerateEvent.Submit)
        vm.onEvent(AiGenerateEvent.Submit)
        assertEquals(1, generation.requests.size)

        generation.gate!!.complete(Unit)
        assertTrue(vm.state.status is GenerationStatus.Done)
    }

    @Test
    fun cancel_stopsTheRequest_andReturnsToIdle() = runTest {
        generation.gate = CompletableDeferred()
        generation.enqueue(generatedWord("deploy"))
        val vm = viewModel()
        vm.onEvent(AiGenerateEvent.Submit)
        vm.onEvent(AiGenerateEvent.Cancel)
        assertEquals(GenerationStatus.Idle, vm.state.status)

        // Even if the late result arrives it must not resurrect the cancelled generation.
        generation.gate!!.complete(Unit)
        assertEquals(GenerationStatus.Idle, vm.state.status)
        assertTrue(history.entries.value.isEmpty())
    }

    @Test
    fun afterCancel_aNewGenerationCanStart() = runTest {
        generation.gate = CompletableDeferred()
        val vm = viewModel()
        vm.onEvent(AiGenerateEvent.Submit)
        vm.onEvent(AiGenerateEvent.Cancel)

        generation.gate = null
        generation.enqueue(generatedWord("deploy"))
        vm.onEvent(AiGenerateEvent.Submit)
        assertTrue(vm.state.status is GenerationStatus.Done)
    }

    @Test
    fun resultConsumed_resetsToIdle() = runTest {
        generation.enqueue(generatedWord("deploy"))
        val vm = viewModel()
        vm.onEvent(AiGenerateEvent.Submit)
        vm.onEvent(AiGenerateEvent.ResultConsumed)
        assertEquals(GenerationStatus.Idle, vm.state.status)
    }

    // ---- error states ----

    @Test
    fun emptyResponse_isItsOwnState() = runTest {
        generation.results += AppResult.Failure(AppError.EmptyResponse)
        val vm = viewModel()
        vm.onEvent(AiGenerateEvent.Submit)
        assertEquals(GenerationStatus.Empty, vm.state.status)
    }

    @Test
    fun eachFailureKind_isKeptForTheUi() = runTest {
        val errors = listOf(
            AppError.Network(),
            AppError.Timeout(),
            AppError.RateLimited(60),
            AppError.Server(503),
            AppError.InvalidResponse("bad"),
            AppError.BackendNotConfigured,
        )
        for (error in errors) {
            generation.results += AppResult.Failure(error)
            val vm = viewModel()
            vm.onEvent(AiGenerateEvent.Submit)
            assertEquals(GenerationStatus.Failed(error), vm.state.status)
        }
    }

    @Test
    fun afterAFailure_theUserCanRetry() = runTest {
        generation.results += AppResult.Failure(AppError.Network())
        generation.enqueue(generatedWord("deploy"))
        val vm = viewModel()
        vm.onEvent(AiGenerateEvent.Submit)
        assertTrue(vm.state.status is GenerationStatus.Failed)
        vm.onEvent(AiGenerateEvent.Submit)
        assertTrue(vm.state.status is GenerationStatus.Done)
    }

    // ---- usage ----

    @Test
    fun usage_isShownOnlyWhenTheBackendReportedIt() = runTest {
        val vm = viewModel()
        assertNull(vm.state.usage)
        generation.setUsage(UsageInfo(remainingRequests = 12, dailyUsed = 8, dailyLimit = 20))
        assertEquals(12, vm.state.usage?.remainingRequests)
    }

    // ---- text mode and privacy ----

    @Test
    fun textMode_asksForConsentFirst_andSendsNothingUntilConfirmed() = runTest {
        generation.enqueue(generatedWord("deploy"))
        val vm = viewModel(GenerationMode.TEXT)
        vm.onEvent(AiGenerateEvent.TextChanged(longText))
        vm.onEvent(AiGenerateEvent.Submit)

        assertTrue(vm.state.showPrivacyDialog)
        assertTrue(generation.requests.isEmpty())

        vm.onEvent(AiGenerateEvent.ConfirmPrivacy)
        assertFalse(vm.state.showPrivacyDialog)
        assertEquals(GenerationInput.Text(longText), generation.requests.single().input)
        assertTrue(settings.notice.value)
    }

    @Test
    fun dismissingTheNotice_sendsNothing() = runTest {
        val vm = viewModel(GenerationMode.TEXT)
        vm.onEvent(AiGenerateEvent.TextChanged(longText))
        vm.onEvent(AiGenerateEvent.Submit)
        vm.onEvent(AiGenerateEvent.DismissPrivacy)
        assertTrue(generation.requests.isEmpty())
        assertFalse(settings.notice.value)
    }

    @Test
    fun onceAccepted_theNoticeDoesNotInterruptAgain() = runTest {
        settings.notice.value = true
        generation.enqueue(generatedWord("deploy"))
        val vm = viewModel(GenerationMode.TEXT)
        vm.onEvent(AiGenerateEvent.TextChanged(longText))
        vm.onEvent(AiGenerateEvent.Submit)
        assertFalse(vm.state.showPrivacyDialog)
        assertEquals(1, generation.requests.size)
    }

    @Test
    fun tooShortText_isRejectedLocally() = runTest {
        settings.notice.value = true
        val vm = viewModel(GenerationMode.TEXT)
        vm.onEvent(AiGenerateEvent.TextChanged("too short"))
        vm.onEvent(AiGenerateEvent.Submit)
        assertEquals(InputError.TOO_SHORT, vm.state.inputError)
        assertTrue(generation.requests.isEmpty())
    }

    // ---- single word ----

    @Test
    fun singleWord_isValidated_trimmed_andSent() = runTest {
        generation.enqueue(generatedWord("serendipity"))
        val vm = viewModel(GenerationMode.WORD)
        vm.onEvent(AiGenerateEvent.WordChanged("слово"))
        vm.onEvent(AiGenerateEvent.Submit)
        assertEquals(InputError.INVALID, vm.state.inputError)

        vm.onEvent(AiGenerateEvent.WordChanged("  serendipity "))
        vm.onEvent(AiGenerateEvent.Submit)
        assertEquals(GenerationInput.SingleWord("serendipity"), generation.requests.single().input)
    }

    @Test
    fun spellingSuggestion_isOfferedButNeverAppliedSilently() = runTest {
        generation.results += AppResult.Success(GenerationResult(emptyList(), suggestedCorrection = "necessary"))
        val vm = viewModel(GenerationMode.WORD)
        vm.onEvent(AiGenerateEvent.WordChanged("neccesary"))
        vm.onEvent(AiGenerateEvent.Submit)

        assertEquals("necessary", vm.state.suggestion)
        assertEquals("neccesary", vm.state.word)
        assertEquals(GenerationStatus.Idle, vm.state.status)

        vm.onEvent(AiGenerateEvent.ApplySuggestion)
        assertEquals("necessary", vm.state.word)
        assertNull(vm.state.suggestion)
    }

    // ---- offline ----

    @Test
    fun offline_isReportedToTheUi_andBlocksTheRequest() = runTest {
        connectivity.setOnline(false)
        val vm = viewModel()

        vm.onEvent(AiGenerateEvent.Submit)

        assertFalse(vm.state.isOnline)
        assertTrue(generation.requests.isEmpty())
        assertEquals(GenerationStatus.Failed(AppError.Offline), vm.state.status)
    }

    @Test
    fun goingOffline_whileTheFormIsOpen_keepsWhatTheUserTyped() = runTest {
        val vm = viewModel(GenerationMode.TEXT)
        vm.onEvent(AiGenerateEvent.TextChanged(longText))

        connectivity.setOnline(false)

        assertEquals(longText, vm.state.text)
        assertFalse(vm.state.isOnline)
    }

    @Test
    fun comingBackOnline_letsTheSameInputBeSubmittedAgain() = runTest {
        connectivity.setOnline(false)
        val vm = viewModel()
        vm.onEvent(AiGenerateEvent.Submit)
        assertTrue(generation.requests.isEmpty())

        connectivity.setOnline(true)
        generation.enqueue(generatedWord("deploy"))
        vm.onEvent(AiGenerateEvent.Submit)

        assertTrue(vm.state.isOnline)
        assertEquals(1, generation.requests.size)
        assertEquals(GenerationStatus.Done("gen-1"), vm.state.status)
    }
}

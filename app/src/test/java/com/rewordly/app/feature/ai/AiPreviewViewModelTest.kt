package com.rewordly.app.feature.ai

import androidx.lifecycle.SavedStateHandle
import com.rewordly.app.core.common.AppError
import com.rewordly.app.core.common.AppResult
import com.rewordly.app.data.local.MockVocabulary
import com.rewordly.app.domain.model.Difficulty
import com.rewordly.app.domain.model.GenerationHistoryEntry
import com.rewordly.app.domain.model.GenerationInput
import com.rewordly.app.domain.model.GenerationMode
import com.rewordly.app.domain.model.GenerationRequest
import com.rewordly.app.domain.model.GenerationSettings
import com.rewordly.app.domain.model.WordProgress
import com.rewordly.app.domain.model.WordWithProgress
import com.rewordly.app.domain.service.GenerationSessionStore
import com.rewordly.app.domain.usecase.DetectDuplicatesUseCase
import com.rewordly.app.domain.usecase.GenerateVocabularyUseCase
import com.rewordly.app.domain.usecase.RegenerateVocabularyUseCase
import com.rewordly.app.domain.usecase.RegenerationRequestProvider
import com.rewordly.app.domain.usecase.SaveGeneratedWordsUseCase
import com.rewordly.app.feature.ai.preview.AiPreviewEvent
import com.rewordly.app.feature.ai.preview.AiPreviewUiState
import com.rewordly.app.feature.ai.preview.AiPreviewViewModel
import com.rewordly.app.feature.ai.preview.PreviewBusy
import com.rewordly.app.feature.ai.preview.PreviewMessage
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

class AiPreviewViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val existingWord = MockVocabulary.words[0]
    private val vocabulary = FakeVocabularyRepository(
        MockVocabulary.words.take(2).map { WordWithProgress(it, WordProgress(it.id)) },
    )
    private val generation = FakeVocabularyGenerationRepository()
    private val history = FakeGenerationHistoryRepository()
    private val sessions = GenerationSessionStore()
    private val settings = FakeGenerationSettingsRepository()
    private val detect = DetectDuplicatesUseCase(vocabulary)
    private val generate = GenerateVocabularyUseCase(
        generation,
        history,
        detect,
        sessions,
        SequentialIdProvider(),
        TestTimeProvider(),
    )

    private suspend fun seed(vararg words: String, mode: GenerationMode = GenerationMode.TOPIC): String {
        val input = if (mode == GenerationMode.TEXT) {
            GenerationInput.Text(
                "t".repeat(50),
            )
        } else {
            GenerationInput.Topic("Tech")
        }
        generation.enqueue(*words.map { generatedWord(it) }.toTypedArray())
        val outcome = generate(GenerationRequest(input, GenerationSettings(Difficulty.B1, 10)))
        return (outcome as AppResult.Success).data.historyId!!
    }

    private fun viewModel(id: String) = AiPreviewViewModel(
        savedStateHandle = SavedStateHandle(mapOf("historyId" to id)),
        historyRepository = history,
        detectDuplicates = detect,
        saveGeneratedWords = SaveGeneratedWordsUseCase(vocabulary, detect),
        regenerate = RegenerateVocabularyUseCase(
            RegenerationRequestProvider(sessions, history, settings),
            generation,
            generate,
            history,
            detect,
        ),
    )

    private fun AiPreviewViewModel.content() = uiState.value as AiPreviewUiState.Content

    // ---- loading ----

    @Test
    fun loadsTheStoredResult_withEverythingSelectedByDefault() = runTest {
        val vm = viewModel(seed("deploy", "compile"))
        val content = vm.content()
        assertEquals(listOf("deploy", "compile"), content.items.map { it.word.word })
        assertTrue(content.items.all { it.selected })
        assertEquals(2, content.selectedCount)
        assertEquals(PreviewBusy.None, content.busy)
    }

    @Test
    fun unknownOrDeletedGeneration_isNotFound() = runTest {
        assertEquals(AiPreviewUiState.NotFound, viewModel("missing").uiState.value)
        val id = seed("deploy")
        history.delete(id)
        assertEquals(AiPreviewUiState.NotFound, viewModel(id).uiState.value)
    }

    @Test
    fun entryWithoutStoredItems_isNotFound() = runTest {
        history.entries.value = listOf(
            GenerationHistoryEntry("old", GenerationMode.TOPIC, "Tech", Difficulty.B1, 10, 0, 1L, hasResult = false),
        )
        assertEquals(AiPreviewUiState.NotFound, viewModel("old").uiState.value)
    }

    // ---- selection ----

    @Test
    fun selectionCanBeToggled_andClearedOrRestoredInBulk() = runTest {
        val vm = viewModel(seed("deploy", "compile", "build"))
        vm.onEvent(AiPreviewEvent.Toggle(1))
        assertEquals(listOf(true, false, true), vm.content().items.map { it.selected })
        assertEquals(2, vm.content().selectedCount)

        vm.onEvent(AiPreviewEvent.SetAllSelected(false))
        assertEquals(0, vm.content().selectedCount)
        assertFalse(vm.content().canSave)

        vm.onEvent(AiPreviewEvent.SetAllSelected(true))
        assertEquals(3, vm.content().selectedCount)
    }

    @Test
    fun duplicates_areMarked_unselected_andCannotBeSelected() = runTest {
        val vm = viewModel(seed(existingWord.text, "deploy"))
        val items = vm.content().items
        assertEquals(existingWord.id, items[0].existingWordId)
        assertFalse(items[0].selected)

        vm.onEvent(AiPreviewEvent.Toggle(0))
        vm.onEvent(AiPreviewEvent.SetAllSelected(true))
        assertFalse(vm.content().items[0].selected)
        assertEquals(1, vm.content().selectedCount)
    }

    // ---- saving ----

    @Test
    fun saving_storesOnlyTheSelectedWords() = runTest {
        val vm = viewModel(seed("deploy", "compile", "build"))
        vm.onEvent(AiPreviewEvent.Toggle(1))
        vm.onEvent(AiPreviewEvent.Save)

        assertEquals(listOf("deploy", "build"), vocabulary.addedWords.map { it.text })
        assertEquals(PreviewMessage.Saved(2), vm.content().message)
        assertEquals(PreviewBusy.None, vm.content().busy)
    }

    @Test
    fun afterSaving_thoseWordsCountAsExisting_soTheyCannotBeSavedTwice() = runTest {
        val vm = viewModel(seed("deploy", "compile"))
        vm.onEvent(AiPreviewEvent.Save)

        assertTrue(vm.content().items.all { it.isDuplicate })
        assertEquals(0, vm.content().selectedCount)
        vm.onEvent(AiPreviewEvent.Save)
        assertEquals(2, vocabulary.addedWords.size)
    }

    @Test
    fun savingNothingSelected_doesNothing() = runTest {
        val vm = viewModel(seed("deploy"))
        vm.onEvent(AiPreviewEvent.SetAllSelected(false))
        vm.onEvent(AiPreviewEvent.Save)
        assertTrue(vocabulary.addedWords.isEmpty())
        assertNull(vm.content().message)
    }

    @Test
    fun aDatabaseErrorWhileSaving_isShown_andNothingIsLost() = runTest {
        val vm = viewModel(seed("deploy"))
        vocabulary.dbFailure = IllegalStateException("disk")
        vm.onEvent(AiPreviewEvent.Save)

        assertTrue(vm.content().error is AppError.Database)
        assertEquals(PreviewBusy.None, vm.content().busy)
        assertTrue(vm.content().items.single().selected)

        vocabulary.dbFailure = null
        vm.onEvent(AiPreviewEvent.DismissError)
        vm.onEvent(AiPreviewEvent.Save)
        assertEquals(1, vocabulary.addedWords.size)
    }

    @Test
    fun existingProgress_survivesSaving() = runTest {
        val before = vocabulary.progressOf(existingWord.id)
        val vm = viewModel(seed(existingWord.text, "deploy"))
        vm.onEvent(AiPreviewEvent.Save)
        assertEquals(before, vocabulary.progressOf(existingWord.id))
    }

    // ---- regeneration ----

    @Test
    fun regeneratingOneWord_replacesIt_andKeepsOtherChoices() = runTest {
        val vm = viewModel(seed("deploy", "compile", "build"))
        vm.onEvent(AiPreviewEvent.Toggle(2))
        generation.enqueue(generatedWord("ship"))
        vm.onEvent(AiPreviewEvent.RegenerateWord(1))

        val items = vm.content().items
        assertEquals(listOf("deploy", "ship", "build"), items.map { it.word.word })
        assertFalse("untouched word keeps its deselected state", items[2].selected)
        assertEquals(PreviewBusy.None, vm.content().busy)
    }

    @Test
    fun regeneratingEverything_replacesTheResult_withAFreshSelection() = runTest {
        val vm = viewModel(seed("deploy", "compile"))
        vm.onEvent(AiPreviewEvent.Toggle(0))
        generation.enqueue(generatedWord("ship"), generatedWord("release"))
        vm.onEvent(AiPreviewEvent.RegenerateAll)

        assertEquals(listOf("ship", "release"), vm.content().items.map { it.word.word })
        assertTrue(vm.content().items.all { it.selected })
    }

    @Test
    fun whileRegenerating_otherActionsAreBlocked() = runTest {
        val vm = viewModel(seed("deploy", "compile"))
        generation.gate = CompletableDeferred()
        generation.enqueue(generatedWord("ship"))
        vm.onEvent(AiPreviewEvent.RegenerateWord(0))

        assertEquals(PreviewBusy.RegeneratingWord(0), vm.content().busy)
        assertFalse(vm.content().canSave)
        vm.onEvent(AiPreviewEvent.RegenerateAll)
        vm.onEvent(AiPreviewEvent.Save)
        assertEquals(1 + 1, generation.requests.size)
        assertTrue(vocabulary.addedWords.isEmpty())

        generation.gate!!.complete(Unit)
        assertEquals(PreviewBusy.None, vm.content().busy)
    }

    @Test
    fun aFailedRegeneration_keepsTheOldResult_andShowsTheError() = runTest {
        val vm = viewModel(seed("deploy"))
        generation.results += AppResult.Failure(AppError.RateLimited(30))
        vm.onEvent(AiPreviewEvent.RegenerateAll)

        assertEquals(listOf("deploy"), vm.content().items.map { it.word.word })
        assertEquals(AppError.RateLimited(30), vm.content().error)
        assertEquals(PreviewBusy.None, vm.content().busy)
    }

    @Test
    fun textGenerationsAfterARestart_cannotBeRegenerated() = runTest {
        val id = seed("deploy", mode = GenerationMode.TEXT)
        sessions.clear()
        val vm = viewModel(id)
        assertFalse(vm.content().canRegenerate)
        vm.onEvent(AiPreviewEvent.RegenerateAll)
        assertEquals(1, generation.requests.size)
    }

    // ---- details sheet ----

    @Test
    fun detailsOpenAndClose_withoutTouchingSelection() = runTest {
        val vm = viewModel(seed("deploy"))
        vm.onEvent(AiPreviewEvent.OpenDetail(0))
        assertEquals(0, vm.content().detailIndex)
        vm.onEvent(AiPreviewEvent.CloseDetail)
        assertNull(vm.content().detailIndex)
        assertTrue(vm.content().items.single().selected)
    }
}

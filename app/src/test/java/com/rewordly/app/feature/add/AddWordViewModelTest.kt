package com.rewordly.app.feature.add

import com.rewordly.app.core.common.AppError
import com.rewordly.app.data.local.MockVocabulary
import com.rewordly.app.domain.model.WordLookup
import com.rewordly.app.domain.model.WordProgress
import com.rewordly.app.domain.model.WordWithProgress
import com.rewordly.app.domain.usecase.AddWordUseCase
import com.rewordly.app.domain.usecase.InputError
import com.rewordly.app.testing.FakeConnectivityObserver
import com.rewordly.app.testing.FakeVocabularyRepository
import com.rewordly.app.testing.FakeWordLookupRepository
import com.rewordly.app.testing.MainDispatcherRule
import com.rewordly.app.testing.foundWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AddWordViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val existing = MockVocabulary.words.take(2).map { WordWithProgress(it, WordProgress(it.id)) }
    private val vocabulary = FakeVocabularyRepository(existing)
    private val lookup = FakeWordLookupRepository()
    private val connectivity = FakeConnectivityObserver()

    private fun viewModel() = AddWordViewModel(AddWordUseCase(lookup, vocabulary), connectivity)

    private fun AddWordViewModel.status() = uiState.value.status

    private fun started(viewModel: AddWordViewModel, word: String) {
        viewModel.onEvent(AddWordEvent.Started(word))
        viewModel.onEvent(AddWordEvent.Submit)
    }

    @Test
    fun startsFromTheWordTheUserWasAlreadySearchingFor() {
        val viewModel = viewModel()

        viewModel.onEvent(AddWordEvent.Started("  deploy  "))

        assertEquals("deploy", viewModel.uiState.value.word)
        assertEquals(AddWordStatus.Idle, viewModel.status())
    }

    @Test
    fun addsTheWordAndReportsItsTranslationAndTranscription() {
        lookup.returns(foundWord("deploy", translation = "развёртывать", transcription = "/dɪˈplɔɪ/"))
        val viewModel = viewModel()

        started(viewModel, "deploy")

        val added = viewModel.status() as AddWordStatus.Added
        assertEquals("deploy", added.word.text)
        assertEquals("развёртывать", added.word.translation.text)
        assertEquals("/dɪˈplɔɪ/", added.word.pronunciation)
        assertTrue(added.hasTranscription)
        assertTrue(viewModel.uiState.value.isSettled)
    }

    @Test
    fun reportsAWordSavedWithoutATranscription() {
        lookup.returns(foundWord("deploy", transcription = ""))
        val viewModel = viewModel()

        started(viewModel, "deploy")

        assertFalse((viewModel.status() as AddWordStatus.Added).hasTranscription)
    }

    @Test
    fun refusesAnEmptyFieldWithoutAskingTheSources() {
        val viewModel = viewModel()

        viewModel.onEvent(AddWordEvent.Submit)

        assertEquals(InputError.EMPTY, viewModel.uiState.value.inputError)
        assertEquals(AddWordStatus.Idle, viewModel.status())
        assertEquals(emptyList<String>(), lookup.lookedUp)
    }

    @Test
    fun refusesSomethingThatIsNotAnEnglishWord() {
        val viewModel = viewModel()

        started(viewModel, "1234")

        assertEquals(InputError.INVALID, viewModel.uiState.value.inputError)
        assertEquals(emptyList<String>(), lookup.lookedUp)
    }

    @Test
    fun staysOfflineWithoutAskingTheSources() {
        connectivity.setOnline(false)
        val viewModel = viewModel()

        started(viewModel, "deploy")

        assertEquals(AddWordStatus.Failed(AppError.Offline), viewModel.status())
        assertEquals(emptyList<String>(), lookup.lookedUp)
        // The typed word is kept so it can be submitted again once the connection is back.
        assertEquals("deploy", viewModel.uiState.value.word)
    }

    @Test
    fun reportsAWordThatIsAlreadyInTheVocabulary() {
        val viewModel = viewModel()

        started(viewModel, existing[0].word.text)

        val exists = viewModel.status() as AddWordStatus.AlreadyExists
        assertEquals(existing[0].word.id, exists.wordId)
        assertEquals(emptyList<String>(), lookup.lookedUp)
    }

    @Test
    fun reportsAMissWithTheWordTheUserTyped() {
        lookup.returns(WordLookup.NotFound)
        val viewModel = viewModel()

        started(viewModel, "deploy")

        assertEquals(AddWordStatus.NotFound("deploy"), viewModel.status())
    }

    @Test
    fun reportsAFailureFromTheSources() {
        lookup.failsWith(AppError.Server(503))
        val viewModel = viewModel()

        started(viewModel, "deploy")

        assertEquals(AddWordStatus.Failed(AppError.Server(503)), viewModel.status())
    }

    @Test
    fun resetClearsBothTheResultAndTheField() {
        lookup.returns(foundWord("deploy"))
        val viewModel = viewModel()
        started(viewModel, "deploy")
        assertTrue(viewModel.status() is AddWordStatus.Added)

        viewModel.onEvent(AddWordEvent.Reset)

        assertEquals("", viewModel.uiState.value.word)
        assertEquals(AddWordStatus.Idle, viewModel.status())
        assertFalse(viewModel.uiState.value.isSettled)
    }

    @Test
    fun cannotSubmitAnEmptyField() {
        val viewModel = viewModel()

        assertFalse(viewModel.uiState.value.canSubmit)

        viewModel.onEvent(AddWordEvent.WordChanged("deploy"))

        assertTrue(viewModel.uiState.value.canSubmit)
    }

    @Test
    fun clearingTheInputErrorWhenTheUserKeepsTyping() {
        val viewModel = viewModel()
        viewModel.onEvent(AddWordEvent.Submit)
        assertEquals(InputError.EMPTY, viewModel.uiState.value.inputError)

        viewModel.onEvent(AddWordEvent.WordChanged("d"))

        assertEquals(null, viewModel.uiState.value.inputError)
    }
}

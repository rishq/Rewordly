package com.rewordly.app.feature.saved

import com.rewordly.app.data.local.MockVocabulary
import com.rewordly.app.domain.model.WordProgress
import com.rewordly.app.domain.model.WordWithProgress
import com.rewordly.app.testing.FakeSettingsRepository
import com.rewordly.app.testing.FakeVocabularyRepository
import com.rewordly.app.testing.MainDispatcherRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SavedViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val words = MockVocabulary.words.take(3).map { WordWithProgress(it, WordProgress(it.id)) }
    private val repository = FakeVocabularyRepository(words)
    private val scope = CoroutineScope(mainDispatcher.dispatcher)
    private var subscription: Job? = null

    // Built inside the test, not as a field: viewModelScope resolves Dispatchers.Main when the
    // view model is created, and the rule only installs the test dispatcher afterwards.
    private fun observingViewModel() = SavedViewModel(FakeSettingsRepository(), repository).also { viewModel ->
        // stateIn(WhileSubscribed) only runs the pipeline while something collects.
        subscription = scope.launch { viewModel.uiState.collect {} }
    }

    @After
    fun unsubscribe() {
        subscription?.cancel()
    }

    @Test
    fun withoutSavedWords_showsTheEmptyState() = runTest {
        assertTrue(observingViewModel().uiState.value is SavedUiState.NoSavedWords)
    }

    @Test
    fun savedWords_areListedAndCanBeFiltered() = runTest {
        repository.setSaved(words[0].word.id, true)
        repository.setSaved(words[1].word.id, true)
        val viewModel = observingViewModel()
        assertEquals(2, (viewModel.uiState.value as SavedUiState.Content).words.size)

        viewModel.onQueryChange(words[0].word.translation.text)
        assertEquals(
            listOf(words[0].word.id),
            (viewModel.uiState.value as SavedUiState.Content).words.map { it.word.id },
        )
    }

    @Test
    fun queryWithoutMatches_showsNoMatches() = runTest {
        repository.setSaved(words[0].word.id, true)
        val viewModel = observingViewModel()
        viewModel.onQueryChange("zzzz")
        assertTrue(viewModel.uiState.value is SavedUiState.NoMatches)
    }

    @Test
    fun unsaving_removesTheWordFromTheList() = runTest {
        repository.setSaved(words[0].word.id, true)
        val viewModel = observingViewModel()
        assertEquals(1, (viewModel.uiState.value as SavedUiState.Content).words.size)
        repository.setSaved(words[0].word.id, false)
        assertTrue(viewModel.uiState.value is SavedUiState.NoSavedWords)
    }
}

package com.rewordly.app.feature.search

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.rewordly.app.R
import com.rewordly.app.data.local.MockVocabulary
import com.rewordly.app.domain.model.DifficultyFilter
import com.rewordly.app.domain.model.StatusFilter
import com.rewordly.app.domain.model.VocabularyFilters
import com.rewordly.app.domain.model.WordProgress
import com.rewordly.app.domain.model.WordWithProgress
import com.rewordly.app.testing.setRewordlyContent
import com.rewordly.app.testing.uiString
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SearchScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val words = MockVocabulary.words.map { WordWithProgress(it, WordProgress(it.id)) }
    private var query = ""
    private var filters = VocabularyFilters()
    private var openedWordId: String? = null

    private fun show(state: SearchUiState) {
        compose.setRewordlyContent {
            SearchContent(
                query = query,
                onQueryChange = { query = it },
                uiState = state,
                filters = filters,
                onFiltersChange = { filters = it },
                onSubmit = {},
                onRecentSelected = {},
                onClearRecent = {},
                onOpenWord = { openedWordId = it },
            )
        }
    }

    @Test
    fun results_showWordAndTranslation() {
        show(SearchUiState.Results(words.take(2)))
        compose.onNodeWithText("beautiful").assertIsDisplayed()
        compose.onNodeWithText("красивый", substring = true).assertIsDisplayed()
    }

    @Test
    fun typing_updatesTheQuery() {
        show(SearchUiState.Results(words))
        compose.onNodeWithText(uiString(R.string.search_hint)).performTextInput("achieve")
        assertEquals("achieve", query)
    }

    @Test
    fun tappingAResult_opensTheWord() {
        val result = words.first()
        show(SearchUiState.Results(listOf(result)))
        compose.onNodeWithText(result.word.text).performClick()
        assertEquals(result.word.id, openedWordId)
    }

    @Test
    fun noResults_showsTheMessageWithTheQuery() {
        show(SearchUiState.NoResults("zzz"))
        compose.onNodeWithText(uiString(R.string.search_empty_title)).assertIsDisplayed()
        compose.onNodeWithText(uiString(R.string.search_empty_message, "zzz"), substring = true).assertIsDisplayed()
    }

    @Test
    fun idleState_showsRecentSearches() {
        show(SearchUiState.Idle(listOf("opportunity")))
        compose.onNodeWithText("opportunity").assertIsDisplayed()
    }

    @Test
    fun difficultyAndStatusFilters_areSelectable() {
        show(SearchUiState.Results(words))
        compose.onNodeWithText(uiString(R.string.filter_b1)).performClick()
        assertEquals(DifficultyFilter.B1, filters.difficulty)
        compose.onNodeWithText(uiString(R.string.status_saved)).performClick()
        assertEquals(StatusFilter.SAVED, filters.status)
    }

    @Test
    fun filterChips_doNotLeakIntoTheResultList() {
        val saved = words.map { it.copy(progress = it.progress.copy(isSaved = true)) }
        show(SearchUiState.Results(saved))
        compose.onAllNodesWithText(uiString(R.string.status_saved)).assertCountEquals(1)
    }
}

package com.rewordly.app.feature.learn

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import com.rewordly.app.R
import com.rewordly.app.core.ui.components.ANSWER_FORGOT_TAG
import com.rewordly.app.core.ui.components.ANSWER_REMEMBERED_TAG
import com.rewordly.app.data.local.MockVocabulary
import com.rewordly.app.domain.model.LearningSession
import com.rewordly.app.domain.model.WordProgress
import com.rewordly.app.domain.model.WordStatus
import com.rewordly.app.domain.model.WordWithProgress
import com.rewordly.app.testing.setRewordlyContent
import com.rewordly.app.testing.uiString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LearnScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val items = MockVocabulary.words.take(3).map { WordWithProgress(it, WordProgress(it.id)) }
    private val session = LearningSession(
        sessionId = "test",
        wordIds = items.map { it.word.id },
        startedAt = 0L,
        completedWordIds = setOf(items[0].word.id),
    )
    private val events = mutableListOf<LearnUiEvent>()
    private var openedWordId: String? = null

    private fun show(
        position: Int = 0,
        revealed: Boolean = false,
        submitting: Boolean = false,
        firstEncounter: Boolean = true,
    ) {
        val progress = if (firstEncounter) {
            WordProgress(items[position].word.id)
        } else {
            WordProgress(items[position].word.id, status = WordStatus.LEARNING, correctAnswers = 1)
        }
        compose.setRewordlyContent {
            LearnContent(
                state = LearnUiState.Content(
                    session = session.copy(currentPosition = position),
                    items = items,
                    current = items[position].copy(progress = progress),
                    revealed = revealed,
                    isSubmitting = submitting,
                    submitFailed = false,
                ),
                onEvent = { events += it },
                onOpenWord = { openedWordId = it },
            )
        }
    }

    @Test
    fun showsPositionAndCompletedCount() {
        show(position = 1)
        compose.onNodeWithText(uiString(R.string.learn_position, 2, 3)).assertIsDisplayed()
        compose.onNodeWithText(uiString(R.string.learn_completed, 1, 3)).assertIsDisplayed()
    }

    @Test
    fun currentCard_showsTheWord_butKeepsTheTranslationHidden() {
        show()
        compose.onNodeWithText(items[0].word.text, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText(items[0].word.translation.text, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun revealing_showsTheTranslation() {
        show(revealed = true)
        compose.onNodeWithText(items[0].word.translation.text, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun revealButton_sendsTheRevealEvent() {
        show()
        compose.onNodeWithText(uiString(R.string.action_reveal)).performClick()
        assertEquals(listOf<LearnUiEvent>(LearnUiEvent.Reveal), events)
    }

    /** A word the user has never met is triaged, so the answers talk about knowing, not remembering. */
    @Test
    fun aBrandNewWord_asksWhetherItIsAlreadyKnown() {
        show(firstEncounter = true)
        compose.onNodeWithText(uiString(R.string.study_answer_known)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(uiString(R.string.study_answer_study)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aWordInProgress_asksWhetherItWasRemembered() {
        show(firstEncounter = false)
        compose.onNodeWithText(uiString(R.string.study_answer_remembered)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(uiString(R.string.study_answer_forgot)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun answerBar_reportsRememberedAndForgotten() {
        show(firstEncounter = false)
        compose.onNodeWithTag(ANSWER_REMEMBERED_TAG).performScrollTo().performClick()
        compose.onNodeWithTag(ANSWER_FORGOT_TAG).performScrollTo().performClick()
        assertEquals(
            listOf(LearnUiEvent.Answer(remembered = true), LearnUiEvent.Answer(remembered = false)),
            events,
        )
    }

    @Test
    fun swipingTheCardLeft_answersRemembered() {
        show(firstEncounter = false)
        compose.onNodeWithText(items[0].word.text, useUnmergedTree = true).performTouchInput { swipeLeft() }
        compose.waitUntil(timeoutMillis = WAIT_MILLIS) { events.isNotEmpty() }
        assertEquals(listOf<LearnUiEvent>(LearnUiEvent.Answer(remembered = true)), events)
    }

    @Test
    fun swipingTheCardRight_answersNotRemembered() {
        show(firstEncounter = false)
        compose.onNodeWithText(items[0].word.text, useUnmergedTree = true).performTouchInput { swipeRight() }
        compose.waitUntil(timeoutMillis = WAIT_MILLIS) { events.isNotEmpty() }
        assertEquals(listOf<LearnUiEvent>(LearnUiEvent.Answer(remembered = false)), events)
    }

    @Test
    fun whileSaving_bothAnswersAreDisabled() {
        show(submitting = true)
        compose.onNodeWithTag(ANSWER_REMEMBERED_TAG).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag(ANSWER_FORGOT_TAG).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun saveAndListen_areVisible() {
        show()
        compose.onNodeWithContentDescription(uiString(R.string.a11y_pronounce, items[0].word.text))
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithContentDescription(uiString(R.string.action_save)).performScrollTo().performClick()
        assertTrue(events.contains(LearnUiEvent.ToggleSaved))
    }

    @Test
    fun detailsButton_opensTheWord() {
        show()
        compose.onNodeWithContentDescription(uiString(R.string.a11y_open_details, items[0].word.text))
            .performScrollTo()
            .performClick()
        assertEquals(items[0].word.id, openedWordId)
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}

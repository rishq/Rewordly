package com.rewordly.app.feature.learn

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
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
        answerMode: StudyMode? = null,
        typed: String = "",
        typeMismatch: Boolean = false,
        pickedVariant: String? = null,
        correctPick: Boolean = false,
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
                    variants = items.map { it.word.text },
                    answerMode = answerMode,
                    typed = typed,
                    typeMismatch = typeMismatch,
                    pickedVariant = pickedVariant,
                    correctPick = correctPick,
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
    fun aWordInProgress_showsTheTranslation_butKeepsTheWordHidden() {
        show(firstEncounter = false)
        compose.onNodeWithText(items[0].word.translation.text, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText(items[0].word.text, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun revealing_showsTheEnglishWord() {
        show(firstEncounter = false, revealed = true)
        compose.onNodeWithText(items[0].word.text, useUnmergedTree = true).assertIsDisplayed()
    }

    /** A word the user has never met is new information, so it is the English word that is face up. */
    @Test
    fun aBrandNewWord_showsTheWord_butKeepsTheTranslationHidden() {
        show(firstEncounter = true)
        compose.onNodeWithText(items[0].word.text, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText(items[0].word.translation.text, useUnmergedTree = true).assertDoesNotExist()
    }

    /** A brand new word has nothing to recall, so there is no mode to pick. */
    @Test
    fun aBrandNewWord_offersOnlyTheRevealButton() {
        show(firstEncounter = true)
        compose.onNodeWithContentDescription(uiString(R.string.learn_mode_see)).assertIsDisplayed()
        compose.onNodeWithContentDescription(uiString(R.string.learn_mode_type)).assertDoesNotExist()
        compose.onNodeWithContentDescription(uiString(R.string.learn_mode_options)).assertDoesNotExist()
    }

    @Test
    fun theRevealButton_reportsTheReveal() {
        show(firstEncounter = true)
        compose.onNodeWithContentDescription(uiString(R.string.learn_mode_see)).performClick()
        assertEquals(listOf<LearnUiEvent>(LearnUiEvent.Reveal), events)
    }

    /** Once the translation is shown the button has done its job and leaves the screen. */
    @Test
    fun afterTheReveal_theRevealButtonIsGone() {
        show(firstEncounter = true, revealed = true)
        compose.onNodeWithContentDescription(uiString(R.string.learn_mode_see)).assertDoesNotExist()
    }

    @Test
    fun theThreeModeButtons_sendSelectModeEvents() {
        show(firstEncounter = false)
        compose.onNodeWithContentDescription(uiString(R.string.learn_mode_type)).performClick()
        assertEquals(listOf<LearnUiEvent>(LearnUiEvent.SelectMode(StudyMode.TYPE)), events)
    }

    @Test
    fun beforeAModeIsChosen_allThreeModeButtonsAreThere() {
        show(firstEncounter = false)
        compose.onNodeWithContentDescription(uiString(R.string.learn_mode_type)).assertIsDisplayed()
        compose.onNodeWithContentDescription(uiString(R.string.learn_mode_see)).assertIsDisplayed()
        compose.onNodeWithContentDescription(uiString(R.string.learn_mode_options)).assertIsDisplayed()
    }

    @Test
    fun afterAModeIsChosen_theOtherModeButtonsLeaveTheScreen() {
        show(firstEncounter = false, answerMode = StudyMode.SEE)
        compose.onNodeWithContentDescription(uiString(R.string.learn_mode_see)).assertIsDisplayed()
        compose.onNodeWithContentDescription(uiString(R.string.learn_mode_type)).assertDoesNotExist()
        compose.onNodeWithContentDescription(uiString(R.string.learn_mode_options)).assertDoesNotExist()
    }

    @Test
    fun typingPanel_acceptsInput_andChecksIt() {
        show(firstEncounter = false, answerMode = StudyMode.TYPE)
        compose.onNodeWithText(uiString(R.string.learn_type_hint)).performTextInput("plan")
        compose.onNodeWithText(uiString(R.string.learn_type_check)).performScrollTo().performClick()
        compose.onNodeWithText(uiString(R.string.learn_type_hint_letter)).performClick()

        assertEquals(
            listOf(
                LearnUiEvent.TypeText("plan"),
                LearnUiEvent.TypeCheck,
                LearnUiEvent.TypeHint,
            ),
            events,
        )
    }

    @Test
    fun optionsPanel_offersVariants_andReportsThePick() {
        val expectedVariant = items[1].word.text
        show(firstEncounter = false, answerMode = StudyMode.OPTIONS)
        compose.onNodeWithText(expectedVariant).performScrollTo().performClick()
        assertEquals(listOf<LearnUiEvent>(LearnUiEvent.PickVariant(expectedVariant)), events)
    }

    @Test
    fun whileTheCorrectPickFlashes_theOptionsAreLocked() {
        show(
            firstEncounter = false,
            answerMode = StudyMode.OPTIONS,
            pickedVariant = items[1].word.text,
            correctPick = true,
        )
        compose.onNodeWithText(items[1].word.text).performScrollTo().assertIsNotEnabled()
    }

    /** A wrong answer ends the panel: the buttons are gone and the right word stands in their place. */
    @Test
    fun aWrongAnswer_replacesThePanelWithTheRightWord() {
        show(firstEncounter = false, answerMode = StudyMode.TYPE, typeMismatch = true)
        compose.onNodeWithText(uiString(R.string.learn_type_mismatch)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(items[0].word.text, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText(uiString(R.string.learn_type_hint)).assertDoesNotExist()
        compose.onNodeWithText(uiString(R.string.learn_type_check)).assertDoesNotExist()
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
        compose.onNodeWithText(items[0].word.translation.text, useUnmergedTree = true)
            .performTouchInput { swipeLeft() }
        compose.waitUntil(timeoutMillis = WAIT_MILLIS) { events.isNotEmpty() }
        assertEquals(listOf(LearnUiEvent.Answer(remembered = true)), events)
    }

    @Test
    fun swipingTheCardRight_answersNotRemembered() {
        show(firstEncounter = false)
        compose.onNodeWithText(items[0].word.translation.text, useUnmergedTree = true)
            .performTouchInput { swipeRight() }
        compose.waitUntil(timeoutMillis = WAIT_MILLIS) { events.isNotEmpty() }
        assertEquals(listOf(LearnUiEvent.Answer(remembered = false)), events)
    }

    @Test
    fun whileSaving_bothAnswersAreDisabled() {
        show(submitting = true)
        compose.onNodeWithTag(ANSWER_REMEMBERED_TAG).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag(ANSWER_FORGOT_TAG).performScrollTo().assertIsNotEnabled()
    }

    /** Saving and the full entry share one overflow menu in the corner of the card. */
    @Test
    fun saveAndDetails_liveInTheOverflowMenu() {
        show()
        compose.onNodeWithContentDescription(uiString(R.string.learn_more_actions)).performClick()
        compose.onNodeWithText(uiString(R.string.action_save)).performClick()
        compose.onNodeWithContentDescription(uiString(R.string.learn_more_actions)).performClick()
        compose.onNodeWithText(uiString(R.string.learn_open_word)).performClick()

        assertTrue(events.contains(LearnUiEvent.ToggleSaved))
        assertEquals(items[0].word.id, openedWordId)
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}

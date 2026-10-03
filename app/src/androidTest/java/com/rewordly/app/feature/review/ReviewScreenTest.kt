package com.rewordly.app.feature.review

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import com.rewordly.app.R
import com.rewordly.app.core.common.AppError
import com.rewordly.app.core.ui.components.ANSWER_FORGOT_TAG
import com.rewordly.app.core.ui.components.ANSWER_REMEMBERED_TAG
import com.rewordly.app.data.local.MockVocabulary
import com.rewordly.app.domain.model.ReviewRating
import com.rewordly.app.domain.model.WordProgress
import com.rewordly.app.domain.model.WordStatus
import com.rewordly.app.domain.model.WordWithProgress
import com.rewordly.app.testing.setRewordlyContent
import com.rewordly.app.testing.uiPlural
import com.rewordly.app.testing.uiString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Drives [ReviewContent] with a tiny in-test state machine, so the full flow runs without Hilt or Room. */
class ReviewScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val items = MockVocabulary.words.take(3)
        .map { WordWithProgress(it, WordProgress(it.id, status = WordStatus.LEARNED)) }
    private val events = mutableListOf<ReviewUiEvent>()

    private fun inProgress(
        index: Int = 0,
        revealed: Boolean = false,
        submitting: Boolean = false,
        feedback: ReviewFeedback? = null,
        current: WordWithProgress = items[index],
    ) = ReviewUiState.InProgress(
        current = current,
        index = index,
        total = items.size,
        dueToday = items.size,
        revealed = revealed,
        isSubmitting = submitting,
        intervals = mapOf(
            ReviewRating.AGAIN to 1,
            ReviewRating.HARD to 2,
            ReviewRating.GOOD to 3,
            ReviewRating.EASY to 4,
        ),
        feedback = feedback,
        submitFailed = false,
    )

    private fun show(state: ReviewUiState, onLearn: () -> Unit = {}) {
        compose.setRewordlyContent {
            ReviewContent(state = state, onEvent = { events += it }, onOpenWord = {}, onStartLearning = onLearn)
        }
    }

    @Test
    fun showsDueCountProgressAndTheWord_withTheTranslationHidden() {
        show(inProgress())
        compose.onNodeWithText(uiString(R.string.learn_position, 1, 3)).assertIsDisplayed()
        compose.onNodeWithText(
            uiPlural(R.plurals.review_due_today, 3, 3),
        ).assertIsDisplayed()
        compose.onNodeWithText(items[0].word.text, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText(items[0].word.translation.text, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun revealButton_sendsTheRevealEvent() {
        show(inProgress())
        compose.onNodeWithText(uiString(R.string.action_reveal)).performClick()
        assertEquals(listOf<ReviewUiEvent>(ReviewUiEvent.Reveal), events)
    }

    /** The two answers are the only grading scale now, and they are reachable before revealing too. */
    @Test
    fun bothAnswers_areOfferedStraightAway() {
        show(inProgress())
        compose.onNodeWithText(uiString(R.string.study_answer_remembered)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(uiString(R.string.study_answer_forgot)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun revealedCard_showsTranslationAndBothAnswers() {
        show(inProgress(revealed = true))
        compose.onNodeWithText(items[0].word.translation.text, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag(ANSWER_REMEMBERED_TAG).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(ANSWER_FORGOT_TAG).performScrollTo().assertIsDisplayed()
    }

    /** The flip keeps only the visible face composed, so a screen reader never hears both sides. */
    @Test
    fun flippingTheCard_neverExposesBothFacesAtOnce() {
        val word = items[0].word.text
        show(inProgress())
        assertEquals(1, compose.onAllNodesWithText(word, useUnmergedTree = true).fetchSemanticsNodes().size)
        show(inProgress(revealed = true))
        assertEquals(1, compose.onAllNodesWithText(word, useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test
    fun answerBar_mapsToRememberedAndForgotten() {
        show(inProgress())
        compose.onNodeWithTag(ANSWER_REMEMBERED_TAG).performScrollTo().performClick()
        compose.onNodeWithTag(ANSWER_FORGOT_TAG).performScrollTo().performClick()
        assertEquals(
            listOf(ReviewUiEvent.Answer(ReviewRating.GOOD), ReviewUiEvent.Answer(ReviewRating.AGAIN)),
            events,
        )
    }

    @Test
    fun swipingLeft_meansRemembered() {
        show(inProgress())
        compose.onNodeWithText(items[0].word.text, useUnmergedTree = true).performTouchInput { swipeLeft() }
        compose.waitUntil(timeoutMillis = WAIT_MILLIS) { events.isNotEmpty() }
        assertEquals(listOf<ReviewUiEvent>(ReviewUiEvent.Answer(ReviewRating.GOOD)), events)
    }

    @Test
    fun swipingRight_meansForgotten() {
        show(inProgress())
        compose.onNodeWithText(items[0].word.text, useUnmergedTree = true).performTouchInput { swipeRight() }
        compose.waitUntil(timeoutMillis = WAIT_MILLIS) { events.isNotEmpty() }
        assertEquals(listOf<ReviewUiEvent>(ReviewUiEvent.Answer(ReviewRating.AGAIN)), events)
    }

    @Test
    fun whileSaving_bothAnswersAreDisabled() {
        show(inProgress(submitting = true))
        compose.onNodeWithTag(ANSWER_REMEMBERED_TAG).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag(ANSWER_FORGOT_TAG).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun wordWithoutExamples_stillShowsTheAnswerControls() {
        val bare = items[0].copy(word = items[0].word.copy(examples = emptyList()))
        show(inProgress(revealed = true, current = bare))
        compose.onNodeWithText(bare.word.translation.text, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag(ANSWER_REMEMBERED_TAG).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun feedbackAfterAnAnswer_namesTheRatingAndTheNextInterval() {
        show(inProgress(index = 1, feedback = ReviewFeedback(ReviewRating.GOOD, intervalDays = 6)))
        val expected = uiPlural(
            R.plurals.review_feedback_next,
            6,
            uiString(R.string.rating_good),
            6,
        )
        compose.onNodeWithText(expected).assertIsDisplayed()
    }

    @Test
    fun fullFlow_revealAnswerNextWord_untilTheSessionFinishes() {
        val state = mutableStateOf<ReviewUiState>(inProgress())
        compose.setRewordlyContent {
            ReviewContent(
                state = state.value,
                onEvent = { event ->
                    val current = state.value as? ReviewUiState.InProgress
                    if (current != null) {
                        state.value = when (event) {
                            ReviewUiEvent.Reveal -> current.copy(revealed = true)
                            is ReviewUiEvent.Answer ->
                                if (current.index + 1 < items.size) {
                                    inProgress(index = current.index + 1, feedback = ReviewFeedback(event.rating, 3))
                                } else {
                                    val answers = mapOf(ReviewRating.GOOD to 3)
                                    ReviewUiState.Finished(ReviewSummary(items.size, answers, 0))
                                }
                            else -> current
                        }
                    }
                },
                onOpenWord = {},
                onStartLearning = {},
            )
        }
        repeat(items.size) { index ->
            compose.onNodeWithText(items[index].word.text, useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithText(uiString(R.string.action_reveal)).performClick()
            compose.onNodeWithTag(ANSWER_REMEMBERED_TAG).performScrollTo().performClick()
        }
        compose.onNodeWithText(uiString(R.string.review_finished_title)).assertIsDisplayed()
        compose.onNodeWithText(uiString(R.string.review_finished_message, 3, 3)).assertIsDisplayed()
    }

    @Test
    fun finishedWithBacklog_offersToReviewMore() {
        show(ReviewUiState.Finished(ReviewSummary(30, mapOf(ReviewRating.GOOD to 30), remainingDue = 5)))
        compose.onNodeWithText(uiString(R.string.action_review_more)).performClick()
        assertTrue(events.contains(ReviewUiEvent.Restart))
    }

    @Test
    fun emptyQueue_showsTheFriendlyDoneState_andLinksToLearning() {
        var learning = false
        show(ReviewUiState.Empty, onLearn = { learning = true })
        compose.onNodeWithText(uiString(R.string.review_empty_done_title)).assertIsDisplayed()
        compose.onNodeWithText(uiString(R.string.action_learn_new)).performClick()
        assertTrue(learning)
    }

    @Test
    fun error_offersRetry() {
        show(ReviewUiState.Error(AppError.Database()))
        compose.onNodeWithText(uiString(R.string.action_retry)).performClick()
        assertEquals(listOf<ReviewUiEvent>(ReviewUiEvent.Restart), events)
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}

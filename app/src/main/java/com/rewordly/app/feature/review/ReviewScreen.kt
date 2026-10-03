package com.rewordly.app.feature.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rewordly.app.R
import com.rewordly.app.core.ui.components.EmptyState
import com.rewordly.app.core.ui.components.ErrorState
import com.rewordly.app.core.ui.components.ExampleSentence
import com.rewordly.app.core.ui.components.FlipCard
import com.rewordly.app.core.ui.components.LoadingState
import com.rewordly.app.core.ui.components.PrimaryButton
import com.rewordly.app.core.ui.components.SecondaryButton
import com.rewordly.app.core.ui.components.StudyAnswerBar
import com.rewordly.app.core.ui.components.SwipeableCard
import com.rewordly.app.core.ui.components.VocabularyCard
import com.rewordly.app.core.ui.messageRes
import com.rewordly.app.core.ui.theme.Dimens
import com.rewordly.app.domain.model.ReviewRating
import com.rewordly.app.domain.model.WordWithProgress
import com.rewordly.app.domain.model.isGraduated

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    onOpenWord: (String) -> Unit,
    onStartLearning: () -> Unit,
    viewModel: ReviewViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Coming back to the tab rebuilds a stale queue (new day, new due words) but never a session in progress.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onEvent(ReviewUiEvent.Refresh) }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.review_title)) }) }) { padding ->
        ReviewContent(
            state = uiState,
            onEvent = viewModel::onEvent,
            onOpenWord = onOpenWord,
            onStartLearning = onStartLearning,
            modifier = Modifier.padding(padding),
        )
    }
}

/** Stateless review UI, driven entirely by [state] so it can be tested without a ViewModel. */
@Composable
fun ReviewContent(
    state: ReviewUiState,
    onEvent: (ReviewUiEvent) -> Unit,
    onOpenWord: (String) -> Unit,
    onStartLearning: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        ReviewUiState.Loading -> LoadingState(modifier)
        ReviewUiState.Empty -> EmptyState(
            icon = Icons.Outlined.TaskAlt,
            title = stringResource(R.string.review_empty_done_title),
            message = stringResource(R.string.review_empty_done_message),
            modifier = modifier,
            action = {
                PrimaryButton(text = stringResource(R.string.action_learn_new), onClick = onStartLearning)
            },
        )
        is ReviewUiState.Error -> ErrorState(
            message = stringResource(state.error.messageRes),
            onRetry = { onEvent(ReviewUiEvent.Restart) },
            modifier = modifier,
        )
        is ReviewUiState.InProgress -> ReviewInProgress(state, onEvent, onOpenWord, modifier)
        is ReviewUiState.Finished -> ReviewFinished(state.summary, onEvent, onStartLearning, modifier)
    }
}

@Composable
private fun ReviewFinished(
    summary: ReviewSummary,
    onEvent: (ReviewUiEvent) -> Unit,
    onStartLearning: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val message = buildString {
        append(stringResource(R.string.review_finished_message, summary.total, summary.remembered))
        if (summary.remainingDue > 0) {
            append('\n')
            append(pluralStringResource(R.plurals.review_remaining, summary.remainingDue, summary.remainingDue))
        }
    }
    EmptyState(
        icon = Icons.Outlined.EmojiEvents,
        title = stringResource(R.string.review_finished_title),
        message = message,
        modifier = modifier,
        action = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
                if (summary.remainingDue > 0) {
                    PrimaryButton(
                        text = stringResource(R.string.action_review_more),
                        onClick = { onEvent(ReviewUiEvent.Restart) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                SecondaryButton(
                    text = stringResource(R.string.action_learn_new),
                    onClick = onStartLearning,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

@Composable
private fun ReviewInProgress(
    state: ReviewUiState.InProgress,
    onEvent: (ReviewUiEvent) -> Unit,
    onOpenWord: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Both answers are the same two the swipe offers, so the wording lives in one place.
    val remembered = stringResource(R.string.study_answer_remembered)
    val forgotten = stringResource(R.string.study_answer_forgot)
    val answer: (Boolean) -> Unit = { wasRemembered ->
        onEvent(ReviewUiEvent.Answer(if (wasRemembered) ReviewRating.GOOD else ReviewRating.AGAIN))
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.screenPadding, vertical = Dimens.spaceSm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = Dimens.maxContentWidth)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceLg),
        ) {
            ReviewHeader(state)
            state.feedback?.let { FeedbackBanner(it) }
            SwipeableCard(
                leftLabel = remembered,
                rightLabel = forgotten,
                onSwipeLeft = { answer(true) },
                onSwipeRight = { answer(false) },
                enabled = !state.isSubmitting,
                // A failed save changes the key too, which springs the card back into view.
                resetKey = state.current.word.id to state.submitFailed,
                modifier = Modifier.fillMaxWidth(),
            ) {
                ReviewFlashCard(item = state.current, revealed = state.revealed)
            }
            if (!state.revealed) {
                Text(
                    text = stringResource(R.string.review_think),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PrimaryButton(
                        text = stringResource(R.string.action_reveal),
                        icon = Icons.Outlined.Visibility,
                        onClick = { onEvent(ReviewUiEvent.Reveal) },
                        modifier = Modifier.weight(1f),
                    )
                    DetailsButton(wordId = state.current.word.id, word = state.current.word.text, onOpenWord)
                }
            } else {
                // Words without examples simply skip this block; the card itself still carries the meaning.
                Column(
                    verticalArrangement = Arrangement.spacedBy(Dimens.spaceLg),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                ) {
                    state.current.word.examples.take(MAX_EXAMPLES).forEach { example ->
                        ExampleSentence(example = example, targetWord = state.current.word.text)
                    }
                }
            }
            StudyAnswerBar(
                rememberedLabel = remembered,
                forgottenLabel = forgotten,
                onAnswer = answer,
                enabled = !state.isSubmitting,
            )
            if (state.submitFailed) {
                Text(
                    text = stringResource(R.string.review_submit_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

@Composable
private fun DetailsButton(wordId: String, word: String, onOpenWord: (String) -> Unit) {
    val description = stringResource(R.string.a11y_open_details, word)
    FilledTonalIconButton(
        onClick = { onOpenWord(wordId) },
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Icon(imageVector = Icons.Outlined.Info, contentDescription = null)
    }
}

/** Front shows the word alone, back adds the answer, so revealing turns the card over. */
@Composable
private fun ReviewFlashCard(item: WordWithProgress, revealed: Boolean) {
    val face: @Composable (Boolean) -> Unit = { showTranslation ->
        VocabularyCard(
            word = item.word,
            isSaved = item.progress.isSaved,
            isLearned = item.progress.isGraduated,
            showTranslation = showTranslation,
        )
    }
    FlipCard(flipped = revealed, front = { face(false) }, back = { face(true) })
}

@Composable
private fun ReviewHeader(state: ReviewUiState.InProgress) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = pluralStringResource(R.plurals.review_due_today, state.dueToday, state.dueToday),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.learn_position, state.index + 1, state.total),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LinearProgressIndicator(progress = { state.fraction }, modifier = Modifier.fillMaxWidth())
        Text(
            text = stringResource(R.string.study_swipe_hint),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun FeedbackBanner(feedback: ReviewFeedback) {
    val colors = ratingColors(feedback.rating)
    val label = stringResource(feedback.rating.labelRes)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.medium,
        color = colors.container,
        contentColor = colors.content,
    ) {
        Text(
            text = pluralStringResource(
                R.plurals.review_feedback_next,
                feedback.intervalDays,
                label,
                feedback.intervalDays,
            ),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = Dimens.spaceLg, vertical = Dimens.spaceSm),
        )
    }
}

private data class RatingColors(val container: Color, val content: Color)

@Composable
private fun ratingColors(rating: ReviewRating): RatingColors {
    val scheme = MaterialTheme.colorScheme
    return when (rating) {
        ReviewRating.AGAIN -> RatingColors(scheme.errorContainer, scheme.onErrorContainer)
        ReviewRating.HARD -> RatingColors(scheme.secondaryContainer, scheme.onSecondaryContainer)
        ReviewRating.GOOD -> RatingColors(scheme.primary, scheme.onPrimary)
        ReviewRating.EASY -> RatingColors(scheme.tertiary, scheme.onTertiary)
    }
}

private val ReviewRating.labelRes: Int
    get() = when (this) {
        ReviewRating.AGAIN -> R.string.rating_again
        ReviewRating.HARD -> R.string.rating_hard
        ReviewRating.GOOD -> R.string.rating_good
        ReviewRating.EASY -> R.string.rating_easy
    }

private const val MAX_EXAMPLES = 2

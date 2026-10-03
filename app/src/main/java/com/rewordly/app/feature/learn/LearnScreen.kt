package com.rewordly.app.feature.learn

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
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rewordly.app.R
import com.rewordly.app.core.ui.components.EmptyState
import com.rewordly.app.core.ui.components.LoadingState
import com.rewordly.app.core.ui.components.PrimaryButton
import com.rewordly.app.core.ui.components.PronunciationButton
import com.rewordly.app.core.ui.components.StudyAnswerBar
import com.rewordly.app.core.ui.components.SwipeableCard
import com.rewordly.app.core.ui.components.VocabularyCard
import com.rewordly.app.core.ui.theme.Dimens
import com.rewordly.app.domain.model.isGraduated

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearnScreen(onOpenWord: (String) -> Unit, viewModel: LearnViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.learn_title)) }) }) { padding ->
        when (val state = uiState) {
            LearnUiState.Loading -> LoadingState(Modifier.padding(padding))
            LearnUiState.Empty -> EmptyState(
                icon = Icons.AutoMirrored.Outlined.MenuBook,
                title = stringResource(R.string.learn_empty_title),
                message = stringResource(R.string.learn_empty_message),
                modifier = Modifier.padding(padding),
            )
            is LearnUiState.Finished -> EmptyState(
                icon = Icons.Outlined.EmojiEvents,
                title = stringResource(R.string.learn_finished_title),
                message = stringResource(R.string.learn_finished_message, state.completedCount, state.total),
                modifier = Modifier.padding(padding),
                action = {
                    PrimaryButton(
                        text = stringResource(R.string.action_review_again),
                        onClick = { viewModel.onEvent(LearnUiEvent.Restart) },
                    )
                },
            )
            is LearnUiState.Content -> LearnContent(
                state = state,
                onEvent = viewModel::onEvent,
                onOpenWord = onOpenWord,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
fun LearnContent(
    state: LearnUiState.Content,
    onEvent: (LearnUiEvent) -> Unit,
    onOpenWord: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // A brand new word is triaged ("do you know it?"), a word in progress is reviewed ("did you recall it?").
    val remembered = stringResource(
        if (state.isFirstEncounter) R.string.study_answer_known else R.string.study_answer_remembered,
    )
    val forgotten = stringResource(
        if (state.isFirstEncounter) R.string.study_answer_study else R.string.study_answer_forgot,
    )
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.screenPadding, vertical = Dimens.spaceSm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceLg),
    ) {
        LearnHeader(state)
        SwipeableCard(
            leftLabel = remembered,
            rightLabel = forgotten,
            onSwipeLeft = { onEvent(LearnUiEvent.Answer(remembered = true)) },
            onSwipeRight = { onEvent(LearnUiEvent.Answer(remembered = false)) },
            enabled = !state.isSubmitting,
            resetKey = state.current.word.id,
            modifier = Modifier
                .widthIn(max = Dimens.maxContentWidth)
                .fillMaxWidth(),
        ) {
            VocabularyCard(
                word = state.current.word,
                isSaved = state.current.progress.isSaved,
                isLearned = state.current.progress.isGraduated,
                showTranslation = state.revealed,
                expanded = state.revealed,
            )
        }
        LearnSecondaryActions(state = state, onEvent = onEvent, onOpenWord = onOpenWord)
        if (!state.revealed) {
            Text(
                text = stringResource(R.string.review_think),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .widthIn(max = Dimens.maxContentWidth)
                    .fillMaxWidth(),
            )
            PrimaryButton(
                text = stringResource(R.string.action_reveal),
                icon = Icons.Outlined.Visibility,
                onClick = { onEvent(LearnUiEvent.Reveal) },
                modifier = Modifier
                    .widthIn(max = Dimens.maxContentWidth)
                    .fillMaxWidth(),
            )
        }
        StudyAnswerBar(
            rememberedLabel = remembered,
            forgottenLabel = forgotten,
            onAnswer = { onEvent(LearnUiEvent.Answer(remembered = it)) },
            enabled = !state.isSubmitting,
            modifier = Modifier.widthIn(max = Dimens.maxContentWidth),
        )
        if (state.submitFailed) {
            Text(
                text = stringResource(R.string.study_submit_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .widthIn(max = Dimens.maxContentWidth)
                    .fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun LearnHeader(state: LearnUiState.Content) {
    Column(
        modifier = Modifier
            .widthIn(max = Dimens.maxContentWidth)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = stringResource(R.string.learn_completed, state.completedCount, state.total),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.learn_position, state.index + 1, state.total),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LinearProgressIndicator(progress = { state.session.fraction }, modifier = Modifier.fillMaxWidth())
        Text(
            text = stringResource(R.string.study_swipe_hint),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Listen, save and open the full entry: secondary to answering, so they are compact icon buttons. */
@Composable
private fun LearnSecondaryActions(
    state: LearnUiState.Content,
    onEvent: (LearnUiEvent) -> Unit,
    onOpenWord: (String) -> Unit,
) {
    val saved = state.current.progress.isSaved
    val saveLabel = stringResource(if (saved) R.string.action_unsave else R.string.action_save)
    val detailsDescription = stringResource(R.string.a11y_open_details, state.current.word.text)
    Row(
        modifier = Modifier
            .widthIn(max = Dimens.maxContentWidth)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PronunciationButton(text = state.current.word.text)
        FilledTonalIconButton(
            onClick = { onEvent(LearnUiEvent.ToggleSaved) },
            modifier = Modifier.semantics { contentDescription = saveLabel },
        ) {
            Icon(
                imageVector = if (saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                contentDescription = null,
            )
        }
        FilledTonalIconButton(
            onClick = { onOpenWord(state.current.word.id) },
            modifier = Modifier.semantics { contentDescription = detailsDescription },
        ) {
            Icon(imageVector = Icons.Outlined.Info, contentDescription = null)
        }
    }
}

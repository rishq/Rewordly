package com.rewordly.app.feature.review

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rewordly.app.R
import com.rewordly.app.core.ui.components.EmptyState
import com.rewordly.app.core.ui.components.ExampleSentence
import com.rewordly.app.core.ui.components.LoadingState
import com.rewordly.app.core.ui.components.PrimaryButton
import com.rewordly.app.core.ui.components.SecondaryButton
import com.rewordly.app.core.ui.components.VocabularyCard
import com.rewordly.app.core.ui.theme.Dimens
import com.rewordly.app.domain.model.WordStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(onOpenWord: (String) -> Unit, viewModel: ReviewViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.review_title)) }) }) { padding ->
        val modifier = Modifier.padding(padding)
        when (val state = uiState) {
            ReviewUiState.Loading -> LoadingState(modifier)
            ReviewUiState.Empty -> EmptyState(
                icon = Icons.Outlined.TaskAlt,
                title = stringResource(R.string.review_empty_title),
                message = stringResource(R.string.review_empty_message),
                modifier = modifier,
            )
            is ReviewUiState.InProgress -> ReviewInProgress(state, viewModel::onEvent, onOpenWord, modifier)
            is ReviewUiState.Finished -> EmptyState(
                icon = Icons.Outlined.EmojiEvents,
                title = stringResource(R.string.review_finished_title),
                message = stringResource(R.string.review_finished_message, state.known, state.unknown),
                modifier = modifier,
                action = {
                    PrimaryButton(
                        text = stringResource(R.string.action_review_again),
                        onClick = { viewModel.onEvent(ReviewUiEvent.Restart) },
                    )
                },
            )
        }
    }
}

@Composable
private fun ReviewInProgress(
    state: ReviewUiState.InProgress,
    onEvent: (ReviewUiEvent) -> Unit,
    onOpenWord: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val item = state.current
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
            Text(
                text = stringResource(R.string.learn_position, state.index + 1, state.total),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = { state.index.toFloat() / state.total },
                modifier = Modifier.fillMaxWidth(),
            )
            VocabularyCard(
                word = item.word,
                isSaved = item.progress.isSaved,
                isLearned = item.progress.status == WordStatus.LEARNED,
                showTranslation = state.revealed,
                onClick = if (state.revealed) ({ onOpenWord(item.word.id) }) else null,
            )
            if (!state.revealed) {
                Text(
                    text = stringResource(R.string.review_think),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                PrimaryButton(
                    text = stringResource(R.string.action_reveal),
                    icon = Icons.Outlined.Visibility,
                    onClick = { onEvent(ReviewUiEvent.Reveal) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            AnimatedVisibility(visible = state.revealed, enter = fadeIn() + expandVertically()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(Dimens.spaceLg),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                ) {
                    item.word.examples.firstOrNull()?.let { ExampleSentence(example = it) }
                    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMd)) {
                        SecondaryButton(
                            text = stringResource(R.string.action_dont_know),
                            icon = Icons.Filled.Close,
                            onClick = { onEvent(ReviewUiEvent.Unknown) },
                            modifier = Modifier.weight(1f),
                        )
                        PrimaryButton(
                            text = stringResource(R.string.action_know),
                            icon = Icons.Filled.Check,
                            onClick = { onEvent(ReviewUiEvent.Known) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

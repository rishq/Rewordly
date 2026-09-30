package com.rewordly.app.feature.learn

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.BookmarkBorder
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rewordly.app.R
import com.rewordly.app.core.ui.components.EmptyState
import com.rewordly.app.core.ui.components.ExampleSentence
import com.rewordly.app.core.ui.components.LoadingState
import com.rewordly.app.core.ui.components.PrimaryButton
import com.rewordly.app.core.ui.components.SecondaryButton
import com.rewordly.app.core.ui.components.SectionHeader
import com.rewordly.app.core.ui.components.VocabularyCard
import com.rewordly.app.core.ui.theme.Dimens
import com.rewordly.app.domain.model.WordStatus

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
private fun LearnContent(
    state: LearnUiState.Content,
    onEvent: (LearnUiEvent) -> Unit,
    onOpenWord: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val positionText = stringResource(R.string.learn_position, state.index + 1, state.total)
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.screenPadding, vertical = Dimens.spaceSm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceLg),
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = Dimens.maxContentWidth)
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs),
        ) {
            Text(
                text = positionText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = { (state.index + 1).toFloat() / state.total },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        AnimatedContent(
            targetState = state.current,
            contentKey = { it.word.id },
            transitionSpec = {
                (slideInHorizontally(tween(DURATION)) { it / 4 } + fadeIn(tween(DURATION))) togetherWith
                    (slideOutHorizontally(tween(DURATION)) { -it / 4 } + fadeOut(tween(DURATION)))
            },
            label = "vocabularyCard",
            modifier = Modifier.widthIn(max = Dimens.maxContentWidth),
        ) { item ->
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceLg)) {
                VocabularyCard(
                    word = item.word,
                    isSaved = item.progress.isSaved,
                    isLearned = item.progress.status == WordStatus.LEARNED,
                    onClick = { onOpenWord(item.word.id) },
                )
                item.word.examples.firstOrNull()?.let { example ->
                    SectionHeader(title = stringResource(R.string.learn_example))
                    ExampleSentence(example = example)
                }
            }
        }

        LearnControls(state = state, onEvent = onEvent)
    }
}

@Composable
private fun LearnControls(state: LearnUiState.Content, onEvent: (LearnUiEvent) -> Unit) {
    val saved = state.current.progress.isSaved
    val saveDescription = stringResource(if (saved) R.string.action_unsave else R.string.action_save)
    val previousDescription = stringResource(R.string.action_previous)
    val nextDescription = stringResource(R.string.action_next)
    Column(
        modifier = Modifier
            .widthIn(max = Dimens.maxContentWidth)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(
                onClick = { onEvent(LearnUiEvent.Previous) },
                enabled = state.canGoBack,
                modifier = Modifier.semantics { contentDescription = previousDescription },
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            }
            SecondaryButton(
                text = saveDescription,
                icon = if (saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                onClick = { onEvent(LearnUiEvent.ToggleSaved) },
            )
            FilledTonalIconButton(
                onClick = { onEvent(LearnUiEvent.Next) },
                enabled = state.canGoForward,
                modifier = Modifier.semantics { contentDescription = nextDescription },
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            }
        }
        PrimaryButton(
            text = stringResource(if (state.isLearned) R.string.action_mark_not_learned else R.string.action_learned),
            icon = Icons.Filled.Check,
            onClick = { onEvent(LearnUiEvent.ToggleLearned) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private const val DURATION = 250

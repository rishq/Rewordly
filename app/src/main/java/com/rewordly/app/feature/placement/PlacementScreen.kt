package com.rewordly.app.feature.placement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rewordly.app.R
import com.rewordly.app.core.ui.components.EmptyState
import com.rewordly.app.core.ui.components.LoadingState
import com.rewordly.app.core.ui.components.PrimaryButton
import com.rewordly.app.core.ui.components.SecondaryButton
import com.rewordly.app.core.ui.components.SectionCard
import com.rewordly.app.core.ui.labelRes
import com.rewordly.app.core.ui.theme.Dimens
import com.rewordly.app.domain.model.PlacementQuestion
import com.rewordly.app.domain.model.PlacementResult

/**
 * Optional vocabulary check that estimates the CEFR level. Everything can be skipped: the back arrow
 * and the skip action leave at any point, and the result is always presented as an estimate.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlacementScreen(onBack: () -> Unit, onOpenSettings: () -> Unit, viewModel: PlacementViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.placement_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    if (uiState is PlacementUiState.InProgress) {
                        TextButton(onClick = { viewModel.onEvent(PlacementEvent.SkipQuestion) }) {
                            Text(stringResource(R.string.action_skip))
                        }
                    }
                },
            )
        },
    ) { padding ->
        when (val state = uiState) {
            PlacementUiState.Loading -> LoadingState(Modifier.padding(padding))
            PlacementUiState.Empty -> EmptyState(
                icon = Icons.Outlined.School,
                title = stringResource(R.string.placement_empty_title),
                message = stringResource(R.string.placement_empty_message),
                modifier = Modifier.padding(padding),
            )
            PlacementUiState.Intro -> IntroContent(
                onStart = { viewModel.onEvent(PlacementEvent.Start) },
                onSkip = onBack,
                modifier = Modifier.padding(padding),
            )
            is PlacementUiState.InProgress -> QuestionContent(
                state = state,
                onAnswer = { index -> viewModel.onEvent(PlacementEvent.Answer(index)) },
                modifier = Modifier.padding(padding),
            )
            is PlacementUiState.Result -> ResultContent(
                result = state.result,
                onApply = { viewModel.onEvent(PlacementEvent.Apply) },
                onRetry = { viewModel.onEvent(PlacementEvent.Restart) },
                onChooseManually = onOpenSettings,
                modifier = Modifier.padding(padding),
            )
            PlacementUiState.Applied -> {
                LaunchedEffect(Unit) { onBack() }
                LoadingState(Modifier.padding(padding))
            }
        }
    }
}

@Composable
private fun IntroContent(onStart: () -> Unit, onSkip: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.screenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.widthIn(max = Dimens.maxContentWidth).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceLg),
        ) {
            Text(
                text = stringResource(R.string.placement_intro_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.placement_intro_message),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PrimaryButton(
                text = stringResource(R.string.action_get_started),
                onClick = onStart,
                modifier = Modifier.fillMaxWidth(),
            )
            SecondaryButton(
                text = stringResource(R.string.action_skip),
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun QuestionContent(
    state: PlacementUiState.InProgress,
    onAnswer: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.screenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.widthIn(max = Dimens.maxContentWidth).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceLg),
        ) {
            LinearProgressIndicator(
                progress = { state.fraction },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.placement_progress, state.index + 1, state.total),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.placement_question),
                style = MaterialTheme.typography.titleMedium,
            )
            QuestionCard(question = state.question)
            state.question.options.forEachIndexed { index, option ->
                SecondaryButton(
                    text = option,
                    onClick = { onAnswer(index) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun QuestionCard(question: PlacementQuestion, modifier: Modifier = Modifier) {
    SectionCard(title = question.level.name, modifier = modifier) {
        Text(
            text = question.prompt,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ResultContent(
    result: PlacementResult,
    onApply: () -> Unit,
    onRetry: () -> Unit,
    onChooseManually: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.screenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.widthIn(max = Dimens.maxContentWidth).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceLg),
        ) {
            Text(
                text = stringResource(R.string.placement_result_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            SectionCard(title = stringResource(R.string.placement_result_title)) {
                Text(
                    text = stringResource(result.level.labelRes),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(
                        R.string.placement_result_score,
                        result.correctAnswers,
                        result.answered,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!result.isConfident) {
                    Text(
                        text = stringResource(R.string.placement_result_uncertain),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = stringResource(R.string.placement_result_disclaimer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PrimaryButton(
                text = stringResource(R.string.placement_result_apply),
                onClick = onApply,
                modifier = Modifier.fillMaxWidth(),
            )
            SecondaryButton(
                text = stringResource(R.string.placement_retry),
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = onChooseManually, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_level))
            }
        }
    }
}

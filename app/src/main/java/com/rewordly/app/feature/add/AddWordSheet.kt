package com.rewordly.app.feature.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rewordly.app.R
import com.rewordly.app.core.ui.components.PrimaryButton
import com.rewordly.app.core.ui.components.SecondaryButton
import com.rewordly.app.core.ui.labelRes
import com.rewordly.app.core.ui.messageRes
import com.rewordly.app.core.ui.theme.Dimens
import com.rewordly.app.core.ui.theme.RewordlyTextStyles
import com.rewordly.app.domain.model.Word

const val ADD_WORD_INPUT_TAG = "add_word_input"
const val ADD_WORD_SUBMIT_TAG = "add_word_submit"
const val ADD_WORD_LOADING_TAG = "add_word_loading"
const val ADD_WORD_ADDED_TAG = "add_word_added"
const val ADD_WORD_OPEN_TAG = "add_word_open"
const val ADD_WORD_AGAIN_TAG = "add_word_again"
const val ADD_WORD_ERROR_TAG = "add_word_error"

/**
 * Bottom sheet for adding a word the user typed.
 *
 * [initialWord] is whatever was already in the search field, so looking up a word that is not in the
 * vocabulary is one tap instead of retyping it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddWordSheet(
    initialWord: String,
    onDismiss: () -> Unit,
    onOpenWord: (String) -> Unit,
    viewModel: AddWordViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // The sheet is composed afresh on every open, so this is the only place the state is reset.
    LaunchedEffect(Unit) { viewModel.onEvent(AddWordEvent.Started(initialWord)) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        AddWordContent(
            state = state,
            onEvent = viewModel::onEvent,
            onOpenWord = onOpenWord,
        )
    }
}

/** Stateless body of the sheet. */
@Composable
fun AddWordContent(
    state: AddWordUiState,
    onEvent: (AddWordEvent) -> Unit,
    onOpenWord: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = Dimens.maxContentWidth)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.screenPadding)
            .padding(bottom = Dimens.spaceXl),
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceLg),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs)) {
            Text(text = stringResource(R.string.add_word_title), style = MaterialTheme.typography.titleLarge)
            Text(
                text = stringResource(R.string.add_word_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!state.isSettled) {
            OutlinedTextField(
                value = state.word,
                onValueChange = { onEvent(AddWordEvent.WordChanged(it)) },
                label = { Text(stringResource(R.string.ai_word_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onEvent(AddWordEvent.Submit) }),
                isError = state.inputError != null,
                supportingText = state.inputError?.let { { Text(stringResource(R.string.ai_word_invalid)) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(ADD_WORD_INPUT_TAG),
            )
            PrimaryButton(
                text = stringResource(R.string.add_word_submit),
                onClick = { onEvent(AddWordEvent.Submit) },
                enabled = state.canSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(ADD_WORD_SUBMIT_TAG),
            )
        }
        StatusArea(state = state, onEvent = onEvent, onOpenWord = onOpenWord)
    }
}

@Composable
private fun StatusArea(state: AddWordUiState, onEvent: (AddWordEvent) -> Unit, onOpenWord: (String) -> Unit) {
    when (val status = state.status) {
        AddWordStatus.Idle -> Unit
        AddWordStatus.Looking -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite }
                .testTag(ADD_WORD_LOADING_TAG),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
        ) {
            // Indeterminate: two HTTP calls give no progress to report, so no percentage is ever shown.
            CircularProgressIndicator(modifier = Modifier.size(Dimens.iconMd), strokeWidth = LOADING_STROKE)
            Text(text = stringResource(R.string.add_word_looking), style = MaterialTheme.typography.bodyMedium)
        }
        is AddWordStatus.Added -> {
            AddedWordCard(word = status.word, hasTranscription = status.hasTranscription)
            SettledActions(
                openWordId = status.word.id,
                onEvent = onEvent,
                onOpenWord = onOpenWord,
            )
        }
        is AddWordStatus.AlreadyExists -> {
            StatusMessage(stringResource(R.string.add_word_exists, status.text))
            SettledActions(
                openWordId = status.wordId,
                onEvent = onEvent,
                onOpenWord = onOpenWord,
            )
        }
        is AddWordStatus.NotFound -> {
            StatusMessage(stringResource(R.string.add_word_not_found, status.word))
            RetryButton(enabled = state.isOnline, onEvent = onEvent)
        }
        is AddWordStatus.Failed -> {
            StatusMessage(stringResource(status.error.messageRes))
            RetryButton(enabled = state.isOnline, onEvent = onEvent)
        }
    }
}

/** What the user can do once the word is in the vocabulary: read its card, or add another one. */
@Composable
private fun SettledActions(openWordId: String, onEvent: (AddWordEvent) -> Unit, onOpenWord: (String) -> Unit) {
    PrimaryButton(
        text = stringResource(R.string.add_word_open),
        onClick = { onOpenWord(openWordId) },
        modifier = Modifier
            .fillMaxWidth()
            .testTag(ADD_WORD_OPEN_TAG),
    )
    SecondaryButton(
        text = stringResource(R.string.add_word_again),
        onClick = { onEvent(AddWordEvent.Reset) },
        modifier = Modifier
            .fillMaxWidth()
            .testTag(ADD_WORD_AGAIN_TAG),
    )
}

@Composable
private fun RetryButton(enabled: Boolean, onEvent: (AddWordEvent) -> Unit) {
    SecondaryButton(
        text = stringResource(R.string.action_retry),
        onClick = { onEvent(AddWordEvent.Submit) },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AddedWordCard(word: Word, hasTranscription: Boolean) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .testTag(ADD_WORD_ADDED_TAG),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(Dimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
            ) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(text = word.text, style = MaterialTheme.typography.headlineSmall)
            }
            if (word.pronunciation.isNotBlank()) {
                Text(
                    text = word.pronunciation,
                    style = RewordlyTextStyles.pronunciation,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = word.translation.text,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(word.partOfSpeech.labelRes),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (word.definition.isNotBlank()) {
                Text(
                    text = word.definition,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!hasTranscription) {
                Text(
                    text = stringResource(R.string.add_word_no_transcription),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatusMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag(ADD_WORD_ERROR_TAG),
    )
}

private val LOADING_STROKE = 2.dp

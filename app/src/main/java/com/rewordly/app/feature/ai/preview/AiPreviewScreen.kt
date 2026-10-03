package com.rewordly.app.feature.ai.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rewordly.app.R
import com.rewordly.app.core.ui.components.EmptyState
import com.rewordly.app.core.ui.components.LoadingState
import com.rewordly.app.core.ui.components.Pill
import com.rewordly.app.core.ui.components.PrimaryButton
import com.rewordly.app.core.ui.components.VocabularyCard
import com.rewordly.app.core.ui.labelRes
import com.rewordly.app.core.ui.theme.Dimens
import com.rewordly.app.domain.model.PreviewItem
import com.rewordly.app.domain.model.toWord
import com.rewordly.app.feature.ai.generationErrorMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiPreviewScreen(
    onBack: () -> Unit,
    onOpenWord: (String) -> Unit,
    viewModel: AiPreviewViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ai_preview_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        bottomBar = {
            (state as? AiPreviewUiState.Content)?.let { SaveBar(it, viewModel::onEvent) }
        },
    ) { padding ->
        AiPreviewContent(
            state = state,
            onEvent = viewModel::onEvent,
            onOpenWord = onOpenWord,
            modifier = Modifier.padding(padding),
        )
    }
}

/** Stateless preview list, driven by [state]. The save bar is separate so tests can use either part. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiPreviewContent(
    state: AiPreviewUiState,
    onEvent: (AiPreviewEvent) -> Unit,
    onOpenWord: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        AiPreviewUiState.Loading -> LoadingState(modifier)
        AiPreviewUiState.NotFound -> EmptyState(
            icon = Icons.Outlined.AutoAwesome,
            title = stringResource(R.string.ai_preview_title),
            message = stringResource(R.string.ai_preview_not_found),
            modifier = modifier,
        )
        is AiPreviewUiState.Content -> {
            PreviewList(state, onEvent, onOpenWord, modifier)
            val detail = state.detailIndex?.let { state.items.getOrNull(it) }
            if (detail != null) {
                ModalBottomSheet(
                    onDismissRequest = { onEvent(AiPreviewEvent.CloseDetail) },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                ) {
                    VocabularyCard(
                        word = detail.word.toWord(),
                        isSaved = false,
                        isLearned = false,
                        expanded = true,
                        modifier = Modifier.padding(horizontal = Dimens.screenPadding, vertical = Dimens.spaceLg),
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewList(
    state: AiPreviewUiState.Content,
    onEvent: (AiPreviewEvent) -> Unit,
    onOpenWord: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .widthIn(max = Dimens.maxContentWidth)
            .fillMaxWidth(),
        contentPadding = PaddingValues(Dimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
    ) {
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs)) {
                Text(
                    text = stringResource(R.string.ai_preview_hint),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
                    TextButton(
                        onClick = { onEvent(AiPreviewEvent.SetAllSelected(true)) },
                        modifier = Modifier.testTag("ai_select_all"),
                    ) { Text(stringResource(R.string.ai_preview_select_all)) }
                    TextButton(
                        onClick = { onEvent(AiPreviewEvent.SetAllSelected(false)) },
                        modifier = Modifier.testTag("ai_select_none"),
                    ) { Text(stringResource(R.string.ai_preview_select_none)) }
                }
                if (state.canRegenerate) {
                    TextButton(
                        onClick = { onEvent(AiPreviewEvent.RegenerateAll) },
                        enabled = !state.isBusy,
                        modifier = Modifier.testTag("ai_regenerate_all"),
                    ) { Text(stringResource(R.string.ai_preview_regenerate_all)) }
                } else {
                    Text(
                        text = stringResource(R.string.ai_preview_regeneration_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.busy == PreviewBusy.RegeneratingAll) BusyRow()
                state.error?.let {
                    Text(
                        text = generationErrorMessage(it),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .semantics { liveRegion = LiveRegionMode.Polite }
                            .testTag("ai_preview_error"),
                    )
                }
            }
        }
        itemsIndexed(state.items, key = { _, item -> item.word.key }) { index, item ->
            PreviewCard(index, item, state, onEvent, onOpenWord)
        }
    }
}

@Composable
private fun BusyRow() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        CircularProgressIndicator(modifier = Modifier.size(Dimens.iconSm), strokeWidth = 2.dp)
        Text(text = stringResource(R.string.ai_preview_regenerating), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PreviewCard(
    index: Int,
    item: PreviewItem,
    state: AiPreviewUiState.Content,
    onEvent: (AiPreviewEvent) -> Unit,
    onOpenWord: (String) -> Unit,
) {
    val word = item.word
    val regenerating = state.busy == PreviewBusy.RegeneratingWord(index)
    val selectLabel = stringResource(R.string.ai_preview_select_item, word.word)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ai_item_$index"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(modifier = Modifier.padding(Dimens.spaceMd), verticalAlignment = Alignment.Top) {
            Checkbox(
                checked = item.selected && !item.isDuplicate,
                onCheckedChange = { onEvent(AiPreviewEvent.Toggle(index)) },
                enabled = !item.isDuplicate && !state.isBusy,
                modifier = Modifier
                    .testTag("ai_check_$index")
                    .semantics { contentDescription = selectLabel },
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs)) {
                Text(text = word.word, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = word.translation,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
                    Pill(text = stringResource(word.difficulty.labelRes))
                    Pill(text = stringResource(word.partOfSpeech.labelRes))
                }
                word.examples.firstOrNull()?.let { example ->
                    Text(text = example.english, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = example.russian,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spaceXs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { onEvent(AiPreviewEvent.OpenDetail(index)) },
                        modifier = Modifier.testTag("ai_details_$index"),
                    ) { Text(stringResource(R.string.ai_preview_details)) }
                    if (item.isDuplicate) {
                        TextButton(
                            onClick = { item.existingWordId?.let(onOpenWord) },
                            modifier = Modifier.testTag("ai_open_existing_$index"),
                        ) { Text(stringResource(R.string.ai_preview_open_existing)) }
                    } else if (state.canRegenerate) {
                        TextButton(
                            onClick = { onEvent(AiPreviewEvent.RegenerateWord(index)) },
                            enabled = !state.isBusy,
                            modifier = Modifier.testTag("ai_regen_$index"),
                        ) { Text(stringResource(R.string.ai_preview_regenerate_word)) }
                    }
                    if (regenerating) {
                        CircularProgressIndicator(modifier = Modifier.size(Dimens.iconSm), strokeWidth = 2.dp)
                    }
                }
                if (item.isDuplicate) {
                    Pill(text = stringResource(R.string.ai_preview_duplicate))
                }
            }
        }
    }
}

@Composable
fun SaveBar(state: AiPreviewUiState.Content, onEvent: (AiPreviewEvent) -> Unit) {
    Surface(tonalElevation = Dimens.cardElevation) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
        ) {
            state.message?.let { message ->
                Text(
                    text = when (message) {
                        is PreviewMessage.Saved ->
                            pluralStringResource(R.plurals.ai_saved_message, message.count, message.count)
                        PreviewMessage.NothingToSave -> stringResource(R.string.ai_saved_nothing)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .semantics { liveRegion = LiveRegionMode.Polite }
                        .testTag("ai_save_message"),
                )
            }
            PrimaryButton(
                text = stringResource(R.string.ai_preview_save, state.selectedCount),
                onClick = { onEvent(AiPreviewEvent.Save) },
                enabled = state.canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_save"),
            )
        }
    }
}

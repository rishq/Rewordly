package com.rewordly.app.feature.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rewordly.app.R
import com.rewordly.app.core.ui.components.EmptyState
import com.rewordly.app.core.ui.components.ErrorState
import com.rewordly.app.core.ui.components.LoadingState
import com.rewordly.app.core.ui.messageRes
import com.rewordly.app.core.ui.theme.Dimens
import com.rewordly.app.domain.model.GenerationHistoryEntry
import com.rewordly.app.domain.model.GenerationMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiHistoryScreen(
    onBack: () -> Unit,
    onOpenEntry: (String) -> Unit,
    viewModel: AiHistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val confirmClear by viewModel.confirmClear.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ai_history_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    if (state is AiHistoryUiState.Content) {
                        IconButton(onClick = viewModel::requestClear, modifier = Modifier.testTag("ai_history_clear")) {
                            Icon(
                                Icons.Outlined.DeleteSweep,
                                contentDescription = stringResource(R.string.ai_history_clear),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (val current = state) {
            AiHistoryUiState.Loading -> LoadingState(modifier)
            AiHistoryUiState.Empty -> EmptyState(
                icon = Icons.Outlined.History,
                title = stringResource(R.string.ai_history_empty_title),
                message = stringResource(R.string.ai_history_empty_message),
                modifier = modifier,
            )
            is AiHistoryUiState.Error -> ErrorState(
                message = stringResource(current.error.messageRes),
                onRetry = {},
                modifier = modifier,
            )
            is AiHistoryUiState.Content -> HistoryList(current.entries, onOpenEntry, viewModel::delete, modifier)
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = viewModel::dismissClear,
            title = { Text(stringResource(R.string.ai_history_clear_title)) },
            text = { Text(stringResource(R.string.ai_history_clear_text)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmClear, modifier = Modifier.testTag("ai_history_clear_confirm")) {
                    Text(stringResource(R.string.ai_history_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissClear) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
fun HistoryList(
    entries: List<GenerationHistoryEntry>,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val dateFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale)
    LazyColumn(
        modifier = modifier
            .widthIn(max = Dimens.maxContentWidth)
            .fillMaxWidth(),
        contentPadding = PaddingValues(Dimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
    ) {
        items(entries, key = { it.id }) { entry ->
            val date = dateFormat.format(Instant.ofEpochMilli(entry.createdAt).atZone(ZoneId.systemDefault()))
            HistoryCard(entry, date, onOpen, onDelete)
        }
    }
}

@Composable
private fun HistoryCard(
    entry: GenerationHistoryEntry,
    date: String,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val title = if (entry.mode == GenerationMode.TEXT) {
        stringResource(R.string.ai_history_text_summary, entry.description.toIntOrNull() ?: 0)
    } else {
        entry.description
    }
    Card(
        onClick = { onOpen(entry.id) },
        enabled = entry.hasResult,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ai_history_${entry.id}"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.padding(start = Dimens.spaceLg, top = Dimens.spaceSm, bottom = Dimens.spaceSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) {},
                verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs),
            ) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(
                        R.string.ai_history_entry_meta,
                        stringResource(entry.mode.shortRes),
                        entry.level.name,
                        entry.resultCount,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = date,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!entry.hasResult) {
                    Text(
                        text = stringResource(R.string.ai_history_open_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = { onDelete(entry.id) }) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.ai_history_delete))
            }
        }
    }
}

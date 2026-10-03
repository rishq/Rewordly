package com.rewordly.app.feature.data

import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rewordly.app.R
import com.rewordly.app.core.ui.components.LoadingState
import com.rewordly.app.core.ui.components.PrimaryButton
import com.rewordly.app.core.ui.components.SecondaryButton
import com.rewordly.app.core.ui.components.SectionCard
import com.rewordly.app.core.ui.messageRes
import com.rewordly.app.core.ui.theme.Dimens
import com.rewordly.app.domain.model.DataStats
import com.rewordly.app.domain.model.DuplicatePolicy
import com.rewordly.app.domain.model.ImportCandidate
import com.rewordly.app.domain.model.ImportIssue
import com.rewordly.app.domain.model.RestoreStrategy
import com.rewordly.app.domain.model.VocabularyEntry
import java.util.Date

/**
 * Import, export and backup, all through the Storage Access Framework.
 *
 * No storage permission is requested anywhere: the user picks the exact document, and only that
 * document is read or written. Nothing leaves the device.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataManagementScreen(onBack: () -> Unit, viewModel: DataManagementViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val onEvent = viewModel::onEvent

    // The picker is given every MIME type on purpose: providers disagree about what a CSV is, and the
    // content is validated anyway - the extension is never trusted on its own.
    val pickImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onEvent(DataManagementEvent.ImportFilePicked(it.toString())) }
    }
    val pickRestore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onEvent(DataManagementEvent.RestoreFilePicked(it.toString())) }
    }
    val createCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(CSV_MIME)) { uri ->
        uri?.let { onEvent(DataManagementEvent.ExportDestinationPicked(ExportKind.CSV, it.toString())) }
    }
    val createJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(JSON_MIME)) { uri ->
        uri?.let { onEvent(DataManagementEvent.ExportDestinationPicked(ExportKind.JSON, it.toString())) }
    }
    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(JSON_MIME)) { uri ->
        uri?.let { onEvent(DataManagementEvent.ExportDestinationPicked(ExportKind.BACKUP, it.toString())) }
    }

    val startExport: (ExportKind) -> Unit = { kind ->
        when (kind) {
            ExportKind.CSV -> createCsv.launch(SUGGESTED_CSV_NAME)
            ExportKind.JSON -> createJson.launch(SUGGESTED_JSON_NAME)
            ExportKind.BACKUP -> createBackup.launch(SUGGESTED_BACKUP_NAME)
        }
    }

    // An empty vocabulary still exports a valid file, but the user is told so first.
    var pendingEmptyExport by rememberSaveable { mutableStateOf<ExportKind?>(null) }
    val requestExport: (ExportKind) -> Unit = { kind ->
        if (state.stats.isEmpty) pendingEmptyExport = kind else startExport(kind)
    }

    val importPrompt = state.importPrompt
    val restorePrompt = state.restorePrompt
    val titleRes = when {
        importPrompt != null -> R.string.data_preview_title
        restorePrompt != null -> R.string.data_restore_title
        else -> R.string.data_title
    }
    // While a preview is open the back arrow cancels it instead of leaving the screen.
    val onNavigateUp: () -> Unit = if (importPrompt != null) {
        { onEvent(DataManagementEvent.DismissImport) }
    } else if (restorePrompt != null) {
        { onEvent(DataManagementEvent.DismissRestore) }
    } else {
        onBack
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(titleRes)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> LoadingState(Modifier.padding(padding))
            importPrompt != null -> ImportPreviewContent(
                prompt = importPrompt,
                isBusy = state.isBusy,
                onEvent = onEvent,
                modifier = Modifier.padding(padding),
            )
            restorePrompt != null -> RestoreConfirmContent(
                prompt = restorePrompt,
                isBusy = state.isBusy,
                onEvent = onEvent,
                modifier = Modifier.padding(padding),
            )
            else -> DataManagementContent(
                state = state,
                onEvent = onEvent,
                onExport = requestExport,
                onPickImport = { pickImport.launch(READABLE_TYPES) },
                onPickRestore = { pickRestore.launch(READABLE_TYPES) },
                modifier = Modifier.padding(padding),
            )
        }
    }

    pendingEmptyExport?.let { kind ->
        AlertDialog(
            onDismissRequest = { pendingEmptyExport = null },
            title = { Text(stringResource(R.string.data_export_empty_title)) },
            text = { Text(stringResource(R.string.data_export_empty_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingEmptyExport = null
                        startExport(kind)
                    },
                ) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingEmptyExport = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun DataManagementContent(
    state: DataManagementUiState,
    onEvent: (DataManagementEvent) -> Unit,
    onExport: (ExportKind) -> Unit,
    onPickImport: () -> Unit,
    onPickRestore: () -> Unit,
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
            modifier = Modifier
                .widthIn(max = Dimens.maxContentWidth)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceLg),
        ) {
            state.outcome?.let { outcome ->
                OutcomeBanner(outcome = outcome, onDismiss = { onEvent(DataManagementEvent.DismissOutcome) })
            }
            if (state.isBusy) BusyIndicator()
            StatsCard(state.stats)
            SectionCard(title = stringResource(R.string.data_import_title)) {
                Text(
                    text = stringResource(R.string.data_import_description),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.data_import_format_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SecondaryButton(
                    text = stringResource(R.string.data_import_action),
                    onClick = onPickImport,
                    enabled = !state.isBusy,
                    icon = Icons.Outlined.UploadFile,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("data_import"),
                )
            }
            SectionCard(title = stringResource(R.string.data_export_title)) {
                Text(
                    text = stringResource(R.string.data_export_description),
                    style = MaterialTheme.typography.bodyMedium,
                )
                SecondaryButton(
                    text = stringResource(R.string.data_export_csv),
                    onClick = { onExport(ExportKind.CSV) },
                    enabled = !state.isBusy,
                    icon = Icons.Outlined.Download,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("data_export_csv"),
                )
                SecondaryButton(
                    text = stringResource(R.string.data_export_json),
                    onClick = { onExport(ExportKind.JSON) },
                    enabled = !state.isBusy,
                    icon = Icons.Outlined.Download,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("data_export_json"),
                )
                SecondaryButton(
                    text = stringResource(R.string.data_export_backup),
                    onClick = { onExport(ExportKind.BACKUP) },
                    enabled = !state.isBusy,
                    icon = Icons.Outlined.Download,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("data_export_backup"),
                )
                Text(
                    text = stringResource(R.string.data_export_backup_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SectionCard(title = stringResource(R.string.data_restore_title)) {
                Text(
                    text = stringResource(R.string.data_restore_description),
                    style = MaterialTheme.typography.bodyMedium,
                )
                SecondaryButton(
                    text = stringResource(R.string.data_restore_action),
                    onClick = onPickRestore,
                    enabled = !state.isBusy,
                    icon = Icons.Outlined.Restore,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("data_restore"),
                )
            }
        }
    }
}

@Composable
private fun StatsCard(stats: DataStats) {
    SectionCard(title = stringResource(R.string.data_stats_title)) {
        StatLine(R.string.data_stat_words, stats.words)
        StatLine(R.string.data_stat_examples, stats.examples)
        StatLine(R.string.data_stat_tracked, stats.trackedWords)
        StatLine(R.string.data_stat_history, stats.reviewLogEntries)
        StatLine(R.string.data_stat_days, stats.activeDays)
    }
}

@Composable
private fun ImportPreviewContent(
    prompt: ImportPrompt,
    isBusy: Boolean,
    onEvent: (DataManagementEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val preview = prompt.preview
    val duplicates = preview.candidates.filter { it.isDuplicate }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.screenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = Dimens.maxContentWidth)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceLg),
        ) {
            prompt.fileName?.let { FileName(it) }
            if (isBusy) BusyIndicator()
            SectionCard(title = stringResource(R.string.data_preview_title)) {
                StatLine(R.string.data_preview_entries, preview.entryCount)
                StatLine(R.string.data_preview_new, preview.newCount)
                StatLine(R.string.data_preview_duplicates, preview.duplicateCount)
                if (preview.issueCount > 0) StatLine(R.string.data_preview_issues, preview.issueCount)
                if (preview.truncated) {
                    Text(
                        text = stringResource(R.string.data_preview_truncated),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            if (!preview.hasContent) {
                SectionCard(title = stringResource(R.string.data_preview_empty_title)) {
                    Text(
                        text = stringResource(R.string.data_preview_empty_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                SectionCard(title = stringResource(R.string.data_preview_list_title)) {
                    EntryList(entries = preview.candidates.map { it.entry }, total = preview.entryCount)
                }
                if (duplicates.isNotEmpty()) {
                    SectionCard(title = stringResource(R.string.data_preview_duplicates_title)) {
                        DuplicateList(duplicates)
                    }
                    SectionCard(title = stringResource(R.string.data_duplicates_title)) {
                        Text(
                            text = stringResource(R.string.data_duplicates_message),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OptionList(
                            options = DUPLICATE_POLICIES,
                            selected = prompt.policy,
                            title = { stringResource(it.titleRes) },
                            hint = { stringResource(it.hintRes) },
                            onSelect = { onEvent(DataManagementEvent.ImportPolicyChanged(it)) },
                        )
                    }
                }
                if (preview.issues.isNotEmpty()) {
                    SectionCard(title = stringResource(R.string.data_preview_issues_title)) {
                        IssueList(preview.issues)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMd)) {
                SecondaryButton(
                    text = stringResource(R.string.data_cancel),
                    onClick = { onEvent(DataManagementEvent.DismissImport) },
                    enabled = !isBusy,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("data_import_cancel"),
                )
                PrimaryButton(
                    text = stringResource(R.string.data_import_confirm),
                    onClick = { onEvent(DataManagementEvent.ConfirmImport) },
                    enabled = preview.hasContent && !isBusy,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("data_import_confirm"),
                )
            }
        }
    }
}

@Composable
private fun RestoreConfirmContent(
    prompt: RestorePrompt,
    isBusy: Boolean,
    onEvent: (DataManagementEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val preview = prompt.preview
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.screenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = Dimens.maxContentWidth)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceLg),
        ) {
            prompt.fileName?.let { FileName(it) }
            if (isBusy) BusyIndicator()
            SectionCard(title = stringResource(R.string.data_restore_backup_title)) {
                if (preview.createdAt > 0) {
                    Text(
                        text = stringResource(R.string.data_restore_from, formatDate(preview.createdAt)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (preview.appVersion.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.data_restore_backup_version, preview.appVersion),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatLine(R.string.data_restore_backup_words, preview.words)
                StatLine(R.string.data_restore_backup_examples, preview.examples)
                StatLine(R.string.data_restore_backup_progress, preview.progress)
                StatLine(R.string.data_restore_backup_history, preview.historyEntries)
            }
            SectionCard(title = stringResource(R.string.data_restore_strategy_title)) {
                OptionList(
                    options = RESTORE_STRATEGIES,
                    selected = prompt.strategy,
                    title = { stringResource(it.titleRes) },
                    hint = { stringResource(it.hintRes) },
                    onSelect = { onEvent(DataManagementEvent.RestoreStrategyChanged(it)) },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMd)) {
                SecondaryButton(
                    text = stringResource(R.string.data_cancel),
                    onClick = { onEvent(DataManagementEvent.DismissRestore) },
                    enabled = !isBusy,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("data_restore_cancel"),
                )
                PrimaryButton(
                    text = stringResource(R.string.data_restore_confirm),
                    onClick = { onEvent(DataManagementEvent.ConfirmRestore) },
                    enabled = !isBusy,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("data_restore_confirm"),
                )
            }
        }
    }
    if (prompt.confirmReplace) {
        AlertDialog(
            onDismissRequest = { onEvent(DataManagementEvent.DismissReplaceWarning) },
            title = { Text(stringResource(R.string.data_restore_confirm_title)) },
            text = { Text(stringResource(R.string.data_restore_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = { onEvent(DataManagementEvent.ConfirmReplaceRestore) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("data_restore_replace_confirm"),
                ) { Text(stringResource(R.string.data_restore_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { onEvent(DataManagementEvent.DismissReplaceWarning) }) {
                    Text(stringResource(R.string.data_cancel))
                }
            },
        )
    }
}

@Composable
private fun OutcomeBanner(outcome: DataOutcome, onDismiss: () -> Unit) {
    val isFailure = outcome is DataOutcome.Failed || outcome is DataOutcome.BackupRejected
    val scheme = MaterialTheme.colorScheme
    val container = if (isFailure) scheme.errorContainer else scheme.primaryContainer
    val content = if (isFailure) scheme.onErrorContainer else scheme.onPrimaryContainer
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag("data_outcome"),
        shape = MaterialTheme.shapes.large,
        color = container,
    ) {
        Column(
            modifier = Modifier.padding(Dimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs),
        ) {
            Text(text = outcomeText(outcome), style = MaterialTheme.typography.bodyMedium, color = content)
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) }
        }
    }
}

@Composable
private fun outcomeText(outcome: DataOutcome): String = when (outcome) {
    is DataOutcome.Imported -> stringResource(
        R.string.data_import_summary,
        outcome.summary.added,
        outcome.summary.replaced,
        outcome.summary.skipped,
    )
    is DataOutcome.Exported -> stringResource(
        R.string.data_export_done_kind,
        stringResource(outcome.kind.titleRes),
    )
    is DataOutcome.Restored -> stringResource(
        R.string.data_restore_summary,
        outcome.summary.wordsAdded,
        outcome.summary.wordsUpdated,
        outcome.summary.progressRestored,
        outcome.summary.historyRestored,
    )
    is DataOutcome.Failed -> stringResource(outcome.error.messageRes)
    is DataOutcome.BackupRejected -> stringResource(outcome.issue.messageRes)
}

@Composable
private fun StatLine(@StringRes label: Int, value: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = stringResource(label), style = MaterialTheme.typography.bodyMedium)
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun FileName(name: String) {
    Text(
        text = name,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun BusyIndicator() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(
            text = stringResource(R.string.data_working),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Dimens.spaceXs),
        )
    }
}

/** Shows at most [PREVIEW_LIST_LIMIT] rows; a 2000 entry file must not be laid out on screen. */
@Composable
private fun EntryList(entries: List<VocabularyEntry>, total: Int) {
    val shown = entries.take(PREVIEW_LIST_LIMIT)
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
        shown.forEach { entry ->
            Column {
                Text(
                    text = entry.text,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = entry.translation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        MoreLine(shown = shown.size, total = total)
    }
}

@Composable
private fun DuplicateList(candidates: List<ImportCandidate>) {
    val shown = candidates.take(PREVIEW_LIST_LIMIT)
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
        shown.forEach { candidate ->
            Column {
                Text(
                    text = candidate.entry.text,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                candidate.duplicate.messageRes?.let { reason ->
                    Text(
                        text = stringResource(reason),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        MoreLine(shown = shown.size, total = candidates.size)
    }
}

@Composable
private fun IssueList(issues: List<ImportIssue>) {
    val shown = issues.take(PREVIEW_LIST_LIMIT)
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs)) {
        shown.forEach { issue ->
            Text(
                text = if (issue.row > 0) {
                    stringResource(R.string.data_issue_line, issue.row, stringResource(issue.reason.messageRes))
                } else {
                    stringResource(issue.reason.messageRes)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        MoreLine(shown = shown.size, total = issues.size)
    }
}

@Composable
private fun MoreLine(shown: Int, total: Int) {
    if (total <= shown) return
    Text(
        text = stringResource(R.string.data_preview_more, total - shown),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Radio group used for both the duplicate policy and the restore strategy. */
@Composable
private fun <T> OptionList(
    options: List<T>,
    selected: T,
    title: @Composable (T) -> String,
    hint: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(Modifier.selectableGroup()) {
        options.forEach { option ->
            val isSelected = option == selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.minTouchTarget)
                    .selectable(
                        selected = isSelected,
                        role = Role.RadioButton,
                        onClick = { onSelect(option) },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = isSelected, onClick = null)
                Column(Modifier.padding(start = Dimens.spaceMd)) {
                    Text(text = title(option), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = hint(option),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@get:StringRes
private val DuplicatePolicy.titleRes: Int
    get() = when (this) {
        DuplicatePolicy.SKIP -> R.string.data_policy_skip
        DuplicatePolicy.REPLACE -> R.string.data_policy_replace
        DuplicatePolicy.KEEP_BOTH -> R.string.data_policy_keep_both
    }

@get:StringRes
private val DuplicatePolicy.hintRes: Int
    get() = when (this) {
        DuplicatePolicy.SKIP -> R.string.data_policy_skip_hint
        DuplicatePolicy.REPLACE -> R.string.data_policy_replace_hint
        DuplicatePolicy.KEEP_BOTH -> R.string.data_policy_keep_both_hint
    }

@get:StringRes
private val RestoreStrategy.titleRes: Int
    get() = when (this) {
        RestoreStrategy.MERGE -> R.string.data_restore_merge
        RestoreStrategy.REPLACE -> R.string.data_restore_replace
    }

@get:StringRes
private val RestoreStrategy.hintRes: Int
    get() = when (this) {
        RestoreStrategy.MERGE -> R.string.data_restore_merge_hint
        RestoreStrategy.REPLACE -> R.string.data_restore_replace_hint
    }

@get:StringRes
private val ExportKind.titleRes: Int
    get() = when (this) {
        ExportKind.CSV -> R.string.data_export_csv
        ExportKind.JSON -> R.string.data_export_json
        ExportKind.BACKUP -> R.string.data_export_backup
    }

@Composable
private fun formatDate(epochMillis: Long): String =
    DateFormat.getMediumDateFormat(LocalContext.current).format(Date(epochMillis))

/** Skip first: the least surprising choice when a file overlaps with what is already saved. */
private val DUPLICATE_POLICIES = listOf(DuplicatePolicy.SKIP, DuplicatePolicy.REPLACE, DuplicatePolicy.KEEP_BOTH)

/** Merge first: it is the only strategy that cannot lose anything. */
private val RESTORE_STRATEGIES = listOf(RestoreStrategy.MERGE, RestoreStrategy.REPLACE)

private const val PREVIEW_LIST_LIMIT = 20
private const val CSV_MIME = "text/csv"
private const val JSON_MIME = "application/json"
private const val SUGGESTED_CSV_NAME = "rewordly-vocabulary.csv"
private const val SUGGESTED_JSON_NAME = "rewordly-vocabulary.json"
private const val SUGGESTED_BACKUP_NAME = "rewordly-backup.json"

/** File names are not localized, and the extension is only a hint: the content decides. */
private val READABLE_TYPES = arrayOf("*/*")

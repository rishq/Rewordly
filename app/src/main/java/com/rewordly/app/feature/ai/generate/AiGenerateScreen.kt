package com.rewordly.app.feature.ai.generate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rewordly.app.R
import com.rewordly.app.core.common.AppError
import com.rewordly.app.core.ui.components.PrimaryButton
import com.rewordly.app.core.ui.components.SecondaryButton
import com.rewordly.app.core.ui.components.SectionCard
import com.rewordly.app.core.ui.labelRes
import com.rewordly.app.core.ui.theme.Dimens
import com.rewordly.app.domain.model.Difficulty
import com.rewordly.app.domain.model.GenerationMode
import com.rewordly.app.domain.model.GenerationSettings
import com.rewordly.app.domain.model.TopicPreset
import com.rewordly.app.domain.usecase.GenerationInputRules
import com.rewordly.app.domain.usecase.InputError
import com.rewordly.app.feature.ai.UsageSummary
import com.rewordly.app.feature.ai.generationErrorMessage
import com.rewordly.app.feature.ai.titleRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiGenerateScreen(
    onBack: () -> Unit,
    onOpenPreview: (String) -> Unit,
    viewModel: AiGenerateViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // The finished generation opens its preview exactly once, then the form returns to idle.
    (state.status as? GenerationStatus.Done)?.let { done ->
        LaunchedEffect(done.historyId) {
            onOpenPreview(done.historyId)
            viewModel.onEvent(AiGenerateEvent.ResultConsumed)
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(state.mode.titleRes)) },
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
    ) { padding ->
        AiGenerateContent(state = state, onEvent = viewModel::onEvent, modifier = Modifier.padding(padding))
    }
}

/** Stateless generation form for all three modes. */
@Composable
fun AiGenerateContent(state: AiGenerateUiState, onEvent: (AiGenerateEvent) -> Unit, modifier: Modifier = Modifier) {
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
            when (state.mode) {
                GenerationMode.TOPIC -> TopicSection(state, onEvent)
                GenerationMode.TEXT -> TextSection(state, onEvent)
                GenerationMode.WORD -> WordSection(state, onEvent)
            }
            SettingsSection(state, onEvent)
            StatusSection(state, onEvent)
            if (!state.isOnline) OfflineNotice(onRetry = { onEvent(AiGenerateEvent.Submit) })
            PrimaryButton(
                text = stringResource(
                    when (state.mode) {
                        GenerationMode.TOPIC -> R.string.ai_generate
                        GenerationMode.TEXT -> R.string.ai_extract
                        GenerationMode.WORD -> R.string.ai_explore
                    },
                ),
                onClick = { onEvent(AiGenerateEvent.Submit) },
                enabled = !state.isLoading && state.isOnline,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_generate"),
            )
            UsageSummary(state.usage)
        }
    }
    if (state.showPrivacyDialog) PrivacyDialog(onEvent)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TopicSection(state: AiGenerateUiState, onEvent: (AiGenerateEvent) -> Unit) {
    SectionCard(title = stringResource(R.string.ai_topic_section)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
        ) {
            TopicPreset.entries.forEach { preset ->
                FilterChip(
                    selected = state.preset == preset,
                    onClick = { onEvent(AiGenerateEvent.SelectPreset(preset)) },
                    label = { Text(stringResource(preset.labelRes)) },
                    modifier = Modifier.testTag("ai_preset_${preset.name}"),
                )
            }
            FilterChip(
                selected = state.preset == null,
                onClick = { onEvent(AiGenerateEvent.SelectPreset(null)) },
                label = { Text(stringResource(R.string.ai_topic_custom)) },
                modifier = Modifier.testTag("ai_preset_CUSTOM"),
            )
        }
        if (state.preset == null) {
            OutlinedTextField(
                value = state.customTopic,
                onValueChange = { onEvent(AiGenerateEvent.CustomTopicChanged(it)) },
                label = { Text(stringResource(R.string.ai_topic_custom_label)) },
                placeholder = { Text(stringResource(R.string.ai_topic_custom_hint)) },
                singleLine = true,
                isError = state.inputError != null,
                supportingText = state.inputError?.let { { Text(stringResource(R.string.ai_topic_invalid)) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_topic_input"),
            )
        } else if (state.inputError != null) {
            Text(
                text = stringResource(R.string.ai_topic_invalid),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun TextSection(state: AiGenerateUiState, onEvent: (AiGenerateEvent) -> Unit) {
    SectionCard(title = stringResource(R.string.ai_text_label)) {
        OutlinedTextField(
            value = state.text,
            onValueChange = { onEvent(AiGenerateEvent.TextChanged(it)) },
            placeholder = { Text(stringResource(R.string.ai_text_hint)) },
            minLines = TEXT_MIN_LINES,
            maxLines = TEXT_MAX_LINES,
            isError = state.inputError != null,
            supportingText = {
                Text(
                    when (state.inputError) {
                        InputError.TOO_SHORT -> stringResource(
                            R.string.ai_text_too_short,
                            GenerationInputRules.MIN_TEXT,
                        )
                        InputError.TOO_LONG -> stringResource(R.string.ai_text_too_long, GenerationInputRules.MAX_TEXT)
                        InputError.EMPTY, InputError.INVALID ->
                            stringResource(R.string.ai_text_too_short, GenerationInputRules.MIN_TEXT)
                        null -> stringResource(
                            R.string.ai_text_counter,
                            state.text.length,
                            GenerationInputRules.MAX_TEXT,
                        )
                    },
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ai_text_input"),
        )
        Text(
            text = stringResource(R.string.ai_privacy_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("ai_privacy_notice"),
        )
    }
}

@Composable
private fun WordSection(state: AiGenerateUiState, onEvent: (AiGenerateEvent) -> Unit) {
    SectionCard(title = stringResource(R.string.ai_word_label)) {
        OutlinedTextField(
            value = state.word,
            onValueChange = { onEvent(AiGenerateEvent.WordChanged(it)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
            isError = state.inputError != null,
            supportingText = state.inputError?.let { { Text(stringResource(R.string.ai_word_invalid)) } },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ai_word_input"),
        )
        state.suggestion?.let { suggestion ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = stringResource(R.string.ai_suggestion, suggestion),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
                TextButton(
                    onClick = { onEvent(AiGenerateEvent.ApplySuggestion) },
                    modifier = Modifier.testTag("ai_apply_suggestion"),
                ) { Text(stringResource(R.string.ai_suggestion_apply)) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsSection(state: AiGenerateUiState, onEvent: (AiGenerateEvent) -> Unit) {
    val settings = state.settings
    SectionCard(title = stringResource(R.string.ai_settings_section)) {
        Text(text = stringResource(R.string.ai_level), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
            Difficulty.entries.forEach { level ->
                FilterChip(
                    selected = settings.level == level,
                    onClick = { onEvent(AiGenerateEvent.SettingsChanged(settings.copy(level = level))) },
                    label = { Text(level.name) },
                    modifier = Modifier.testTag("ai_level_${level.name}"),
                )
            }
        }
        if (state.mode != GenerationMode.WORD) {
            Text(text = stringResource(R.string.ai_word_count), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
                GenerationSettings.WORD_COUNT_OPTIONS.forEach { count ->
                    FilterChip(
                        selected = settings.wordCount == count,
                        onClick = { onEvent(AiGenerateEvent.SettingsChanged(settings.copy(wordCount = count))) },
                        label = { Text(count.toString()) },
                        modifier = Modifier.testTag("ai_count_$count"),
                    )
                }
            }
        }
        OptionSwitch(R.string.ai_include_examples, settings.includeExamples, "ai_opt_examples") {
            onEvent(AiGenerateEvent.SettingsChanged(settings.copy(includeExamples = it)))
        }
        OptionSwitch(R.string.ai_include_synonyms, settings.includeSynonyms, "ai_opt_synonyms") {
            onEvent(AiGenerateEvent.SettingsChanged(settings.copy(includeSynonyms = it)))
        }
        OptionSwitch(R.string.ai_include_pronunciation, settings.includePronunciation, "ai_opt_pronunciation") {
            onEvent(AiGenerateEvent.SettingsChanged(settings.copy(includePronunciation = it)))
        }
    }
}

@Composable
private fun OptionSwitch(label: Int, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.minTouchTarget)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = stringResource(label), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun StatusSection(state: AiGenerateUiState, onEvent: (AiGenerateEvent) -> Unit) {
    when (val status = state.status) {
        GenerationStatus.Loading -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
        ) {
            // Indeterminate: the backend reports no progress, so no percentage is ever shown.
            CircularProgressIndicator(modifier = Modifier.size(Dimens.iconMd), strokeWidth = LOADING_STROKE)
            Text(
                text = stringResource(R.string.ai_loading),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            SecondaryButton(
                text = stringResource(R.string.ai_cancel),
                onClick = { onEvent(AiGenerateEvent.Cancel) },
                modifier = Modifier.testTag("ai_cancel"),
            )
        }
        GenerationStatus.Empty -> StatusMessage(stringResource(R.string.ai_empty_response), "ai_status_empty")
        // Being offline has its own notice above the button, so the same text is not repeated here.
        is GenerationStatus.Failed ->
            if (status.error != AppError.Offline) StatusMessage(generationErrorMessage(status.error), "ai_status_error")
        GenerationStatus.Idle, is GenerationStatus.Done -> Unit
    }
}

/**
 * Shown only on the screens that actually need the backend. Generation is blocked, the input is kept,
 * and the retry simply re-submits once a connection is available again.
 */
@Composable
private fun OfflineNotice(onRetry: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag("ai_offline"),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(Dimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
        ) {
            Text(text = stringResource(R.string.ai_offline_title), style = MaterialTheme.typography.titleSmall)
            Text(
                text = stringResource(R.string.ai_offline_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SecondaryButton(
                text = stringResource(R.string.ai_offline_retry),
                onClick = onRetry,
                modifier = Modifier.testTag("ai_offline_retry"),
            )
        }
    }
}

@Composable
private fun StatusMessage(text: String, tag: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag(tag),
    )
}

@Composable
private fun PrivacyDialog(onEvent: (AiGenerateEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(AiGenerateEvent.DismissPrivacy) },
        title = { Text(stringResource(R.string.ai_privacy_title)) },
        text = { Text(stringResource(R.string.ai_privacy_notice)) },
        confirmButton = {
            TextButton(
                onClick = { onEvent(AiGenerateEvent.ConfirmPrivacy) },
                modifier = Modifier.testTag("ai_privacy_confirm"),
            ) { Text(stringResource(R.string.ai_privacy_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(AiGenerateEvent.DismissPrivacy) }) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

private const val TEXT_MIN_LINES = 6
private const val TEXT_MAX_LINES = 12
private val LOADING_STROKE = 2.dp

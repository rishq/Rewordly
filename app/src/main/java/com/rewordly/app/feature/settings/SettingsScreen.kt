package com.rewordly.app.feature.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rewordly.app.BuildConfig
import com.rewordly.app.R
import com.rewordly.app.core.notifications.ReminderNotifier
import com.rewordly.app.core.ui.components.LoadingState
import com.rewordly.app.core.ui.labelRes
import com.rewordly.app.core.ui.theme.Dimens
import com.rewordly.app.domain.model.AiProvider
import com.rewordly.app.domain.model.AiSettings
import com.rewordly.app.domain.model.Difficulty
import com.rewordly.app.domain.model.InterfaceLanguage
import com.rewordly.app.domain.model.LearningGoal
import com.rewordly.app.domain.model.LearningLanguage
import com.rewordly.app.domain.model.LearningProfile
import com.rewordly.app.domain.model.ThemeMode
import com.rewordly.app.domain.model.TopicPreset
import com.rewordly.app.domain.model.UserSettings
import java.util.Calendar

private enum class SettingsDialog {
    INTERFACE_LANGUAGE,
    LEARNING_LANGUAGE,
    THEME,
    DAILY_GOAL,
    REMINDER_TIME,
    NOTIFICATIONS_DENIED,
    ABOUT,
    PRIVACY,
    LEVEL,
    GOAL,
    INTERESTS,
    SESSION_LENGTH,
    AI_PROVIDER,
    AI_MODEL,
    AI_API_KEY,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenPlacement: () -> Unit,
    onOpenDataManagement: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
        when (val state = uiState) {
            SettingsUiState.Loading -> LoadingState(Modifier.padding(padding))
            is SettingsUiState.Content -> SettingsContent(
                settings = state.settings,
                ai = state.ai,
                onEvent = viewModel::onEvent,
                onOpenPlacement = onOpenPlacement,
                onOpenDataManagement = onOpenDataManagement,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun SettingsContent(
    settings: UserSettings,
    ai: AiSettings,
    onEvent: (SettingsUiEvent) -> Unit,
    onOpenPlacement: () -> Unit,
    onOpenDataManagement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dialog by rememberSaveable { mutableStateOf<SettingsDialog?>(null) }
    val context = LocalContext.current
    var canNotify by remember { mutableStateOf(ReminderNotifier.canNotify(context)) }
    // The user can change the permission in system settings, so re-check whenever the screen is shown again.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { canNotify = ReminderNotifier.canNotify(context) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        canNotify = granted
        if (granted) onEvent(SettingsUiEvent.SetNotifications(true)) else dialog = SettingsDialog.NOTIFICATIONS_DENIED
    }
    val onToggleReminders = { enable: Boolean ->
        when {
            !enable -> onEvent(SettingsUiEvent.SetNotifications(false))
            canNotify -> onEvent(SettingsUiEvent.SetNotifications(true))
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            else -> dialog = SettingsDialog.NOTIFICATIONS_DENIED
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(modifier = Modifier.widthIn(max = Dimens.maxContentWidth).fillMaxWidth()) {
            GroupHeader(R.string.settings_group_language)
            SettingsRow(
                icon = Icons.Outlined.Language,
                title = stringResource(R.string.settings_interface_language),
                value = stringResource(settings.interfaceLanguage.labelRes),
                onClick = { dialog = SettingsDialog.INTERFACE_LANGUAGE },
            )
            SettingsRow(
                icon = Icons.Outlined.School,
                title = stringResource(R.string.settings_learning_language),
                value = stringResource(settings.learningLanguage.labelRes),
                onClick = { dialog = SettingsDialog.LEARNING_LANGUAGE },
            )
            HorizontalDivider(Modifier.padding(vertical = Dimens.spaceSm))

            GroupHeader(R.string.settings_group_learning)
            SettingsRow(
                icon = Icons.Outlined.Flag,
                title = stringResource(R.string.settings_daily_goal),
                value = pluralStringResource(R.plurals.words_count, settings.dailyGoal, settings.dailyGoal),
                onClick = { dialog = SettingsDialog.DAILY_GOAL },
            )
            NotificationsRow(
                enabled = settings.notificationsEnabled,
                blocked = settings.notificationsEnabled && !canNotify,
                onToggle = onToggleReminders,
            )
            if (settings.notificationsEnabled) {
                SettingsRow(
                    icon = Icons.Outlined.Schedule,
                    title = stringResource(R.string.settings_reminder_time),
                    value = formatTime(context, settings.reminderHour, settings.reminderMinute),
                    onClick = { dialog = SettingsDialog.REMINDER_TIME },
                )
            }
            HorizontalDivider(Modifier.padding(vertical = Dimens.spaceSm))

            GroupHeader(R.string.settings_group_profile)
            SettingsRow(
                icon = Icons.Outlined.School,
                title = stringResource(R.string.settings_level),
                value = settings.level?.let { stringResource(it.labelRes) }
                    ?: stringResource(R.string.settings_level_none),
                onClick = { dialog = SettingsDialog.LEVEL },
            )
            SettingsRow(
                icon = Icons.Outlined.Flag,
                title = stringResource(R.string.settings_goal),
                value = stringResource(settings.learningGoal.labelRes),
                onClick = { dialog = SettingsDialog.GOAL },
            )
            SettingsRow(
                icon = Icons.Outlined.Bookmark,
                title = stringResource(R.string.settings_interests),
                value = interestsLabel(settings.interests),
                onClick = { dialog = SettingsDialog.INTERESTS },
            )
            SettingsRow(
                icon = Icons.AutoMirrored.Outlined.MenuBook,
                title = stringResource(R.string.settings_session_length),
                value = pluralStringResource(R.plurals.words_count, settings.sessionLength, settings.sessionLength),
                onClick = { dialog = SettingsDialog.SESSION_LENGTH },
            )
            SettingsRow(
                icon = Icons.Outlined.CheckCircle,
                title = stringResource(R.string.settings_placement),
                value = stringResource(R.string.settings_placement_hint),
                onClick = onOpenPlacement,
            )
            HorizontalDivider(Modifier.padding(vertical = Dimens.spaceSm))

            GroupHeader(R.string.settings_group_ai)
            SettingsRow(
                icon = Icons.Outlined.AutoAwesome,
                title = stringResource(R.string.settings_ai_provider),
                value = ai.provider?.displayName ?: stringResource(R.string.settings_ai_provider_none),
                onClick = { dialog = SettingsDialog.AI_PROVIDER },
            )
            // Model and key only make sense once a provider is chosen; the hint below covers the empty case.
            if (ai.provider != null) {
                SettingsRow(
                    icon = Icons.Outlined.Memory,
                    title = stringResource(R.string.settings_ai_model),
                    value = ai.effectiveModel,
                    onClick = { dialog = SettingsDialog.AI_MODEL },
                )
                SettingsRow(
                    icon = Icons.Outlined.Key,
                    title = stringResource(R.string.settings_ai_api_key),
                    value = if (ai.apiKey.isBlank()) {
                        stringResource(R.string.settings_ai_api_key_none)
                    } else {
                        maskApiKey(ai.apiKey)
                    },
                    onClick = { dialog = SettingsDialog.AI_API_KEY },
                )
            }
            AiStatusHint(ai)
            HorizontalDivider(Modifier.padding(vertical = Dimens.spaceSm))

            GroupHeader(R.string.settings_group_appearance)
            SettingsRow(
                icon = Icons.Outlined.DarkMode,
                title = stringResource(R.string.settings_theme),
                value = stringResource(settings.themeMode.labelRes),
                onClick = { dialog = SettingsDialog.THEME },
            )
            HorizontalDivider(Modifier.padding(vertical = Dimens.spaceSm))

            GroupHeader(R.string.settings_group_data)
            SettingsRow(
                icon = Icons.Outlined.Storage,
                title = stringResource(R.string.settings_data_management),
                value = stringResource(R.string.settings_data_management_hint),
                onClick = onOpenDataManagement,
            )
            HorizontalDivider(Modifier.padding(vertical = Dimens.spaceSm))

            GroupHeader(R.string.settings_group_info)
            SettingsRow(
                icon = Icons.Outlined.Info,
                title = stringResource(R.string.settings_about),
                value = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                onClick = { dialog = SettingsDialog.ABOUT },
            )
            SettingsRow(
                icon = Icons.Outlined.PrivacyTip,
                title = stringResource(R.string.settings_privacy),
                value = null,
                onClick = { dialog = SettingsDialog.PRIVACY },
            )
        }
    }

    val dismiss = { dialog = null }
    when (dialog) {
        SettingsDialog.INTERFACE_LANGUAGE -> ChoiceDialog(
            title = R.string.settings_interface_language,
            options = InterfaceLanguage.entries,
            selected = settings.interfaceLanguage,
            label = { stringResource(it.labelRes) },
            onSelect = { onEvent(SettingsUiEvent.SetInterfaceLanguage(it)) },
            onDismiss = dismiss,
        )
        SettingsDialog.LEARNING_LANGUAGE -> ChoiceDialog(
            title = R.string.settings_learning_language,
            options = LearningLanguage.entries,
            selected = settings.learningLanguage,
            label = { stringResource(it.labelRes) },
            onSelect = { onEvent(SettingsUiEvent.SetLearningLanguage(it)) },
            onDismiss = dismiss,
        )
        SettingsDialog.THEME -> ChoiceDialog(
            title = R.string.settings_theme,
            options = ThemeMode.entries,
            selected = settings.themeMode,
            label = { stringResource(it.labelRes) },
            onSelect = { onEvent(SettingsUiEvent.SetTheme(it)) },
            onDismiss = dismiss,
        )
        SettingsDialog.DAILY_GOAL -> ChoiceDialog(
            title = R.string.settings_daily_goal,
            options = UserSettings.DAILY_GOAL_OPTIONS,
            selected = settings.dailyGoal,
            label = { pluralStringResource(R.plurals.words_count, it, it) },
            onSelect = { onEvent(SettingsUiEvent.SetDailyGoal(it)) },
            onDismiss = dismiss,
        )
        SettingsDialog.REMINDER_TIME -> ReminderTimeDialog(
            hour = settings.reminderHour,
            minute = settings.reminderMinute,
            onConfirm = { hour, minute -> onEvent(SettingsUiEvent.SetReminderTime(hour, minute)) },
            onDismiss = dismiss,
        )
        SettingsDialog.NOTIFICATIONS_DENIED -> AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringResource(R.string.settings_notifications_denied_title)) },
            text = { Text(stringResource(R.string.settings_notifications_denied_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        dismiss()
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                        )
                    },
                ) { Text(stringResource(R.string.action_open_settings)) }
            },
            dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.action_cancel)) } },
        )
        SettingsDialog.LEVEL -> ChoiceDialog(
            title = R.string.settings_level,
            options = LEVEL_OPTIONS,
            selected = settings.level,
            label = { level ->
                level?.let { stringResource(it.labelRes) } ?: stringResource(R.string.settings_level_none)
            },
            onSelect = { onEvent(SettingsUiEvent.SetLevel(it)) },
            onDismiss = dismiss,
        )
        SettingsDialog.GOAL -> ChoiceDialog(
            title = R.string.settings_goal,
            options = LearningGoal.entries,
            selected = settings.learningGoal,
            label = { stringResource(it.labelRes) },
            onSelect = { onEvent(SettingsUiEvent.SetGoal(it)) },
            onDismiss = dismiss,
        )
        SettingsDialog.INTERESTS -> MultiChoiceDialog(
            title = R.string.settings_interests_title,
            options = TopicPreset.entries,
            selected = settings.interests,
            label = { stringResource(it.labelRes) },
            onToggle = { onEvent(SettingsUiEvent.SetInterests(it)) },
            onDismiss = dismiss,
        )
        SettingsDialog.SESSION_LENGTH -> ChoiceDialog(
            title = R.string.settings_session_length,
            options = LearningProfile.SESSION_LENGTH_OPTIONS,
            selected = settings.sessionLength,
            label = { pluralStringResource(R.plurals.words_count, it, it) },
            onSelect = { onEvent(SettingsUiEvent.SetSessionLength(it)) },
            onDismiss = dismiss,
        )
        SettingsDialog.ABOUT -> InfoDialog(R.string.settings_about, R.string.settings_about_text, dismiss)
        SettingsDialog.PRIVACY -> InfoDialog(R.string.settings_privacy, R.string.settings_privacy_text, dismiss)
        SettingsDialog.AI_PROVIDER -> ChoiceDialog(
            title = R.string.settings_ai_provider,
            options = AI_PROVIDER_OPTIONS,
            selected = ai.provider,
            label = { provider -> provider?.displayName ?: stringResource(R.string.settings_ai_provider_none) },
            onSelect = { onEvent(SettingsUiEvent.SetAiProvider(it)) },
            onDismiss = dismiss,
        )
        SettingsDialog.AI_MODEL -> ai.provider?.let { provider ->
            AiModelDialog(
                provider = provider,
                initial = ai.model,
                onSave = { onEvent(SettingsUiEvent.SetAiModel(it)) },
                onDismiss = dismiss,
            )
        }
        SettingsDialog.AI_API_KEY -> ai.provider?.let { provider ->
            AiApiKeyDialog(
                provider = provider,
                current = ai.apiKey,
                onConfirm = { onEvent(SettingsUiEvent.SetAiApiKey(it)) },
                onDismiss = dismiss,
            )
        }
        null -> Unit
    }
}

/**
 * "Not selected" stays a real option: with no provider the app falls back to its own backend, and the user must
 * be able to get back to that state after trying a key.
 */
private val AI_PROVIDER_OPTIONS: List<AiProvider?> = listOf<AiProvider?>(null) + AiProvider.entries

/** Shows the tail of the key only, which is enough to recognise it without putting the secret on screen. */
private fun maskApiKey(apiKey: String): String =
    if (apiKey.length <= MASK_VISIBLE_TAIL) MASK else MASK + apiKey.takeLast(MASK_VISIBLE_TAIL)

private const val MASK = "\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022"
private const val MASK_VISIBLE_TAIL = 4

/** States where generation will actually go, so the effect of the choice is never a guess. */
@Composable
private fun AiStatusHint(ai: AiSettings) {
    val provider = ai.provider
    val text = when {
        provider == null -> stringResource(R.string.settings_ai_hint_empty)
        ai.apiKey.isBlank() -> stringResource(R.string.settings_ai_hint_no_key, provider.displayName)
        else -> stringResource(R.string.settings_ai_hint_ready, provider.displayName)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Dimens.screenPadding, vertical = Dimens.spaceSm),
    )
}

/**
 * Model names are typed rather than picked from a fixed list, because every one of these vendors retires models
 * on a schedule and a hard-coded list would rot. The suggestions are a starting point, not a constraint.
 */
@Composable
private fun AiModelDialog(provider: AiProvider, initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_ai_model)) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    placeholder = { Text(provider.defaultModel) },
                    supportingText = {
                        Text(stringResource(R.string.settings_ai_model_default, provider.defaultModel))
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(Modifier.selectableGroup()) {
                    Text(
                        text = stringResource(R.string.settings_ai_model_suggestions),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Dimens.spaceMd),
                    )
                    provider.suggestedModels.forEach { model ->
                        val isSelected = model == text.trim()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = Dimens.minTouchTarget)
                                .selectable(
                                    selected = isSelected,
                                    role = Role.RadioButton,
                                    onClick = { text = model },
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = isSelected, onClick = null)
                            Text(
                                text = model,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = Dimens.spaceMd),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(text)
                    onDismiss()
                },
            ) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/**
 * The key is masked by default and can be revealed, which is the convention users expect. Nothing about it is
 * logged or sent anywhere except to the provider it belongs to.
 */
@Composable
private fun AiApiKeyDialog(
    provider: AiProvider,
    current: String,
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    // Deliberately `remember`, not `rememberSaveable`: this holds a plaintext secret, and a saveable String
    // is written into the saved-instance-state bundle, which outlives the process. Losing an unconfirmed
    // edit on rotation is the cheaper price.
    var text by remember { mutableStateOf(current) }
    var visible by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_ai_api_key)) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text(provider.displayName) },
                    visualTransformation = if (visible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(
                                imageVector = if (visible) {
                                    Icons.Outlined.VisibilityOff
                                } else {
                                    Icons.Outlined.Visibility
                                },
                                contentDescription = stringResource(
                                    if (visible) R.string.a11y_hide_key else R.string.a11y_show_key,
                                ),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.settings_ai_api_key_where, provider.displayName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Dimens.spaceSm),
                )
                TextButton(onClick = { uriHandler.openUri(provider.keyConsoleUrl) }) {
                    Text(stringResource(R.string.settings_ai_get_key))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(text)
                    onDismiss()
                },
            ) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = {
            Row {
                if (current.isNotBlank()) {
                    TextButton(
                        onClick = {
                            onConfirm(null)
                            onDismiss()
                        },
                    ) { Text(stringResource(R.string.settings_ai_api_key_clear)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

/** "Not set" stays a real option: the profile is optional and can always be cleared again. */
private val LEVEL_OPTIONS: List<Difficulty?> = listOf<Difficulty?>(null) + Difficulty.entries

/** Joins the chosen topic labels; falls back to an explicit "none" instead of an empty value. */
@Composable
private fun interestsLabel(interests: Set<TopicPreset>): String {
    val labels = TopicPreset.entries.filter { it in interests }.map { stringResource(it.labelRes) }
    return if (labels.isEmpty()) stringResource(R.string.settings_interests_none) else labels.joinToString(", ")
}

@Composable
private fun <T> MultiChoiceDialog(
    @StringRes title: Int,
    options: List<T>,
    selected: Set<T>,
    label: @Composable (T) -> String,
    onToggle: (Set<T>) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            Column {
                options.forEach { option ->
                    val isSelected = option in selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = Dimens.minTouchTarget)
                            .toggleable(
                                value = isSelected,
                                role = Role.Checkbox,
                                onValueChange = { checked ->
                                    onToggle(if (checked) selected + option else selected - option)
                                },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = isSelected, onCheckedChange = null)
                        Text(
                            text = label(option),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = Dimens.spaceMd),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) }
        },
    )
}

@Composable
private fun GroupHeader(@StringRes title: Int) {
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(horizontal = Dimens.screenPadding, vertical = Dimens.spaceSm)
            .semantics { heading() },
    )
}

@Composable
private fun SettingsRow(icon: ImageVector, title: String, value: String?, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = value?.let { { Text(it) } },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
    )
}

@Composable
private fun NotificationsRow(enabled: Boolean, blocked: Boolean, onToggle: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_notifications)) },
        supportingContent = {
            Text(
                stringResource(
                    if (blocked) R.string.settings_notifications_blocked else R.string.settings_notifications_hint,
                ),
            )
        },
        leadingContent = { Icon(Icons.Outlined.Notifications, contentDescription = null) },
        trailingContent = { Switch(checked = enabled, onCheckedChange = null) },
        modifier = Modifier.toggleable(value = enabled, role = Role.Switch, onValueChange = onToggle),
    )
}

@Composable
private fun <T> ChoiceDialog(
    @StringRes title: Int,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
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
                                onClick = {
                                    onSelect(option)
                                    onDismiss()
                                },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = isSelected, onClick = null)
                        Text(
                            text = label(option),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = Dimens.spaceMd),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun InfoDialog(@StringRes title: Int, @StringRes text: Int, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(text)) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimeDialog(hour: Int, minute: Int, onConfirm: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(
        initialHour = hour,
        initialMinute = minute,
        is24Hour = DateFormat.is24HourFormat(LocalContext.current),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_reminder_time)) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(state.hour, state.minute)
                    onDismiss()
                },
            ) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private fun formatTime(context: Context, hour: Int, minute: Int): String = DateFormat.getTimeFormat(context).format(
    Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
    }.time,
)

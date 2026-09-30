package com.rewordly.app.feature.settings

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rewordly.app.BuildConfig
import com.rewordly.app.R
import com.rewordly.app.core.ui.components.LoadingState
import com.rewordly.app.core.ui.labelRes
import com.rewordly.app.core.ui.theme.Dimens
import com.rewordly.app.domain.model.InterfaceLanguage
import com.rewordly.app.domain.model.LearningLanguage
import com.rewordly.app.domain.model.ThemeMode
import com.rewordly.app.domain.model.UserSettings

private enum class SettingsDialog { INTERFACE_LANGUAGE, LEARNING_LANGUAGE, THEME, DAILY_GOAL, ABOUT, PRIVACY }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
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
                onEvent = viewModel::onEvent,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun SettingsContent(
    settings: UserSettings,
    onEvent: (SettingsUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dialog by rememberSaveable { mutableStateOf<SettingsDialog?>(null) }

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
                onToggle = { onEvent(SettingsUiEvent.SetNotifications(it)) },
            )
            HorizontalDivider(Modifier.padding(vertical = Dimens.spaceSm))

            GroupHeader(R.string.settings_group_appearance)
            SettingsRow(
                icon = Icons.Outlined.DarkMode,
                title = stringResource(R.string.settings_theme),
                value = stringResource(settings.themeMode.labelRes),
                onClick = { dialog = SettingsDialog.THEME },
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
        SettingsDialog.ABOUT -> InfoDialog(R.string.settings_about, R.string.settings_about_text, dismiss)
        SettingsDialog.PRIVACY -> InfoDialog(R.string.settings_privacy, R.string.settings_privacy_text, dismiss)
        null -> Unit
    }
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
private fun NotificationsRow(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_notifications)) },
        supportingContent = { Text(stringResource(R.string.settings_notifications_hint)) },
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

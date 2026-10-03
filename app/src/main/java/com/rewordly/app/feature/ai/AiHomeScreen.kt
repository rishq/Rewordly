package com.rewordly.app.feature.ai

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
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Topic
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rewordly.app.R
import com.rewordly.app.core.ui.components.SecondaryButton
import com.rewordly.app.core.ui.theme.Dimens
import com.rewordly.app.domain.model.AiSettings
import com.rewordly.app.domain.model.GenerationMode
import com.rewordly.app.domain.model.UsageInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiHomeScreen(
    onBack: () -> Unit,
    onOpenMode: (GenerationMode) -> Unit,
    onOpenHistory: () -> Unit,
    viewModel: AiHomeViewModel = hiltViewModel(),
) {
    val usage by viewModel.usage.collectAsStateWithLifecycle()
    val ai by viewModel.ai.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ai_title)) },
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
        AiHomeContent(
            usage = usage,
            ai = ai,
            onOpenMode = onOpenMode,
            onOpenHistory = onOpenHistory,
            modifier = Modifier.padding(padding),
        )
    }
}

/** Stateless body of the AI Vocabulary screen with its three entry points. */
@Composable
fun AiHomeContent(
    usage: UsageInfo?,
    ai: AiSettings,
    onOpenMode: (GenerationMode) -> Unit,
    onOpenHistory: () -> Unit,
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
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
        ) {
            Text(
                text = stringResource(R.string.ai_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ModeCard(
                icon = Icons.Outlined.Topic,
                title = stringResource(R.string.ai_action_topic),
                hint = stringResource(R.string.ai_action_topic_hint),
                tag = "ai_mode_topic",
                onClick = { onOpenMode(GenerationMode.TOPIC) },
            )
            ModeCard(
                icon = Icons.AutoMirrored.Outlined.Article,
                title = stringResource(R.string.ai_action_text),
                hint = stringResource(R.string.ai_action_text_hint),
                tag = "ai_mode_text",
                onClick = { onOpenMode(GenerationMode.TEXT) },
            )
            ModeCard(
                icon = Icons.Outlined.Search,
                title = stringResource(R.string.ai_action_word),
                hint = stringResource(R.string.ai_action_word_hint),
                tag = "ai_mode_word",
                onClick = { onOpenMode(GenerationMode.WORD) },
            )
            SecondaryButton(
                text = stringResource(R.string.ai_action_history),
                icon = Icons.Outlined.History,
                onClick = onOpenHistory,
                modifier = Modifier.fillMaxWidth(),
            )
            UsageSummary(usage)
            val ownProvider = ai.provider?.takeIf { ai.isReady }
            Text(
                text = if (ownProvider == null) {
                    stringResource(R.string.ai_provider_notice)
                } else {
                    stringResource(R.string.ai_provider_notice_own, ownProvider.displayName)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ModeCard(icon: ImageVector, title: String, hint: String, tag: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.cardPadding)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceLg),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun AiEntryCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.cardPadding)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceLg),
        ) {
            Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs)) {
                Text(text = stringResource(R.string.ai_home_card), style = MaterialTheme.typography.titleMedium)
                Text(text = stringResource(R.string.ai_home_card_hint), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

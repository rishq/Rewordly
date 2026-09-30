package com.rewordly.app.feature.profile

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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rewordly.app.R
import com.rewordly.app.core.ui.components.ActivityChart
import com.rewordly.app.core.ui.components.LoadingState
import com.rewordly.app.core.ui.components.SectionCard
import com.rewordly.app.core.ui.components.StatTile
import com.rewordly.app.core.ui.theme.Dimens
import com.rewordly.app.domain.model.ProgressOverview

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onOpenSettings: () -> Unit, viewModel: ProfileViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_title)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                },
            )
        },
    ) { padding ->
        when (val state = uiState) {
            ProfileUiState.Loading -> LoadingState(Modifier.padding(padding))
            is ProfileUiState.Content -> ProfileContent(state.overview, Modifier.padding(padding))
        }
    }
}

@Composable
private fun ProfileContent(overview: ProgressOverview, modifier: Modifier = Modifier) {
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
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMd)) {
                StatTile(
                    icon = Icons.Outlined.CheckCircle,
                    value = overview.wordsLearned.toString(),
                    label = stringResource(R.string.profile_words_learned),
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    icon = Icons.Outlined.Replay,
                    value = overview.wordsReviewed.toString(),
                    label = stringResource(R.string.profile_words_reviewed),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMd)) {
                StatTile(
                    icon = Icons.Outlined.LocalFireDepartment,
                    value = pluralStringResource(R.plurals.days_count, overview.currentStreak, overview.currentStreak),
                    label = stringResource(R.string.profile_current_streak),
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    icon = Icons.Outlined.EmojiEvents,
                    value = pluralStringResource(R.plurals.days_count, overview.longestStreak, overview.longestStreak),
                    label = stringResource(R.string.profile_longest_streak),
                    modifier = Modifier.weight(1f),
                )
            }
            StatTile(
                icon = Icons.Outlined.Flag,
                value = pluralStringResource(R.plurals.words_count, overview.today.goal, overview.today.goal),
                label = stringResource(R.string.profile_daily_goal),
                modifier = Modifier.fillMaxWidth(),
            )
            SectionCard(
                title = stringResource(R.string.profile_activity),
                modifier = Modifier.padding(top = Dimens.spaceSm),
            ) {
                ActivityChart(days = overview.activity)
            }
        }
    }
}

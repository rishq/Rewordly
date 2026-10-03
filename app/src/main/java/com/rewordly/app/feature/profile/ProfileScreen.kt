package com.rewordly.app.feature.profile

import androidx.annotation.StringRes
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
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rewordly.app.R
import com.rewordly.app.core.ui.components.ActivityChart
import com.rewordly.app.core.ui.components.ErrorState
import com.rewordly.app.core.ui.components.LoadingState
import com.rewordly.app.core.ui.components.RecommendedWordItem
import com.rewordly.app.core.ui.components.SecondaryButton
import com.rewordly.app.core.ui.components.SectionCard
import com.rewordly.app.core.ui.components.StatTile
import com.rewordly.app.core.ui.messageRes
import com.rewordly.app.core.ui.theme.Dimens
import com.rewordly.app.domain.model.LearningInsight
import com.rewordly.app.domain.model.ProgressOverview
import com.rewordly.app.domain.model.ReviewStatistics
import com.rewordly.app.domain.model.weakReason
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenWord: (String) -> Unit,
    viewModel: ProgressViewModel = hiltViewModel(),
) {
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
            ProgressUiState.Loading -> LoadingState(Modifier.padding(padding))
            is ProgressUiState.Error -> ErrorState(
                message = stringResource(state.error.messageRes),
                onRetry = viewModel::retry,
                modifier = Modifier.padding(padding),
            )
            is ProgressUiState.Content -> ProfileContent(
                overview = state.overview,
                statistics = state.statistics,
                insight = state.insight,
                onOpenHistory = onOpenHistory,
                onOpenWord = onOpenWord,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun ProfileContent(
    overview: ProgressOverview,
    statistics: ReviewStatistics,
    insight: LearningInsight,
    onOpenHistory: () -> Unit,
    onOpenWord: (String) -> Unit,
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
                    value = pluralStringResource(
                        R.plurals.days_count,
                        overview.streak.current,
                        overview.streak.current,
                    ),
                    label = stringResource(R.string.profile_current_streak),
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    icon = Icons.Outlined.EmojiEvents,
                    value = pluralStringResource(
                        R.plurals.days_count,
                        overview.streak.longest,
                        overview.streak.longest,
                    ),
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
            InsightsCard(insight = insight, onOpenWord = onOpenWord)
            ReviewStatsCard(statistics)
            SecondaryButton(
                text = stringResource(R.string.action_history),
                icon = Icons.Outlined.History,
                onClick = onOpenHistory,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Words introduced, mastered and still in review are shown as three separate numbers, so "learned"
 * can never be confused with "opened once".
 */
@Composable
private fun InsightsCard(insight: LearningInsight, onOpenWord: (String) -> Unit, modifier: Modifier = Modifier) {
    val none = stringResource(R.string.stats_none)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Dimens.spaceMd)) {
        SectionCard(title = stringResource(R.string.insights_title)) {
            if (!insight.hasHistory) {
                Text(
                    text = stringResource(R.string.insights_empty_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                StatRow(R.string.insights_introduced, insight.wordsIntroduced.toString())
                StatRow(R.string.insights_mastered, insight.wordsMastered.toString())
                StatRow(R.string.insights_in_review, insight.wordsInReview.toString())
                StatRow(
                    R.string.insights_accuracy,
                    insight.recentAccuracy?.let {
                        stringResource(
                            R.string.insights_accuracy_value,
                            (it * 100).roundToInt(),
                            insight.reviewsConsidered,
                        )
                    } ?: none,
                )
                Text(
                    text = stringResource(R.string.insights_explanation),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (insight.hasWeakWords) {
            SectionCard(title = stringResource(R.string.insights_weak_title)) {
                insight.weakWords.forEach { item ->
                    RecommendedWordItem(
                        word = item.word.text,
                        translation = item.word.translation.text,
                        difficulty = item.word.difficulty,
                        reason = item.progress.weakReason,
                        onClick = { onOpenWord(item.word.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReviewStatsCard(statistics: ReviewStatistics, modifier: Modifier = Modifier) {
    val none = stringResource(R.string.stats_none)
    SectionCard(title = stringResource(R.string.stats_title), modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
            StatRow(R.string.stats_total_reviews, statistics.totalReviews.toString())
            StatRow(R.string.stats_correct, statistics.correctAnswers.toString())
            StatRow(R.string.stats_incorrect, statistics.incorrectAnswers.toString())
            StatRow(R.string.stats_remembered, statistics.wordsRemembered.toString())
            StatRow(R.string.stats_forgotten, statistics.wordsForgotten.toString())
            StatRow(
                R.string.stats_difficulty,
                statistics.averageDifficulty?.let {
                    stringResource(R.string.stats_difficulty_value, String.format(Locale.getDefault(), "%.1f", it))
                } ?: none,
            )
            StatRow(
                R.string.stats_completion,
                statistics.completionRate?.let { stringResource(R.string.percent, (it * 100).roundToInt()) } ?: none,
            )
        }
    }
}

@Composable
private fun StatRow(@StringRes label: Int, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.titleMedium)
    }
}

package com.rewordly.app.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rewordly.app.R
import com.rewordly.app.core.ui.components.EmptyState
import com.rewordly.app.core.ui.components.ErrorState
import com.rewordly.app.core.ui.components.GoalReachedCard
import com.rewordly.app.core.ui.components.LoadingState
import com.rewordly.app.core.ui.components.PrimaryButton
import com.rewordly.app.core.ui.components.ProgressCard
import com.rewordly.app.core.ui.components.RecommendedWordItem
import com.rewordly.app.core.ui.components.ReviewDoneCard
import com.rewordly.app.core.ui.components.ReviewDueCard
import com.rewordly.app.core.ui.components.SectionCard
import com.rewordly.app.core.ui.components.SectionHeader
import com.rewordly.app.core.ui.components.StatTile
import com.rewordly.app.core.ui.components.TodayPlanCard
import com.rewordly.app.core.ui.components.WordListItem
import com.rewordly.app.core.ui.messageRes
import com.rewordly.app.core.ui.theme.Dimens
import com.rewordly.app.domain.model.weakReason
import com.rewordly.app.domain.usecase.Greeting
import com.rewordly.app.feature.ai.AiEntryCard

@Composable
fun HomeScreen(
    onContinueLearning: () -> Unit,
    onStartReview: () -> Unit,
    onOpenWord: (String) -> Unit,
    onOpenSaved: () -> Unit,
    onOpenAi: () -> Unit,
    onOpenPlacement: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold { padding ->
        when (val state = uiState) {
            HomeUiState.Loading -> LoadingState(Modifier.padding(padding))
            is HomeUiState.Error -> ErrorState(
                message = stringResource(state.error.messageRes),
                onRetry = viewModel::retry,
                modifier = Modifier.padding(padding),
            )
            is HomeUiState.Success -> HomeContent(
                state = state,
                contentPadding = padding,
                onContinueLearning = onContinueLearning,
                onStartReview = onStartReview,
                onOpenWord = onOpenWord,
                onOpenSaved = onOpenSaved,
                onOpenAi = onOpenAi,
                onOpenPlacement = onOpenPlacement,
            )
        }
    }
}

@Composable
private fun HomeContent(
    state: HomeUiState.Success,
    contentPadding: PaddingValues,
    onContinueLearning: () -> Unit,
    onStartReview: () -> Unit,
    onOpenWord: (String) -> Unit,
    onOpenSaved: () -> Unit,
    onOpenAi: () -> Unit,
    onOpenPlacement: () -> Unit,
) {
    val overview = state.overview
    val insight = state.insight
    if (overview.totalWords == 0) {
        EmptyState(
            icon = Icons.AutoMirrored.Outlined.MenuBook,
            title = stringResource(R.string.home_empty_vocabulary_title),
            message = stringResource(R.string.home_empty_vocabulary_message),
            modifier = Modifier.padding(contentPadding),
        )
        return
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(Dimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceLg),
    ) {
        item(key = "greeting") {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs)) {
                Text(
                    text = stringResource(state.greeting.labelRes),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.home_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item(key = "plan") {
            TodayPlanCard(
                reviewCount = state.plan.reviewCount,
                newWordCount = state.plan.newWordCount,
                easedOff = state.plan.easedOff,
                onStartReview = onStartReview,
                onStartLearning = onContinueLearning,
            )
        }
        item(key = "review") {
            if (overview.wordsDueToday > 0) {
                ReviewDueCard(
                    dueToday = overview.wordsDueToday,
                    reviewedToday = overview.reviewedToday,
                    onStartReview = onStartReview,
                )
            } else {
                ReviewDoneCard(reviewedToday = overview.reviewedToday)
            }
        }
        item(key = "progress") {
            if (insight.isGoalReached) {
                GoalReachedCard(
                    learnedToday = insight.learnedToday,
                    dailyGoal = insight.dailyGoal,
                    onReview = onStartReview,
                )
            } else {
                ProgressCard(
                    learnedToday = insight.learnedToday,
                    dailyGoal = insight.dailyGoal,
                    fraction = insight.goalFraction,
                    onContinue = onContinueLearning,
                    continueEnabled = overview.newWordsAvailable > 0,
                )
            }
        }
        if (state.showPlacementPrompt) {
            item(key = "placement") {
                PlacementPromptCard(onOpenPlacement = onOpenPlacement)
            }
        }
        item(key = "stats") {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
                    StatTile(
                        icon = Icons.Outlined.LocalFireDepartment,
                        value = pluralStringResource(
                            R.plurals.days_count,
                            overview.streak.current,
                            overview.streak.current,
                        ),
                        label = stringResource(R.string.home_streak),
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        icon = Icons.AutoMirrored.Outlined.MenuBook,
                        value = overview.newWordsAvailable.toString(),
                        label = stringResource(R.string.home_new_words),
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
                    StatTile(
                        icon = Icons.Outlined.CheckCircle,
                        value = overview.totalWords.toString(),
                        label = stringResource(R.string.home_total_words),
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        icon = Icons.Outlined.Bookmark,
                        value = overview.savedWords.toString(),
                        label = stringResource(R.string.home_saved_words),
                        modifier = Modifier.weight(1f),
                        onClick = onOpenSaved,
                    )
                }
            }
        }
        if (insight.hasWeakWords) {
            item(key = "weakHeader") {
                SectionHeader(
                    title = stringResource(R.string.home_weak_words_title),
                    modifier = Modifier.padding(top = Dimens.spaceSm),
                )
            }
            item(key = "weakHint") {
                Text(
                    text = stringResource(R.string.home_weak_words_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(insight.weakWords, key = { "weak-${it.word.id}" }) { item ->
                RecommendedWordItem(
                    word = item.word.text,
                    translation = item.word.translation.text,
                    difficulty = item.word.difficulty,
                    reason = item.progress.weakReason,
                    onClick = { onOpenWord(item.word.id) },
                )
            }
        }
        if (state.plan.newWords.isNotEmpty()) {
            item(key = "recommendedHeader") {
                SectionHeader(
                    title = stringResource(R.string.home_recommended_title),
                    modifier = Modifier.padding(top = Dimens.spaceSm),
                )
            }
            item(key = "recommendedHint") {
                Text(
                    text = stringResource(R.string.home_recommended_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(state.plan.newWords, key = { "recommended-${it.word.id}" }) { item ->
                RecommendedWordItem(
                    word = item.word.text,
                    translation = item.word.translation.text,
                    difficulty = item.word.difficulty,
                    reason = item.reason,
                    onClick = { onOpenWord(item.word.id) },
                )
            }
        }
        item(key = "ai") {
            AiEntryCard(onClick = onOpenAi)
        }
        if (state.recentWords.isNotEmpty()) {
            item(key = "recentHeader") {
                SectionHeader(
                    title = stringResource(R.string.home_recent_words),
                    modifier = Modifier.padding(top = Dimens.spaceSm),
                )
            }
            items(state.recentWords, key = { "recent-${it.word.id}" }) { item ->
                WordListItem(item = item, onClick = { onOpenWord(item.word.id) })
            }
        }
    }
}

/** Optional, dismissible-by-ignoring invitation to estimate the level. Nothing here is mandatory. */
@Composable
private fun PlacementPromptCard(onOpenPlacement: () -> Unit, modifier: Modifier = Modifier) {
    SectionCard(title = stringResource(R.string.home_check_level_title), modifier = modifier) {
        Text(
            text = stringResource(R.string.home_check_level_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PrimaryButton(
            text = stringResource(R.string.home_check_level_action),
            icon = Icons.Outlined.School,
            onClick = onOpenPlacement,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private val Greeting.labelRes: Int
    get() = when (this) {
        Greeting.MORNING -> R.string.greeting_morning
        Greeting.AFTERNOON -> R.string.greeting_afternoon
        Greeting.EVENING -> R.string.greeting_evening
        Greeting.NIGHT -> R.string.greeting_night
    }

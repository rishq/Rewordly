package com.rewordly.app.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.LocalFireDepartment
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
import com.rewordly.app.core.ui.components.DailyGoalCard
import com.rewordly.app.core.ui.components.ErrorState
import com.rewordly.app.core.ui.components.LoadingState
import com.rewordly.app.core.ui.components.ProgressCard
import com.rewordly.app.core.ui.components.SectionHeader
import com.rewordly.app.core.ui.components.StatTile
import com.rewordly.app.core.ui.components.WordListItem
import com.rewordly.app.core.ui.messageRes
import com.rewordly.app.core.ui.theme.Dimens
import com.rewordly.app.domain.usecase.Greeting

@Composable
fun HomeScreen(
    onContinueLearning: () -> Unit,
    onStartReview: () -> Unit,
    onOpenWord: (String) -> Unit,
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
) {
    val overview = state.overview
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
        item(key = "progress") {
            ProgressCard(
                learnedToday = overview.today.wordsLearned,
                dailyGoal = overview.today.goal,
                fraction = overview.today.goalFraction,
                onContinue = onContinueLearning,
            )
        }
        item(key = "stats") {
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMd)) {
                StatTile(
                    icon = Icons.Outlined.LocalFireDepartment,
                    value = pluralStringResource(R.plurals.days_count, overview.currentStreak, overview.currentStreak),
                    label = stringResource(R.string.home_streak),
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    icon = Icons.Outlined.CheckCircle,
                    value = overview.wordsLearned.toString(),
                    label = stringResource(R.string.home_words_learned),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item(key = "review") {
            DailyGoalCard(wordsToReview = overview.wordsToReview, onStartReview = onStartReview)
        }
        item(key = "recentHeader") {
            SectionHeader(
                title = stringResource(R.string.home_recent_words),
                modifier = Modifier.padding(top = Dimens.spaceSm),
            )
        }
        items(state.recentWords, key = { it.word.id }) { item ->
            WordListItem(item = item, onClick = { onOpenWord(item.word.id) })
        }
    }
}

private val Greeting.labelRes: Int
    get() = when (this) {
        Greeting.MORNING -> R.string.greeting_morning
        Greeting.AFTERNOON -> R.string.greeting_afternoon
        Greeting.EVENING -> R.string.greeting_evening
        Greeting.NIGHT -> R.string.greeting_night
    }

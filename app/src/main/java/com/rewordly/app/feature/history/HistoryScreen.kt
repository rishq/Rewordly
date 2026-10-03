package com.rewordly.app.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.History
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rewordly.app.R
import com.rewordly.app.core.ui.components.ActivityHeatmap
import com.rewordly.app.core.ui.components.EmptyState
import com.rewordly.app.core.ui.components.ErrorState
import com.rewordly.app.core.ui.components.LoadingState
import com.rewordly.app.core.ui.components.SectionCard
import com.rewordly.app.core.ui.messageRes
import com.rewordly.app.core.ui.theme.Dimens
import com.rewordly.app.domain.model.ReviewDay
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onBack: () -> Unit, viewModel: HistoryViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
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
        val modifier = Modifier.padding(padding)
        when (val state = uiState) {
            HistoryUiState.Loading -> LoadingState(modifier)
            HistoryUiState.Empty -> EmptyState(
                icon = Icons.Outlined.History,
                title = stringResource(R.string.history_empty_title),
                message = stringResource(R.string.history_empty_message),
                modifier = modifier,
            )
            is HistoryUiState.Error -> ErrorState(
                message = stringResource(state.error.messageRes),
                onRetry = viewModel::retry,
                modifier = modifier,
            )
            is HistoryUiState.Content -> HistoryContent(state, modifier)
        }
    }
}

@Composable
fun HistoryContent(state: HistoryUiState.Content, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = Dimens.maxContentWidth),
        contentPadding = PaddingValues(Dimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item(key = "calendar") {
            SectionCard(title = stringResource(R.string.history_calendar_title)) {
                ActivityHeatmap(weeks = state.calendar, firstDayOfWeek = state.firstDayOfWeek)
            }
        }
        item(key = "daysHeader") {
            Text(
                text = stringResource(R.string.history_days_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        items(state.days, key = { it.date.toEpochDay() }) { day ->
            DayCard(day = day, title = dateFormat.format(day.date), locale = locale)
        }
    }
}

@Composable
private fun DayCard(day: ReviewDay, title: String, locale: Locale) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(Dimens.spaceLg),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            if (day.reviews > 0) {
                Text(
                    text = stringResource(R.string.history_day_summary, day.reviewedWords, day.correct, day.incorrect),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (day.wordsLearned > 0) {
                Text(
                    text = stringResource(R.string.history_learned, day.wordsLearned),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            day.studyTimeMs?.let {
                Text(
                    text = stringResource(R.string.history_time, formatStudyTime(it)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun formatStudyTime(millis: Long): String {
    val minutes = (millis / MILLIS_PER_MINUTE).toInt()
    return if (minutes < 1) {
        stringResource(R.string.history_time_less_than_minute)
    } else {
        stringResource(R.string.history_time_minutes, minutes)
    }
}

private const val MILLIS_PER_MINUTE = 60_000L

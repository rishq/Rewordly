package com.rewordly.app.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.rewordly.app.R
import com.rewordly.app.core.ui.theme.Dimens
import com.rewordly.app.domain.model.DailyProgress
import java.time.format.DateTimeFormatter

/** Simple bar chart of words learned per day. */
@Composable
fun ActivityChart(days: List<DailyProgress>, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    // The month under the bar, named where it starts. A weekday letter alone did not say which day it was.
    val monthFormat = remember(locale) { DateTimeFormatter.ofPattern("MMM", locale) }
    val max = (days.maxOfOrNull { it.wordsLearned } ?: 0).coerceAtLeast(1)
    val totalLearned = days.sumOf { it.wordsLearned }
    val summary = pluralStringResource(R.plurals.a11y_activity_chart, totalLearned, totalLearned, days.size)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = summary },
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
        verticalAlignment = Alignment.Bottom,
    ) {
        days.forEachIndexed { index, day ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs),
            ) {
                Text(
                    text = day.wordsLearned.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(
                    modifier = Modifier
                        .height(Dimens.activityBarHeight)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight((day.wordsLearned.toFloat() / max).coerceAtLeast(MIN_BAR_FRACTION))
                            .background(
                                color = if (day.wordsLearned >= day.goal) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer
                                },
                                shape = MaterialTheme.shapes.small,
                            ),
                    )
                }
                Text(
                    text = day.date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
                // The month is named where it starts, so a week that crosses two months says so.
                Box(modifier = Modifier.height(MONTH_ROW_HEIGHT), contentAlignment = Alignment.Center) {
                    val month = monthFormat.format(day.date)
                    val previous = days.getOrNull(index - 1)?.let { monthFormat.format(it.date) }
                    if (month != previous) {
                        Text(
                            text = month,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

private const val MIN_BAR_FRACTION = 0.04f
private val MONTH_ROW_HEIGHT = 16.dp

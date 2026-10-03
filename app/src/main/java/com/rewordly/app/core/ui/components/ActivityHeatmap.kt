package com.rewordly.app.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.rewordly.app.R
import com.rewordly.app.core.ui.theme.Dimens
import com.rewordly.app.domain.usecase.ActivityCalendarBuilder
import com.rewordly.app.domain.usecase.CalendarCell
import java.time.DayOfWeek
import java.time.format.TextStyle

/**
 * GitHub-style calendar: one column per week, one row per weekday. Colours are derived from the theme
 * (surface to primary), so the contrast holds in light and dark mode. The grid is summarised for screen
 * readers; the day list below it carries the exact numbers.
 */
@Composable
fun ActivityHeatmap(weeks: List<List<CalendarCell>>, firstDayOfWeek: DayOfWeek, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val activeDays = weeks.sumOf { week -> week.count { it.activity > 0 } }
    val summary = stringResource(R.string.a11y_heatmap, weeks.size, activeDays)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = summary },
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(CELL_GAP)) {
            Column(
                modifier = Modifier.width(LABEL_WIDTH),
                verticalArrangement = Arrangement.spacedBy(CELL_GAP),
            ) {
                repeat(DAYS_IN_WEEK) { row ->
                    Box(modifier = Modifier.aspectRatio(1f), contentAlignment = Alignment.CenterStart) {
                        if (row % 2 == 0) {
                            Text(
                                text = firstDayOfWeek.plus(row.toLong()).getDisplayName(TextStyle.NARROW, locale),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            weeks.forEach { week ->
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CELL_GAP)) {
                    week.forEach { cell -> HeatmapCell(cell) }
                }
            }
        }
        HeatmapLegend()
    }
}

@Composable
private fun HeatmapCell(cell: CalendarCell) {
    val color = if (cell.isFuture) Color.Transparent else levelColor(cell.level)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(color, MaterialTheme.shapes.extraSmall),
    )
}

@Composable
private fun HeatmapLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceXs, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.history_legend_less),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        (0..ActivityCalendarBuilder.LEVELS).forEach { level ->
            Box(modifier = Modifier.size(LEGEND_CELL).background(levelColor(level), MaterialTheme.shapes.extraSmall))
        }
        Text(
            text = stringResource(R.string.history_legend_more),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun levelColor(level: Int): Color {
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest
    if (level <= 0) return empty
    return lerp(empty, MaterialTheme.colorScheme.primary, level.toFloat() / ActivityCalendarBuilder.LEVELS)
}

private const val DAYS_IN_WEEK = 7
private val CELL_GAP = 3.dp
private val LABEL_WIDTH = 14.dp
private val LEGEND_CELL = 12.dp

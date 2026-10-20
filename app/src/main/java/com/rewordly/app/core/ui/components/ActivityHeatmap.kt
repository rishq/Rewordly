package com.rewordly.app.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rewordly.app.R
import com.rewordly.app.core.ui.theme.Dimens
import com.rewordly.app.domain.usecase.ActivityCalendarBuilder
import com.rewordly.app.domain.usecase.CalendarCell
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle

/**
 * GitHub-style calendar: one column per week, one row per weekday. Colours are derived from the theme
 * (surface to primary), so the contrast holds in light and dark mode. The grid is summarised for screen
 * readers; the day list below it carries the exact numbers.
 *
 * The months are named above the columns and every cell can be tapped, so a day is never just a coloured
 * square: the date and what happened on it are always one tap away.
 */
@Composable
fun ActivityHeatmap(weeks: List<List<CalendarCell>>, firstDayOfWeek: DayOfWeek, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val activeDays = weeks.sumOf { week -> week.count { it.activity > 0 } }
    val summary = stringResource(R.string.a11y_heatmap, weeks.size, activeDays)
    val dateFormat = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
    val monthLabels = remember(weeks, locale) { ActivityCalendarBuilder.monthLabels(weeks, locale) }
    var selected by remember { mutableStateOf<CalendarCell?>(null) }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
    ) {
        BoxWithConstraints {
            // The cells are square, so the weekday column has to be as tall as a cell for the letters to
            // land on their own rows instead of piling up at the top.
            val cellSize = cellSize(maxWidth, weeks.size)
            Row(
                modifier = Modifier.clearAndSetSemantics { contentDescription = summary },
                horizontalArrangement = Arrangement.spacedBy(CELL_GAP),
            ) {
                Column(
                    modifier = Modifier.width(LABEL_WIDTH),
                    verticalArrangement = Arrangement.spacedBy(CELL_GAP),
                ) {
                    Spacer(modifier = Modifier.height(MONTH_ROW_HEIGHT))
                    repeat(DAYS_IN_WEEK) { row ->
                        Box(modifier = Modifier.height(cellSize), contentAlignment = Alignment.CenterStart) {
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
                weeks.forEachIndexed { index, week ->
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CELL_GAP)) {
                        Box(modifier = Modifier.height(MONTH_ROW_HEIGHT), contentAlignment = Alignment.CenterStart) {
                            Text(
                                text = monthLabels.getOrElse(index) { "" },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                                // The month name is wider than a week column, so it is allowed to run over
                                // the empty columns next to it instead of being cut in half.
                                overflow = TextOverflow.Visible,
                                modifier = Modifier.wrapContentWidth(unbounded = true),
                            )
                        }
                        week.forEach { cell ->
                            HeatmapCell(
                                cell = cell,
                                size = cellSize,
                                selected = selected == cell,
                                onClick = { selected = cell },
                            )
                        }
                    }
                }
            }
        }
        selected?.let { cell ->
            Text(
                text = pluralStringResource(
                    R.plurals.history_calendar_day,
                    cell.activity,
                    dateFormat.format(cell.date),
                    cell.activity,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HeatmapLegend()
    }
}

/** The width of one day cell: what is left after the weekday column and the gaps, split over the weeks. */
private fun cellSize(maxWidth: Dp, weeks: Int): Dp {
    if (weeks <= 0) return MIN_CELL_SIZE
    // One gap between the weekday column and the first week, plus one between every two weeks.
    val free = maxWidth - LABEL_WIDTH - CELL_GAP * weeks
    return (free / weeks).coerceAtLeast(MIN_CELL_SIZE)
}

@Composable
private fun HeatmapCell(cell: CalendarCell, size: Dp, selected: Boolean, onClick: () -> Unit) {
    val color = if (cell.isFuture) Color.Transparent else levelColor(cell.level)
    val border = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(size)
            .background(color, MaterialTheme.shapes.extraSmall)
            .border(1.dp, border, MaterialTheme.shapes.extraSmall)
            .clickable(enabled = !cell.isFuture, onClick = onClick),
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
private val MONTH_ROW_HEIGHT = 16.dp
private val MIN_CELL_SIZE = 8.dp

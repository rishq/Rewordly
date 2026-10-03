package com.rewordly.app.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.rewordly.app.R
import com.rewordly.app.core.ui.theme.Dimens
import com.rewordly.app.core.ui.theme.RewordlyTextStyles

/** Today's learning progress with a ring indicator and a primary call to action. */
@Composable
fun ProgressCard(
    learnedToday: Int,
    dailyGoal: Int,
    fraction: Float,
    onContinue: () -> Unit,
    continueEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val animated by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = 700),
        label = "progress",
    )
    val progressText = stringResource(R.string.home_progress_value, learnedToday, dailyGoal)
    val progressDescription = pluralStringResource(R.plurals.a11y_progress, dailyGoal, learnedToday, dailyGoal)
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Column(
            modifier = Modifier.padding(Dimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceLg),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spaceLg),
                modifier = Modifier.clearAndSetSemantics { contentDescription = progressDescription },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { animated },
                        modifier = Modifier.size(Dimens.progressRing),
                        color = MaterialTheme.colorScheme.onPrimary,
                        trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = TRACK_ALPHA),
                        strokeWidth = Dimens.progressStroke,
                        strokeCap = StrokeCap.Round,
                    )
                    Text(
                        text = stringResource(R.string.percent, (fraction * 100).toInt()),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs)) {
                    Text(
                        text = stringResource(R.string.home_progress_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(text = progressText, style = RewordlyTextStyles.statValue)
                    Text(
                        text = pluralStringResource(R.plurals.words_today_goal, dailyGoal, dailyGoal),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            FilledTonalButton(
                onClick = onContinue,
                enabled = continueEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Dimens.spaceXs),
            ) {
                Text(text = stringResource(R.string.action_continue_learning))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(start = Dimens.spaceSm)
                        .size(Dimens.iconSm),
                )
            }
        }
    }
}

/** Compact metric tile (streak, totals). */
@Composable
fun StatTile(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.spaceLg)
                .semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs),
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
            Text(text = value, style = RewordlyTextStyles.statValue, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = MaterialTheme.shapes.large, colors = colors) {
            content()
        }
    } else {
        Card(modifier = modifier, shape = MaterialTheme.shapes.large, colors = colors) {
            content()
        }
    }
}

/** Shown on Home when the daily goal is already met. */
@Composable
fun GoalReachedCard(learnedToday: Int, dailyGoal: Int, onReview: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onReview,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
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
            Icon(
                imageVector = Icons.Outlined.EmojiEvents,
                contentDescription = null,
                modifier = Modifier.size(Dimens.iconLg),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs)) {
                Text(text = stringResource(R.string.goal_reached_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(R.string.goal_reached_message, learnedToday, dailyGoal),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.goal_reached_action),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private const val TRACK_ALPHA = 0.25f

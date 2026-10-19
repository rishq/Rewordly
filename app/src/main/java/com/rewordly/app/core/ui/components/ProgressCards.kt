package com.rewordly.app.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.rewordly.app.R
import com.rewordly.app.core.ui.theme.Dimens
import com.rewordly.app.core.ui.theme.RewordlyTextStyles
import com.rewordly.app.core.ui.theme.extraColors

/** Today's learning progress with a brand-gradient ring and a primary call to action. */
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
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
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
                    BrandProgressRing(
                        fraction = animated,
                        modifier = Modifier.size(Dimens.progressRing),
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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

/** The daily-goal ring drawn with the brand gradient and a muted track. */
@Composable
private fun BrandProgressRing(fraction: Float, modifier: Modifier = Modifier) {
    val ringBrush = MaterialTheme.extraColors.brandGradient
    val track = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier = modifier) {
        val stroke = Dimens.progressStroke.toPx()
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawArc(
            color = track,
            startAngle = RING_START_DEGREES,
            sweepAngle = FULL_SWEEP_DEGREES,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        drawArc(
            brush = ringBrush,
            startAngle = RING_START_DEGREES,
            sweepAngle = FULL_SWEEP_DEGREES * fraction.coerceIn(0f, 1f),
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

/** Compact metric tile (streak, totals). */
@Composable
fun StatTile(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.tertiary,
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
            Icon(imageVector = icon, contentDescription = null, tint = tint)
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

private val RING_START_DEGREES = -90f
private const val FULL_SWEEP_DEGREES = 360f

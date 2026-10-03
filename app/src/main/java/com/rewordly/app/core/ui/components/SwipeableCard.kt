package com.rewordly.app.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rewordly.app.core.ui.motionMillis
import com.rewordly.app.core.ui.theme.Dimens
import kotlinx.coroutines.launch

/** Which way the card was thrown. [LEFT] is the "I remembered it" side, [RIGHT] the "not yet" side. */
enum class SwipeSide { LEFT, RIGHT }

/**
 * Wraps [content] in a card the user can throw sideways to answer without hunting for a button.
 *
 * - Dragging past [SWIPE_THRESHOLD] flings the card off screen and reports the direction.
 * - Dragging less than that springs the card back, so a nervous finger never answers by accident.
 * - A badge slides in on the uncovered edge to say what the gesture is about to do.
 *
 * The same two answers are always reachable as real buttons (see [StudyAnswerBar]) and as
 * accessibility actions, because a drag-only interaction is unusable with a screen reader.
 *
 * [resetKey] must identify the card being shown. When it changes the offset starts from the middle again,
 * so the next word is centred; it also changes when an answer fails, which springs the card back.
 */
@Composable
fun SwipeableCard(
    leftLabel: String,
    rightLabel: String,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    resetKey: Any? = null,
    content: @Composable () -> Unit,
) {
    val threshold = with(LocalDensity.current) { SWIPE_THRESHOLD.toPx() }
    // Keyed on the card, so a new word (or a failed answer) starts centred without a frame of stale offset.
    val offset = remember(resetKey) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    val flingDuration = motionMillis(FLING_MILLIS)

    Box(
        modifier = modifier
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(leftLabel) {
                        onSwipeLeft()
                        true
                    },
                    CustomAccessibilityAction(rightLabel) {
                        onSwipeRight()
                        true
                    },
                )
            }
            .pointerInput(enabled, resetKey) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val travelled = offset.value
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        when {
                            travelled <= -threshold -> scope.launch {
                                offset.animateTo(-width, tween(flingDuration))
                                onSwipeLeft()
                            }
                            travelled >= threshold -> scope.launch {
                                offset.animateTo(width, tween(flingDuration))
                                onSwipeRight()
                            }
                            else -> scope.launch { offset.animateTo(0f, spring()) }
                        }
                    },
                    onDragCancel = { scope.launch { offset.animateTo(0f, spring()) } },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        scope.launch { offset.snapTo(offset.value + dragAmount) }
                    },
                )
            },
    ) {
        val progress = (offset.value / threshold).coerceIn(-1f, 1f)
        // The badge sits on the edge the card is moving away from, so it is revealed by the gesture.
        SwipeBadge(
            text = rightLabel,
            container = scheme.errorContainer,
            content = scheme.onErrorContainer,
            alignment = Alignment.TopStart,
            rotation = -BADGE_ROTATION,
            alpha = progress.coerceAtLeast(0f),
        )
        SwipeBadge(
            text = leftLabel,
            container = scheme.primary,
            content = scheme.onPrimary,
            alignment = Alignment.TopEnd,
            rotation = BADGE_ROTATION,
            alpha = (-progress).coerceAtLeast(0f),
        )
        Box(
            modifier = Modifier.graphicsLayer {
                translationX = offset.value
                rotationZ = (offset.value / ROTATION_DIVISOR).coerceIn(-MAX_ROTATION, MAX_ROTATION)
            },
        ) {
            content()
        }
    }
}

@Composable
private fun BoxScope.SwipeBadge(
    text: String,
    container: Color,
    content: Color,
    alignment: Alignment,
    rotation: Float,
    alpha: Float,
) {
    Surface(
        modifier = Modifier
            .align(alignment)
            .padding(Dimens.spaceMd)
            .graphicsLayer {
                this.alpha = alpha
                rotationZ = rotation
            }
            .clearAndSetSemantics { },
        shape = MaterialTheme.shapes.medium,
        color = container,
        contentColor = content,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = Dimens.spaceMd, vertical = Dimens.spaceXs),
        )
    }
}

/** How far the card must travel before letting go counts as an answer. */
private val SWIPE_THRESHOLD = 96.dp
private const val FLING_MILLIS = 220
private const val ROTATION_DIVISOR = 60f
private const val MAX_ROTATION = 12f
private const val BADGE_ROTATION = 8f

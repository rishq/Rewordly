package com.rewordly.app.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.rewordly.app.core.ui.motionMillis

private const val FLIP_MILLIS = 300

/**
 * Turns [front] away around the Y axis and brings [back] in. Only the visible face is composed, so
 * screen readers never see both sides and the hidden face costs nothing.
 */
@Composable
fun FlipCard(
    flipped: Boolean,
    modifier: Modifier = Modifier,
    front: @Composable () -> Unit,
    back: @Composable () -> Unit,
) {
    val duration = motionMillis(FLIP_MILLIS)
    val angle by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = tween(durationMillis = duration),
        label = "flipCard",
    )
    Box(modifier = modifier.graphicsLayer { rotationY = angle }) {
        if (angle <= 90f) {
            front()
        } else {
            // Counter-rotation keeps the incoming face readable instead of mirrored.
            Box(Modifier.graphicsLayer { rotationY = 180f }) { back() }
        }
    }
}

package com.rewordly.app.core.ui

import android.animation.ValueAnimator
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * True when the user allows motion. Reads the platform animation scale and, on Android 13+, the
 * "remove animations" accessibility toggle, so every animation in the app can honour it.
 */
@Composable
fun rememberMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) { context.motionEnabled() }
}

/** Animation duration that collapses to [DURATION_DISABLED] when the user asked for less motion. */
@Composable
fun motionMillis(durationMillis: Int): Int = if (rememberMotionEnabled()) durationMillis else DURATION_DISABLED

const val DURATION_DISABLED = 0

private fun Context.motionEnabled(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    ValueAnimator.areAnimatorsEnabled()
} else {
    Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
}

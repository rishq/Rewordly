package com.rewordly.app.widget

import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProvider

/**
 * Widget colours, matching the app palette so the widget looks like part of Rewordly.
 *
 * Each colour carries a day and a night value, which is how a Glance widget follows the system light/dark
 * setting without the app having to observe configuration changes itself.
 *
 * The values are [Color] literals rather than `@ColorRes` ids on purpose. In Glance 1.1 every resource-based
 * factory (`ColorProvider(@ColorRes Int)`, `ResourceColorProvider`) is annotated
 * `@RestrictTo(LIBRARY_GROUP)`, so calling them from an app trips the `RestrictedApi` lint check, and the
 * day/night factory below is the supported public one. Keep the two columns in sync when editing: the day
 * value is what used to live in `values/`, the night value what used to live in `values-night/`.
 */
object WidgetColors {
    val background = ColorProvider(day = Color(0xFFFBF8FF), night = Color(0xFF1F1F25))
    val onBackground = ColorProvider(day = Color(0xFF1B1B21), night = Color(0xFFE4E1E9))
    val muted = ColorProvider(day = Color(0xFF5B5D72), night = Color(0xFFC4C5DD))
    val primary = ColorProvider(day = Color(0xFF4C56AF), night = Color(0xFFBDC2FF))
    val onPrimary = ColorProvider(day = Color(0xFFFBF8FF), night = Color(0xFF000C62))
    val track = ColorProvider(day = Color(0xFFDFE0FF), night = Color(0xFF2D2F42))

    /** Streak accent; the warm coral already used for streaks inside the app. */
    val accent = ColorProvider(day = Color(0xFF9C4234), night = Color(0xFFFFB4A8))
}

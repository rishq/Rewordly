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
    val background = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF000000))
    val onBackground = ColorProvider(day = Color(0xFF1D1D1F), night = Color(0xFFF5F5F7))
    val muted = ColorProvider(day = Color(0xFF86868B), night = Color(0xFF98989D))
    val primary = ColorProvider(day = Color(0xFF007AFF), night = Color(0xFF0A84FF))
    val onPrimary = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFFFFFFFF))
    val track = ColorProvider(day = Color(0xFFE5E5EA), night = Color(0xFF38383A))

    /** Streak accent follows the Apple Blue accent in both themes. */
    val accent = ColorProvider(day = Color(0xFF007AFF), night = Color(0xFF0A84FF))
}

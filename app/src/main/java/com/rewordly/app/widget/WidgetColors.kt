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
    val background = ColorProvider(day = Color(0xFFF7F6FF), night = Color(0xFF0D0F24))
    val onBackground = ColorProvider(day = Color(0xFF1F2340), night = Color(0xFFECEEFF))
    val muted = ColorProvider(day = Color(0xFF6B7090), night = Color(0xFF9AA0C8))
    val primary = ColorProvider(day = Color(0xFF6A3DF0), night = Color(0xFF9B7BFF))
    val onPrimary = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF1F0A5C))
    val track = ColorProvider(day = Color(0xFFE6DEFF), night = Color(0xFF3A22A8))

    /**
     * Streak accent. Gold is the brand streak colour, but it only contrasts on dark surfaces,
     * so the day column uses a deep amber of the same hue (AA on the light widget background).
     */
    val accent = ColorProvider(day = Color(0xFF8A6100), night = Color(0xFFFFC53D))
}

package com.rewordly.app.core.navigation

/**
 * Where a notification tap or action button should take the user.
 *
 * Only destinations the app can reach safely are listed: a notification never starts a learning
 * session in the background, it opens the matching screen and lets the user decide.
 */
enum class NotificationDestination(val extraValue: String) {
    HOME("home"),
    LEARN("learn"),
    REVIEW("review"),
    ;

    companion object {
        /** Intent extra carrying a [extraValue]. */
        const val EXTRA = "com.rewordly.app.extra.NOTIFICATION_DESTINATION"

        fun fromExtra(value: String?): NotificationDestination? = entries.firstOrNull { it.extraValue == value }
    }
}

package com.rewordly.app.core.notifications

import com.rewordly.app.core.navigation.NotificationDestination
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Carries a notification destination from the Activity to the navigation host.
 *
 * The Activity cannot navigate directly: on a cold start the intent arrives before the back stack
 * exists, and the splash screen still has to resolve the start destination. Holding the request here
 * and consuming it once the host is ready is what keeps a notification tap from creating a duplicate
 * screen or corrupting the current navigation state.
 */
@Singleton
class NotificationNavigationBus @Inject constructor() {
    private val _pending = MutableStateFlow<NotificationDestination?>(null)

    /** The destination waiting to be applied, or null when there is nothing pending. */
    val pending: StateFlow<NotificationDestination?> = _pending.asStateFlow()

    /** Remembers the destination of an incoming intent. A newer request replaces an older one. */
    fun request(destination: NotificationDestination) {
        _pending.value = destination
    }

    /** Clears the request. Called only after navigation succeeded, so it is applied exactly once. */
    fun consume() {
        _pending.value = null
    }
}

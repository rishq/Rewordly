package com.rewordly.app.domain.repository

import com.rewordly.app.domain.model.ReminderKind
import java.time.LocalDate

/**
 * Remembers what was already sent, so the same reminder is never repeated and two notifications never
 * land close together. Only the kind and the timestamp are stored - never anything about the words.
 */
interface NotificationLedger {
    /** When the last notification of any kind was delivered, or null when none ever was. */
    suspend fun lastNotifiedAt(): Long?

    /** Kinds already delivered on [day]; empty for any other day. */
    suspend fun kindsNotifiedOn(day: LocalDate): Set<ReminderKind>

    /** Records a delivery so it is not repeated on the same day. */
    suspend fun record(kind: ReminderKind, day: LocalDate, at: Long)
}

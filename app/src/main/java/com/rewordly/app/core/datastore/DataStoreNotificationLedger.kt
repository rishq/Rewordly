package com.rewordly.app.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.rewordly.app.domain.model.ReminderKind
import com.rewordly.app.domain.repository.NotificationLedger
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first

/**
 * [NotificationLedger] on the app's DataStore.
 *
 * The delivered kinds are stored together with the day they belong to, so a stale entry from a
 * previous day is simply ignored instead of needing a nightly reset job.
 */
@Singleton
class DataStoreNotificationLedger @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : NotificationLedger {

    private val preferences = dataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    override suspend fun lastNotifiedAt(): Long? = preferences.first()[Keys.LAST_AT]

    override suspend fun kindsNotifiedOn(day: LocalDate): Set<ReminderKind> {
        val prefs = preferences.first()
        if (prefs[Keys.LAST_DAY] != day.toString()) return emptySet()
        return prefs[Keys.KINDS].orEmpty()
            .mapNotNull { name -> ReminderKind.entries.firstOrNull { it.name == name } }
            .toSet()
    }

    override suspend fun record(kind: ReminderKind, day: LocalDate, at: Long) {
        dataStore.edit { prefs ->
            val sameDay = prefs[Keys.LAST_DAY] == day.toString()
            val existing = if (sameDay) prefs[Keys.KINDS].orEmpty() else emptySet()
            prefs[Keys.KINDS] = existing + kind.name
            prefs[Keys.LAST_DAY] = day.toString()
            prefs[Keys.LAST_AT] = at
        }
    }

    private object Keys {
        val LAST_AT = longPreferencesKey("notification_last_at")
        val LAST_DAY = stringPreferencesKey("notification_last_day")
        val KINDS = stringSetPreferencesKey("notification_kinds_sent")
    }
}

package com.rewordly.app.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.rewordly.app.domain.repository.SeedPackTracker
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Stores which bundled pack versions were applied. One preference key holds the whole map as
 * `id=version` pairs joined with `;`: the set is small, written rarely and must always arrive whole,
 * so an atomic single string beats many per-pack keys.
 */
@Singleton
class DataStoreSeedPackTracker @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SeedPackTracker {

    private val versions: Flow<Map<String, Int>> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs -> parse(prefs[KEY].orEmpty()) }

    override suspend fun appliedVersions(): Map<String, Int> = versions.first()

    override suspend fun markApplied(id: String, version: Int) {
        dataStore.edit { prefs ->
            val applied = parse(prefs[KEY].orEmpty()) + (id to version)
            prefs[KEY] = applied.entries.joinToString(PAIR_SEPARATOR) { "${it.key}=${it.value}" }
        }
    }

    /** Corrupted entries are dropped rather than failing the whole read, like the settings parser does. */
    private fun parse(raw: String): Map<String, Int> = raw
        .splitToSequence(PAIR_SEPARATOR)
        .mapNotNull { pair ->
            val parts = pair.split('=', limit = 2)
            val version = if (parts.size == 2) parts[1].toIntOrNull() else null
            if (parts[0].isNotBlank() && version != null && version > 0) parts[0] to version else null
        }
        .toMap()

    private companion object {
        val KEY = stringPreferencesKey("word_pack_versions")
        const val PAIR_SEPARATOR = ";"
    }
}

package com.rewordly.app.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.rewordly.app.core.security.SecretCipher
import com.rewordly.app.domain.model.AiProvider
import com.rewordly.app.domain.model.AiSettings
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Reads and writes the user's own AI provider settings, in the same preferences file as everything else.
 *
 * The API key is run through [SecretCipher] before it is stored, so the preferences file never holds it in the
 * clear. This is deliberately a separate data source from [UserPreferencesDataSource]: the rest of the app has no
 * business reading a provider key, and keeping it out of the user settings flow makes that structural.
 */
@Singleton
class AiPreferencesDataSource @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val cipher: SecretCipher,
) {
    private val preferences: Flow<Preferences> = dataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    val settings: Flow<AiSettings> = preferences.map { prefs ->
        AiSettings(
            provider = AiProvider.fromName(prefs[Keys.PROVIDER]),
            model = prefs[Keys.MODEL].orEmpty(),
            apiKey = prefs[Keys.API_KEY]?.let(cipher::decrypt).orEmpty(),
        )
    }.distinctUntilChanged()

    /**
     * Stores the provider. A model name belongs to the provider it was chosen for, so switching to a different
     * provider clears it in the same edit and the new provider's default applies.
     */
    suspend fun setProvider(provider: AiProvider?) = edit { prefs ->
        val previous = AiProvider.fromName(prefs[Keys.PROVIDER])
        if (provider == null) prefs.remove(Keys.PROVIDER) else prefs[Keys.PROVIDER] = provider.name
        if (provider != previous) prefs.remove(Keys.MODEL)
    }

    suspend fun setModel(model: String) = edit { prefs ->
        val trimmed = model.trim()
        if (trimmed.isEmpty()) prefs.remove(Keys.MODEL) else prefs[Keys.MODEL] = trimmed
    }

    /** Trims surrounding whitespace, which a pasted key usually picks up, and encrypts the rest. */
    suspend fun setApiKey(apiKey: String?) = edit { prefs ->
        val trimmed = apiKey?.trim().orEmpty()
        if (trimmed.isEmpty()) prefs.remove(Keys.API_KEY) else prefs[Keys.API_KEY] = cipher.encrypt(trimmed)
    }

    private suspend fun edit(block: (MutablePreferences) -> Unit) = dataStore.edit(block)

    private object Keys {
        val PROVIDER = stringPreferencesKey("ai_provider")
        val MODEL = stringPreferencesKey("ai_model")
        val API_KEY = stringPreferencesKey("ai_api_key_encrypted")
    }
}

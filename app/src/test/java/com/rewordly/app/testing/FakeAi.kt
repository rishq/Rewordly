package com.rewordly.app.testing

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.rewordly.app.core.security.SecretCipher
import com.rewordly.app.domain.model.AiProvider
import com.rewordly.app.domain.model.AiSettings
import com.rewordly.app.domain.repository.AiSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Reversible stand-in for the Android Keystore, which does not exist off-device. The prefix makes it obvious in
 * an assertion whether a value went through the cipher at all.
 */
class FakeSecretCipher(private val prefix: String = "enc:") : SecretCipher {
    override fun encrypt(plain: String): String = prefix + plain

    override fun decrypt(encoded: String): String? =
        if (encoded.startsWith(prefix)) encoded.removePrefix(prefix) else null
}

/** In-memory [DataStore] so the preferences layer can be exercised without the Android framework. */
class InMemoryPreferencesDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())

    override val data: Flow<Preferences> = state.asStateFlow()

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}

/** Mirrors the real repository's provider-switch rule, which is the part the UI depends on. */
class FakeAiSettingsRepository(initial: AiSettings = AiSettings()) : AiSettingsRepository {
    val state = MutableStateFlow(initial)

    override val settings: Flow<AiSettings> = state

    override suspend fun setProvider(provider: AiProvider?) {
        val previous = state.value.provider
        state.value = state.value.copy(
            provider = provider,
            model = if (provider == previous) state.value.model else "",
        )
    }

    override suspend fun setModel(model: String) {
        state.value = state.value.copy(model = model)
    }

    override suspend fun setApiKey(apiKey: String?) {
        state.value = state.value.copy(apiKey = apiKey.orEmpty())
    }
}

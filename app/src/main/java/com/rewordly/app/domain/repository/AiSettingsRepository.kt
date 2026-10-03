package com.rewordly.app.domain.repository

import com.rewordly.app.domain.model.AiProvider
import com.rewordly.app.domain.model.AiSettings
import kotlinx.coroutines.flow.Flow

/**
 * The user's own AI provider configuration.
 *
 * The key is stored encrypted at rest and is never logged, never written to a backup and never sent anywhere
 * except to the provider the user picked. Everything else in the app sees only [AiSettings].
 */
interface AiSettingsRepository {
    val settings: Flow<AiSettings>

    /**
     * Changes the provider. Moving to a *different* provider also clears the model override, so the new
     * provider's own default applies instead of a model name that belongs to the old one.
     */
    suspend fun setProvider(provider: AiProvider?)

    /** Stores a model override; blank restores the provider default. */
    suspend fun setModel(model: String)

    /** Replaces the key. Null or blank removes it, which turns generation back to the backend path. */
    suspend fun setApiKey(apiKey: String?)
}

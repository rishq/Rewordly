package com.rewordly.app.data.repository

import com.rewordly.app.core.datastore.AiPreferencesDataSource
import com.rewordly.app.domain.model.AiProvider
import com.rewordly.app.domain.model.AiSettings
import com.rewordly.app.domain.repository.AiSettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Applies the input limits before anything reaches storage, so an accidental paste of a whole document cannot
 * end up in preferences.
 */
@Singleton
class DataStoreAiSettingsRepository @Inject constructor(
    private val dataSource: AiPreferencesDataSource,
) : AiSettingsRepository {
    override val settings: Flow<AiSettings> = dataSource.settings

    override suspend fun setProvider(provider: AiProvider?) {
        dataSource.setProvider(provider)
    }

    override suspend fun setModel(model: String) {
        dataSource.setModel(model.take(AiSettings.MAX_MODEL_LENGTH))
    }

    override suspend fun setApiKey(apiKey: String?) {
        dataSource.setApiKey(apiKey?.take(AiSettings.MAX_API_KEY_LENGTH))
    }
}

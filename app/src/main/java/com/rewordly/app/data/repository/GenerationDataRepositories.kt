package com.rewordly.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.rewordly.app.core.common.AppResult
import com.rewordly.app.core.database.dao.GenerationHistoryDao
import com.rewordly.app.core.database.entity.GenerationHistoryEntity
import com.rewordly.app.core.database.safeDbCall
import com.rewordly.app.data.local.decodeGeneratedWords
import com.rewordly.app.data.local.encodeToJson
import com.rewordly.app.data.local.toDomain
import com.rewordly.app.domain.model.Difficulty
import com.rewordly.app.domain.model.GeneratedWord
import com.rewordly.app.domain.model.GenerationHistoryEntry
import com.rewordly.app.domain.model.GenerationSettings
import com.rewordly.app.domain.repository.GenerationHistoryRepository
import com.rewordly.app.domain.repository.GenerationSettingsRepository
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

@Singleton
class OfflineGenerationHistoryRepository @Inject constructor(
    private val dao: GenerationHistoryDao,
    private val json: Json,
) : GenerationHistoryRepository {
    override fun observeHistory(): Flow<List<GenerationHistoryEntry>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun record(entry: GenerationHistoryEntry, items: List<GeneratedWord>): AppResult<Unit> =
        safeDbCall {
            dao.insert(
                GenerationHistoryEntity(
                    id = entry.id,
                    mode = entry.mode.name,
                    description = entry.description,
                    level = entry.level.name,
                    requestedCount = entry.requestedCount,
                    resultCount = items.size,
                    createdAt = entry.createdAt,
                    resultJson = items.encodeToJson(json),
                ),
            )
        }

    override suspend fun getEntry(id: String): AppResult<GenerationHistoryEntry?> = safeDbCall {
        dao.get(id)?.toDomain()
    }

    override suspend fun getItems(id: String): AppResult<List<GeneratedWord>> = safeDbCall {
        dao.get(id)?.resultJson.decodeGeneratedWords(json)
    }

    override suspend fun updateItems(id: String, items: List<GeneratedWord>): AppResult<Unit> = safeDbCall {
        dao.updateResult(id, items.encodeToJson(json), items.size)
    }

    override suspend fun delete(id: String): AppResult<Unit> = safeDbCall { dao.delete(id) }

    override suspend fun clear(): AppResult<Unit> = safeDbCall { dao.clear() }
}

@Singleton
class DataStoreGenerationSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : GenerationSettingsRepository {
    private val preferences: Flow<Preferences> = dataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    override val settings: Flow<GenerationSettings> = preferences.map { prefs ->
        GenerationSettings(
            level = prefs[Keys.LEVEL]?.let { name -> Difficulty.entries.firstOrNull { it.name == name } }
                ?: GenerationSettings().level,
            wordCount = prefs[Keys.COUNT]?.takeIf { it in GenerationSettings.WORD_COUNT_OPTIONS }
                ?: GenerationSettings.DEFAULT_WORD_COUNT,
            includeExamples = prefs[Keys.EXAMPLES] ?: true,
            includeSynonyms = prefs[Keys.SYNONYMS] ?: true,
            includePronunciation = prefs[Keys.PRONUNCIATION] ?: true,
        )
    }.distinctUntilChanged()

    override val lastTopic: Flow<String> = preferences.map { it[Keys.LAST_TOPIC].orEmpty() }.distinctUntilChanged()

    override val textNoticeAccepted: Flow<Boolean> =
        preferences.map { it[Keys.TEXT_NOTICE] ?: false }.distinctUntilChanged()

    override suspend fun save(settings: GenerationSettings) {
        dataStore.edit {
            it[Keys.LEVEL] = settings.level.name
            it[Keys.COUNT] = settings.wordCount
            it[Keys.EXAMPLES] = settings.includeExamples
            it[Keys.SYNONYMS] = settings.includeSynonyms
            it[Keys.PRONUNCIATION] = settings.includePronunciation
        }
    }

    override suspend fun saveLastTopic(topic: String) {
        dataStore.edit { it[Keys.LAST_TOPIC] = topic }
    }

    override suspend fun acceptTextNotice() {
        dataStore.edit { it[Keys.TEXT_NOTICE] = true }
    }

    private object Keys {
        val LEVEL = stringPreferencesKey("gen_level")
        val COUNT = intPreferencesKey("gen_count")
        val EXAMPLES = booleanPreferencesKey("gen_examples")
        val SYNONYMS = booleanPreferencesKey("gen_synonyms")
        val PRONUNCIATION = booleanPreferencesKey("gen_pronunciation")
        val LAST_TOPIC = stringPreferencesKey("gen_last_topic")
        val TEXT_NOTICE = booleanPreferencesKey("gen_text_notice_accepted")
    }
}

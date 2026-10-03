package com.rewordly.app.domain.repository

import com.rewordly.app.core.common.AppResult
import com.rewordly.app.domain.model.GeneratedWord
import com.rewordly.app.domain.model.GenerationHistoryEntry
import com.rewordly.app.domain.model.GenerationRequest
import com.rewordly.app.domain.model.GenerationResult
import com.rewordly.app.domain.model.GenerationSettings
import com.rewordly.app.domain.model.UsageInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Source of AI generated vocabulary. Implementations must not expose any AI provider details. */
interface VocabularyGenerationRepository {
    /** Latest usage numbers the backend reported, or null while none were verified. */
    val usage: StateFlow<UsageInfo?>

    /** Cancelling the calling coroutine cancels the request. */
    suspend fun generate(request: GenerationRequest): AppResult<GenerationResult>
}

/** Local record of past generations. Never holds the raw pasted text. */
interface GenerationHistoryRepository {
    fun observeHistory(): Flow<List<GenerationHistoryEntry>>

    suspend fun record(entry: GenerationHistoryEntry, items: List<GeneratedWord>): AppResult<Unit>

    /** Items of a stored generation, or null when it was deleted or never stored. */
    suspend fun getEntry(id: String): AppResult<GenerationHistoryEntry?>

    suspend fun getItems(id: String): AppResult<List<GeneratedWord>>

    suspend fun updateItems(id: String, items: List<GeneratedWord>): AppResult<Unit>

    suspend fun delete(id: String): AppResult<Unit>

    suspend fun clear(): AppResult<Unit>
}

/** Remembers the last used generation options and the privacy notice acknowledgement. */
interface GenerationSettingsRepository {
    val settings: Flow<GenerationSettings>

    val lastTopic: Flow<String>

    val textNoticeAccepted: Flow<Boolean>

    suspend fun save(settings: GenerationSettings)

    suspend fun saveLastTopic(topic: String)

    suspend fun acceptTextNotice()
}

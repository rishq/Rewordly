package com.rewordly.app.core.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.rewordly.app.core.database.entity.GenerationHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GenerationHistoryDao {
    /**
     * The list only ever shows the header fields. [GenerationHistoryEntity.resultJson] holds every generated
     * word (tens of kilobytes per row), so it is replaced by a flag and read one row at a time, only when a
     * preview is actually opened.
     */
    @Query(
        """
        SELECT id, mode, description, level, requested_count, result_count, created_at,
               (result_json IS NOT NULL) AS has_result
        FROM generation_history
        ORDER BY created_at DESC
        """,
    )
    fun observeSummaries(): Flow<List<GenerationHistorySummary>>

    @Query("SELECT * FROM generation_history WHERE id = :id")
    suspend fun get(id: String): GenerationHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: GenerationHistoryEntity)

    @Query("UPDATE generation_history SET result_json = :json, result_count = :count WHERE id = :id")
    suspend fun updateResult(id: String, json: String?, count: Int)

    @Query("DELETE FROM generation_history WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM generation_history")
    suspend fun clear()
}

/** A history row without its payload: everything the list needs, and nothing it does not. */
data class GenerationHistorySummary(
    val id: String,
    val mode: String,
    val description: String,
    val level: String,
    @ColumnInfo(name = "requested_count") val requestedCount: Int,
    @ColumnInfo(name = "result_count") val resultCount: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "has_result") val hasResult: Boolean,
)

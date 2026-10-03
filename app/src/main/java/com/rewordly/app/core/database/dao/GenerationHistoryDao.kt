package com.rewordly.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.rewordly.app.core.database.entity.GenerationHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GenerationHistoryDao {
    @Query("SELECT * FROM generation_history ORDER BY created_at DESC")
    fun observeAll(): Flow<List<GenerationHistoryEntity>>

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

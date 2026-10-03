package com.rewordly.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.rewordly.app.core.database.entity.ReviewLogEntity
import com.rewordly.app.core.database.entity.WordProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReviewLogDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLog(log: ReviewLogEntity): Long

    @Upsert
    suspend fun upsertProgress(progress: WordProgressEntity)

    /**
     * Appends [log] and stores [progress] atomically. Returns false, leaving progress untouched,
     * when the log row already exists (duplicate submission).
     */
    @Transaction
    suspend fun record(log: ReviewLogEntity, progress: WordProgressEntity): Boolean {
        if (insertLog(log) == -1L) return false
        upsertProgress(progress)
        return true
    }

    @Query("SELECT * FROM review_log WHERE reviewed_at >= :sinceMillis ORDER BY reviewed_at ASC")
    fun observeSince(sinceMillis: Long): Flow<List<ReviewLogEntity>>

    @Query(
        """
        SELECT COUNT(*) AS total,
            COALESCE(SUM(CASE WHEN quality >= 3 THEN 1 ELSE 0 END), 0) AS correct,
            COALESCE(SUM(quality), 0) AS qualitySum
        FROM review_log WHERE kind = 'REVIEW'
        """,
    )
    fun observeTotals(): Flow<ReviewLogTotals>
}

data class ReviewLogTotals(val total: Int, val correct: Int, val qualitySum: Int)

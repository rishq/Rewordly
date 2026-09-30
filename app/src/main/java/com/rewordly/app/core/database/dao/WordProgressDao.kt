package com.rewordly.app.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.rewordly.app.core.database.entity.WordProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WordProgressDao {
    @Query("SELECT * FROM word_progress WHERE word_id = :wordId")
    suspend fun get(wordId: String): WordProgressEntity?

    @Upsert
    suspend fun upsert(progress: WordProgressEntity)

    @Query(
        """
        SELECT
            COALESCE(SUM(CASE WHEN status = 'LEARNED' THEN 1 ELSE 0 END), 0) AS learned,
            COALESCE(SUM(CASE WHEN status = 'LEARNING' OR is_saved = 1 THEN 1 ELSE 0 END), 0) AS toReview,
            COALESCE(SUM(CASE WHEN status = 'LEARNED' AND last_reviewed_at >= :sinceMillis THEN 1 ELSE 0 END), 0)
                AS learnedSince,
            COALESCE(SUM(correct_answers + incorrect_answers), 0) AS reviewed
        FROM word_progress
        """,
    )
    fun observeCounts(sinceMillis: Long): Flow<ProgressCounts>
}

data class ProgressCounts(
    val learned: Int,
    val toReview: Int,
    val learnedSince: Int,
    val reviewed: Int,
)

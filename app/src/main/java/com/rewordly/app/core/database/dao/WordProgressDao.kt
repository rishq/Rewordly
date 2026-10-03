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

    /** Words due before [endOfTodayMillis]. Missing or implausible schedules count as due. */
    @Query(
        """
        SELECT COUNT(*) FROM word_progress
        WHERE status != 'NEW'
          AND (next_review_at IS NULL OR next_review_at < 0
               OR next_review_at > :invalidAfterMillis OR next_review_at < :endOfTodayMillis)
        """,
    )
    suspend fun countDue(endOfTodayMillis: Long, invalidAfterMillis: Long): Int

    @Query(
        """
        SELECT
            COALESCE(SUM(CASE WHEN status = 'LEARNED' THEN 1 ELSE 0 END), 0) AS learned,
            COALESCE(SUM(CASE WHEN status != 'NEW' THEN 1 ELSE 0 END), 0) AS started,
            COALESCE(SUM(CASE WHEN status != 'NEW' AND (next_review_at IS NULL OR next_review_at < 0
                OR next_review_at > :invalidAfterMillis OR next_review_at < :endOfTodayMillis)
                THEN 1 ELSE 0 END), 0) AS due,
            COALESCE(SUM(CASE WHEN repetition_count > 0 AND consecutive_incorrect = 0 THEN 1 ELSE 0 END), 0)
                AS remembered,
            COALESCE(SUM(CASE WHEN consecutive_incorrect > 0 THEN 1 ELSE 0 END), 0) AS forgotten,
            COALESCE(SUM(correct_answers + incorrect_answers), 0) AS reviewed,
            COALESCE(SUM(is_saved), 0) AS saved
        FROM word_progress
        """,
    )
    fun observeCounts(endOfTodayMillis: Long, invalidAfterMillis: Long): Flow<ProgressCounts>
}

data class ProgressCounts(
    val learned: Int,
    val started: Int,
    val due: Int,
    val remembered: Int,
    val forgotten: Int,
    val reviewed: Int,
    val saved: Int,
)

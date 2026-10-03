package com.rewordly.app.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import com.rewordly.app.core.database.entity.DailyActivityEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyActivityDao {
    @Query("SELECT * FROM daily_activity WHERE day >= :fromDay AND day <= :toDay ORDER BY day ASC")
    fun observeRange(fromDay: String, toDay: String): Flow<List<DailyActivityEntity>>

    @Query(
        """
        INSERT INTO daily_activity(day, words_learned, words_reviewed)
        VALUES(:day, :learned, :reviewed)
        ON CONFLICT(day) DO UPDATE SET
            words_learned = words_learned + :learned,
            words_reviewed = words_reviewed + :reviewed
        """,
    )
    suspend fun addActivity(day: String, learned: Int, reviewed: Int)
}

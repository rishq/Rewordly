package com.rewordly.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.rewordly.app.core.database.dao.DailyActivityDao
import com.rewordly.app.core.database.dao.DataTransferDao
import com.rewordly.app.core.database.dao.GenerationHistoryDao
import com.rewordly.app.core.database.dao.ReviewLogDao
import com.rewordly.app.core.database.dao.WordDao
import com.rewordly.app.core.database.dao.WordProgressDao
import com.rewordly.app.core.database.entity.DailyActivityEntity
import com.rewordly.app.core.database.entity.GenerationHistoryEntity
import com.rewordly.app.core.database.entity.ReviewLogEntity
import com.rewordly.app.core.database.entity.WordEntity
import com.rewordly.app.core.database.entity.WordExampleEntity
import com.rewordly.app.core.database.entity.WordProgressEntity

@Database(
    entities = [
        WordEntity::class,
        WordExampleEntity::class,
        WordProgressEntity::class,
        DailyActivityEntity::class,
        ReviewLogEntity::class,
        GenerationHistoryEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class RewordlyDatabase : RoomDatabase() {
    abstract fun wordDao(): WordDao

    abstract fun wordProgressDao(): WordProgressDao

    abstract fun dailyActivityDao(): DailyActivityDao

    abstract fun reviewLogDao(): ReviewLogDao

    abstract fun generationHistoryDao(): GenerationHistoryDao

    abstract fun dataTransferDao(): DataTransferDao

    companion object {
        const val NAME = "rewordly.db"
    }
}

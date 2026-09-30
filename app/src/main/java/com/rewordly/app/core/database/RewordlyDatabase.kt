package com.rewordly.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.rewordly.app.core.database.dao.WordDao
import com.rewordly.app.core.database.dao.WordProgressDao
import com.rewordly.app.core.database.entity.WordEntity
import com.rewordly.app.core.database.entity.WordExampleEntity
import com.rewordly.app.core.database.entity.WordProgressEntity

@Database(
    entities = [WordEntity::class, WordExampleEntity::class, WordProgressEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class RewordlyDatabase : RoomDatabase() {
    abstract fun wordDao(): WordDao

    abstract fun wordProgressDao(): WordProgressDao

    companion object {
        const val NAME = "rewordly.db"
    }
}

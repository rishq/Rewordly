package com.rewordly.app.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** One row per calendar day on which at least one learning action happened. ISO-8601 [day] key. */
@Entity(tableName = "daily_activity")
data class DailyActivityEntity(
    @PrimaryKey @ColumnInfo(name = "day") val day: String,
    @ColumnInfo(name = "words_learned") val wordsLearned: Int,
    @ColumnInfo(name = "words_reviewed") val wordsReviewed: Int,
)

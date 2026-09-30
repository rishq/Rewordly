package com.rewordly.app.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "word_examples",
    foreignKeys = [
        ForeignKey(
            entity = WordEntity::class,
            parentColumns = ["id"],
            childColumns = ["word_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["word_id"])],
)
data class WordExampleEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "word_id") val wordId: String,
    val text: String,
    val translation: String,
    val position: Int,
)

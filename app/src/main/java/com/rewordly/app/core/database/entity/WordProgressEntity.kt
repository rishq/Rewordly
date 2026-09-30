package com.rewordly.app.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "word_progress",
    foreignKeys = [
        ForeignKey(
            entity = WordEntity::class,
            parentColumns = ["id"],
            childColumns = ["word_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["status"])],
)
data class WordProgressEntity(
    @PrimaryKey @ColumnInfo(name = "word_id") val wordId: String,
    val status: String,
    @ColumnInfo(name = "is_saved") val isSaved: Boolean,
    @ColumnInfo(name = "correct_answers") val correctAnswers: Int,
    @ColumnInfo(name = "incorrect_answers") val incorrectAnswers: Int,
    @ColumnInfo(name = "last_reviewed_at") val lastReviewedAt: Long?,
    @ColumnInfo(name = "next_review_at") val nextReviewAt: Long?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

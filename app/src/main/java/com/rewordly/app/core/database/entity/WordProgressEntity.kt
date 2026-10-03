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
    indices = [Index(value = ["status"]), Index(value = ["is_saved"]), Index(value = ["next_review_at"])],
)
data class WordProgressEntity(
    @PrimaryKey @ColumnInfo(name = "word_id") val wordId: String,
    val status: String,
    @ColumnInfo(name = "is_saved") val isSaved: Boolean,
    @ColumnInfo(name = "views", defaultValue = "0") val views: Int,
    @ColumnInfo(name = "correct_answers") val correctAnswers: Int,
    @ColumnInfo(name = "incorrect_answers") val incorrectAnswers: Int,
    @ColumnInfo(name = "last_viewed_at") val lastViewedAt: Long?,
    @ColumnInfo(name = "last_reviewed_at") val lastReviewedAt: Long?,
    @ColumnInfo(name = "next_review_at") val nextReviewAt: Long?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "repetition_count", defaultValue = "0") val repetitionCount: Int = 0,
    @ColumnInfo(name = "ease_factor", defaultValue = "2.5") val easeFactor: Double = 2.5,
    @ColumnInfo(name = "interval_days", defaultValue = "0") val intervalDays: Int = 0,
    @ColumnInfo(name = "consecutive_correct", defaultValue = "0") val consecutiveCorrect: Int = 0,
    @ColumnInfo(name = "consecutive_incorrect", defaultValue = "0") val consecutiveIncorrect: Int = 0,
)

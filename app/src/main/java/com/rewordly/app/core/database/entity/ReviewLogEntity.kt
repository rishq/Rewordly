package com.rewordly.app.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Append-only history of learning actions. [reviewedAt] is UTC epoch millis; local days are derived on read.
 * The unique (session, word, kind) index makes a repeated submission a no-op.
 */
@Entity(
    tableName = "review_log",
    foreignKeys = [
        ForeignKey(
            entity = WordEntity::class,
            parentColumns = ["id"],
            childColumns = ["word_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["session_id", "word_id", "kind"], unique = true),
        Index(value = ["reviewed_at"]),
        Index(value = ["word_id"]),
    ],
)
data class ReviewLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "word_id") val wordId: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    val kind: String,
    val quality: Int,
    @ColumnInfo(name = "reviewed_at") val reviewedAt: Long,
    @ColumnInfo(name = "duration_ms") val durationMs: Long?,
    @ColumnInfo(name = "interval_before") val intervalBefore: Int,
    @ColumnInfo(name = "interval_after") val intervalAfter: Int,
)

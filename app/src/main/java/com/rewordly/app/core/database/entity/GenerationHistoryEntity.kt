package com.rewordly.app.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One AI generation. [description] is a topic, a word or a neutral summary of pasted text; the pasted text itself
 * is never stored. [resultJson] keeps the generated items so the preview can be reopened, and is cleared on delete.
 */
@Entity(tableName = "generation_history", indices = [Index(value = ["created_at"])])
data class GenerationHistoryEntity(
    @PrimaryKey val id: String,
    val mode: String,
    val description: String,
    val level: String,
    @ColumnInfo(name = "requested_count") val requestedCount: Int,
    @ColumnInfo(name = "result_count") val resultCount: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "result_json") val resultJson: String?,
)

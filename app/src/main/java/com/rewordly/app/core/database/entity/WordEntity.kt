package com.rewordly.app.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "words",
    indices = [Index(value = ["language", "text"])],
)
data class WordEntity(
    @PrimaryKey val id: String,
    val language: String,
    val text: String,
    val translation: String,
    @ColumnInfo(name = "translation_language") val translationLanguage: String,
    val pronunciation: String,
    @ColumnInfo(name = "part_of_speech") val partOfSpeech: String,
    val difficulty: String,
    @ColumnInfo(name = "definition_en", defaultValue = "''") val definition: String,
    @ColumnInfo(name = "definition_ru", defaultValue = "''") val definitionTranslation: String,
    val forms: List<String>,
    @ColumnInfo(name = "related_words") val relatedWords: List<String>,
    val synonyms: List<String>,
    @ColumnInfo(name = "audio_url") val audioUrl: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "source", defaultValue = "BUNDLED") val source: String = "BUNDLED",
    /** Comma-separated topic names from an imported file; empty when the word was not imported. */
    @ColumnInfo(name = "topic", defaultValue = "''") val topic: String = "",
)

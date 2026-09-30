package com.rewordly.app.core.database.entity

import androidx.room.Embedded
import androidx.room.Relation

/** A word with its examples and (optional) progress row, loaded in one Room transaction. */
data class PopulatedWord(
    @Embedded val word: WordEntity,
    @Relation(parentColumn = "id", entityColumn = "word_id")
    val examples: List<WordExampleEntity>,
    @Relation(parentColumn = "id", entityColumn = "word_id")
    val progress: WordProgressEntity?,
)

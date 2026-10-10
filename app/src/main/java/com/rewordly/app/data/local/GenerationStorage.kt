package com.rewordly.app.data.local

import com.rewordly.app.core.database.dao.GenerationHistorySummary
import com.rewordly.app.core.database.entity.GenerationHistoryEntity
import com.rewordly.app.domain.model.Difficulty
import com.rewordly.app.domain.model.GeneratedExample
import com.rewordly.app.domain.model.GeneratedWord
import com.rewordly.app.domain.model.GenerationHistoryEntry
import com.rewordly.app.domain.model.GenerationMode
import com.rewordly.app.domain.model.PartOfSpeech
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Storage shape of a generated word inside a history row. Kept apart from network DTOs on purpose. */
@Serializable
data class StoredWord(
    val word: String,
    val translation: String,
    val pronunciation: String = "",
    val partOfSpeech: String = PartOfSpeech.PHRASE.name,
    val difficulty: String = Difficulty.B1.name,
    val definition: String = "",
    val definitionTranslation: String = "",
    val examples: List<StoredExample> = emptyList(),
    val synonyms: List<String> = emptyList(),
    val relatedWords: List<String> = emptyList(),
)

@Serializable
data class StoredExample(val english: String, val russian: String)

fun GeneratedWord.toStored() = StoredWord(
    word = word,
    translation = translation,
    pronunciation = pronunciation,
    partOfSpeech = partOfSpeech.name,
    difficulty = difficulty.name,
    definition = definition,
    definitionTranslation = definitionTranslation,
    examples = examples.map { StoredExample(it.english, it.russian) },
    synonyms = synonyms,
    relatedWords = relatedWords,
)

fun StoredWord.toDomain() = GeneratedWord(
    word = word,
    translation = translation,
    pronunciation = pronunciation,
    partOfSpeech = PartOfSpeech.entries.firstOrNull { it.name == partOfSpeech } ?: PartOfSpeech.PHRASE,
    difficulty = Difficulty.entries.firstOrNull { it.name == difficulty } ?: Difficulty.B1,
    definition = definition,
    definitionTranslation = definitionTranslation,
    examples = examples.map { GeneratedExample(it.english, it.russian) },
    synonyms = synonyms,
    relatedWords = relatedWords,
)

fun List<GeneratedWord>.encodeToJson(json: Json): String =
    json.encodeToString(kotlinx.serialization.builtins.ListSerializer(StoredWord.serializer()), map { it.toStored() })

/** Unreadable or missing JSON simply yields no items: a damaged history row must never crash the screen. */
fun String?.decodeGeneratedWords(json: Json): List<GeneratedWord> {
    if (this == null) return emptyList()
    return try {
        json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(StoredWord.serializer()), this)
            .map { it.toDomain() }
    } catch (e: kotlinx.serialization.SerializationException) {
        emptyList()
    } catch (e: IllegalArgumentException) {
        emptyList()
    }
}

fun GenerationHistoryEntity.toDomain() = GenerationHistoryEntry(
    id = id,
    mode = GenerationMode.entries.firstOrNull { it.name == mode } ?: GenerationMode.TOPIC,
    description = description,
    level = Difficulty.entries.firstOrNull { it.name == level } ?: Difficulty.B1,
    requestedCount = requestedCount,
    resultCount = resultCount,
    createdAt = createdAt,
    hasResult = resultJson != null,
)

/** The list projection carries the same fields minus the payload, so it maps the same way. */
fun GenerationHistorySummary.toDomain() = GenerationHistoryEntry(
    id = id,
    mode = GenerationMode.entries.firstOrNull { it.name == mode } ?: GenerationMode.TOPIC,
    description = description,
    level = Difficulty.entries.firstOrNull { it.name == level } ?: Difficulty.B1,
    requestedCount = requestedCount,
    resultCount = resultCount,
    createdAt = createdAt,
    hasResult = hasResult,
)

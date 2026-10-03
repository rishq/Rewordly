package com.rewordly.app.domain.service

import com.rewordly.app.domain.model.BackupDecodeResult
import com.rewordly.app.domain.model.BackupIssue
import com.rewordly.app.domain.model.BackupLimits
import com.rewordly.app.domain.model.BackupWord
import com.rewordly.app.domain.model.VocabularyBackup
import com.rewordly.app.domain.model.WordWithProgress
import javax.inject.Inject
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Writes the vocabulary out as CSV or JSON, and reads a full backup back in.
 *
 * The JSON export deliberately uses the same shape the importer accepts, so a file that was exported
 * can be imported again unchanged. CSV is flat: several examples are joined with `" | "` inside the
 * `example` column, which is exactly what the importer splits on.
 */
class VocabularyExporter @Inject constructor(private val json: Json) {

    /** One row per word, header first. An empty vocabulary still produces a valid, header-only file. */
    fun exportCsv(words: List<WordWithProgress>): String {
        val rows = buildList {
            add(CSV_HEADER)
            words.forEach { item ->
                add(
                    listOf(
                        item.word.text,
                        item.word.translation.text,
                        item.word.partOfSpeech.name,
                        item.word.definition,
                        item.word.definitionTranslation,
                        item.word.examples.joinToString(EXAMPLE_SEPARATOR) { it.text },
                        item.word.examples.joinToString(EXAMPLE_SEPARATOR) { it.translation },
                        item.word.topic,
                        item.word.difficulty.name,
                    ),
                )
            }
        }
        return CsvCodec.write(rows)
    }

    fun exportJson(words: List<WordWithProgress>): String = json.encodeToString(
        ImportJsonFile.serializer(),
        ImportJsonFile(
            version = EXPORT_VERSION,
            words = words.map { item ->
                ImportJsonWord(
                    word = item.word.text,
                    translation = item.word.translation.text,
                    partOfSpeech = item.word.partOfSpeech.name,
                    definition = item.word.definition,
                    definitionTranslation = item.word.definitionTranslation,
                    topic = item.word.topic,
                    difficulty = item.word.difficulty.name,
                    examples = item.word.examples.map { ImportJsonExample(it.text, it.translation) },
                )
            },
        ),
    )

    private companion object {
        const val EXPORT_VERSION = 1
        const val EXAMPLE_SEPARATOR = " | "

        val CSV_HEADER = listOf(
            "word",
            "translation",
            "part_of_speech",
            "definition",
            "definition_translation",
            "example",
            "example_translation",
            "topic",
            "difficulty",
        )
    }
}

/** Encodes and validates full backups. */
class BackupCodec @Inject constructor(private val json: Json) {

    fun encode(backup: VocabularyBackup): String = json.encodeToString(VocabularyBackup.serializer(), backup)

    /**
     * Reads a backup and refuses anything that is not clearly one. The checks run before the caller
     * touches the database, so a corrupt, newer or oversized file can never leave a half-restored state.
     *
     * [limits] bounds what the file may contain. The entry count is checked against the raw array before the
     * document is decoded, so an oversized file is refused without paying to parse it first.
     */
    fun decode(content: String, limits: BackupLimits = BackupLimits()): BackupDecodeResult {
        val trimmed = content.removePrefix(BOM).trim()
        if (trimmed.isEmpty()) return BackupDecodeResult.Invalid(BackupIssue.UNREADABLE)
        val element = try {
            json.parseToJsonElement(trimmed)
        } catch (e: SerializationException) {
            return BackupDecodeResult.Invalid(BackupIssue.UNREADABLE)
        } catch (e: IllegalArgumentException) {
            return BackupDecodeResult.Invalid(BackupIssue.UNREADABLE)
        }
        val root = element as? JsonObject ?: return BackupDecodeResult.Invalid(BackupIssue.NOT_A_BACKUP)
        val version = (root[SCHEMA_VERSION_KEY] as? JsonPrimitive)?.content?.toIntOrNull()
            ?: return BackupDecodeResult.Invalid(BackupIssue.NOT_A_BACKUP)
        if (version <= 0 || version > VocabularyBackup.CURRENT_SCHEMA_VERSION) {
            return BackupDecodeResult.Invalid(BackupIssue.UNSUPPORTED_VERSION)
        }
        val words = root[WORDS_KEY] as? JsonArray ?: return BackupDecodeResult.Invalid(BackupIssue.NOT_A_BACKUP)
        if (words.size > limits.maxWords) return BackupDecodeResult.Invalid(BackupIssue.TOO_LARGE)
        val backup = try {
            json.decodeFromJsonElement(VocabularyBackup.serializer(), root)
        } catch (e: SerializationException) {
            return BackupDecodeResult.Invalid(BackupIssue.UNREADABLE)
        } catch (e: IllegalArgumentException) {
            return BackupDecodeResult.Invalid(BackupIssue.UNREADABLE)
        }
        if (backup.words.isEmpty()) return BackupDecodeResult.Invalid(BackupIssue.EMPTY)
        if (backup.words.any { it.hasFieldLongerThan(limits.maxFieldLength) }) {
            return BackupDecodeResult.Invalid(BackupIssue.TOO_LARGE)
        }
        return BackupDecodeResult.Valid(backup)
    }

    /** True when any text this word would store is longer than [max]. */
    private fun BackupWord.hasFieldLongerThan(max: Int): Boolean = text.length > max ||
        translation.length > max ||
        partOfSpeech.length > max ||
        difficulty.length > max ||
        definition.length > max ||
        definitionTranslation.length > max ||
        topic.length > max ||
        examples.any { it.text.length > max || it.translation.length > max }

    private companion object {
        const val BOM = "\uFEFF"
        const val SCHEMA_VERSION_KEY = "schemaVersion"
        const val WORDS_KEY = "words"
    }
}

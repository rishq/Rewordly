package com.rewordly.app.domain.service

import com.rewordly.app.domain.model.ImportIssue
import com.rewordly.app.domain.model.ImportIssueReason
import com.rewordly.app.domain.model.ParsedEntry
import com.rewordly.app.domain.model.VocabularyEntry
import com.rewordly.app.domain.model.VocabularyEntryExample
import com.rewordly.app.domain.model.VocabularyFileFormat
import javax.inject.Inject
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** Limits that keep a hostile or simply huge file from exhausting memory. */
data class ImportLimits(
    val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    val maxFieldLength: Int = DEFAULT_MAX_FIELD_LENGTH,
) {
    companion object {
        const val DEFAULT_MAX_ENTRIES = 2_000
        const val DEFAULT_MAX_FIELD_LENGTH = 500
    }
}

/** Entries that passed validation, plus the reason every rejected row was rejected. */
data class ParsedVocabularyFile(
    val format: VocabularyFileFormat,
    val entries: List<ParsedEntry>,
    val issues: List<ImportIssue>,
    val truncated: Boolean,
)

/**
 * Reads CSV and JSON vocabulary files into [VocabularyEntry] values.
 *
 * Everything is validated before it reaches the database, and every failure is reported as a row
 * number plus a reason instead of an exception, so the preview can show exactly what was wrong.
 * The parser never throws for malformed input: an unreadable file simply yields one issue.
 */
class VocabularyImportParser @Inject constructor(private val json: Json) {

    /**
     * Picks the format from the file content first, and only falls back to the file name. A `.csv`
     * file that actually contains JSON is therefore still handled correctly.
     */
    fun detectFormat(fileName: String, content: String): VocabularyFileFormat =
        sniff(content) ?: byExtension(fileName) ?: VocabularyFileFormat.CSV

    fun parse(
        content: String,
        format: VocabularyFileFormat,
        limits: ImportLimits = ImportLimits(),
    ): ParsedVocabularyFile = when (format) {
        VocabularyFileFormat.CSV -> parseCsv(content, limits)
        VocabularyFileFormat.JSON -> parseJson(content, limits)
    }

    // ------------------------------------------------------------------ CSV

    private fun parseCsv(content: String, limits: ImportLimits): ParsedVocabularyFile {
        val rows = CsvCodec.parse(content)
        if (rows.isEmpty()) return unusable(VocabularyFileFormat.CSV)
        val header = headerIndex(rows.first())
        val dataRows = if (header == null) rows else rows.drop(1)
        val firstLine = if (header == null) 1 else 2
        val entries = mutableListOf<ParsedEntry>()
        val issues = mutableListOf<ImportIssue>()
        var truncated = false
        dataRows.forEachIndexed { offset, row ->
            if (entries.size >= limits.maxEntries) {
                truncated = true
                return@forEachIndexed
            }
            when (val result = csvEntry(row, header, limits)) {
                is RowResult.Entry -> entries += ParsedEntry(firstLine + offset, result.entry)
                is RowResult.Problem -> issues += ImportIssue(firstLine + offset, result.reason)
            }
        }
        return ParsedVocabularyFile(VocabularyFileFormat.CSV, entries, issues, truncated)
    }

    private fun csvEntry(row: List<String>, header: Map<CsvColumn, Int>?, limits: ImportLimits): RowResult {
        fun value(column: CsvColumn, fallbackIndex: Int): String {
            val index = if (header == null) fallbackIndex else header[column] ?: return ""
            return row.getOrNull(index)?.trim().orEmpty()
        }
        val text = value(CsvColumn.WORD, 0)
        val translation = value(CsvColumn.TRANSLATION, 1)
        if (text.isEmpty()) return RowResult.Problem(ImportIssueReason.MISSING_WORD)
        if (translation.isEmpty()) return RowResult.Problem(ImportIssueReason.MISSING_TRANSLATION)
        if (text.length > limits.maxFieldLength || translation.length > limits.maxFieldLength) {
            return RowResult.Problem(ImportIssueReason.TOO_LONG)
        }
        val examples = pairExamples(value(CsvColumn.EXAMPLE, 5), value(CsvColumn.EXAMPLE_TRANSLATION, 6))
        return RowResult.Entry(
            VocabularyEntry(
                text = text,
                translation = translation,
                partOfSpeech = value(CsvColumn.PART_OF_SPEECH, 2),
                definition = value(CsvColumn.DEFINITION, 3),
                definitionTranslation = value(CsvColumn.DEFINITION_TRANSLATION, 4),
                examples = examples,
                topic = value(CsvColumn.TOPIC, 7),
                difficulty = value(CsvColumn.DIFFICULTY, 8),
            ),
        )
    }

    /** Reads the first row as a header when it names at least one known column. */
    private fun headerIndex(row: List<String>): Map<CsvColumn, Int>? {
        val found = mutableMapOf<CsvColumn, Int>()
        row.forEachIndexed { index, cell ->
            val column = COLUMN_ALIASES[normalizeHeader(cell)] ?: return@forEachIndexed
            found.putIfAbsent(column, index)
        }
        val usable = found.containsKey(CsvColumn.WORD) || found.containsKey(CsvColumn.TRANSLATION)
        return if (usable) found else null
    }

    // ----------------------------------------------------------------- JSON

    private fun parseJson(content: String, limits: ImportLimits): ParsedVocabularyFile {
        val trimmed = content.removePrefix(BOM).trim()
        if (trimmed.isEmpty()) return unusable(VocabularyFileFormat.JSON)
        val element = try {
            json.parseToJsonElement(trimmed)
        } catch (e: SerializationException) {
            return unusable(VocabularyFileFormat.JSON)
        } catch (e: IllegalArgumentException) {
            return unusable(VocabularyFileFormat.JSON)
        }
        val file = when (element) {
            is JsonArray -> ImportJsonFile(words = decodeWords(element))
            is JsonObject -> {
                if (element[WORDS_KEY] !is JsonArray) return unusable(VocabularyFileFormat.JSON)
                decodeFile(element)
            }
            else -> return unusable(VocabularyFileFormat.JSON)
        } ?: return unusable(VocabularyFileFormat.JSON)

        if (file.version != null && file.version > SUPPORTED_IMPORT_VERSION) {
            return ParsedVocabularyFile(
                VocabularyFileFormat.JSON,
                emptyList(),
                listOf(ImportIssue(0, ImportIssueReason.UNSUPPORTED_VERSION)),
                false,
            )
        }

        val entries = mutableListOf<ParsedEntry>()
        val issues = mutableListOf<ImportIssue>()
        var truncated = false
        file.words.forEachIndexed { index, word ->
            if (entries.size >= limits.maxEntries) {
                truncated = true
                return@forEachIndexed
            }
            when (val result = jsonEntry(word, limits)) {
                is RowResult.Entry -> entries += ParsedEntry(index + 1, result.entry)
                is RowResult.Problem -> issues += ImportIssue(index + 1, result.reason)
            }
        }
        return ParsedVocabularyFile(VocabularyFileFormat.JSON, entries, issues, truncated)
    }

    private fun decodeWords(array: JsonArray): List<ImportJsonWord> = try {
        json.decodeFromJsonElement(ListSerializer(ImportJsonWord.serializer()), array)
    } catch (e: SerializationException) {
        emptyList()
    } catch (e: IllegalArgumentException) {
        emptyList()
    }

    private fun decodeFile(element: JsonObject): ImportJsonFile? = try {
        json.decodeFromJsonElement(ImportJsonFile.serializer(), element)
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    private fun jsonEntry(word: ImportJsonWord, limits: ImportLimits): RowResult {
        val text = word.word.trim()
        val translation = word.translation.trim()
        if (text.isEmpty()) return RowResult.Problem(ImportIssueReason.MISSING_WORD)
        if (translation.isEmpty()) return RowResult.Problem(ImportIssueReason.MISSING_TRANSLATION)
        if (text.length > limits.maxFieldLength || translation.length > limits.maxFieldLength) {
            return RowResult.Problem(ImportIssueReason.TOO_LONG)
        }
        val examples = word.examples
            .map { VocabularyEntryExample(it.english.trim(), it.russian.trim()) }
            .filter { it.english.isNotEmpty() }
        return RowResult.Entry(
            VocabularyEntry(
                text = text,
                translation = translation,
                partOfSpeech = word.partOfSpeech.trim(),
                definition = word.definition.trim(),
                definitionTranslation = word.definitionTranslation.trim(),
                examples = examples,
                topic = word.topic.trim(),
                difficulty = word.difficulty.trim(),
            ),
        )
    }

    // -------------------------------------------------------------- helpers

    private fun pairExamples(english: String, russian: String): List<VocabularyEntryExample> {
        val sentences = english.split(EXAMPLE_SEPARATOR)
        val translations = russian.split(EXAMPLE_SEPARATOR)
        return sentences.mapIndexedNotNull { index, sentence ->
            val trimmed = sentence.trim()
            if (trimmed.isEmpty()) {
                null
            } else {
                VocabularyEntryExample(
                    trimmed,
                    translations.getOrNull(index)?.trim().orEmpty(),
                )
            }
        }
    }

    private fun unusable(format: VocabularyFileFormat) = ParsedVocabularyFile(
        format = format,
        entries = emptyList(),
        issues = listOf(ImportIssue(0, ImportIssueReason.INVALID_FORMAT)),
        truncated = false,
    )

    private fun sniff(content: String): VocabularyFileFormat? {
        val trimmed = content.removePrefix(BOM).trimStart()
        if (trimmed.isEmpty()) return null
        val looksLikeJson = trimmed.startsWith("{") || trimmed.startsWith("[")
        return if (looksLikeJson) VocabularyFileFormat.JSON else VocabularyFileFormat.CSV
    }

    private fun byExtension(fileName: String): VocabularyFileFormat? =
        when (fileName.substringAfterLast('.', "").lowercase()) {
            "json" -> VocabularyFileFormat.JSON
            "csv", "txt" -> VocabularyFileFormat.CSV
            else -> null
        }

    private fun normalizeHeader(cell: String): String = cell.trim().lowercase().replace(' ', '_').replace('-', '_')

    private sealed interface RowResult {
        data class Entry(val entry: VocabularyEntry) : RowResult

        data class Problem(val reason: ImportIssueReason) : RowResult
    }

    private enum class CsvColumn {
        WORD,
        TRANSLATION,
        PART_OF_SPEECH,
        DEFINITION,
        DEFINITION_TRANSLATION,
        EXAMPLE,
        EXAMPLE_TRANSLATION,
        TOPIC,
        DIFFICULTY,
    }

    private companion object {
        const val SUPPORTED_IMPORT_VERSION = 1
        const val BOM = "\uFEFF"
        const val EXAMPLE_SEPARATOR = " | "
        const val WORDS_KEY = "words"

        val COLUMN_ALIASES: Map<String, CsvColumn> = buildMap {
            fun aliases(column: CsvColumn, vararg names: String) = names.forEach { put(it, column) }
            aliases(CsvColumn.WORD, "word", "english", "term", "слово", "английский")
            aliases(CsvColumn.TRANSLATION, "translation", "russian", "перевод", "значение")
            aliases(CsvColumn.PART_OF_SPEECH, "part_of_speech", "partofspeech", "pos", "часть_речи")
            aliases(CsvColumn.DEFINITION, "definition", "definition_en", "определение")
            aliases(CsvColumn.DEFINITION_TRANSLATION, "definition_translation", "definition_ru", "перевод_определения")
            aliases(CsvColumn.EXAMPLE, "example", "example_en", "sentence", "пример")
            aliases(CsvColumn.EXAMPLE_TRANSLATION, "example_translation", "example_ru", "перевод_примера")
            aliases(CsvColumn.TOPIC, "topic", "category", "тема")
            aliases(CsvColumn.DIFFICULTY, "difficulty", "level", "уровень")
        }
    }
}

/** Storage shape of one imported JSON word. Unknown keys are ignored. */
@Serializable
internal data class ImportJsonWord(
    val word: String = "",
    val translation: String = "",
    val partOfSpeech: String = "",
    val definition: String = "",
    val definitionTranslation: String = "",
    val topic: String = "",
    val difficulty: String = "",
    val examples: List<ImportJsonExample> = emptyList(),
)

@Serializable
internal data class ImportJsonExample(val english: String = "", val russian: String = "")

/**
 * The document shape shared by the importer and the JSON exporter.
 *
 * `words` has no default value on purpose: the app writes files with `encodeDefaults = false`, so a
 * defaulted empty list would be dropped entirely when the vocabulary is empty, and a file that the
 * app itself exported would no longer be recognisable as a vocabulary file.
 */
@Serializable
internal data class ImportJsonFile(val version: Int? = null, val words: List<ImportJsonWord>)

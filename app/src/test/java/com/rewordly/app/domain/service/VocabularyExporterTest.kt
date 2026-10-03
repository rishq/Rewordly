package com.rewordly.app.domain.service

import com.rewordly.app.domain.model.Difficulty
import com.rewordly.app.domain.model.LearningLanguage
import com.rewordly.app.domain.model.PartOfSpeech
import com.rewordly.app.domain.model.VocabularyFileFormat
import com.rewordly.app.domain.model.Word
import com.rewordly.app.domain.model.WordExample
import com.rewordly.app.domain.model.WordProgress
import com.rewordly.app.domain.model.WordTranslation
import com.rewordly.app.domain.model.WordWithProgress
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A file the app exports has to be importable again unchanged, so the CSV and JSON writers are tested
 * through the reader rather than field by field.
 */
class VocabularyExporterTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
    private val exporter = VocabularyExporter(json)
    private val parser = VocabularyImportParser(json)

    private fun word(
        text: String,
        translation: String,
        topic: String = "",
        examples: List<Pair<String, String>> = emptyList(),
    ): WordWithProgress = WordWithProgress(
        word = Word(
            id = "id-$text",
            language = LearningLanguage.ENGLISH,
            text = text,
            translation = WordTranslation("ru", translation),
            pronunciation = "",
            partOfSpeech = PartOfSpeech.NOUN,
            difficulty = Difficulty.A1,
            definition = "definition of $text",
            definitionTranslation = "определение $text",
            examples = examples.mapIndexed { index, (english, russian) ->
                WordExample(id = "example-$text-$index", wordId = "id-$text", text = english, translation = russian)
            },
            topic = topic,
        ),
        progress = WordProgress(wordId = "id-$text"),
    )

    // ------------------------------------------------------------------- CSV

    @Test
    fun exportCsv_writesAHeaderAndOneRowPerWord() {
        val csv = exporter.exportCsv(listOf(word("apple", "яблоко", topic = "food")))

        val rows = CsvCodec.parse(csv)
        assertEquals("word", rows.first().first())
        assertEquals("topic", rows.first()[7])
        assertEquals("apple", rows[1][0])
        assertEquals("яблоко", rows[1][1])
        assertEquals("food", rows[1][7])
    }

    @Test
    fun exportCsv_quotesSentencesThatContainCommas() {
        val csv = exporter.exportCsv(
            listOf(word("hold on", "держаться", examples = listOf("Hold on, please." to "Подожди, пожалуйста."))),
        )

        val entry = parser.parse(csv, VocabularyFileFormat.CSV).entries.single().entry
        assertEquals("Hold on, please.", entry.examples.single().english)
        assertEquals("Подожди, пожалуйста.", entry.examples.single().russian)
    }

    @Test
    fun exportCsv_ofAnEmptyVocabulary_isStillAValidFile() {
        val csv = exporter.exportCsv(emptyList())

        val rows = CsvCodec.parse(csv)
        assertEquals(1, rows.size)
        assertEquals(9, rows.single().size)
    }

    /**
     * The word column is the first thing a spreadsheet reads, so a word that looks like a formula has to be
     * defused on the way out - and the app's own importer has to give back exactly what was exported.
     */
    @Test
    fun exportCsv_defusesAWordThatLooksLikeAFormula_andCanBeImportedBack() {
        val payload = "=cmd|'/C calc'!A0"
        val csv = exporter.exportCsv(listOf(word(payload, "команда")))

        val row = csv.lineSequence().drop(1).first()
        assertFalse("the word was written as a live formula: $row", row.startsWith("="))
        assertEquals(payload, parser.parse(csv, VocabularyFileFormat.CSV).entries.single().entry.text)
    }

    // ------------------------------------------------------------------ JSON

    @Test
    fun exportJson_canBeImportedAgainUnchanged() {
        val words = listOf(
            word("apple", "яблоко", topic = "food", examples = listOf("I ate an apple." to "Я съел яблоко.")),
            word("run", "бежать"),
        )

        val exported = exporter.exportJson(words)
        val parsed = parser.parse(exported, VocabularyFileFormat.JSON)

        assertEquals(2, parsed.entries.size)
        assertTrue(parsed.issues.isEmpty())
        val apple = parsed.entries.first().entry
        assertEquals("apple", apple.text)
        assertEquals("яблоко", apple.translation)
        assertEquals("food", apple.topic)
        assertEquals("I ate an apple.", apple.examples.single().english)
        assertEquals("Я съел яблоко.", apple.examples.single().russian)
    }

    @Test
    fun exportJson_ofAnEmptyVocabulary_isAnEmptyButValidDocument() {
        val exported = exporter.exportJson(emptyList())

        val parsed = parser.parse(exported, VocabularyFileFormat.JSON)
        assertTrue(parsed.entries.isEmpty())
        assertTrue(parsed.issues.isEmpty())
        assertTrue(exported.contains("\"words\""))
    }
}

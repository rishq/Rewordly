package com.rewordly.app.domain.service

import com.rewordly.app.domain.model.ImportIssueReason
import com.rewordly.app.domain.model.VocabularyFileFormat
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parser is the only thing standing between an untrusted file and the database, so it is tested
 * for the happy paths *and* for everything that must be refused instead of half-imported.
 */
class VocabularyImportParserTest {
    private val parser = VocabularyImportParser(
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        },
    )

    private fun parseCsv(content: String, limits: ImportLimits = ImportLimits()) =
        parser.parse(content, VocabularyFileFormat.CSV, limits)

    private fun parseJson(content: String, limits: ImportLimits = ImportLimits()) =
        parser.parse(content, VocabularyFileFormat.JSON, limits)

    /** Builds a CSV document from rows, so a test can keep every row on its own line. */
    private fun csv(vararg rows: String): String = rows.joinToString("\n") + "\n"

    // ------------------------------------------------------------------- CSV

    @Test
    fun csv_mapsEveryDocumentedColumn() {
        val content = """
            word,translation,part_of_speech,definition,definition_translation,example,example_translation,topic,difficulty
            apple,яблоко,NOUN,a round fruit,круглый фрукт,I ate an apple.,Я съел яблоко.,food,A1
        """.trimIndent()

        val parsed = parseCsv(content)
        val entry = parsed.entries.single().entry

        assertEquals("apple", entry.text)
        assertEquals("яблоко", entry.translation)
        assertEquals("NOUN", entry.partOfSpeech)
        assertEquals("a round fruit", entry.definition)
        assertEquals("круглый фрукт", entry.definitionTranslation)
        assertEquals("food", entry.topic)
        assertEquals("A1", entry.difficulty)
        assertEquals("I ate an apple.", entry.examples.single().english)
        assertEquals("Я съел яблоко.", entry.examples.single().russian)
        assertTrue(parsed.issues.isEmpty())
    }

    @Test
    fun csv_keepsCommasInsideQuotedSentences() {
        val quoted = csv(
            "word,translation,example,example_translation",
            "\"hold on\",держаться,\"Hold on, please.\",\"Подожди, пожалуйста.\"",
        )
        val unquoted = csv(
            "word,translation,example,example_translation",
            "\"hold on\",держаться,\"Hold on, please.\",Подожди пожалуйста.",
        )

        val fromQuoted = parseCsv(quoted).entries.single().entry
        val fromUnquoted = parseCsv(unquoted).entries.single().entry

        assertEquals("hold on", fromQuoted.text)
        assertEquals("Hold on, please.", fromQuoted.examples.single().english)
        assertEquals("Подожди, пожалуйста.", fromQuoted.examples.single().russian)
        assertEquals("Подожди пожалуйста.", fromUnquoted.examples.single().russian)
    }

    @Test
    fun csv_withoutAHeader_usesTheDocumentedColumnOrder() {
        val content = "apple,яблоко,NOUN,a fruit,фрукт,I ate an apple.,Я съел яблоко.,food,A1\n"

        val entry = parseCsv(content).entries.single().entry

        assertEquals("apple", entry.text)
        assertEquals("яблоко", entry.translation)
        assertEquals("A1", entry.difficulty)
    }

    @Test
    fun csv_acceptsRussianHeaderNames() {
        val content = "слово,перевод,часть_речи,пример\napple,яблоко,NOUN,I ate an apple.\n"

        val entry = parseCsv(content).entries.single().entry

        assertEquals("apple", entry.text)
        assertEquals("яблоко", entry.translation)
        assertEquals("NOUN", entry.partOfSpeech)
        assertEquals("I ate an apple.", entry.examples.single().english)
    }

    @Test
    fun csv_reportsTheRowNumberOfEverySkippedLine() {
        val content = "word,translation\napple,яблоко\n,нет слова\npear,\n"

        val parsed = parseCsv(content)

        assertEquals(1, parsed.entries.size)
        assertEquals(
            listOf(3 to ImportIssueReason.MISSING_WORD, 4 to ImportIssueReason.MISSING_TRANSLATION),
            parsed.issues.map { it.row to it.reason },
        )
    }

    @Test
    fun csv_refusesAValueThatIsTooLong() {
        val content = "word,translation\napple,${"я".repeat(20)}\n"

        val parsed = parseCsv(content, ImportLimits(maxEntries = 10, maxFieldLength = 5))

        assertTrue(parsed.entries.isEmpty())
        assertEquals(ImportIssueReason.TOO_LONG, parsed.issues.single().reason)
    }

    @Test
    fun csv_stopsAtTheEntryLimit_andSaysSo() {
        val content = buildString {
            append("word,translation\n")
            repeat(5) { index -> append("word$index,перевод$index\n") }
        }

        val parsed = parseCsv(content, ImportLimits(maxEntries = 2))

        assertEquals(2, parsed.entries.size)
        assertTrue(parsed.truncated)
    }

    @Test
    fun csv_splitsSeveralExamplesOnTheDocumentedSeparator() {
        val content = csv(
            "word,translation,example,example_translation",
            "run,бежать,Run fast. | I run daily.,Беги быстро. | Я бегаю каждый день.",
        )

        val examples = parseCsv(content).entries.single().entry.examples

        assertEquals(2, examples.size)
        assertEquals("I run daily.", examples[1].english)
        assertEquals("Я бегаю каждый день.", examples[1].russian)
    }

    @Test
    fun csv_refusesAnEmptyDocument() {
        val parsed = parseCsv("")

        assertTrue(parsed.entries.isEmpty())
        assertEquals(ImportIssueReason.INVALID_FORMAT, parsed.issues.single().reason)
        assertEquals(0, parsed.issues.single().row)
    }

    // ------------------------------------------------------------------ JSON

    @Test
    fun json_readsATopLevelArray() {
        val content = """
            [
              {"word": "apple", "translation": "яблоко", "partOfSpeech": "NOUN", "topic": "food",
               "examples": [{"english": "I ate an apple.", "russian": "Я съел яблоко."}]}
            ]
        """.trimIndent()

        val entry = parseJson(content).entries.single().entry

        assertEquals("apple", entry.text)
        assertEquals("яблоко", entry.translation)
        assertEquals("food", entry.topic)
        assertEquals("I ate an apple.", entry.examples.single().english)
    }

    @Test
    fun json_readsTheDocumentedObjectShape() {
        val content = """{"version": 1, "words": [{"word": "apple", "translation": "яблоко"}]}"""

        val entry = parseJson(content).entries.single().entry

        assertEquals("apple", entry.text)
    }

    @Test
    fun json_ignoresUnknownFields() {
        val content = """
            {"version": 1, "generatedBy": "somewhere",
             "words": [{"word": "apple", "translation": "яблоко", "extra": 7}]}
        """.trimIndent()

        assertEquals("apple", parseJson(content).entries.single().entry.text)
    }

    @Test
    fun json_reportsAnUnsupportedVersion() {
        val content = """{"version": 99, "words": [{"word": "apple", "translation": "яблоко"}]}"""

        val parsed = parseJson(content)

        assertTrue(parsed.entries.isEmpty())
        assertEquals(ImportIssueReason.UNSUPPORTED_VERSION, parsed.issues.single().reason)
    }

    @Test
    fun json_reportsMalformedContent() {
        val parsed = parseJson("""{"version": 1, "words": [{"word": }]}""")

        assertTrue(parsed.entries.isEmpty())
        assertEquals(ImportIssueReason.INVALID_FORMAT, parsed.issues.single().reason)
    }

    @Test
    fun json_refusesAnObjectWithoutAWordsArray() {
        val parsed = parseJson("""{"version": 1, "items": []}""")

        assertTrue(parsed.entries.isEmpty())
        assertEquals(ImportIssueReason.INVALID_FORMAT, parsed.issues.single().reason)
    }

    @Test
    fun json_refusesAScalarDocument() {
        assertEquals(ImportIssueReason.INVALID_FORMAT, parseJson("\"just a string\"").issues.single().reason)
    }

    @Test
    fun json_reportsTheEntryIndexForEverySkippedEntry() {
        val content = """
            {"version": 1, "words": [{"translation": "яблоко"}, {"word": "pear", "translation": "груша"}]}
        """.trimIndent()

        val parsed = parseJson(content)

        assertEquals("pear", parsed.entries.single().entry.text)
        assertEquals(1, parsed.issues.single().row)
        assertEquals(ImportIssueReason.MISSING_WORD, parsed.issues.single().reason)
    }

    @Test
    fun json_refusesAnEmptyDocument() {
        val parsed = parseJson("   ")

        assertTrue(parsed.entries.isEmpty())
        assertEquals(ImportIssueReason.INVALID_FORMAT, parsed.issues.single().reason)
    }

    // -------------------------------------------------------- format detection

    @Test
    fun detectFormat_trustsTheContentOverTheExtension() {
        val jsonInACsvFile = """{"version": 1, "words": []}"""

        assertEquals(VocabularyFileFormat.JSON, parser.detectFormat("words.csv", jsonInACsvFile))
        assertEquals(VocabularyFileFormat.CSV, parser.detectFormat("words.json", "word,translation\n"))
    }

    @Test
    fun detectFormat_fallsBackToTheExtension_forAnEmptyFile() {
        assertEquals(VocabularyFileFormat.JSON, parser.detectFormat("backup.json", ""))
        assertEquals(VocabularyFileFormat.CSV, parser.detectFormat("words.csv", ""))
        assertEquals(VocabularyFileFormat.CSV, parser.detectFormat("no-extension", ""))
    }

    @Test
    fun detectFormat_seesThroughAByteOrderMark() {
        assertEquals(VocabularyFileFormat.JSON, parser.detectFormat("words.csv", "\uFEFF[{\"word\": \"apple\"}]"))
    }
}

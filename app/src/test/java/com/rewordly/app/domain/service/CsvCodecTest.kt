package com.rewordly.app.domain.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The CSV reader and writer are the same code on both sides of an export/import round trip, so a file
 * the app writes must come back exactly as it went out - including Russian text and quoted sentences.
 */
class CsvCodecTest {
    @Test
    fun parse_readsAPlainTable() {
        val rows = CsvCodec.parse("word,translation\napple,яблоко\npear,груша\n")

        assertEquals(listOf(listOf("word", "translation"), listOf("apple", "яблоко"), listOf("pear", "груша")), rows)
    }

    @Test
    fun parse_keepsCommasInsideQuotedValues() {
        val rows = CsvCodec.parse("word,example\n\"hold on\",\"Hold on, please.\"\n")

        assertEquals(listOf("hold on", "Hold on, please."), rows[1])
    }

    @Test
    fun parse_unescapesDoubledQuotes() {
        val rows = CsvCodec.parse("word,definition\nsay,\"He said \"\"hello\"\".\"\n")

        assertEquals("He said \"hello\".", rows[1][1])
    }

    @Test
    fun parse_keepsLineBreaksInsideQuotedValues() {
        val rows = CsvCodec.parse("word,example\ntree,\"First line.\nSecond line.\"\n")

        assertEquals(2, rows.size)
        assertEquals("First line.\nSecond line.", rows[1][1])
    }

    @Test
    fun parse_acceptsCrlf_aByteOrderMark_andATrailingNewline() {
        val rows = CsvCodec.parse("\uFEFFword,translation\r\napple,яблоко\r\n")

        assertEquals(listOf(listOf("word", "translation"), listOf("apple", "яблоко")), rows)
    }

    @Test
    fun parse_dropsBlankRows() {
        val rows = CsvCodec.parse("word,translation\n\n,\napple,яблоко\n\n")

        assertEquals(2, rows.size)
        assertEquals("apple", rows[1][0])
    }

    @Test
    fun parse_returnsNothingForAnEmptyDocument() {
        assertTrue(CsvCodec.parse("").isEmpty())
        assertTrue(CsvCodec.parse("\n\n").isEmpty())
    }

    @Test
    fun escape_quotesOnlyTheValuesThatNeedIt() {
        assertEquals("plain", CsvCodec.escape("plain"))
        assertEquals("\"with,comma\"", CsvCodec.escape("with,comma"))
        assertEquals("\"with \"\"quote\"\"\"", CsvCodec.escape("with \"quote\""))
        assertEquals("\"two\nlines\"", CsvCodec.escape("two\nlines"))
    }

    @Test
    fun write_usesCrlfAndRoundTripsThroughParse() {
        val rows = listOf(
            listOf("word", "translation", "example"),
            listOf("say", "говорить", "He said \"hello\", loudly."),
            listOf("tree", "дерево", "First line.\nSecond line."),
        )

        val written = CsvCodec.write(rows)

        assertTrue(written.contains("\r\n"))
        assertEquals(rows, CsvCodec.parse(written))
    }

    /**
     * Excel and Sheets evaluate a cell that starts with `=`, `+`, `-` or `@` even when the field is quoted,
     * so quoting is not a defence. A vocabulary file is user-controlled text, and the export is meant to be
     * opened in a spreadsheet, which makes this the one place the app hands data to an interpreter.
     */
    @Test
    fun escape_neutralisesEveryCharacterASpreadsheetWouldEvaluate() {
        FORMULA_PAYLOADS.forEach { payload ->
            val escaped = CsvCodec.escape(payload)
            assertFalse(
                "'$payload' is still a formula after escaping: '$escaped'",
                escaped.first() in FORMULA_STARTERS,
            )
        }
    }

    @Test
    fun write_neutralisesFormulas_andStillRoundTripsThroughParse() {
        FORMULA_PAYLOADS.forEach { payload ->
            val written = CsvCodec.write(listOf(listOf(payload)))

            assertFalse(
                "'$payload' reached the file as a formula: ${written.trim()}",
                written.first() in FORMULA_STARTERS,
            )
            assertEquals(
                "the guard changed the value on the way back in",
                listOf(listOf(payload)),
                CsvCodec.parse(written),
            )
        }
    }

    @Test
    fun aLeadingApostropheThatIsRealData_isNotEaten() {
        val written = CsvCodec.write(listOf(listOf("'tis the season")))

        assertEquals(listOf(listOf("'tis the season")), CsvCodec.parse(written))
    }

    private companion object {
        val FORMULA_PAYLOADS = listOf(
            "=1+1",
            "+1+1",
            "-1+1",
            "@SUM(A1)",
            "=HYPERLINK(\"http://example.invalid\",\"click\")",
            "=cmd|'/C calc'!A0",
        )

        /** The characters a spreadsheet treats as the start of a formula, plus the two that also work. */
        val FORMULA_STARTERS = setOf('=', '+', '-', '@', '\t', '\r')
    }
}

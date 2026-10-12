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

    /**
     * A literal apostrophe followed by a formula character is the one value the guard could not tell apart
     * from itself: it used to be written unchanged and read back with the apostrophe eaten, so a round trip
     * through export and import silently changed the user's text.
     */
    @Test
    fun aLiteralApostropheBeforeAFormulaCharacter_survivesTheRoundTrip() {
        AMBIGUOUS_APOSTROPHE_VALUES.forEach { value ->
            val written = CsvCodec.write(listOf(listOf(value)))

            assertEquals(
                "'$value' did not survive the round trip (file held ${written.trim()})",
                listOf(listOf(value)),
                CsvCodec.parse(written),
            )
        }
    }

    /**
     * The literal case has to be written in a shape [parse] reads back as data rather than as a guard: the
     * apostrophe is doubled, and because the guard was added the value is quoted too, so a reader that strips
     * quotes before looking for the apostrophe still finds it.
     */
    @Test
    fun escape_protectsALiteralApostropheInFrontOfAFormulaCharacter() {
        assertEquals("\"''=foo\"", CsvCodec.escape("'=foo"))
        assertEquals("\"'''\"", CsvCodec.escape("''"))
        assertEquals("'tis the season", CsvCodec.escape("'tis the season"))
    }

    /** A single apostrophe is never a guard, so it must be left exactly as it is. */
    @Test
    fun escape_leavesABareApostropheAlone() {
        assertEquals("'", CsvCodec.escape("'"))
        assertEquals(listOf(listOf("'")), CsvCodec.parse(CsvCodec.write(listOf(listOf("'")))))
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

        /** Literal data that starts with an apostrophe in the shape of a guard. */
        val AMBIGUOUS_APOSTROPHE_VALUES = listOf(
            "'=foo",
            "'+1",
            "'-1",
            "'@name",
            "''",
            "'=HYPERLINK(\"http://example.invalid\")",
        )

        /** The characters a spreadsheet treats as the start of a formula, plus the two that also work. */
        val FORMULA_STARTERS = setOf('=', '+', '-', '@', '\t', '\r')
    }
}

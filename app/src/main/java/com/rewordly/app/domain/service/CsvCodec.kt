package com.rewordly.app.domain.service

/**
 * A small RFC 4180 style CSV reader and writer.
 *
 * It is deliberately hand written instead of pulling in a dependency: the app needs exactly two
 * features, and both must behave identically for import and export so a file that was exported can
 * be imported again without surprises.
 *
 * Supported on read: quoted values, escaped quotes (`""`), commas and line breaks inside quotes,
 * `\r\n` and `\n` line endings, a UTF-8 byte order mark, and a trailing line break.
 *
 * A cell a spreadsheet would evaluate as a formula is defused on write and restored on read, so the guard
 * never becomes visible in the app's own data. See [escape].
 */
object CsvCodec {
    private const val DELIMITER = ','
    private const val QUOTE = '"'
    private const val ESCAPED_QUOTE = "\"\""
    private const val BOM = "\uFEFF"
    private const val LINE_BREAK = "\r\n"

    /**
     * Prepended to a value a spreadsheet would run as a formula. Excel, Sheets and LibreOffice all read a
     * leading apostrophe as "this cell is text", which is what makes it the standard guard for this.
     */
    private const val GUARD = '\''

    /**
     * The characters that make a spreadsheet evaluate a cell. `\t` and `\r` are included because some
     * versions strip them before deciding what the cell contains.
     */
    private val FORMULA_STARTERS = setOf('=', '+', '-', '@', '\t', '\r')

    /** Splits [text] into rows of fields. Blank rows are dropped so trailing newlines are harmless. */
    fun parse(text: String): List<List<String>> {
        val input = text.removePrefix(BOM)
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var index = 0
        while (index < input.length) {
            val char = input[index]
            if (quoted) {
                when {
                    char == QUOTE && input.getOrNull(index + 1) == QUOTE -> {
                        field.append(QUOTE)
                        index++
                    }
                    char == QUOTE -> quoted = false
                    else -> field.append(char)
                }
            } else {
                when (char) {
                    QUOTE -> if (field.isEmpty()) quoted = true else field.append(char)
                    DELIMITER -> {
                        row.add(unescapeGuard(field.toString()))
                        field.setLength(0)
                    }
                    '\n' -> {
                        row.add(unescapeGuard(field.toString()))
                        field.setLength(0)
                        rows.add(row)
                        row = mutableListOf()
                    }
                    '\r' -> Unit
                    else -> field.append(char)
                }
            }
            index++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row.add(unescapeGuard(field.toString()))
            rows.add(row)
        }
        return rows.filter { line -> line.any(String::isNotBlank) }
    }

    /** Serializes [rows] with CRLF line endings, quoting only the fields that need it. */
    fun write(rows: List<List<String>>): String = buildString {
        rows.forEach { row ->
            append(row.joinToString(DELIMITER.toString()) { escape(it) })
            append(LINE_BREAK)
        }
    }

    /**
     * Wraps [value] in quotes when it contains a delimiter, a quote or a line break, and defuses it first
     * when a spreadsheet would otherwise run it as a formula.
     *
     * Quoting is not a defence on its own: Excel evaluates a quoted `=1+1` as well. The guard is a leading
     * apostrophe, and [parse] removes it again, so the app's own export still round trips unchanged. A
     * guarded value is always quoted as well, so a reader that strips quotes before looking for the
     * apostrophe still sees it.
     */
    fun escape(value: String): String {
        val guarded = if (value.isNotEmpty() && value[0] in FORMULA_STARTERS) GUARD + value else value
        val needsQuotes = guarded.length != value.length ||
            guarded.any { it == DELIMITER || it == QUOTE || it == '\n' || it == '\r' }
        return if (needsQuotes) QUOTE + guarded.replace(QUOTE.toString(), ESCAPED_QUOTE) + QUOTE else guarded
    }

    /**
     * Undoes the guard [escape] added.
     *
     * Only a single apostrophe directly in front of a formula character is removed, so ordinary text such as
     * `'tis` is left alone. A value that genuinely begins with `'=` therefore loses its apostrophe on the way
     * back in: that is the one case this encoding cannot tell apart from a guard.
     */
    private fun unescapeGuard(value: String): String =
        if (value.length > 1 && value[0] == GUARD && value[1] in FORMULA_STARTERS) value.substring(1) else value
}

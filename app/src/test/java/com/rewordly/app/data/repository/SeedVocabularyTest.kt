package com.rewordly.app.data.repository

import com.rewordly.app.domain.service.CsvCodec
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the bundled seed vocabulary shape: the importer tolerates ragged rows, but the shipped file
 * must stay clean so no word ever lands with a category slug in its definition or a guessed difficulty.
 */
class SeedVocabularyTest {
    private val rows: List<List<String>> by lazy {
        val target = File("src/main/assets/seed_vocabulary.csv")
        require(target.exists()) { "seed asset missing: ${target.absolutePath}" }
        CsvCodec.parse(target.readText(Charsets.UTF_8))
    }

    @Test
    fun shape_everyRowHasNineColumns() {
        assertEquals(
            listOf(9),
            rows.map { it.size }.distinct(),
        )
        assertEquals(
            "word,translation,part_of_speech,definition," +
                "definition_translation,example,example_translation,topic,difficulty",
            rows.first().joinToString(","),
        )
    }

    @Test
    fun words_areUniqueAndFilled() {
        val words = rows.drop(1).map { it[0].trim().lowercase() }
        assertTrue(words.all(String::isNotEmpty))
        assertEquals(words.size, words.toSet().size)
        assertTrue(rows.drop(1).all { it[1].isNotBlank() })
    }

    @Test
    fun difficulties_areRealLevels_notBlanks() {
        val levels = setOf("A1", "A2", "B1", "B2")
        rows.drop(1).forEach { row ->
            assertTrue("difficulty '${row[8]}' on word ${row[0]}", row[8] in levels)
        }
    }

    @Test
    fun topics_areCleanSlugTokens() {
        // TopicPreset.parseTopicNames lowercases/underscores each token and ignores the rest, so any
        // human-readable category label that leaked into this column would be dropped or garbled.
        val slug = Regex("^[a-z][a-z0-9]*( [a-z0-9]+)*$")
        rows.drop(1).forEach { row ->
            assertTrue("topic '${row[7]}' on word ${row[0]}", row[7].matches(slug))
        }
    }

    @Test
    fun noDefinitionCarriesATopicLabel() {
        // The historical data bug: the human-readable category landed in the definition column.
        rows.drop(1).forEach { row ->
            val definition = row[3]
            assertTrue(definition, "IT / разработка" !in definition && "Грамматика" !in definition)
        }
    }
}

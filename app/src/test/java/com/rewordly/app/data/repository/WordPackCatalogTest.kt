package com.rewordly.app.data.repository

import com.rewordly.app.domain.model.Difficulty
import com.rewordly.app.domain.model.PartOfSpeech
import com.rewordly.app.domain.model.VocabularyEntry
import com.rewordly.app.domain.model.WordKeys
import com.rewordly.app.domain.service.VocabularyImportParser
import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the shipped word packs: every bundled document must parse with the import parser, hold
 * unique well-formed entries (difficulty, topic, pronunciation, examples) and never drift into
 * partial examples or guessed levels.
 */
class WordPackCatalogTest {
    private val parser = VocabularyImportParser(Json { ignoreUnknownKeys = true })

    private data class ParsedPack(val pack: BundledWordPack, val entries: List<VocabularyEntry>)

    private val parsedPacks: List<ParsedPack> by lazy {
        WordPackCatalog.packs.map { pack ->
            val file = File("src/main/assets/${pack.assetName}")
            assertTrue("pack asset missing: ${file.absolutePath}", file.exists())
            val content = file.readText(Charsets.UTF_8)
            val parsed = parser.parse(content, parser.detectFormat(pack.assetName, content))
            assertTrue("pack ${pack.id} has parser issues: ${parsed.issues}", parsed.issues.isEmpty())
            assertTrue("pack ${pack.id} parsed as empty", parsed.entries.isNotEmpty())
            ParsedPack(pack, parsed.entries.map { it.entry })
        }
    }

    @Test
    fun everyPack_parsesWithoutIssues() {
        assertEquals(WordPackCatalog.packs.size, parsedPacks.size)
    }

    @Test
    fun packEntries_haveNoDuplicateWords() {
        parsedPacks.forEach { data ->
            val keys = data.entries.map { WordKeys.normalize(it.text) }
            assertEquals("duplicate words in pack ${data.pack.id}", keys.size, keys.toSet().size)
        }
    }

    @Test
    fun packEntries_areFilledAndUseRealLevels() {
        parsedPacks.forEach { data ->
            data.entries.forEach { entry ->
                assertTrue("blank word in ${data.pack.id}", entry.text.isNotBlank())
                assertTrue("blank translation on ${entry.text}", entry.translation.isNotBlank())
                assertTrue(
                    "unknown difficulty '${entry.difficulty}' on ${entry.text}",
                    Difficulty.entries.any { it.name.equals(entry.difficulty, ignoreCase = true) },
                )
            }
        }
    }

    @Test
    fun packEntries_useKnownPartOfSpeechTokens() {
        parsedPacks.forEach { data ->
            data.entries.forEach { entry ->
                val token = entry.partOfSpeech.trim()
                val ok = token.isEmpty() || PartOfSpeech.entries.any { it.name.equals(token, ignoreCase = true) }
                assertTrue("unknown part of speech '$token' on ${entry.text}", ok)
            }
        }
    }

    @Test
    fun singleWordEntries_haveSlashPronunciation() {
        // The starter list predates the pack format and carries no pronunciation at all.
        parsedPacks.filter { it.pack.hasPronunciations }.forEach { data ->
            data.entries.filter { !it.text.contains(' ') }.forEach { entry ->
                assertTrue(
                    "pronunciation without slashes on ${entry.text}: ${entry.pronunciation}",
                    entry.pronunciation.startsWith('/'),
                )
            }
        }
    }

    @Test
    fun packExamples_arePaired() {
        parsedPacks.forEach { data ->
            data.entries.forEach { entry ->
                entry.examples.forEach { example ->
                    assertTrue("empty english example on ${entry.text}", example.english.isNotBlank())
                    assertTrue("empty russian example on ${entry.text}", example.russian.isNotBlank())
                }
            }
        }
    }
}

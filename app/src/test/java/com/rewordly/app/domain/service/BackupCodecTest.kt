package com.rewordly.app.domain.service

import com.rewordly.app.domain.model.BackupDay
import com.rewordly.app.domain.model.BackupDecodeResult
import com.rewordly.app.domain.model.BackupExample
import com.rewordly.app.domain.model.BackupIssue
import com.rewordly.app.domain.model.BackupLimits
import com.rewordly.app.domain.model.BackupProgress
import com.rewordly.app.domain.model.BackupReviewLog
import com.rewordly.app.domain.model.BackupSettings
import com.rewordly.app.domain.model.BackupWord
import com.rewordly.app.domain.model.VocabularyBackup
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A backup is refused *before* the database is touched, so every rejection path is tested here: a
 * corrupt or newer file must never reach the restore step.
 */
class BackupCodecTest {
    private val codec = BackupCodec(
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        },
    )

    private val backup = VocabularyBackup(
        schemaVersion = VocabularyBackup.CURRENT_SCHEMA_VERSION,
        createdAt = 1_700_000_000_000,
        appVersion = "0.1.0",
        words = listOf(
            BackupWord(
                id = "word-1",
                text = "apple",
                translation = "яблоко",
                partOfSpeech = "NOUN",
                difficulty = "A1",
                topic = "food",
                examples = listOf(BackupExample("example-1", "I ate an apple.", "Я съел яблоко.", 0)),
            ),
        ),
        progress = listOf(BackupProgress(wordId = "word-1", repetitionCount = 3, intervalDays = 6, easeFactor = 2.6)),
        reviewLog = listOf(BackupReviewLog(wordId = "word-1", sessionId = "session-1", quality = 4, reviewedAt = 1)),
        activity = listOf(BackupDay(day = "2023-11-15", wordsLearned = 1, wordsReviewed = 1)),
        settings = BackupSettings(interfaceLanguage = "ru", dailyGoal = 15, interests = listOf("TRAVEL")),
    )

    @Test
    fun encodeThenDecode_preservesEverythingThatMatters() {
        val decoded = codec.decode(codec.encode(backup))

        assertTrue(decoded is BackupDecodeResult.Valid)
        val restored = (decoded as BackupDecodeResult.Valid).backup
        assertEquals(backup.createdAt, restored.createdAt)
        assertEquals(backup.appVersion, restored.appVersion)
        assertEquals(backup.words, restored.words)
        assertEquals(backup.progress, restored.progress)
        assertEquals(backup.reviewLog, restored.reviewLog)
        assertEquals(backup.activity, restored.activity)
        assertEquals(backup.settings, restored.settings)
    }

    @Test
    fun decode_seesThroughAByteOrderMark() {
        val decoded = codec.decode("\uFEFF" + codec.encode(backup))

        assertTrue(decoded is BackupDecodeResult.Valid)
    }

    @Test
    fun decode_refusesSomethingThatIsNotJson() {
        assertEquals(BackupIssue.UNREADABLE, invalid(codec.decode("this is not json at all")))
        assertEquals(BackupIssue.UNREADABLE, invalid(codec.decode("")))
    }

    @Test
    fun decode_refusesJsonThatIsNotABackup() {
        assertEquals(BackupIssue.NOT_A_BACKUP, invalid(codec.decode("""[{"word": "apple"}]""")))
        assertEquals(BackupIssue.NOT_A_BACKUP, invalid(codec.decode("""{"words": []}""")))
        assertEquals(BackupIssue.NOT_A_BACKUP, invalid(codec.decode("""{"schemaVersion": "one", "words": []}""")))
        assertEquals(BackupIssue.NOT_A_BACKUP, invalid(codec.decode("""{"schemaVersion": 1, "words": {}}""")))
    }

    @Test
    fun decode_refusesABackupFromANewerApp() {
        val newerVersion = VocabularyBackup.CURRENT_SCHEMA_VERSION + 1
        val newer = """
            {"schemaVersion": $newerVersion, "words": [{"id": "w", "text": "a", "translation": "б"}]}
        """.trimIndent()

        assertEquals(BackupIssue.UNSUPPORTED_VERSION, invalid(codec.decode(newer)))
    }

    @Test
    fun decode_refusesABackupWithoutWords() {
        val empty = """{"schemaVersion": 1, "words": []}"""

        assertEquals(BackupIssue.EMPTY, invalid(codec.decode(empty)))
    }

    @Test
    fun decode_refusesATruncatedBackup() {
        val truncated = codec.encode(backup).take(codec.encode(backup).length / 2)

        assertEquals(BackupIssue.UNREADABLE, invalid(codec.decode(truncated)))
    }

    @Test
    fun decode_toleratesUnknownFieldsFromAFutureMinorVersion() {
        val content = """
            {"schemaVersion": 1, "createdAt": 1, "brandNewField": {"a": 1},
             "words": [{"id": "w", "text": "apple", "translation": "яблоко"}]}
        """.trimIndent()

        assertTrue(codec.decode(content) is BackupDecodeResult.Valid)
    }

    /**
     * The CSV importer caps entries and field length; the restore path used to have no equivalent, so a
     * crafted backup went straight into one transaction. A backup is the app's own full-state file, so its
     * allowance is deliberately much larger than the importer's - but it has to exist.
     */
    @Test
    fun decode_refusesABackupWithMoreWordsThanTheAllowance() {
        assertEquals(BackupIssue.TOO_LARGE, invalid(codec.decode(backupOf(words = 11), SMALL_LIMITS)))
    }

    @Test
    fun decode_refusesABackupWithAnOversizedField() {
        val oversized = backupOf(words = 1, textLength = SMALL_LIMITS.maxFieldLength + 1)

        assertEquals(BackupIssue.TOO_LARGE, invalid(codec.decode(oversized, SMALL_LIMITS)))
    }

    @Test
    fun decode_refusesAnOversizedExampleSentence() {
        val content = """
            {"schemaVersion": 1, "words": [{"id": "w", "text": "apple", "translation": "яблоко",
             "examples": [{"id": "e", "text": "${"a".repeat(21)}", "translation": "б"}]}]}
        """.trimIndent()

        assertEquals(BackupIssue.TOO_LARGE, invalid(codec.decode(content, SMALL_LIMITS)))
    }

    @Test
    fun decode_acceptsABackupThatSitsExactlyAtTheAllowance() {
        assertTrue(codec.decode(backupOf(words = 10), SMALL_LIMITS) is BackupDecodeResult.Valid)
    }

    @Test
    fun theDefaultAllowance_doesNotInheritTheCsvImportCeiling() {
        // Reusing ImportLimits here would refuse a legitimate backup from a user with a big vocabulary.
        assertTrue(BackupLimits.DEFAULT_MAX_WORDS > ImportLimits.DEFAULT_MAX_ENTRIES * 10)
        assertTrue(BackupLimits.DEFAULT_MAX_FIELD_LENGTH > ImportLimits.DEFAULT_MAX_FIELD_LENGTH)
    }

    /** Minimal but valid backup JSON, built by hand so the size can be varied per test. */
    private fun backupOf(words: Int, textLength: Int = 1): String {
        val body = (1..words).joinToString(",") { index ->
            val text = "a".repeat(textLength)
            """{"id":"w$index","text":"$text","translation":"б"}"""
        }
        return """{"schemaVersion": 1, "words": [$body]}"""
    }

    private fun invalid(result: BackupDecodeResult): BackupIssue? = (result as? BackupDecodeResult.Invalid)?.issue

    private companion object {
        val SMALL_LIMITS = BackupLimits(maxWords = 10, maxFieldLength = 20)
    }
}

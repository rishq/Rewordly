package com.rewordly.app.data.repository

import com.rewordly.app.core.common.AppError
import com.rewordly.app.core.common.AppResult
import com.rewordly.app.core.database.entity.DailyActivityEntity
import com.rewordly.app.core.database.entity.ReviewLogEntity
import com.rewordly.app.core.database.entity.WordEntity
import com.rewordly.app.core.database.entity.WordExampleEntity
import com.rewordly.app.core.database.entity.WordProgressEntity
import com.rewordly.app.domain.model.BackupIssue
import com.rewordly.app.domain.model.DuplicateKind
import com.rewordly.app.domain.model.DuplicatePolicy
import com.rewordly.app.domain.model.RestoreStrategy
import com.rewordly.app.domain.model.VocabularyFileFormat
import com.rewordly.app.domain.repository.BackupInspection
import com.rewordly.app.domain.repository.RestoreOutcome
import com.rewordly.app.domain.service.BackupCodec
import com.rewordly.app.domain.service.VocabularyExporter
import com.rewordly.app.domain.service.VocabularyImportParser
import com.rewordly.app.testing.FakeDataTransferDao
import com.rewordly.app.testing.FakeSettingsRepository
import com.rewordly.app.testing.SequentialIdProvider
import com.rewordly.app.testing.TestTimeProvider
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end behaviour of import, export and restore against an in-memory database that keeps the
 * guarantees Room provides: `INSERT OR IGNORE`, cascading foreign keys, and transactional rollback.
 *
 * The two things that must never happen are asserted throughout: losing learning progress, and
 * leaving the database half-written.
 */
class OfflineDataManagementRepositoryTest {
    private val dao = FakeDataTransferDao()
    private val settings = FakeSettingsRepository()
    private val time = TestTimeProvider()
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val repository = OfflineDataManagementRepository(
        dao = dao,
        importParser = VocabularyImportParser(json),
        exporter = VocabularyExporter(json),
        backupCodec = BackupCodec(json),
        settingsRepository = settings,
        timeProvider = time,
        idProvider = SequentialIdProvider(prefix = "new"),
    )

    // ------------------------------------------------------------------- stats

    @Test
    fun stats_countsEveryLocalCollection() = runTest {
        seedWord("w1")
        dao.seedProgress(listOf(progress("w1")))
        dao.seedReviewLogs(listOf(log("w1", "session-1")))
        dao.seedActivity(listOf(DailyActivityEntity("2023-11-15", 1, 2)))

        val stats = repository.stats().value()

        assertEquals(1, stats.words)
        assertEquals(1, stats.trackedWords)
        assertEquals(1, stats.reviewLogEntries)
        assertEquals(1, stats.activeDays)
    }

    // ----------------------------------------------------------------- preview

    @Test
    fun previewImport_marksWordsThatAreAlreadySaved_andReportsTheCounts() = runTest {
        seedWord("w1", text = "apple")

        val preview = repository.previewImport("words.csv", "word,translation\napple,яблоко\npear,груша\n").value()

        assertEquals(VocabularyFileFormat.CSV, preview.format)
        assertEquals(2, preview.entryCount)
        assertEquals(1, preview.newCount)
        assertEquals(1, preview.duplicateCount)
        assertEquals(DuplicateKind.EXISTING, preview.candidates.first().duplicate)
        assertEquals("w1", preview.candidates.first().existingWordId)
    }

    @Test
    fun previewImport_reportsSkippedRowsWithoutImportingThem() = runTest {
        val preview = repository.previewImport("words.csv", "word,translation\napple,яблоко\n,нет\n").value()

        assertEquals(1, preview.entryCount)
        assertEquals(1, preview.issueCount)
    }

    @Test
    fun previewImport_writesNothing() = runTest {
        repository.previewImport("words.csv", "word,translation\napple,яблоко\n").value()

        assertEquals(0, dao.writeCount)
    }

    // ------------------------------------------------------------- applyImport

    @Test
    fun applyImport_skip_addsOnlyNewWords() = runTest {
        seedWord("w1", text = "apple")
        val preview = repository.previewImport("words.csv", "word,translation\napple,яблоко\npear,груша\n").value()

        val summary = repository.applyImport(preview, DuplicatePolicy.SKIP).value()

        assertEquals(1, summary.added)
        assertEquals(0, summary.replaced)
        assertEquals(1, summary.skipped)
        assertEquals(setOf("apple", "pear"), dao.words.value.map { it.text }.toSet())
    }

    @Test
    fun applyImport_replace_updatesTheContentInPlace_andKeepsLearningProgress() = runTest {
        seedWord("w1", text = "apple", translation = "старое")
        dao.seedProgress(listOf(progress("w1", repetitionCount = 4, intervalDays = 6)))
        val preview = repository.previewImport("words.csv", "word,translation\napple,новое\n").value()

        val summary = repository.applyImport(preview, DuplicatePolicy.REPLACE).value()

        assertEquals(1, summary.replaced)
        assertEquals(1, dao.words.value.size)
        assertEquals("новое", dao.word("w1")?.translation)
        val kept = dao.progressOf("w1")
        assertEquals(4, kept?.repetitionCount)
        assertEquals(6, kept?.intervalDays)
    }

    @Test
    fun applyImport_replace_swapsTheExampleSentencesForTheOnesInTheFile() = runTest {
        seedWord("w1", text = "apple", examples = listOf("An old example." to "Старый пример."))
        val csv = "word,translation,example,example_translation\napple,яблоко,A new example.,Новый пример.\n"
        val preview = repository.previewImport("words.csv", csv).value()

        repository.applyImport(preview, DuplicatePolicy.REPLACE).value()

        val examples = dao.examplesOf("w1")
        assertEquals(1, examples.size)
        assertEquals("A new example.", examples.single().text)
        assertEquals("Новый пример.", examples.single().translation)
    }

    @Test
    fun applyImport_keepBoth_addsTheWordASecondTime() = runTest {
        seedWord("w1", text = "apple", translation = "банк")
        val preview = repository.previewImport("words.csv", "word,translation\napple,берег\n").value()

        val summary = repository.applyImport(preview, DuplicatePolicy.KEEP_BOTH).value()

        assertEquals(1, summary.added)
        assertEquals(0, summary.skipped)
        assertEquals(2, dao.words.value.size)
        assertEquals(setOf("банк", "берег"), dao.words.value.map { it.translation }.toSet())
    }

    @Test
    fun applyImport_marksImportedWordsAsImported() = runTest {
        val preview = repository.previewImport("words.csv", "word,translation\napple,яблоко\n").value()

        repository.applyImport(preview, DuplicatePolicy.SKIP).value()

        assertEquals("IMPORTED", dao.words.value.single().source)
    }

    @Test
    fun applyImport_keepsTheRowsOfTheFileInTheStoredExamples() = runTest {
        val csv = "word,translation,example,example_translation\n" +
            "run,бежать,Run fast. | I run daily.,Беги быстро. | Я бегаю.\n"
        val preview = repository.previewImport("words.csv", csv).value()

        repository.applyImport(preview, DuplicatePolicy.SKIP).value()

        val examples = dao.examplesOf(dao.words.value.single().id)
        assertEquals(listOf(0, 1), examples.map { it.position })
        assertEquals(listOf("Run fast.", "I run daily."), examples.map { it.text })
    }

    // ------------------------------------------------------------------ export

    @Test
    fun exportVocabulary_csvContainsTheSavedWords() = runTest {
        seedWord("w1", text = "apple", translation = "яблоко")

        val csv = repository.exportVocabulary(VocabularyFileFormat.CSV).value()

        assertTrue(csv.startsWith("word,translation,"))
        assertTrue(csv.contains("apple,яблоко"))
    }

    @Test
    fun exportVocabulary_ofAnEmptyVocabulary_isAValidFile() = runTest {
        val csv = repository.exportVocabulary(VocabularyFileFormat.CSV).value()
        val exportedJson = repository.exportVocabulary(VocabularyFileFormat.JSON).value()

        assertEquals(1, csv.trim().lines().size)
        assertTrue(exportedJson.contains("\"words\":[]"))
    }

    @Test
    fun exportBackup_carriesVocabularyProgressHistoryActivityAndSettings() = runTest {
        seedWord("w1", text = "apple", examples = listOf("I ate an apple." to "Я съел яблоко."))
        dao.seedProgress(listOf(progress("w1", repetitionCount = 3)))
        dao.seedReviewLogs(listOf(log("w1", "session-1")))
        dao.seedActivity(listOf(DailyActivityEntity("2023-11-15", 1, 1)))
        settings.setDailyGoal(25)

        val exported = repository.exportBackup().value()
        val preview = (repository.inspectBackup(exported).value() as BackupInspection.Valid).preview

        assertEquals(1, preview.words)
        assertEquals(1, preview.examples)
        assertEquals(1, preview.progress)
        assertEquals(1, preview.historyEntries)
        assertTrue(exported.contains("\"dailyGoal\":25"))
    }

    @Test
    fun exportBackup_ofAnEmptyVocabulary_isStillRecognisableAsABackup() = runTest {
        val exported = repository.exportBackup().value()

        val inspection = repository.inspectBackup(exported).value()

        assertEquals(BackupIssue.EMPTY, (inspection as BackupInspection.Rejected).issue)
    }

    // --------------------------------------------------------------- inspection

    @Test
    fun inspectBackup_rejectsAFileThatIsNotABackup() = runTest {
        val inspection = repository.inspectBackup("""{"words": []}""").value()

        assertEquals(BackupIssue.NOT_A_BACKUP, (inspection as BackupInspection.Rejected).issue)
    }

    // ---------------------------------------------------------------- restore

    @Test
    fun restoreMerge_addsWhatIsMissing_andKeepsWhatIsAlreadyThere() = runTest {
        seedWord("w1", text = "apple", translation = "старое")
        dao.seedProgress(listOf(progress("w1", repetitionCount = 7)))
        val backup = backupJson(
            """
            {"id": "w1", "text": "apple", "translation": "обновлённое"},
            {"id": "w2", "text": "pear", "translation": "груша"}
            """.trimIndent(),
        )

        val summary = repository.restoreBackup(backup, RestoreStrategy.MERGE).value()

        val restored = (summary as RestoreOutcome.Restored).summary
        assertEquals(1, restored.wordsAdded)
        assertEquals(1, restored.wordsUpdated)
        assertEquals(setOf("apple", "pear"), dao.words.value.map { it.text }.toSet())
        assertEquals("обновлённое", dao.word("w1")?.translation)
        assertEquals(7, dao.progressOf("w1")?.repetitionCount)
    }

    @Test
    fun restoreMerge_doesNotAddASecondCopyOfAWordThatIsAlreadySavedUnderAnotherId() = runTest {
        seedWord("local-1", text = "apple", translation = "яблоко")
        val backup = backupJson("""{"id": "backup-1", "text": "  Apple ", "translation": "яблоко"}""")

        val summary = repository.restoreBackup(backup, RestoreStrategy.MERGE).value()

        val restored = (summary as RestoreOutcome.Restored).summary
        assertEquals(0, restored.wordsAdded)
        assertEquals(0, restored.wordsUpdated)
        assertEquals(1, dao.words.value.size)
    }

    @Test
    fun restoreMerge_doesNotDuplicateReviewHistoryThatIsAlreadyOnTheDevice() = runTest {
        seedWord("w1", text = "apple")
        dao.seedReviewLogs(listOf(log("w1", "session-1")))
        val backup = backupJson(
            words = """{"id": "w1", "text": "apple", "translation": "яблоко"}""",
            progress = """{"wordId": "w1"}""",
            reviewLog = """{"wordId": "w1", "sessionId": "session-1", "quality": 4, "reviewedAt": 5}""",
        )

        val summary = repository.restoreBackup(backup, RestoreStrategy.MERGE).value()

        val restored = (summary as RestoreOutcome.Restored).summary
        assertEquals(0, restored.historyRestored)
        assertEquals(1, dao.logs.value.size)
    }

    @Test
    fun restoreMerge_dropsChildRowsThatPointAtAWordTheBackupDoesNotContain() = runTest {
        seedWord("w1", text = "apple")
        val backup = backupJson(
            words = """{"id": "w1", "text": "apple", "translation": "яблоко"}""",
            progress = """{"wordId": "ghost", "repetitionCount": 9}, {"wordId": "w1", "repetitionCount": 2}""",
        )

        val summary = repository.restoreBackup(backup, RestoreStrategy.MERGE).value()

        assertTrue(summary is RestoreOutcome.Restored)
        assertEquals(listOf("w1"), dao.progress.value.map { it.wordId })
    }

    @Test
    fun restoreReplace_replacesEverythingThatWasThere() = runTest {
        seedWord("local-1", text = "apple")
        dao.seedProgress(listOf(progress("local-1", repetitionCount = 9)))
        dao.seedReviewLogs(listOf(log("local-1", "session-old")))
        val backup = backupJson(
            words = """{"id": "w9", "text": "pear", "translation": "груша"}""",
            progress = """{"wordId": "w9", "repetitionCount": 1}""",
        )

        val summary = repository.restoreBackup(backup, RestoreStrategy.REPLACE).value()

        val restored = (summary as RestoreOutcome.Restored).summary
        assertEquals(1, restored.wordsAdded)
        assertEquals(listOf("w9"), dao.words.value.map { it.id })
        assertEquals(listOf("w9"), dao.progress.value.map { it.wordId })
        assertTrue(dao.logs.value.isEmpty())
    }

    @Test
    fun restoreBackup_refusesAnUnusableFileWithoutWritingAnything() = runTest {
        seedWord("w1", text = "apple")

        val outcome = repository.restoreBackup("not a backup at all", RestoreStrategy.REPLACE).value()

        assertEquals(BackupIssue.UNREADABLE, (outcome as RestoreOutcome.Rejected).issue)
        assertEquals(0, dao.writeCount)
        assertEquals(1, dao.words.value.size)
    }

    @Test
    fun restoreBackup_leavesNothingBehindWhenAWriteFails() = runTest {
        seedWord("w1", text = "apple", translation = "яблоко")
        dao.seedProgress(listOf(progress("w1", repetitionCount = 5)))
        val backup = backupJson(
            """
            {"id": "w2", "text": "pear", "translation": "груша",
             "examples": [{"id": "e1", "text": "A pear.", "translation": "Груша."}]}
            """.trimIndent(),
        )
        // The second write of the transaction - inserting the examples - fails.
        dao.failOnWrite = 2

        val result = repository.restoreBackup(backup, RestoreStrategy.MERGE)

        assertTrue(result is AppResult.Failure)
        assertTrue((result as AppResult.Failure).error is AppError.Database)
        assertEquals(listOf("w1"), dao.words.value.map { it.id })
        assertTrue(dao.examples.value.isEmpty())
        assertEquals(5, dao.progressOf("w1")?.repetitionCount)
    }

    @Test
    fun applyImport_leavesNothingBehindWhenAWriteFails() = runTest {
        val preview = repository.previewImport("words.csv", "word,translation\napple,яблоко\n").value()
        dao.failOnWrite = 1

        val result = repository.applyImport(preview, DuplicatePolicy.SKIP)

        assertTrue(result is AppResult.Failure)
        assertTrue(dao.words.value.isEmpty())
    }

    // ----------------------------------------------------------------- helpers

    private fun seedWord(
        id: String,
        text: String = "apple",
        translation: String = "яблоко",
        examples: List<Pair<String, String>> = emptyList(),
    ) {
        dao.seedWords(
            dao.words.value + WordEntity(
                id = id,
                language = "en",
                text = text,
                translation = translation,
                translationLanguage = "ru",
                pronunciation = "",
                partOfSpeech = "NOUN",
                difficulty = "A1",
                definition = "",
                definitionTranslation = "",
                forms = emptyList(),
                relatedWords = emptyList(),
                synonyms = emptyList(),
                audioUrl = null,
                createdAt = dao.words.value.size.toLong(),
            ),
        )
        if (examples.isNotEmpty()) {
            dao.examples.value = dao.examples.value + examples.mapIndexed { index, (english, russian) ->
                WordExampleEntity(
                    id = "example-$id-$index",
                    wordId = id,
                    text = english,
                    translation = russian,
                    position = index,
                )
            }
        }
    }

    private fun progress(wordId: String, repetitionCount: Int = 0, intervalDays: Int = 0) = WordProgressEntity(
        wordId = wordId,
        status = "LEARNING",
        isSaved = false,
        views = 1,
        correctAnswers = 1,
        incorrectAnswers = 0,
        lastViewedAt = null,
        lastReviewedAt = null,
        nextReviewAt = null,
        updatedAt = 1,
        repetitionCount = repetitionCount,
        easeFactor = 2.5,
        intervalDays = intervalDays,
        consecutiveCorrect = 0,
        consecutiveIncorrect = 0,
    )

    private fun log(wordId: String, sessionId: String) = ReviewLogEntity(
        wordId = wordId,
        sessionId = sessionId,
        kind = "REVIEW",
        quality = 4,
        reviewedAt = 1,
        durationMs = null,
        intervalBefore = 0,
        intervalAfter = 1,
    )

    /** Builds a backup document by hand, so a test can describe exactly what a file claims to hold. */
    private fun backupJson(words: String, progress: String = "", reviewLog: String = ""): String = """
        {
          "schemaVersion": 1,
          "createdAt": 1700000000000,
          "appVersion": "0.1.0",
          "words": [$words],
          "progress": [${progress.ifBlank { "" }}],
          "reviewLog": [${reviewLog.ifBlank { "" }}],
          "activity": []
        }
    """.trimIndent()

    private fun <T> AppResult<T>.value(): T = (this as AppResult.Success<T>).data
}

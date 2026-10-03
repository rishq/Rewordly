package com.rewordly.app.testing

import com.rewordly.app.core.database.dao.DataTransferDao
import com.rewordly.app.core.database.dao.ImportWriteResult
import com.rewordly.app.core.database.dao.RestoreWriteResult
import com.rewordly.app.core.database.dao.WordKeyRow
import com.rewordly.app.core.database.entity.DailyActivityEntity
import com.rewordly.app.core.database.entity.ReviewLogEntity
import com.rewordly.app.core.database.entity.WordEntity
import com.rewordly.app.core.database.entity.WordExampleEntity
import com.rewordly.app.core.database.entity.WordProgressEntity
import com.rewordly.app.core.network.ConnectivityObserver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory [DataTransferDao] that keeps the two guarantees the real database provides and the
 * repository depends on: `@Insert(IGNORE)` skips conflicting rows instead of replacing them, and a
 * failing transaction leaves nothing behind.
 *
 * Foreign keys are enforced too, so a test that accidentally writes progress for a word that is not
 * there fails the same way Room would.
 */
class FakeDataTransferDao : DataTransferDao {
    val words = MutableStateFlow<List<WordEntity>>(emptyList())
    val examples = MutableStateFlow<List<WordExampleEntity>>(emptyList())
    val progress = MutableStateFlow<List<WordProgressEntity>>(emptyList())
    val logs = MutableStateFlow<List<ReviewLogEntity>>(emptyList())
    val activity = MutableStateFlow<List<DailyActivityEntity>>(emptyList())

    /** Mutating calls made so far; a test can make the Nth one fail to exercise rollback. */
    var writeCount = 0

    /** When set to a call number, that mutating call throws, as a database error would. */
    var failOnWrite: Int? = null

    private var nextLogId = 1L

    fun seedWords(rows: List<WordEntity>) {
        words.value = rows
    }

    fun seedProgress(rows: List<WordProgressEntity>) {
        progress.value = rows
    }

    fun seedReviewLogs(rows: List<ReviewLogEntity>) {
        logs.value = rows
        nextLogId = (rows.maxOfOrNull { it.id } ?: 0L) + 1
    }

    fun seedActivity(rows: List<DailyActivityEntity>) {
        activity.value = rows
    }

    fun progressOf(wordId: String): WordProgressEntity? = progress.value.firstOrNull { it.wordId == wordId }

    fun word(id: String): WordEntity? = words.value.firstOrNull { it.id == id }

    fun examplesOf(wordId: String): List<WordExampleEntity> =
        examples.value.filter { it.wordId == wordId }.sortedBy { it.position }

    // ------------------------------------------------------------------ reads

    override suspend fun allWords(): List<WordEntity> = words.value.sortedBy { it.createdAt }

    override suspend fun allExamples(): List<WordExampleEntity> = examples.value

    override suspend fun allProgress(): List<WordProgressEntity> = progress.value

    override suspend fun allReviewLogs(): List<ReviewLogEntity> = logs.value.sortedBy { it.reviewedAt }

    override suspend fun allActivity(): List<DailyActivityEntity> = activity.value.sortedBy { it.day }

    override suspend fun wordCount(): Int = words.value.size

    override suspend fun exampleCount(): Int = examples.value.size

    override suspend fun progressCount(): Int = progress.value.size

    override suspend fun reviewLogCount(): Int = logs.value.size

    override suspend fun activityCount(): Int = activity.value.size

    override suspend fun findWordsByNormalizedText(language: String, keys: List<String>): List<WordKeyRow> = words.value
        .filter { it.language == language && it.text.trim().lowercase() in keys }
        .map { WordKeyRow(it.id, it.text) }

    override suspend fun wordsByIds(ids: List<String>): List<WordEntity> = words.value.filter { it.id in ids }

    // ----------------------------------------------------------------- writes

    override suspend fun insertWords(words: List<WordEntity>): List<Long> {
        noteWrite()
        val accepted = words.filter { candidate -> this.words.value.none { it.id == candidate.id } }
        this.words.value = this.words.value + accepted
        val acceptedIds = accepted.map { it.id }.toSet()
        return words.map { row -> if (row.id in acceptedIds) nextRowId() else IGNORED }
    }

    override suspend fun updateWords(words: List<WordEntity>): Int {
        noteWrite()
        val ids = words.map { it.id }.toSet()
        val matched = this.words.value.count { it.id in ids }
        this.words.value = this.words.value.map { existing -> words.firstOrNull { it.id == existing.id } ?: existing }
        return matched
    }

    override suspend fun insertExamples(examples: List<WordExampleEntity>) {
        noteWrite()
        requireParents(examples.map { it.wordId })
        val accepted = examples.filter { candidate -> this.examples.value.none { it.id == candidate.id } }
        this.examples.value = this.examples.value + accepted
    }

    override suspend fun deleteExamplesOf(wordIds: List<String>) {
        noteWrite()
        examples.value = examples.value.filterNot { it.wordId in wordIds }
    }

    override suspend fun insertProgress(rows: List<WordProgressEntity>): List<Long> {
        noteWrite()
        requireParents(rows.map { it.wordId })
        val accepted = rows.filter { candidate -> progress.value.none { it.wordId == candidate.wordId } }
        progress.value = progress.value + accepted
        val acceptedIds = accepted.map { it.wordId }.toSet()
        return rows.map { row -> if (row.wordId in acceptedIds) nextRowId() else IGNORED }
    }

    override suspend fun insertReviewLogs(rows: List<ReviewLogEntity>): List<Long> {
        noteWrite()
        requireParents(rows.map { it.wordId })
        val accepted = rows.filter { candidate -> !hasLog(candidate) }
        logs.value = logs.value + accepted.map { it.copy(id = nextLogId++) }
        return rows.map { row -> if (accepted.any { sameLog(it, row) }) nextRowId() else IGNORED }
    }

    override suspend fun insertActivity(rows: List<DailyActivityEntity>): List<Long> {
        noteWrite()
        val accepted = rows.filter { candidate -> activity.value.none { it.day == candidate.day } }
        activity.value = activity.value + accepted
        val acceptedDays = accepted.map { it.day }.toSet()
        return rows.map { row -> if (row.day in acceptedDays) nextRowId() else IGNORED }
    }

    override suspend fun clearReviewLogs() {
        noteWrite()
        logs.value = emptyList()
    }

    override suspend fun clearProgress() {
        noteWrite()
        progress.value = emptyList()
    }

    override suspend fun clearExamples() {
        noteWrite()
        examples.value = emptyList()
    }

    override suspend fun clearWords() {
        noteWrite()
        words.value = emptyList()
    }

    override suspend fun clearActivity() {
        noteWrite()
        activity.value = emptyList()
    }

    // ----------------------------------------------------------- transactions

    override suspend fun writeImport(
        newWords: List<WordEntity>,
        newExamples: List<WordExampleEntity>,
        replacedWords: List<WordEntity>,
        replacedExamples: List<WordExampleEntity>,
    ): ImportWriteResult = transaction {
        var replaced = 0
        if (replacedWords.isNotEmpty()) {
            deleteExamplesOf(replacedWords.map { it.id })
            replaced = updateWords(replacedWords)
            insertExamples(replacedExamples)
        }
        val added = if (newWords.isEmpty()) 0 else insertWords(newWords).count { it != IGNORED }
        if (newExamples.isNotEmpty()) insertExamples(newExamples)
        ImportWriteResult(added = added, replaced = replaced)
    }

    override suspend fun restoreMerge(
        newWords: List<WordEntity>,
        newExamples: List<WordExampleEntity>,
        updatedWords: List<WordEntity>,
        updatedExamples: List<WordExampleEntity>,
        progress: List<WordProgressEntity>,
        reviewLogs: List<ReviewLogEntity>,
        activity: List<DailyActivityEntity>,
    ): RestoreWriteResult = transaction {
        val written = writeImport(newWords, newExamples, updatedWords, updatedExamples)
        val progressRestored = if (progress.isEmpty()) 0 else insertProgress(progress).count { it != IGNORED }
        val historyRestored = if (reviewLogs.isEmpty()) 0 else insertReviewLogs(reviewLogs).count { it != IGNORED }
        if (activity.isNotEmpty()) insertActivity(activity)
        RestoreWriteResult(
            wordsAdded = written.added,
            wordsUpdated = written.replaced,
            progressRestored = progressRestored,
            historyRestored = historyRestored,
        )
    }

    override suspend fun restoreReplace(
        words: List<WordEntity>,
        examples: List<WordExampleEntity>,
        progress: List<WordProgressEntity>,
        reviewLogs: List<ReviewLogEntity>,
        activity: List<DailyActivityEntity>,
    ): RestoreWriteResult = transaction {
        clearReviewLogs()
        clearProgress()
        clearExamples()
        clearWords()
        clearActivity()
        val added = insertWords(words).count { it != IGNORED }
        insertExamples(examples)
        val progressRestored = if (progress.isEmpty()) 0 else insertProgress(progress).count { it != IGNORED }
        val historyRestored = if (reviewLogs.isEmpty()) 0 else insertReviewLogs(reviewLogs).count { it != IGNORED }
        if (activity.isNotEmpty()) insertActivity(activity)
        RestoreWriteResult(
            added,
            wordsUpdated = 0,
            progressRestored = progressRestored,
            historyRestored = historyRestored,
        )
    }

    // ----------------------------------------------------------------- helpers

    private suspend fun <T> transaction(block: suspend () -> T): T {
        val snapshot = snapshot()
        return try {
            block()
        } catch (e: Exception) {
            restore(snapshot)
            throw e
        }
    }

    private fun snapshot() = Snapshot(
        words = words.value,
        examples = examples.value,
        progress = progress.value,
        logs = logs.value,
        activity = activity.value,
        nextLogId = nextLogId,
    )

    private fun restore(snapshot: Snapshot) {
        words.value = snapshot.words
        examples.value = snapshot.examples
        progress.value = snapshot.progress
        logs.value = snapshot.logs
        activity.value = snapshot.activity
        nextLogId = snapshot.nextLogId
    }

    /** Room would reject a child row whose parent is missing; the fake does the same. */
    private fun requireParents(wordIds: List<String>) {
        val missing = wordIds.filterNot { id -> words.value.any { it.id == id } }
        check(missing.isEmpty()) { "FOREIGN KEY constraint failed: $missing" }
    }

    /** The unique (session, word, kind) index of `review_log`, which makes a repeated answer a no-op. */
    private fun hasLog(candidate: ReviewLogEntity): Boolean = logs.value.any { sameLog(it, candidate) }

    private fun sameLog(first: ReviewLogEntity, second: ReviewLogEntity): Boolean =
        first.sessionId == second.sessionId && first.wordId == second.wordId && first.kind == second.kind

    private fun noteWrite() {
        writeCount++
        if (writeCount == failOnWrite) throw IllegalStateException("simulated write failure")
    }

    private var rowId = 0L

    private fun nextRowId(): Long = ++rowId

    private data class Snapshot(
        val words: List<WordEntity>,
        val examples: List<WordExampleEntity>,
        val progress: List<WordProgressEntity>,
        val logs: List<ReviewLogEntity>,
        val activity: List<DailyActivityEntity>,
        val nextLogId: Long,
    )

    private companion object {
        const val IGNORED = -1L
    }
}

/** Connectivity that a test can flip, so offline behaviour is exercised without a device. */
class FakeConnectivityObserver(online: Boolean = true) : ConnectivityObserver {
    val state = MutableStateFlow(online)

    override val isOnline: Flow<Boolean> = state

    fun setOnline(online: Boolean) {
        state.value = online
    }
}

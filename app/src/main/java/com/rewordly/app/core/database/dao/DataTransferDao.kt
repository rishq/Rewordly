package com.rewordly.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.rewordly.app.core.database.entity.DailyActivityEntity
import com.rewordly.app.core.database.entity.ReviewLogEntity
import com.rewordly.app.core.database.entity.WordEntity
import com.rewordly.app.core.database.entity.WordExampleEntity
import com.rewordly.app.core.database.entity.WordProgressEntity

/**
 * How many ids fit into a single `IN (...)` clause. Comfortably below the 999 bind variables that
 * older SQLite builds - the ones shipped with the oldest supported Android versions - accept.
 */
internal const val SQLITE_BIND_LIMIT = 500

/** `@Insert(IGNORE)` reports a skipped row as this row id. */
private const val IGNORED_ROW = -1L

/** What an import actually wrote. */
data class ImportWriteResult(val added: Int, val replaced: Int)

/** What a restore actually wrote. */
data class RestoreWriteResult(
    val wordsAdded: Int,
    val wordsUpdated: Int,
    val progressRestored: Int,
    val historyRestored: Int,
)

/**
 * Bulk reads and transactional writes for import, export and backup.
 *
 * Every write uses [Insert] with [OnConflictStrategy.IGNORE] plus [Update], never `REPLACE`.
 * `REPLACE` deletes the conflicting row before inserting the new one, which fires the
 * `ON DELETE CASCADE` on `word_examples`, `word_progress` and `review_log` and would silently
 * destroy learning progress - exactly what an import or a restore must preserve.
 *
 * The multi-table writes are `@Transaction` methods, so a failure halfway through rolls back
 * completely instead of leaving orphaned examples or progress rows behind. Child rows are only ever
 * written for words the transaction has just inserted or updated; a progress or history row that
 * pointed at a missing word would violate the foreign key and abort the whole restore.
 */
@Dao
interface DataTransferDao {
    // ------------------------------------------------------------------ reads

    @Query("SELECT * FROM words ORDER BY created_at ASC, text ASC")
    suspend fun allWords(): List<WordEntity>

    @Query("SELECT * FROM word_examples ORDER BY word_id ASC, position ASC")
    suspend fun allExamples(): List<WordExampleEntity>

    @Query("SELECT * FROM word_progress")
    suspend fun allProgress(): List<WordProgressEntity>

    @Query("SELECT * FROM review_log ORDER BY reviewed_at ASC")
    suspend fun allReviewLogs(): List<ReviewLogEntity>

    @Query("SELECT * FROM daily_activity ORDER BY day ASC")
    suspend fun allActivity(): List<DailyActivityEntity>

    @Query("SELECT COUNT(*) FROM words")
    suspend fun wordCount(): Int

    @Query("SELECT COUNT(*) FROM word_examples")
    suspend fun exampleCount(): Int

    @Query("SELECT COUNT(*) FROM word_progress")
    suspend fun progressCount(): Int

    @Query("SELECT COUNT(*) FROM review_log")
    suspend fun reviewLogCount(): Int

    @Query("SELECT COUNT(*) FROM daily_activity")
    suspend fun activityCount(): Int

    /**
     * Existing words whose trimmed, lower-cased text matches one of [keys]. Used to detect duplicates.
     * Callers chunk [keys]: SQLite caps how many bind variables a single statement may carry.
     */
    @Query("SELECT id, text FROM words WHERE language = :language AND LOWER(TRIM(text)) IN (:keys)")
    suspend fun findWordsByNormalizedText(language: String, keys: List<String>): List<WordKeyRow>

    /** Callers chunk [ids] for the same reason as [findWordsByNormalizedText]. */
    @Query("SELECT * FROM words WHERE id IN (:ids)")
    suspend fun wordsByIds(ids: List<String>): List<WordEntity>

    // ----------------------------------------------------------------- writes

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertWords(words: List<WordEntity>): List<Long>

    @Update
    suspend fun updateWords(words: List<WordEntity>): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertExamples(examples: List<WordExampleEntity>)

    /** One bind variable per word id: callers chunk long lists, see [SQLITE_BIND_LIMIT]. */
    @Query("DELETE FROM word_examples WHERE word_id IN (:wordIds)")
    suspend fun deleteExamplesOf(wordIds: List<String>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertProgress(rows: List<WordProgressEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertReviewLogs(rows: List<ReviewLogEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertActivity(rows: List<DailyActivityEntity>): List<Long>

    @Query("DELETE FROM review_log")
    suspend fun clearReviewLogs()

    @Query("DELETE FROM word_progress")
    suspend fun clearProgress()

    @Query("DELETE FROM word_examples")
    suspend fun clearExamples()

    @Query("DELETE FROM words")
    suspend fun clearWords()

    @Query("DELETE FROM daily_activity")
    suspend fun clearActivity()

    // ----------------------------------------------------------- transactions

    /**
     * Writes a whole import in one transaction: the content of replaced words is overwritten in place
     * and brand new words are inserted. Progress rows are never touched, so replacing the text of a
     * word keeps the schedule and the answers the user already gave.
     */
    @Transaction
    suspend fun writeImport(
        newWords: List<WordEntity>,
        newExamples: List<WordExampleEntity>,
        replacedWords: List<WordEntity>,
        replacedExamples: List<WordExampleEntity>,
    ): ImportWriteResult {
        var replaced = 0
        if (replacedWords.isNotEmpty()) {
            replacedWords.map { it.id }.chunked(SQLITE_BIND_LIMIT).forEach { deleteExamplesOf(it) }
            replaced = updateWords(replacedWords)
            insertExamples(replacedExamples)
        }
        val added = if (newWords.isEmpty()) 0 else insertWords(newWords).count { it != IGNORED_ROW }
        if (newExamples.isNotEmpty()) insertExamples(newExamples)
        return ImportWriteResult(added = added, replaced = replaced)
    }

    /**
     * Adds what the backup has and the device does not, and refreshes the words that match by id.
     * Existing progress and history are kept: they are only inserted when the device has none.
     */
    @Transaction
    suspend fun restoreMerge(
        newWords: List<WordEntity>,
        newExamples: List<WordExampleEntity>,
        updatedWords: List<WordEntity>,
        updatedExamples: List<WordExampleEntity>,
        progress: List<WordProgressEntity>,
        reviewLogs: List<ReviewLogEntity>,
        activity: List<DailyActivityEntity>,
    ): RestoreWriteResult {
        val written = writeImport(
            newWords = newWords,
            newExamples = newExamples,
            replacedWords = updatedWords,
            replacedExamples = updatedExamples,
        )
        val progressRestored = if (progress.isEmpty()) 0 else insertProgress(progress).count { it != IGNORED_ROW }
        val historyRestored = if (reviewLogs.isEmpty()) 0 else insertReviewLogs(reviewLogs).count { it != IGNORED_ROW }
        if (activity.isNotEmpty()) insertActivity(activity)
        return RestoreWriteResult(
            wordsAdded = written.added,
            wordsUpdated = written.replaced,
            progressRestored = progressRestored,
            historyRestored = historyRestored,
        )
    }

    /**
     * Wipes the learning data and writes the backup in its place, all inside one transaction. The
     * child tables are cleared before `words` so the result does not depend on cascade behaviour.
     */
    @Transaction
    suspend fun restoreReplace(
        words: List<WordEntity>,
        examples: List<WordExampleEntity>,
        progress: List<WordProgressEntity>,
        reviewLogs: List<ReviewLogEntity>,
        activity: List<DailyActivityEntity>,
    ): RestoreWriteResult {
        clearReviewLogs()
        clearProgress()
        clearExamples()
        clearWords()
        clearActivity()
        val added = insertWords(words).count { it != IGNORED_ROW }
        insertExamples(examples)
        val progressRestored = if (progress.isEmpty()) 0 else insertProgress(progress).count { it != IGNORED_ROW }
        val historyRestored = if (reviewLogs.isEmpty()) 0 else insertReviewLogs(reviewLogs).count { it != IGNORED_ROW }
        if (activity.isNotEmpty()) insertActivity(activity)
        return RestoreWriteResult(
            wordsAdded = added,
            wordsUpdated = 0,
            progressRestored = progressRestored,
            historyRestored = historyRestored,
        )
    }
}

package com.rewordly.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.rewordly.app.core.database.entity.PopulatedWord
import com.rewordly.app.core.database.entity.WordEntity
import com.rewordly.app.core.database.entity.WordExampleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WordDao {
    @Query("SELECT COUNT(*) FROM words")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM words WHERE language = :language")
    fun observeCount(language: String): Flow<Int>

    @Transaction
    @Query("SELECT * FROM words WHERE language = :language ORDER BY created_at ASC, text ASC")
    fun observeAll(language: String): Flow<List<PopulatedWord>>

    @Transaction
    @Query(
        """
        SELECT words.* FROM words
        LEFT JOIN word_progress ON word_progress.word_id = words.id
        WHERE words.language = :language
        ORDER BY COALESCE(word_progress.updated_at, words.created_at) DESC
        LIMIT :limit
        """,
    )
    fun observeRecent(language: String, limit: Int): Flow<List<PopulatedWord>>

    @Transaction
    @Query(
        """
        SELECT * FROM words
        WHERE language = :language
          AND id IN (SELECT word_id FROM word_progress WHERE is_saved = 1)
        ORDER BY text ASC
        """,
    )
    fun observeSaved(language: String): Flow<List<PopulatedWord>>

    @Transaction
    @Query("SELECT * FROM words WHERE id = :id")
    fun observeById(id: String): Flow<PopulatedWord?>

    /** Only the requested rows, for callers that know exactly which words they need (e.g. a session). */
    @Transaction
    @Query("SELECT * FROM words WHERE id IN (:ids)")
    fun observeByIds(ids: List<String>): Flow<List<PopulatedWord>>

    /** Existing words whose lower-cased, trimmed text is one of [keys]; used to detect duplicates. */
    @Query("SELECT id, text FROM words WHERE language = :language AND LOWER(TRIM(text)) IN (:keys)")
    suspend fun findByNormalizedText(language: String, keys: List<String>): List<WordKeyRow>

    /** Full rows for duplicate detection during pack sync; the caller filters by source. */
    @Query("SELECT * FROM words WHERE language = :language AND LOWER(TRIM(text)) IN (:keys) AND source IN (:sources)")
    suspend fun findRows(language: String, sources: List<String>, keys: List<String>): List<WordEntity>

    /** Full rows by id, for callers that already know exactly which words they update. */
    @Query("SELECT * FROM words WHERE id IN (:ids)")
    suspend fun findRowsByIds(ids: List<String>): List<WordEntity>

    @Update
    suspend fun updateWords(words: List<WordEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertWords(words: List<WordEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertExamples(examples: List<WordExampleEntity>)

    @Query("DELETE FROM word_examples WHERE word_id IN (:wordIds)")
    suspend fun removeExamples(wordIds: List<String>)

    @Transaction
    suspend fun insertVocabulary(words: List<WordEntity>, examples: List<WordExampleEntity>) {
        insertWords(words)
        insertExamples(examples)
    }

    /** Replaces the examples of [wordIds] with [examples] in one transaction. */
    @Transaction
    suspend fun replaceExamples(wordIds: List<String>, examples: List<WordExampleEntity>) {
        if (wordIds.isEmpty()) return
        removeExamples(wordIds)
        insertExamples(examples)
    }
}

data class WordKeyRow(val id: String, val text: String)

package com.rewordly.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertWords(words: List<WordEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertExamples(examples: List<WordExampleEntity>)

    @Transaction
    suspend fun insertVocabulary(words: List<WordEntity>, examples: List<WordExampleEntity>) {
        insertWords(words)
        insertExamples(examples)
    }
}

data class WordKeyRow(val id: String, val text: String)

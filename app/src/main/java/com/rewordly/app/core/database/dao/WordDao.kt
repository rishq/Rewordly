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
    @Query("SELECT * FROM words WHERE id = :id")
    fun observeById(id: String): Flow<PopulatedWord?>

    @Transaction
    @Query(
        """
        SELECT * FROM words
        WHERE language = :language AND (text LIKE :pattern ESCAPE '\' OR translation LIKE :pattern ESCAPE '\')
        ORDER BY CASE WHEN text LIKE :prefix ESCAPE '\' THEN 0 ELSE 1 END, text ASC
        LIMIT 50
        """,
    )
    fun search(language: String, pattern: String, prefix: String): Flow<List<PopulatedWord>>

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

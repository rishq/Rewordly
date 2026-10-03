package com.rewordly.app.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        RewordlyDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    private fun insertWord(db: SupportSQLiteDatabase, id: String) {
        db.execSQL(
            "INSERT INTO words (id, language, text, translation, translation_language, pronunciation, " +
                "part_of_speech, difficulty, definition_en, definition_ru, forms, related_words, synonyms, " +
                "audio_url, created_at) VALUES ('$id', 'en', '$id', 'x', 'ru', '', 'NOUN', 'A1', '', '', " +
                "'[]', '[]', '[]', NULL, 0)",
        )
    }

    private fun insertProgress(db: SupportSQLiteDatabase, id: String, status: String, saved: Int) {
        db.execSQL(
            "INSERT INTO word_progress (word_id, status, is_saved, views, correct_answers, incorrect_answers, " +
                "last_viewed_at, last_reviewed_at, next_review_at, updated_at) " +
                "VALUES ('$id', '$status', $saved, 4, 3, 1, NULL, 100, NULL, 100)",
        )
    }

    @Test
    fun migrate2To3_keepsProgress_andSchedulesLearnedWordsAsDue() {
        helper.createDatabase(TEST_DB, 2).apply {
            insertWord(this, "learned")
            insertWord(this, "fresh")
            insertProgress(this, "learned", "LEARNED", saved = 1)
            insertProgress(this, "fresh", "NEW", saved = 0)
            execSQL("INSERT INTO daily_activity (day, words_learned, words_reviewed) VALUES ('2024-05-10', 2, 1)")
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_2_3)

        db.query(
            "SELECT is_saved, views, correct_answers, ease_factor, repetition_count, interval_days, next_review_at " +
                "FROM word_progress WHERE word_id = 'learned'",
        ).use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
            assertEquals(4, it.getInt(1))
            assertEquals(3, it.getInt(2))
            assertEquals(2.5, it.getDouble(3), 0.0)
            assertEquals(0, it.getInt(4))
            assertEquals(1, it.getInt(5))
            assertTrue("learned words become due now", it.getLong(6) > 0)
        }
        db.query("SELECT next_review_at FROM word_progress WHERE word_id = 'fresh'").use {
            assertTrue(it.moveToFirst())
            assertTrue(it.isNull(0))
        }
        db.query("SELECT words_learned FROM daily_activity").use {
            assertTrue(it.moveToFirst())
            assertEquals(2, it.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM review_log").use {
            assertTrue(it.moveToFirst())
            assertEquals(0, it.getInt(0))
        }
    }

    @Test
    fun migrateAll_fromVersion1_reachesTheLatestSchema() {
        helper.createDatabase(TEST_DB, 1).close()
        helper.runMigrationsAndValidate(TEST_DB, 5, true, MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
    }

    @Test
    fun migrate3To4_marksExistingWordsAsBundled_andKeepsProgressAndReviewHistory() {
        helper.createDatabase(TEST_DB, 3).apply {
            insertWord(this, "kept")
            execSQL(
                "INSERT INTO word_progress (word_id, status, is_saved, views, correct_answers, incorrect_answers, " +
                    "last_viewed_at, last_reviewed_at, next_review_at, updated_at, repetition_count, ease_factor, " +
                    "interval_days, consecutive_correct, consecutive_incorrect) " +
                    "VALUES ('kept', 'LEARNED', 1, 2, 5, 1, NULL, 100, 200, 100, 3, 2.1, 9, 2, 0)",
            )
            execSQL(
                "INSERT INTO review_log (word_id, session_id, kind, quality, reviewed_at, duration_ms, " +
                    "interval_before, interval_after) VALUES ('kept', 's1', 'REVIEW', 4, 100, 3000, 3, 9)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 4, true, MIGRATION_3_4)

        db.query("SELECT source FROM words WHERE id = 'kept'").use {
            assertTrue(it.moveToFirst())
            assertEquals("BUNDLED", it.getString(0))
        }
        db.query("SELECT repetition_count, ease_factor, interval_days, next_review_at FROM word_progress").use {
            assertTrue(it.moveToFirst())
            assertEquals(3, it.getInt(0))
            assertEquals(2.1, it.getDouble(1), 0.0)
            assertEquals(9, it.getInt(2))
            assertEquals(200L, it.getLong(3))
        }
        db.query("SELECT COUNT(*) FROM review_log").use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM generation_history").use {
            assertTrue(it.moveToFirst())
            assertEquals(0, it.getInt(0))
        }
    }

    @Test
    fun migrate4To5_addsTheTopicColumnWithoutTouchingExistingWords() {
        helper.createDatabase(TEST_DB, 4).apply {
            insertWord(this, "kept")
            execSQL("UPDATE words SET source = 'GENERATED' WHERE id = 'kept'")
            execSQL(
                "INSERT INTO word_progress (word_id, status, is_saved, views, correct_answers, incorrect_answers, " +
                    "last_viewed_at, last_reviewed_at, next_review_at, updated_at, repetition_count, ease_factor, " +
                    "interval_days, consecutive_correct, consecutive_incorrect) " +
                    "VALUES ('kept', 'LEARNED', 1, 2, 5, 1, NULL, 100, 200, 100, 3, 2.1, 9, 2, 0)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 5, true, MIGRATION_4_5)

        db.query("SELECT text, source, topic FROM words WHERE id = 'kept'").use {
            assertTrue(it.moveToFirst())
            assertEquals("kept", it.getString(0))
            assertEquals("GENERATED", it.getString(1))
            assertEquals("", it.getString(2))
        }
        db.query("SELECT repetition_count, interval_days FROM word_progress WHERE word_id = 'kept'").use {
            assertTrue(it.moveToFirst())
            assertEquals(3, it.getInt(0))
            assertEquals(9, it.getInt(1))
        }
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}

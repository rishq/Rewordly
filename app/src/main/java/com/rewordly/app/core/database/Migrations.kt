package com.rewordly.app.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** v1 -> v2: word definitions, per-word view counters and a real daily activity table. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE words ADD COLUMN definition_en TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE words ADD COLUMN definition_ru TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE word_progress ADD COLUMN views INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE word_progress ADD COLUMN last_viewed_at INTEGER")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_word_progress_is_saved ON word_progress (is_saved)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS daily_activity (
                day TEXT NOT NULL,
                words_learned INTEGER NOT NULL,
                words_reviewed INTEGER NOT NULL,
                PRIMARY KEY(day)
            )
            """.trimIndent(),
        )
    }
}

/**
 * v2 -> v3: spaced-repetition columns on word_progress and the review_log history table.
 * Words learned before this version get a schedule that makes them due immediately, so no progress is lost.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE word_progress ADD COLUMN repetition_count INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE word_progress ADD COLUMN ease_factor REAL NOT NULL DEFAULT 2.5")
        db.execSQL("ALTER TABLE word_progress ADD COLUMN interval_days INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE word_progress ADD COLUMN consecutive_correct INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE word_progress ADD COLUMN consecutive_incorrect INTEGER NOT NULL DEFAULT 0")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_word_progress_next_review_at ON word_progress (next_review_at)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS review_log (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                word_id TEXT NOT NULL,
                session_id TEXT NOT NULL,
                kind TEXT NOT NULL,
                quality INTEGER NOT NULL,
                reviewed_at INTEGER NOT NULL,
                duration_ms INTEGER,
                interval_before INTEGER NOT NULL,
                interval_after INTEGER NOT NULL,
                FOREIGN KEY(word_id) REFERENCES words(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_review_log_session_id_word_id_kind " +
                "ON review_log (session_id, word_id, kind)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_review_log_reviewed_at ON review_log (reviewed_at)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_review_log_word_id ON review_log (word_id)")
        // Words already learned or in progress become due now with a one day interval.
        db.execSQL(
            """
            UPDATE word_progress
            SET next_review_at = CAST(strftime('%s', 'now') AS INTEGER) * 1000, interval_days = 1
            WHERE status IN ('LEARNED', 'LEARNING') AND next_review_at IS NULL
            """.trimIndent(),
        )
    }
}

/**
 * v3 -> v4: words remember whether they were saved from an AI generation, and generation history gets a table.
 * Existing words keep their ids, progress and review history; they are all marked BUNDLED.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE words ADD COLUMN source TEXT NOT NULL DEFAULT 'BUNDLED'")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS generation_history (
                id TEXT NOT NULL,
                mode TEXT NOT NULL,
                description TEXT NOT NULL,
                level TEXT NOT NULL,
                requested_count INTEGER NOT NULL,
                result_count INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                result_json TEXT,
                PRIMARY KEY(id)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_generation_history_created_at ON generation_history (created_at)")
    }
}

/**
 * v4 -> v5: words can carry the topic an imported file provided.
 *
 * Purely additive: the column defaults to an empty string, so every existing word, its progress and
 * its review history are untouched, and the topic taxonomy keeps categorising them as before.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE words ADD COLUMN topic TEXT NOT NULL DEFAULT ''")
    }
}

package com.rewordly.app.domain.repository

import com.rewordly.app.core.common.AppResult
import com.rewordly.app.domain.model.BackupIssue
import com.rewordly.app.domain.model.BackupPreview
import com.rewordly.app.domain.model.DataStats
import com.rewordly.app.domain.model.DuplicatePolicy
import com.rewordly.app.domain.model.ImportPreview
import com.rewordly.app.domain.model.ImportSummary
import com.rewordly.app.domain.model.RestoreStrategy
import com.rewordly.app.domain.model.RestoreSummary
import com.rewordly.app.domain.model.VocabularyFileFormat

/**
 * Import, export and backup of the local learning data.
 *
 * Nothing here talks to a server: files are read from and written to locations the user picked, and
 * every write goes through a database transaction.
 */
interface DataManagementRepository {
    /** Sizes of the local collections, used to describe what an export will contain. */
    suspend fun stats(): AppResult<DataStats>

    /**
     * Parses and validates [content] without touching the database, so the user can review the
     * entries, the rejected rows and the likely duplicates before anything is imported.
     */
    suspend fun previewImport(fileName: String, content: String): AppResult<ImportPreview>

    /** Writes the entries of [preview] according to [policy]. Returns what actually changed. */
    suspend fun applyImport(preview: ImportPreview, policy: DuplicatePolicy): AppResult<ImportSummary>

    /** The vocabulary as CSV or JSON. Empty collections still produce a valid file. */
    suspend fun exportVocabulary(format: VocabularyFileFormat): AppResult<String>

    /** A complete local backup: vocabulary, examples, progress, history and relevant settings. */
    suspend fun exportBackup(): AppResult<String>

    /**
     * Reads a backup without writing anything, so the user can be told what it holds - and warned
     * when it is unusable - before a restore is confirmed.
     */
    suspend fun inspectBackup(content: String): AppResult<BackupInspection>

    /** Validates [content] and writes it. [RestoreOutcome.Rejected] means the file was not a usable backup. */
    suspend fun restoreBackup(content: String, strategy: RestoreStrategy): AppResult<RestoreOutcome>
}

/** Either the backup was applied, or it was refused before the database was touched. */
sealed interface RestoreOutcome {
    data class Restored(val summary: RestoreSummary) : RestoreOutcome

    data class Rejected(val issue: BackupIssue) : RestoreOutcome
}

/** What a backup file turned out to be, decided before the database is touched. */
sealed interface BackupInspection {
    data class Valid(val preview: BackupPreview) : BackupInspection

    data class Rejected(val issue: BackupIssue) : BackupInspection
}

package com.rewordly.app.domain.model

/** File formats the app can read vocabulary from and write it to. */
enum class VocabularyFileFormat { CSV, JSON }

/**
 * What to do with an imported entry whose word already exists in the vocabulary.
 *
 * Merging two entries with possibly different meanings is never automatic: the user picks one of these.
 */
enum class DuplicatePolicy {
    /** Keep the word that is already there and ignore the imported entry. */
    SKIP,

    /** Overwrite the vocabulary content (translation, definition, examples) but keep all learning progress. */
    REPLACE,

    /** Add the imported entry as a second word, so both meanings stay available. */
    KEEP_BOTH,
}

/** How a restore treats the data that is already on the device. */
enum class RestoreStrategy {
    /** Add what is missing and update what matches; nothing is deleted. */
    MERGE,

    /** Clear the local vocabulary, progress and history, then write the backup. Requires confirmation. */
    REPLACE,
}

/** One vocabulary entry in a format-independent shape, as read from or written to a file. */
data class VocabularyEntry(
    val text: String,
    val translation: String,
    val partOfSpeech: String = "",
    val definition: String = "",
    val definitionTranslation: String = "",
    val examples: List<VocabularyEntryExample> = emptyList(),
    val topic: String = "",
    val difficulty: String = "",
)

/** One example sentence with its translation, in the same shape as the rest of the app. */
data class VocabularyEntryExample(val english: String, val russian: String)

/** Why a row of an imported file was rejected. Mapped to a localized message by the UI. */
enum class ImportIssueReason {
    MISSING_WORD,
    MISSING_TRANSLATION,
    MALFORMED_ROW,
    INVALID_FORMAT,
    UNSUPPORTED_VERSION,
    TOO_LONG,
    LIMIT_EXCEEDED,
}

/** A rejected row. [row] is the 1-based line in the file, or 0 when the whole file is unusable. */
data class ImportIssue(val row: Int, val reason: ImportIssueReason)

/** Where the word of an imported entry already appears. */
enum class DuplicateKind {
    NONE,

    /** The same word appears more than once inside the file being imported. */
    WITHIN_FILE,

    /** The word is already in the user's vocabulary. */
    EXISTING,
}

/** One validated entry together with the 1-based row it was read from. */
data class ParsedEntry(val row: Int, val entry: VocabularyEntry)

/** One importable entry with everything the preview needs to describe it. */
data class ImportCandidate(
    val entry: VocabularyEntry,
    val row: Int,
    val duplicate: DuplicateKind,
    /** Id of the existing word when [duplicate] is [DuplicateKind.EXISTING]. */
    val existingWordId: String? = null,
) {
    val isDuplicate: Boolean get() = duplicate != DuplicateKind.NONE
}

/** The result of reading a file, before anything is written to the database. */
data class ImportPreview(
    val format: VocabularyFileFormat,
    val candidates: List<ImportCandidate>,
    val issues: List<ImportIssue>,
    /** True when the file had more entries than the configured limit and the rest was ignored. */
    val truncated: Boolean = false,
) {
    val entryCount: Int get() = candidates.size

    val issueCount: Int get() = issues.size

    val duplicateCount: Int get() = candidates.count { it.isDuplicate }

    val newCount: Int get() = candidates.count { !it.isDuplicate }

    val hasContent: Boolean get() = candidates.isNotEmpty()
}

/** What an applied import actually changed. */
data class ImportSummary(val added: Int, val replaced: Int, val skipped: Int) {
    val total: Int get() = added + replaced + skipped
}

/** What a restore actually wrote. */
data class RestoreSummary(
    val wordsAdded: Int,
    val wordsUpdated: Int,
    val progressRestored: Int,
    val historyRestored: Int,
)

/** Sizes of the local collections, shown before an export or a backup. */
data class DataStats(
    val words: Int = 0,
    val examples: Int = 0,
    val trackedWords: Int = 0,
    val reviewLogEntries: Int = 0,
    val activeDays: Int = 0,
) {
    val isEmpty: Boolean get() = words == 0
}

/** What a backup file holds, read without writing anything so the user can confirm first. */
data class BackupPreview(
    val createdAt: Long,
    val appVersion: String,
    val words: Int,
    val examples: Int,
    val progress: Int,
    val historyEntries: Int,
)

/**
 * How much a picked file may weigh. Files are refused by size before they are read into memory, so a
 * mistaken pick - a video, a whole disk image - cannot exhaust it. Entry counts and field lengths are
 * capped separately: by [ImportLimits] for a vocabulary file, and by [BackupLimits] for a backup.
 */
object DataTransferLimits {
    /** Generous for a vocabulary file, small enough that holding it in memory stays harmless. */
    const val MAX_IMPORT_BYTES = 8L * 1024 * 1024

    /** A backup also carries progress, history and settings, so it gets a bigger allowance. */
    const val MAX_BACKUP_BYTES = 32L * 1024 * 1024
}

/**
 * Ceilings for a backup that is about to be restored.
 *
 * A backup is the app's own full-state file, so it deliberately does not inherit
 * [com.rewordly.app.domain.service.ImportLimits]: those would refuse a legitimate file from a user with a
 * large vocabulary. It still needs a bound of its own, because the byte cap above allows tens of megabytes
 * of JSON - a crafted file of minimal entries would otherwise become unbounded rows inside one transaction.
 */
data class BackupLimits(
    val maxWords: Int = DEFAULT_MAX_WORDS,
    val maxFieldLength: Int = DEFAULT_MAX_FIELD_LENGTH,
) {
    companion object {
        /** Twenty-five times what a single vocabulary import accepts, which no realistic vocabulary reaches. */
        const val DEFAULT_MAX_WORDS = 50_000

        /** Ten times the import cap: room for a long definition, not enough to bloat a row. */
        const val DEFAULT_MAX_FIELD_LENGTH = 5_000
    }
}

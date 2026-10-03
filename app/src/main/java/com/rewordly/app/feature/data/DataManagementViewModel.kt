package com.rewordly.app.feature.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rewordly.app.core.common.AppError
import com.rewordly.app.core.common.AppResult
import com.rewordly.app.core.storage.DocumentStore
import com.rewordly.app.domain.model.BackupIssue
import com.rewordly.app.domain.model.BackupPreview
import com.rewordly.app.domain.model.DataStats
import com.rewordly.app.domain.model.DataTransferLimits
import com.rewordly.app.domain.model.DuplicatePolicy
import com.rewordly.app.domain.model.ImportPreview
import com.rewordly.app.domain.model.ImportSummary
import com.rewordly.app.domain.model.RestoreStrategy
import com.rewordly.app.domain.model.RestoreSummary
import com.rewordly.app.domain.model.VocabularyFileFormat
import com.rewordly.app.domain.repository.BackupInspection
import com.rewordly.app.domain.repository.DataManagementRepository
import com.rewordly.app.domain.repository.RestoreOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the user is exporting. Each kind gets its own suggested file name. */
enum class ExportKind { CSV, JSON, BACKUP }

/** A parsed import waiting for the user to pick how duplicates are handled. */
data class ImportPrompt(
    val fileName: String?,
    val preview: ImportPreview,
    val policy: DuplicatePolicy = DuplicatePolicy.SKIP,
)

/** A validated backup waiting for the user to confirm the restore and choose a strategy. */
data class RestorePrompt(
    val fileName: String?,
    val content: String,
    val preview: BackupPreview,
    val strategy: RestoreStrategy = RestoreStrategy.MERGE,
    /** Set once the user picked [RestoreStrategy.REPLACE] and has to confirm the destructive step. */
    val confirmReplace: Boolean = false,
)

/** The last finished operation, shown as a banner until it is dismissed. */
sealed interface DataOutcome {
    data class Imported(val summary: ImportSummary) : DataOutcome

    data class Exported(val kind: ExportKind) : DataOutcome

    data class Restored(val summary: RestoreSummary) : DataOutcome

    /** The file could not be read or written. */
    data class Failed(val error: AppError) : DataOutcome

    /** The file was readable but is not a backup this app can restore. */
    data class BackupRejected(val issue: BackupIssue) : DataOutcome
}

data class DataManagementUiState(
    val stats: DataStats = DataStats(),
    val isLoading: Boolean = true,
    /** True while a file is being read, parsed or written; the screen shows progress and blocks input. */
    val isBusy: Boolean = false,
    val importPrompt: ImportPrompt? = null,
    val restorePrompt: RestorePrompt? = null,
    val outcome: DataOutcome? = null,
)

sealed interface DataManagementEvent {
    /** The system file picker returned a document to read vocabulary from. */
    data class ImportFilePicked(val handle: String) : DataManagementEvent

    data class ImportPolicyChanged(val policy: DuplicatePolicy) : DataManagementEvent

    data object ConfirmImport : DataManagementEvent

    data object DismissImport : DataManagementEvent

    /** A destination was chosen for [kind]. [handle] is the document the export is written to. */
    data class ExportDestinationPicked(val kind: ExportKind, val handle: String) : DataManagementEvent

    /** The system file picker returned a document to restore from. */
    data class RestoreFilePicked(val handle: String) : DataManagementEvent

    data class RestoreStrategyChanged(val strategy: RestoreStrategy) : DataManagementEvent

    /** The user asked to restore; [RestoreStrategy.REPLACE] first opens the warning. */
    data object ConfirmRestore : DataManagementEvent

    data object ConfirmReplaceRestore : DataManagementEvent

    /** The warning about replacing everything was closed without restoring. */
    data object DismissReplaceWarning : DataManagementEvent

    data object DismissRestore : DataManagementEvent

    data object DismissOutcome : DataManagementEvent
}

/**
 * Drives import, export and restore.
 *
 * The screen never sees a byte of the picked file: it hands over the document handle and gets back a
 * summary. Nothing is written to the database until the user confirms, and every long step runs in
 * [viewModelScope] so navigating away cancels it instead of leaving work running in the background.
 */
@HiltViewModel
class DataManagementViewModel @Inject constructor(
    private val repository: DataManagementRepository,
    private val documentStore: DocumentStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DataManagementUiState())
    val uiState: StateFlow<DataManagementUiState> = _uiState.asStateFlow()

    private var job: Job? = null

    init {
        // Loading the counters is cheap and not a file operation, so it never shows as busy.
        viewModelScope.launch { loadStats() }
    }

    fun onEvent(event: DataManagementEvent) {
        when (event) {
            is DataManagementEvent.ImportFilePicked -> readImport(event.handle)
            is DataManagementEvent.ImportPolicyChanged ->
                update { copy(importPrompt = importPrompt?.copy(policy = event.policy)) }
            DataManagementEvent.ConfirmImport -> confirmImport()
            DataManagementEvent.DismissImport -> update { copy(importPrompt = null) }
            is DataManagementEvent.ExportDestinationPicked -> export(event.kind, event.handle)
            is DataManagementEvent.RestoreFilePicked -> readRestore(event.handle)
            is DataManagementEvent.RestoreStrategyChanged ->
                update { copy(restorePrompt = restorePrompt?.copy(strategy = event.strategy, confirmReplace = false)) }
            DataManagementEvent.ConfirmRestore -> confirmRestore()
            DataManagementEvent.ConfirmReplaceRestore -> restorePrompt()?.let { restore(it) }
            DataManagementEvent.DismissReplaceWarning ->
                update { copy(restorePrompt = restorePrompt?.copy(confirmReplace = false)) }
            DataManagementEvent.DismissRestore -> update { copy(restorePrompt = null) }
            DataManagementEvent.DismissOutcome -> update { copy(outcome = null) }
        }
    }

    // ------------------------------------------------------------------ import

    private fun readImport(handle: String) = start {
        val name = documentStore.displayName(handle)
        when (val read = documentStore.readText(handle, DataTransferLimits.MAX_IMPORT_BYTES)) {
            is AppResult.Failure -> fail(read.error)
            is AppResult.Success -> when (val preview = repository.previewImport(name.orEmpty(), read.data)) {
                is AppResult.Failure -> fail(preview.error)
                is AppResult.Success -> update {
                    copy(
                        importPrompt = ImportPrompt(
                            fileName = name?.takeIf { it.isNotBlank() },
                            preview = preview.data,
                        ),
                        outcome = null,
                    )
                }
            }
        }
    }

    private fun confirmImport() {
        val prompt = _uiState.value.importPrompt ?: return
        start {
            when (val applied = repository.applyImport(prompt.preview, prompt.policy)) {
                is AppResult.Success -> {
                    update { copy(importPrompt = null, outcome = DataOutcome.Imported(applied.data)) }
                    loadStats()
                }
                is AppResult.Failure -> update {
                    copy(
                        importPrompt = null,
                        outcome = DataOutcome.Failed(applied.error),
                    )
                }
            }
        }
    }

    // ------------------------------------------------------------------ export

    private fun export(kind: ExportKind, handle: String) = start {
        val produced = when (kind) {
            ExportKind.CSV -> repository.exportVocabulary(VocabularyFileFormat.CSV)
            ExportKind.JSON -> repository.exportVocabulary(VocabularyFileFormat.JSON)
            ExportKind.BACKUP -> repository.exportBackup()
        }
        when (produced) {
            is AppResult.Failure -> fail(produced.error)
            is AppResult.Success -> when (val written = documentStore.writeText(handle, produced.data)) {
                is AppResult.Failure -> fail(written.error)
                is AppResult.Success -> update { copy(outcome = DataOutcome.Exported(kind)) }
            }
        }
    }

    // ----------------------------------------------------------------- restore

    private fun readRestore(handle: String) = start {
        val name = documentStore.displayName(handle)
        when (val read = documentStore.readText(handle, DataTransferLimits.MAX_BACKUP_BYTES)) {
            is AppResult.Failure -> fail(read.error)
            is AppResult.Success -> when (val inspected = repository.inspectBackup(read.data)) {
                is AppResult.Failure -> fail(inspected.error)
                is AppResult.Success -> when (val inspection = inspected.data) {
                    is BackupInspection.Rejected ->
                        update { copy(outcome = DataOutcome.BackupRejected(inspection.issue)) }
                    is BackupInspection.Valid -> update {
                        copy(
                            restorePrompt = RestorePrompt(
                                fileName = name?.takeIf { it.isNotBlank() },
                                content = read.data,
                                preview = inspection.preview,
                            ),
                            outcome = null,
                        )
                    }
                }
            }
        }
    }

    /** Replacing everything is destructive, so it only runs after the warning has been confirmed. */
    private fun confirmRestore() {
        val prompt = restorePrompt() ?: return
        if (prompt.strategy == RestoreStrategy.REPLACE) {
            update { copy(restorePrompt = prompt.copy(confirmReplace = true)) }
        } else {
            restore(prompt)
        }
    }

    private fun restore(prompt: RestorePrompt) = start {
        when (val outcome = repository.restoreBackup(prompt.content, prompt.strategy)) {
            is AppResult.Failure -> update { copy(restorePrompt = null, outcome = DataOutcome.Failed(outcome.error)) }
            is AppResult.Success -> when (val result = outcome.data) {
                is RestoreOutcome.Rejected ->
                    update { copy(restorePrompt = null, outcome = DataOutcome.BackupRejected(result.issue)) }
                is RestoreOutcome.Restored -> {
                    update { copy(restorePrompt = null, outcome = DataOutcome.Restored(result.summary)) }
                    loadStats()
                }
            }
        }
    }

    // ----------------------------------------------------------------- helpers

    private suspend fun loadStats() {
        when (val result = repository.stats()) {
            is AppResult.Success -> update { copy(stats = result.data, isLoading = false) }
            is AppResult.Failure -> update { copy(isLoading = false) }
        }
    }

    /** Runs one file operation, keeping the busy flag accurate even when it is cancelled. */
    private fun start(block: suspend () -> Unit) {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            update { copy(isBusy = true) }
            try {
                block()
            } finally {
                update { copy(isBusy = false) }
            }
        }
    }

    private fun fail(error: AppError) = update { copy(outcome = DataOutcome.Failed(error)) }

    private fun restorePrompt(): RestorePrompt? = _uiState.value.restorePrompt

    private fun update(transform: DataManagementUiState.() -> DataManagementUiState) {
        _uiState.update(transform)
    }
}

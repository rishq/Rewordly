package com.rewordly.app.core.storage

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import com.rewordly.app.core.common.AppError
import com.rewordly.app.core.common.AppResult
import com.rewordly.app.core.common.FileProblem
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [DocumentStore] on top of the Storage Access Framework.
 *
 * Everything runs on the IO dispatcher and nothing about the file's content is ever logged: an
 * imported file is untrusted input, and vocabulary is personal data.
 */
@Singleton
class AndroidDocumentStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : DocumentStore {

    override suspend fun displayName(handle: String): String? = withContext(Dispatchers.IO) {
        queryMetadata(handle) { cursor -> cursor.getString(NAME_COLUMN) }
    }

    override suspend fun sizeBytes(handle: String): Long? = withContext(Dispatchers.IO) {
        queryMetadata(handle) { cursor -> if (cursor.isNull(SIZE_COLUMN)) null else cursor.getLong(SIZE_COLUMN) }
    }

    override suspend fun readText(handle: String, maxBytes: Long): AppResult<String> = withContext(Dispatchers.IO) {
        try {
            val stream = context.contentResolver.openInputStream(Uri.parse(handle))
                ?: return@withContext AppResult.Failure(AppError.FileAccess(FileProblem.UNREADABLE))
            stream.use { input ->
                val bytes = input.readAtMost(maxBytes + 1)
                if (bytes.size.toLong() > maxBytes) {
                    return@withContext AppResult.Failure(AppError.FileAccess(FileProblem.TOO_LARGE))
                }
                AppResult.Success(bytes.toString(Charsets.UTF_8))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            AppResult.Failure(AppError.FileAccess(FileProblem.UNREADABLE, e))
        } catch (e: SecurityException) {
            AppResult.Failure(AppError.FileAccess(FileProblem.UNREADABLE, e))
        } catch (e: IllegalArgumentException) {
            AppResult.Failure(AppError.FileAccess(FileProblem.UNREADABLE, e))
        }
    }

    override suspend fun writeText(handle: String, text: String): AppResult<Unit> = withContext(Dispatchers.IO) {
        try {
            // "wt" truncates, so a shorter export never leaves the tail of the previous one behind.
            val stream = context.contentResolver.openOutputStream(Uri.parse(handle), "wt")
                ?: return@withContext AppResult.Failure(AppError.FileAccess(FileProblem.WRITE_FAILED))
            stream.use { output -> output.write(text.toByteArray(Charsets.UTF_8)) }
            AppResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            AppResult.Failure(AppError.FileAccess(FileProblem.WRITE_FAILED, e))
        } catch (e: SecurityException) {
            AppResult.Failure(AppError.FileAccess(FileProblem.WRITE_FAILED, e))
        } catch (e: IllegalArgumentException) {
            AppResult.Failure(AppError.FileAccess(FileProblem.WRITE_FAILED, e))
        }
    }

    private fun <T> queryMetadata(handle: String, read: (Cursor) -> T?): T? = try {
        context.contentResolver.query(
            Uri.parse(handle),
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor -> if (cursor.moveToFirst()) read(cursor) else null }
    } catch (e: Exception) {
        null
    }
}

/**
 * Reads at most [limit] bytes so a document larger than the configured maximum is detected by its
 * size instead of being loaded into memory first.
 */
private fun InputStream.readAtMost(limit: Long): ByteArray {
    val collected = ByteArrayOutputStream()
    val chunk = ByteArray(DEFAULT_CHUNK)
    var total = 0L
    while (total < limit) {
        val requested = minOf(chunk.size.toLong(), limit - total).toInt()
        val read = read(chunk, 0, requested)
        if (read <= 0) break
        collected.write(chunk, 0, read)
        total += read
    }
    return collected.toByteArray()
}

private const val NAME_COLUMN = 0
private const val SIZE_COLUMN = 1
private const val DEFAULT_CHUNK = 8 * 1024

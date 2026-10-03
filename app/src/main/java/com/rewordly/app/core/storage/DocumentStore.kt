package com.rewordly.app.core.storage

import com.rewordly.app.core.common.AppResult

/**
 * Reads and writes text documents the user picked through the Storage Access Framework.
 *
 * Handles are opaque strings (a content URI in the Android implementation) so nothing above this
 * layer depends on `android.net.Uri`. SAF grants access to exactly the file the user chose, which is
 * why the app never asks for a storage permission.
 */
interface DocumentStore {
    /** A human readable file name, when the provider reports one. Never used for validation. */
    suspend fun displayName(handle: String): String?

    /** Size in bytes, or null when the provider does not report one. */
    suspend fun sizeBytes(handle: String): Long?

    /**
     * Reads the whole document as UTF-8 text.
     *
     * Fails with [com.rewordly.app.core.common.FileProblem.TOO_LARGE] when the document is bigger than
     * [maxBytes], so a huge file is refused before it is loaded into memory.
     */
    suspend fun readText(handle: String, maxBytes: Long): AppResult<String>

    /** Writes [text] as UTF-8, replacing whatever the destination holds. */
    suspend fun writeText(handle: String, text: String): AppResult<Unit>
}

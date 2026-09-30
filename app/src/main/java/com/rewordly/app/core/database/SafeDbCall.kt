package com.rewordly.app.core.database

import com.rewordly.app.core.common.AppError
import com.rewordly.app.core.common.AppResult
import kotlinx.coroutines.CancellationException

/** Executes a database operation and maps failures into [AppError.Database]. */
suspend fun <T> safeDbCall(block: suspend () -> T): AppResult<T> = try {
    AppResult.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    AppResult.Failure(AppError.Database(e))
}

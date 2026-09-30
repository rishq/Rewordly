package com.rewordly.app.core.network

import com.rewordly.app.core.common.AppError
import com.rewordly.app.core.common.AppResult
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

/** Executes a network call and maps any failure into an [AppError]. */
suspend fun <T> safeApiCall(call: suspend () -> T): AppResult<T> = try {
    AppResult.Success(call())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    AppResult.Failure(e.toNetworkAppError())
}

fun Throwable.toNetworkAppError(): AppError = when (this) {
    is SocketTimeoutException -> AppError.Timeout(this)
    is InterruptedIOException -> AppError.Timeout(this)
    is IOException -> AppError.Network(this)
    is HttpException -> when (code()) {
        401, 403 -> AppError.Unauthorized(this)
        else -> AppError.Server(code(), this)
    }
    is SerializationException -> AppError.Unknown(this)
    else -> AppError.Unknown(this)
}

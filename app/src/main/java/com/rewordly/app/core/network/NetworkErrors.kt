package com.rewordly.app.core.network

import com.rewordly.app.core.common.AppError
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

/** Maps a failed HTTP call into the app's normalized [AppError]. */
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

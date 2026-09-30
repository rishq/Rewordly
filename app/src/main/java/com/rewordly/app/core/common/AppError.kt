package com.rewordly.app.core.common

/** Normalized error model shared by every layer. UI maps it to user-facing text. */
sealed interface AppError {
    val cause: Throwable?

    data class Network(override val cause: Throwable? = null) : AppError

    data class Timeout(override val cause: Throwable? = null) : AppError

    data class Server(val code: Int, override val cause: Throwable? = null) : AppError

    data class Unauthorized(override val cause: Throwable? = null) : AppError

    data object EmptyResponse : AppError {
        override val cause: Throwable? = null
    }

    data class Database(override val cause: Throwable? = null) : AppError

    data class Unknown(override val cause: Throwable? = null) : AppError
}

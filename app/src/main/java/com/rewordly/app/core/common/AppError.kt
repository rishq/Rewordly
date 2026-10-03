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

    /** The backend answered 429. [retryAfterSeconds] comes from the response when it is provided. */
    data class RateLimited(val retryAfterSeconds: Int? = null, override val cause: Throwable? = null) : AppError

    /** The HTTP call worked but the payload failed validation (malformed JSON, nothing usable inside). */
    data class InvalidResponse(val reason: String = "", override val cause: Throwable? = null) : AppError

    /** No backend URL was configured at build time, so there is nothing to call. */
    data object BackendNotConfigured : AppError {
        override val cause: Throwable? = null
    }

    /** Neither the user's own provider key nor a backend URL is available, so there is nothing to call. */
    data object AiNotConfigured : AppError {
        override val cause: Throwable? = null
    }

    /** The provider rejected the key the user entered. Distinct from a generic access problem: it is fixable. */
    data class InvalidApiKey(val provider: String, override val cause: Throwable? = null) : AppError

    /**
     * The provider refused the request and said why. [detail] is the provider's own message, which is the only
     * thing that explains a mistyped model name or an exhausted quota; it is shown to the user who owns the key.
     */
    data class ProviderRejected(
        val provider: String,
        val status: Int,
        val detail: String,
        override val cause: Throwable? = null,
    ) : AppError

    /** A document the user picked could not be read or written. See [FileProblem] for the reason. */
    data class FileAccess(val problem: FileProblem, override val cause: Throwable? = null) : AppError

    /** The device is offline, so a feature that needs the backend cannot run right now. */
    data object Offline : AppError {
        override val cause: Throwable? = null
    }

    data class Unknown(override val cause: Throwable? = null) : AppError
}

/** Why a picked document could not be used. Kept coarse on purpose: the UI only needs a hint. */
enum class FileProblem {
    /** The provider refused access, the file is gone, or its content is not readable text. */
    UNREADABLE,

    /** Larger than the configured limit, so it is refused instead of being loaded into memory. */
    TOO_LARGE,

    /** The destination could not be written to, for example because it was removed meanwhile. */
    WRITE_FAILED,
}

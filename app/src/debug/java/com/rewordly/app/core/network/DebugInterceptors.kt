package com.rewordly.app.core.network

import com.rewordly.app.core.network.ai.AiRequestFactory
import com.rewordly.app.domain.model.AiApiStyle
import okhttp3.Interceptor
import okhttp3.logging.HttpLoggingInterceptor

/** Debug-only HTTP logging. Headers carrying credentials are redacted. */
object DebugInterceptors {
    fun all(): List<Interceptor> = listOf(logging())

    /**
     * Built here rather than inline so a test can pass a capturing logger and assert on exactly what would
     * have been written to logcat.
     */
    fun logging(logger: HttpLoggingInterceptor.Logger = HttpLoggingInterceptor.Logger.DEFAULT) =
        HttpLoggingInterceptor(logger).apply {
            level = HttpLoggingInterceptor.Level.BASIC
            SENSITIVE_HEADERS.forEach(::redactHeader)
        }

    /**
     * Every header that can carry a credential.
     *
     * The vendor list comes from [AiRequestFactory] instead of being written out again here. Redacting
     * `Authorization` alone used to be enough and stopped being enough as soon as the app began calling
     * Anthropic (`x-api-key`) and Gemini (`x-goog-api-key`) with the user's own key.
     */
    private val SENSITIVE_HEADERS: List<String> =
        (AiApiStyle.entries.map(AiRequestFactory::authHeaderName) + "Cookie").distinct()
}

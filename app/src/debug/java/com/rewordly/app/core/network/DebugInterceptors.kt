package com.rewordly.app.core.network

import okhttp3.Interceptor
import okhttp3.logging.HttpLoggingInterceptor

/** Debug-only HTTP logging. Headers carrying credentials are redacted. */
object DebugInterceptors {
    fun all(): List<Interceptor> = listOf(
        HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
            redactHeader("Authorization")
            redactHeader("Cookie")
        },
    )
}

package com.rewordly.app.core.network

import okhttp3.Interceptor

/** Release builds never log network traffic. */
object DebugInterceptors {
    fun all(): List<Interceptor> = emptyList()
}

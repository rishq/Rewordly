package com.rewordly.app.domain.service

import com.rewordly.app.domain.model.GenerationRequest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory only: remembers the requests behind recent generations so they can be repeated. It is the one place
 * that may hold pasted text, and it is dropped with the process; nothing here is persisted or logged.
 */
@Singleton
class GenerationSessionStore @Inject constructor() {
    private val requests = object : LinkedHashMap<String, GenerationRequest>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, GenerationRequest>?): Boolean =
            size > MAX_ENTRIES
    }

    @Synchronized
    fun put(id: String, request: GenerationRequest) {
        requests[id] = request
    }

    @Synchronized
    fun get(id: String): GenerationRequest? = requests[id]

    @Synchronized
    fun remove(id: String) {
        requests.remove(id)
    }

    @Synchronized
    fun clear() = requests.clear()

    private companion object {
        const val MAX_ENTRIES = 10
    }
}

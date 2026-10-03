package com.rewordly.app.core.common

import java.util.UUID
import javax.inject.Inject

/** Source of unique ids, replaceable in tests. */
interface IdProvider {
    fun newId(): String
}

class UuidProvider @Inject constructor() : IdProvider {
    override fun newId(): String = UUID.randomUUID().toString()
}

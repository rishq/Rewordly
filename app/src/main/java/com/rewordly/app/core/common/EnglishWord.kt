package com.rewordly.app.core.common

/**
 * The single definition of what counts as an English word or short expression in this app.
 * Shared by client-side input validation and by validation of untrusted backend payloads,
 * so both accept exactly the same shape: it must start with a letter, and may continue with
 * letters, digits, spaces, apostrophes, dots and hyphens.
 */
object EnglishWord {
    private val pattern = Regex("^[A-Za-z][A-Za-z0-9 '’.\\-]*$")

    fun matches(value: String): Boolean = pattern.matches(value)
}

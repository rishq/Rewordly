package com.rewordly.app.core.security

/**
 * Encrypts a small secret before it is written to preferences.
 *
 * Abstracted rather than used directly so the storage layer can be exercised in plain JVM tests, where the
 * Android Keystore does not exist.
 */
interface SecretCipher {
    /** Returns an opaque, storable form of [plain]. */
    fun encrypt(plain: String): String

    /**
     * Reverses [encrypt]. Returns null when the value cannot be read, for example when it was written by a
     * Keystore entry that is no longer available; the caller then treats the secret as absent.
     */
    fun decrypt(encoded: String): String?
}

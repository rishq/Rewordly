package com.rewordly.app.domain.repository

import com.rewordly.app.core.common.AppResult
import com.rewordly.app.domain.model.WordLookup

/**
 * Reads a single word from free, keyless public dictionaries.
 *
 * Deliberately separate from [VocabularyGenerationRepository]: that one needs the user's own API key or
 * the project's backend, while this one works on a fresh install with nothing configured. Implementations
 * must never require a key, an account or a sign-in.
 *
 * A word that no source knows is [WordLookup.NotFound] and not an error; a failure means the network or
 * the service itself was unreachable, which the user can retry.
 */
interface WordLookupRepository {
    suspend fun lookUp(word: String): AppResult<WordLookup>
}

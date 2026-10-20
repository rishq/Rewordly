package com.rewordly.app.domain.repository

/**
 * Versions of the bundled word packs already applied to the local dictionary. Kept alongside the
 * other app-level state so a pack is synced exactly once per version.
 */
interface SeedPackTracker {

    /** Applied pack versions by pack id; a missing id means the pack was never applied. */
    suspend fun appliedVersions(): Map<String, Int>

    /** Records [version] as the applied version of the pack [id]. */
    suspend fun markApplied(id: String, version: Int)
}

package com.rewordly.app.domain.service

import com.rewordly.app.domain.model.DuplicateKind
import com.rewordly.app.domain.model.ImportCandidate
import com.rewordly.app.domain.model.ParsedEntry
import com.rewordly.app.domain.model.WordKeys

/**
 * Decides which imported entries repeat a word that is already known.
 *
 * Duplicates are only *marked*, never merged: the user picks [com.rewordly.app.domain.model.DuplicatePolicy]
 * afterwards. Two entries that happen to share a spelling may still mean different things, so silently
 * collapsing them would lose vocabulary.
 *
 * Comparison uses [WordKeys.normalize], which trims, collapses inner whitespace and lowercases, so
 * `"  Apple "` and `"apple"` are recognised as the same word.
 */
object DuplicateDetector {
    /**
     * Marks every entry against the words already seen in the same file and against
     * [existingIdsByKey], which maps a normalized word to the id of the local word.
     */
    fun mark(entries: List<ParsedEntry>, existingIdsByKey: Map<String, String>): List<ImportCandidate> {
        val seenInFile = mutableSetOf<String>()
        return entries.map { parsed ->
            val key = WordKeys.normalize(parsed.entry.text)
            val candidate = when {
                !seenInFile.add(key) -> ImportCandidate(parsed.entry, parsed.row, DuplicateKind.WITHIN_FILE)
                existingIdsByKey.containsKey(key) ->
                    ImportCandidate(
                        entry = parsed.entry,
                        row = parsed.row,
                        duplicate = DuplicateKind.EXISTING,
                        existingWordId = existingIdsByKey.getValue(key),
                    )
                else -> ImportCandidate(parsed.entry, parsed.row, DuplicateKind.NONE)
            }
            candidate
        }
    }
}

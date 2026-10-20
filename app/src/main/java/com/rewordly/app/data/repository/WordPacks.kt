package com.rewordly.app.data.repository

/**
 * One vocabulary pack bundled with the app.
 *
 * A pack is a document inside `app/src/main/assets` (CSV or JSON — both are read by the same
 * [com.rewordly.app.domain.service.VocabularyImportParser] the import feature uses). The pack id
 * never changes across versions; bumping [version] makes the app refresh the content on the next
 * launch instead of seeding it only once.
 */
data class BundledWordPack(
    val id: String,
    val version: Int,
    val assetName: String,
    /**
     * Whether the pack carries a pronunciation for every word. The starter list predates the pack
     * format and has none; the packs authored in it have one for every entry.
     */
    val hasPronunciations: Boolean = false,
)

/** The packs this build ships, in first-run insertion order (lower levels first). */
object WordPackCatalog {

    /** B1 starter list, authored for this project. `seed_vocabulary.csv` predates the pack format. */
    val B1_STARTER = BundledWordPack(
        id = "starter_b1",
        version = 1,
        assetName = "seed_vocabulary.csv",
    )

    /** A1-A2 core vocabulary, authored for this project. */
    val A1_A2_CORE = BundledWordPack(
        id = "a1_a2_core",
        version = 1,
        assetName = "word_packs/a1_a2_core.json",
        hasPronunciations = true,
    )

    val packs: List<BundledWordPack> = listOf(A1_A2_CORE, B1_STARTER)
}

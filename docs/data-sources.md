# Data sources & licenses

What the app reads, from where, and under what terms. Same table ships inside the app
in `Настройки → О приложении` so attribution travels with the product.

## Bundled starter vocabulary

- **File:** `app/src/main/assets/seed_vocabulary.csv`
- **Use:** starter word packs installed into Room on the very first run (`OfflineVocabularyRepository.ensureSeeded`).
- **Source:** original to this project — authored for Rewordly, not copied from a commercial list.
- **License:** follows the main repository license. Until a repository license is chosen, the data is
  treated as all-rights-reserved to the repository owner; once the repo gets a license (e.g. Apache-2.0
  or MIT), the pack falls under it.

## Wiktionary

- **Use:** transcription (IPA), part of speech, definitions and the Russian translation when the user
  looks a word up; `WiktionaryParser` reads wikitext via `action=parse`, at most two requests per word,
  with a descriptive `User-Agent`.
- **Source:** [en.wiktionary.org](https://en.wiktionary.org)
- **License:** Wiktionary text is dual-licensed under the
  [GNU Free Documentation License](https://www.mediawiki.org/wiki/Licensing/GFDL) and the
  [Creative Commons Attribution–ShareAlike 4.0 license (CC BY-SA 4.0)](https://creativecommons.org/licenses/by-sa/4.0).
  The app uses the content with attribution to Wiktionary contributors and a link to the license, as
  CC BY-SA requires. No fetched wikitext is stored verbatim in the shipped database.
- **Requires verification:** the exact attribution wording shown in the About screen
  (see the `about_data_*` strings).

## MyMemory

- **Use:** fallback Russian translation on lookup, only when Wiktionary has no translation.
- **Source:** [mymemory.translated.net](https://mymemory.translated.net) (keyless API)
- **License / terms:** MyMemory free usage terms
  ([usagelimits.php](https://mymemory.translated.net/doc/usagelimits.php)): FREE usage is limited
  (per-day/per-week quotas) and requires putting the attribution
  `"MyMemory — translation memory (beta)"; translator` into the derived work/page. Low request volume in
  Rewordly (single fallback request per lookup) fits the free tier.
- **Requires verification:** whether the terms' attribution requirement applies to a private app where the
  translation is shown as a UI field; conservatively the About screen credits MyMemory anyway.

## AI-generated vocabulary

- **Use:** on-demand word packs the user generates with OpenAI, Anthropic or Google models, or through the
  project backend holding the provider secret.
- **License:** the generated dictionaries are not third-party copyrighted data; the provider's API terms
  govern the call itself. The user's own API key or the project's paid usage are at the user's choice —
  see `docs/ai-backend-contract.md`.

## Not included

- Commercial frequency lists and copyrighted dictionary editions on purpose — nothing in the app is
  copied from lists whose license forbids redistribution.
- Source datasets that could not be verified: none currently bundled.

## Verification checklist

Run `./gradlew testDebugUnitTest` — `SeedVocabularyTest` validates the bundled pack shape, word
uniqueness and CEFR levels.

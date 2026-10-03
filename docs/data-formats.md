# Import / export / backup formats

Rewordly reads and writes files only through the Android Storage Access Framework (SAF). The app
never asks for a storage permission and never sends a file anywhere: import, export and backup are
entirely local operations.

There are three file shapes:

| File | Purpose | Written by | Read by |
| --- | --- | --- | --- |
| Vocabulary CSV | Flat, spreadsheet-friendly vocabulary list | Export → CSV | Import |
| Vocabulary JSON | Same data as CSV, nested examples | Export → JSON | Import |
| Backup JSON | Everything needed to rebuild the app state | Export → backup | Restore |

All three are UTF-8. A UTF-8 BOM at the start of the file is accepted and stripped.

---

## 1. Vocabulary CSV

One row per word, header row first. The header is optional: without it, columns are read
positionally in the order below.

```csv
word,translation,part_of_speech,definition,definition_translation,example,example_translation,topic,difficulty
deploy,развёртывать,VERB,"to make software available for use","сделать ПО доступным для использования","We deploy every Friday.","Мы развёртываем каждую пятницу.",Technology,B2
```

| # | Column | Required | Notes |
| --- | --- | --- | --- |
| 1 | `word` | **yes** | The English word or phrase. A row without it is rejected. |
| 2 | `translation` | **yes** | The Russian translation. A row without it is rejected. |
| 3 | `part_of_speech` | no | A part-of-speech name, e.g. `NOUN`, `VERB`, `ADJECTIVE`. An unknown value falls back to the default. |
| 4 | `definition` | no | English definition. |
| 5 | `definition_translation` | no | Russian translation of the definition. |
| 6 | `example` | no | One or more English example sentences. |
| 7 | `example_translation` | no | The matching Russian translations, **in the same order**. |
| 8 | `topic` | no | A topic / category name. |
| 9 | `difficulty` | no | A level name, e.g. `A2`, `B1`. |

### Quoting rules

The reader follows RFC 4180:

- A field containing `,`, `"` or a line break **must** be wrapped in double quotes.
- A literal `"` inside a quoted field is written twice: `""`.
- Unquoted commas always split columns — `Подожди, пожалуйста.` in an unquoted field becomes two
  fields. Quote it: `"Подожди, пожалуйста."`.
- Both `\n` and `\r\n` line endings are accepted; the exporter always writes `\r\n`.

### Several examples in one row

CSV is flat, so multiple examples share one column and are joined with the separator ` | ` (space,
pipe, space). The same separator splits `example_translation`:

```csv
word,translation,example,example_translation
deploy,развёртывать,"We deploy every Friday. | Deploy it now.","Мы развёртываем каждую пятницу. | Разверни это сейчас."
```

Example *n* is paired with translation *n*. A missing translation leaves the pair empty rather than
shifting the remaining ones. If a sentence itself contains ` | `, the parts after the separator are
treated as separate examples — this is a documented limitation of the flat format; use JSON when
sentences may contain that sequence.

### Accepted header names

Header matching is case-insensitive, and `-`/space are treated like `_`. Both English and Russian
names are recognised, so a hand-made file in either language works:

| Column | Accepted names |
| --- | --- |
| word | `word`, `english`, `term`, `слово`, `английский` |
| translation | `translation`, `russian`, `перевод`, `значение` |
| part_of_speech | `part_of_speech`, `partofspeech`, `pos`, `часть_речи` |
| definition | `definition`, `definition_en`, `определение` |
| definition_translation | `definition_translation`, `definition_ru`, `перевод_определения` |
| example | `example`, `example_en`, `sentence`, `пример` |
| example_translation | `example_translation`, `example_ru`, `перевод_примера` |
| topic | `topic`, `category`, `тема` |
| difficulty | `difficulty`, `level`, `уровень` |

The first row is treated as a header only when it names at least `word` or `translation`. Otherwise
the whole file is read positionally, so a headerless file works.

An exported CSV can be imported again unchanged.

---

## 2. Vocabulary JSON

The importer accepts **two** shapes:

**A. A bare array of words**

```json
[
  {
    "word": "deploy",
    "translation": "развёртывать",
    "partOfSpeech": "VERB",
    "definition": "to make software available for use",
    "definitionTranslation": "сделать ПО доступным для использования",
    "topic": "Technology",
    "difficulty": "B2",
    "examples": [
      { "english": "We deploy every Friday.", "russian": "Мы развёртываем каждую пятницу." }
    ]
  }
]
```

**B. An object with a version and a `words` array** — this is what the app writes:

```json
{
  "version": 1,
  "words": [ /* the same word objects as above */ ]
}
```

Field rules:

| Field | Required | Notes |
| --- | --- | --- |
| `word` | **yes** | Empty or missing → the entry is rejected. |
| `translation` | **yes** | Empty or missing → the entry is rejected. |
| `partOfSpeech`, `definition`, `definitionTranslation`, `topic`, `difficulty` | no | Plain strings, default `""`. |
| `examples` | no | Array of `{ "english": ..., "russian": ... }`. An example with an empty `english` is dropped. |
| `version` | no | Only in shape B. Must be `≤ 1`; a higher number is reported as an unsupported version instead of being half-read. |

Unknown keys are ignored, so a file written by a future version that only *adds* fields still
imports. An exported JSON file can be imported again unchanged.

---

## 3. Backup JSON

A backup is a superset of the vocabulary export: it also carries progress, spaced-repetition
scheduling, activity history and the learning-relevant preferences. It always has the object shape,
and it is the only file type `Restore` accepts.

```json
{
  "schemaVersion": 1,
  "createdAt": 1790000000000,
  "appVersion": "1.0.0",
  "words": [ { "id": "…", "text": "deploy", "translation": "развёртывать", "examples": [ … ] } ],
  "progress": [ { "wordId": "…", "status": "REVIEW", "easeFactor": 2.5, "intervalDays": 6, "nextReviewAt": 1790000000000 } ],
  "reviewLog": [ { "wordId": "…", "sessionId": "…", "quality": 4, "reviewedAt": 1790000000000 } ],
  "activity": [ { "day": "2026-10-02", "wordsLearned": 12, "wordsReviewed": 30 } ],
  "settings": { "interfaceLanguage": "ru", "dailyGoal": 20, "level": "B1", "interests": [ "Technology" ] }
}
```

### Validation before anything is written

`Restore` reads and checks the file *before* it touches the database, so a bad file can never leave a
half-restored state. A file is refused when:

| Reported as | Cause |
| --- | --- |
| Unreadable | Not valid JSON. Bytes are decoded as UTF-8 leniently, so a file in another encoding shows up here as a parse failure. |
| Not a backup | The root is not an object, `schemaVersion` is missing or not an integer, or `words` is not an array. |
| Unsupported version | `schemaVersion` is `≤ 0` or greater than the version this build understands (currently `1`). |
| Empty | `schemaVersion` and `words` are fine, but the backup contains no words. |

### What restore does

Both strategies run inside a single database transaction — either the whole backup is applied or
nothing is.

- **Merge** — words already present are updated in place, keeping their row id so their progress and
  review history stay attached; new words are inserted. A backup word whose English text already
  exists under a *different* id is skipped rather than duplicated (see *Known limitations*).
- **Replace** — the existing vocabulary, progress, review history and activity are cleared first, then
  the backup is written in. This is the destructive option and the UI asks for confirmation before
  running it.

Rows in `progress` / `reviewLog` whose `wordId` is not among the restored words are dropped, so a
partially hand-edited backup cannot create orphaned records.

---

## Limits

Configurable in `DataTransferLimits` and `ImportLimits`:

| Limit | Value | Behaviour when exceeded |
| --- | --- | --- |
| Imported file size | 8 MiB | The file is refused before it is loaded into memory. |
| Backup file size | 32 MiB | Same. |
| Entries per import | 2000 | The extra rows are skipped and the preview says the file was truncated. |
| Characters per `word` / `translation` | 500 | The row is reported as too long. |

Files are read with a hard byte cap and parsed off the main thread, so a large or hostile file cannot
exhaust memory or freeze the UI.

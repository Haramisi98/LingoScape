# Editing translations

These UTF-8 JSON files are the editable source of translations. Only the 21 previously
translated Danish menu actions have been migrated, verbatim, to [dk/menu.json](dk/menu.json).
They are marked `draft` because automated tests do not constitute linguistic or in-game review.
No untranslated collection entries have been imported.

Each language has its own directory (`dk` currently matches the plugin's existing Danish code).
Each file contains an array of records:

```json
[
  {
    "id": "menu.use",
    "category": "menu",
    "english": "Use",
    "translation": "Brug",
    "status": "draft"
  }
]
```

- Keep the ID stable when correcting text. IDs must be unique across the language's files.
- Categories currently supported by the plugin: `menu`, `item`, `npc`, `object`, `dialogue`.
- Status is `draft` or `reviewed`. **Both are included in the development plugin**, so drafts
  can be tested. Mark reviewed only after checking the wording and in-game behavior.
- Omit an entry until it has a translation. Do not add blank values or `translationMissing`.
- Preserve numeric placeholders such as `{0}`, bracket placeholders such as `[player name]`,
  and formatting tags. Validation compares their counts; it does not prove grammar, tag nesting,
  or runtime support for every placeholder syntax.
- English remains the runtime lookup key. NPC/object keys must be uppercase to match the
  existing loader's lookup conventions. IDs organize editing; they do not change game matching.
- JSON handles quotes, the old `~|~` delimiter, and newlines (`\n`) without custom escaping rules.

## Build and test

```powershell
.\gradlew.bat build shadowJar --console=plain
.\gradlew.bat run --console=plain
```

Gradle validates all translation records, then generates compact lookup maps under
`build/generated/translations/translations/<language>/`. This automatically runs before resource
packaging, including for `run`. Duplicate IDs/lookup keys, invalid fields/status/categories,
blank values, and placeholder/tag mismatches fail the build. `check` also runs validation fixtures.
No Python installation or online service is required for this workflow.

Edit these source files, not generated files. Restart the development client after edits.
Missing keys still display English. Adding another language also requires exposing it in the
plugin's language selector.

## Other data

- The [English collection](../data/english/README.md) is research/input for future translation;
  it is not automatically added to these files.
- Legacy CSV dictionaries remain in the repository as reference/collector input. They are
  excluded from plugin resources and no longer read by the plugin. Their old translated values
  are historical copies; future edits belong here only.

# English game-text collection

This is LingoScape's collected English source material for future translation. No new translations
have been generated. Existing runtime dictionaries and their Danish translations are unchanged.
This folder is not bundled into the plugin or loaded at runtime. Translation editing now uses
[structured translation records](../../translations/README.md); legacy CSVs remain collector inputs.

## Contents

Each `.jsonl` file contains one JSON object per line:

```json
{"sources": ["cache-items", "items"], "text": "Dwarf remains"}
```

- [dialogue.jsonl](dialogue.jsonl): NPC/player lines and dialogue choices.
- [menu.jsonl](menu.jsonl): interaction labels, including nested and conditional cache options.
- [item.jsonl](item.jsonl), [npc.jsonl](npc.jsonl), [object.jsonl](object.jsonl): names.
- [examine.jsonl](examine.jsonl): examine descriptions.
- [interface.jsonl](interface.jsonl): messages and input prompts from dialogue transcripts.
- [quest.jsonl](quest.jsonl), [skill.jsonl](skill.jsonl), [prayer.jsonl](prayer.jsonl),
  [prayer_description.jsonl](prayer_description.jsonl): additional labels/descriptions.
- [summary.json](summary.json): exact counts, including additions over existing dictionaries.
- [sources.json](sources.json): source URLs, pinned revisions, and SHA-256 checksums.

Every entry lists all datasets that supplied its exact text. Source IDs resolve through
the source manifest. Duplicate text is removed within each category; cross-category entries
are kept because an action, item, and dialogue phrase may require different translations.
Capitalization, markup, numbers, whitespace, and placeholder spelling are preserved. The
existing uppercase NPC/object names may therefore coexist with mixed-case cache names.

## Sources and attribution

- Existing dictionaries in this repository, including the example dictionaries: English keys only.
  Their earlier upstream origin has not been established. File hashes record the import inputs.
- [wvanderp/osrs-db](https://github.com/wvanderp/osrs-db), revision
  `c3d25660cf90b5ad56ae5e705a138f57a7dc7b24`: cache item/NPC/object definitions,
  actions, item examine text, quests, and skills. Its repository license is retained in
  [licenses/osrs-db.txt](licenses/osrs-db.txt).
- [RSPSApp/osrsreboxed-db](https://github.com/RSPSApp/osrsreboxed-db), revision
  `1c4e3b58db6f03e5fe150fe6a7f25e9eadc82624`: item/monster data, NPC/object names,
  NPC actions, prayers, and dialogue exports. This is a fork of OSRSReboxed/OSRSBox and
  includes material derived from the [OSRS Wiki](https://oldschool.runescape.wiki/).
  Its repository license is retained in [licenses/osrsreboxed.txt](licenses/osrsreboxed.txt).

RuneScape game text belongs to Jagex. The copied repository licenses are upstream notices;
they do not imply that Jagex's game text was authored by those repository maintainers.
The transformations performed here are field extraction, categorization, exact deduplication,
and source annotation. Pinned raw-source URLs and the importer preserve the reconstruction path.

## Rebuild

From the project root, with Python 3.9 or newer (no extra packages):

```powershell
python tools/collect_english.py
python -m unittest discover -s tools -p "test_*.py"
```

The first run downloads approximately 245 MB of raw JSON into the ignored
`build/collection-cache` directory. Later runs reuse it and check checksums against the manifest.
To regenerate without network access:

```powershell
python tools/collect_english.py --offline
```

`gradlew clean` deletes this download cache, so the next collection run needs internet access.
Output is sorted and deterministic for identical inputs. No API key, translation service,
game login, or RuneLite instance is required. Nothing runs automatically during Gradle builds.

To deliberately update a source, change the pinned revision in
[tools/collect_english.py](../../tools/collect_english.py), remove only the corresponding cached
input and its old manifest entry, and rerun. Review source and count changes before accepting
the update. A checksum mismatch fails the import instead of silently trusting changed input.

## Coverage and limits

This is a large candidate collection, not a verified complete snapshot of all live OSRS text.
Sources have different update schedules and may include historical, unused, or quest-specific
variants. The current source skill list, for example, has 23 entries; it is not a completeness
guarantee. Wiki placeholders such as `[player name]` are preserved and need matching rules
before runtime use. Existing dictionaries are retained as supplied, including any older noise.

The dialogue extractor collects spoken lines, selectable responses, messages, and input prompts.
It excludes typed editorial actions, conditions, cross-references, and random-branch headings.
It does not follow missing transcript references or guess missing text. Untyped source errors
can still require manual review. This collection does not implement live missing-text capture.

Before feeding entries into the plugin, review them and apply the plugin's lookup conventions
(including uppercase NPC/object keys and numeric placeholders). JSONL preserves original text
without the delimiter/newline limitations of the legacy `~|~` dictionaries.

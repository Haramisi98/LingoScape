# Archived dictionary inputs

These JSON files replace the old `~|~` CSV dictionaries. Each `rows` entry is
`[english, historical_translation]`; row order, duplicates, whitespace, markup,
and historical translation values are preserved. `original_csv` records the
former repository path and SHA-256 checksum for provenance, not a live dependency.

The English collector reads only the first value of each pair. Historical
`local:dk/...` and `local:exampleFiles/...` source IDs remain unchanged, so the
existing English collection retains its exact text and attribution.

This archive is not loaded or packaged by the plugin. Edit active translations
in [translations](../../translations/README.md), not these reference files.
All ten inputs are required: the collector fails if any is missing.

"""Collect source English without modifying runtime dictionaries. Python 3.9+, stdlib only."""
import argparse
import hashlib
import json
from pathlib import Path
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
CACHE_REV = "c3d25660cf90b5ad56ae5e705a138f57a7dc7b24"
BOX_REV = "1c4e3b58db6f03e5fe150fe6a7f25e9eadc82624"
CACHE_BASE = f"https://media.githubusercontent.com/media/wvanderp/osrs-db/{CACHE_REV}/"
BOX_BASE = f"https://raw.githubusercontent.com/RSPSApp/osrsreboxed-db/{BOX_REV}/"
SOURCES = [
    (f"cache-{kind}", CACHE_BASE + f"data/{kind}.g.json", "osrs-db")
    for kind in ("items", "npcs", "objects", "quests", "skills")
] + [
    (name, BOX_BASE + "docs/" + filename + ".json", "osrsreboxed")
    for name, filename in (
        ("items", "items-complete"), ("monsters", "monsters-complete"),
        ("prayers", "prayers-complete"), ("dialogues", "npc-dialogues"),
        ("npc-names", "npcs-summary"), ("object-names", "objects-summary"),
        ("npc-actions", "npcs-interactions"),
    )
]
LICENSES = {
    "osrs-db": f"https://raw.githubusercontent.com/wvanderp/osrs-db/{CACHE_REV}/LICENCE",
    "osrsreboxed": BOX_BASE + "LICENSE",
}


def encode(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True)


class Collection:
    def __init__(self):
        self.categories = {}

    def add(self, category, text, source):
        if not isinstance(text, str) or not text.strip():
            return
        if text.strip().lower() in ("null", "hidden", "translationmissing"):
            return
        # Preserve exact case, markup, placeholders and whitespace for later review.
        self.categories.setdefault(category, {}).setdefault(text, set()).add(source)

    def counts(self):
        return {name: len(rows) for name, rows in sorted(self.categories.items())}


def option_texts(value):
    """Only called on option fields, never arbitrary cache metadata."""
    if isinstance(value, str):
        yield value
    elif isinstance(value, list):
        for child in value:
            yield from option_texts(child)
    elif isinstance(value, dict):
        for key, child in value.items():
            if (key == "text" or str(key).isdigit()) and isinstance(child, str):
                yield child
            elif isinstance(child, (dict, list)):
                yield from option_texts(child)


def collect_dialogue(value, result, source):
    if isinstance(value, list):
        for child in value:
            collect_dialogue(child, result, source)
    elif isinstance(value, dict):
        for role in ("player", "npc"):
            result.add("dialogue", value.get(role), source)
        kind = value.get("type")
        if kind == "line":
            result.add("dialogue", value.get("text"), source)
        elif kind in ("message", "input"):
            result.add("interface", value.get("text"), source)
        elif kind == "choice":
            result.add("dialogue", value.get("prompt"), source)
            for option in value.get("options", []):
                if isinstance(option, dict):
                    result.add("dialogue", option.get("text"), source)
        # Traverse nested branches, but never import action/condition labels or random headings.
        for child in value.values():
            if isinstance(child, (dict, list)):
                collect_dialogue(child, result, source)


def extract(source, data, result):
    if source == "npc-actions":
        data = data["npcs"]
    if source == "dialogues":
        collect_dialogue(data, result, source)
        return
    if source == "cache-skills":
        for text in data:
            result.add("skill", text, source)
        return
    category = {
        "cache-items": "item", "items": "item", "cache-npcs": "npc",
        "monsters": "npc", "npc-names": "npc", "cache-objects": "object",
        "object-names": "object", "cache-quests": "quest", "prayers": "prayer",
        "npc-actions": "npc",
    }[source]
    rows = data.values() if isinstance(data, dict) else data
    for row in rows:
        if not isinstance(row, dict):
            raise ValueError(f"Unexpected row schema in {source}")
        result.add(category, row.get("name"), source)
        result.add("examine", row.get("examine"), source)
        if source == "prayers":
            result.add("prayer_description", row.get("description"), source)
        for field in ("ops", "groundOps", "interfaceOptions", "subops", "options"):
            for text in option_texts(row.get(field)):
                result.add("menu", text, source)


def fetch(url, path, offline, expected_hash=None):
    if not path.exists():
        if offline:
            raise FileNotFoundError(f"Offline source missing: {path}")
        request = urllib.request.Request(url, headers={"User-Agent": "LingoScape-English-Collector/1.0"})
        with urllib.request.urlopen(request, timeout=120) as response:
            raw = response.read()
        if expected_hash and hashlib.sha256(raw).hexdigest() != expected_hash:
            raise ValueError(f"Source checksum changed: {url}")
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(raw)
    raw = path.read_bytes()
    digest = hashlib.sha256(raw).hexdigest()
    if expected_hash and digest != expected_hash:
        raise ValueError(f"Cached source checksum mismatch: {path}")
    return raw, digest


def collect(root=ROOT, offline=False):
    out = root / "data/english"
    cache = root / "build/collection-cache"
    manifest_path = out / "sources.json"
    previous = json.loads(manifest_path.read_text(encoding="utf-8")) if manifest_path.exists() else {}
    result = Collection()
    manifest = {}
    resource_root = root / "src/main/resources/net/runelite/client/plugins/lingoScape"
    for path in sorted(resource_root.glob("*/*.csv")):
        relative = path.relative_to(root).as_posix()
        source = "local:" + path.parent.name + "/" + path.stem
        raw = path.read_bytes()
        manifest[source] = {"path": relative, "sha256": hashlib.sha256(raw).hexdigest(),
                            "note": "Existing project dictionary; upstream origin not established."}
        category = path.stem.split("_", 1)[1]
        for number, line in enumerate(raw.decode("utf-8-sig").splitlines(), 1):
            if not line.strip():
                continue
            if "~|~" not in line:
                raise ValueError(f"Malformed dictionary row: {relative}:{number}")
            result.add(category, line.split("~|~", 1)[0], source)
    baseline = result.counts()
    for name, url, project in SOURCES:
        raw, digest = fetch(url, cache / (name + ".json"), offline, previous.get(name, {}).get("sha256"))
        data = json.loads(raw)
        before = sum(result.counts().values())
        extract(name, data, result)
        manifest[name] = {"url": url, "sha256": digest, "bytes": len(raw), "project": project,
                          "revision": CACHE_REV if project == "osrs-db" else BOX_REV}
        print(f"{name}: {sum(result.counts().values()) - before:,} additional category/text pairs", flush=True)
    # Prepare all inputs before publishing output; fail rather than silently exporting partial data.
    licenses = {}
    for project, url in LICENSES.items():
        key = "license:" + project
        raw, digest = fetch(url, cache / (project + "-LICENSE.txt"), offline, previous.get(key, {}).get("sha256"))
        licenses[project] = raw
        manifest[key] = {"url": url, "sha256": digest}
    out.mkdir(parents=True, exist_ok=True)
    for category, rows in sorted(result.categories.items()):
        content = "".join(encode({"text": text, "sources": sorted(sources)}) + "\n"
                          for text, sources in sorted(rows.items()))
        with (out / (category + ".jsonl")).open("w", encoding="utf-8", newline="\n") as stream:
            stream.write(content)
    (out / "licenses").mkdir(exist_ok=True)
    for project, raw in licenses.items():
        (out / "licenses" / (project + ".txt")).write_bytes(raw)
    manifest_path.write_text(encode(manifest) + "\n", encoding="utf-8")
    counts = result.counts()
    summary = {"categories": counts, "existing_unique_by_category": baseline,
               "new_by_category": {key: count - baseline.get(key, 0) for key, count in counts.items()},
               "total_category_text_pairs": sum(counts.values()),
               "unique_texts_across_categories": len({text for rows in result.categories.values() for text in rows})}
    (out / "summary.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--offline", action="store_true", help="Require cached sources; make no network requests")
    args = parser.parse_args()
    collect(offline=args.offline)

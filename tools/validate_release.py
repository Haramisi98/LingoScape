"""Validate the releaseJar output using only the checkout and local build outputs."""
import argparse
import json
from pathlib import Path
from zipfile import ZipFile


CATEGORIES = ("menu", "item", "npc", "object", "dialogue")
PLUGIN = "com/haramisi98/LingoScape/TranslationPlugin.class"
INDEX = "translations/known-text.bin"


def validate_release(root, artifact):
    expected = {}
    for language in sorted((root / "translations").iterdir()):
        if not language.is_dir():
            continue
        rows = []
        sources = sorted(language.glob("*.json"))
        for source in sources:
            rows.extend(json.loads(source.read_text(encoding="utf-8")))
        if not sources:
            continue
        for category in CATEGORIES:
            expected[f"translations/{language.name}/{category}.json"] = {
                row["english"]: row["translation"] for row in rows
                if row["category"] == category and row["status"] == "reviewed"
            }
    if not expected:
        raise ValueError("No source dictionaries found")

    classes = root / "build/classes/java/main"
    class_names = {path.relative_to(classes).as_posix() for path in classes.rglob("*.class")}
    if PLUGIN not in class_names:
        raise ValueError("Compiled plugin class is missing; run the Gradle build first")
    allowed = class_names | set(expected) | {INDEX, "META-INF/MANIFEST.MF"}
    with ZipFile(artifact) as jar:
        names = [entry.filename for entry in jar.infolist() if not entry.is_dir()]
        if len(names) != len(set(names)):
            raise ValueError("Duplicate JAR entries")
        if set(names) != allowed:
            raise ValueError(f"Unexpected JAR contents: missing={sorted(allowed - set(names))}, "
                             f"extra={sorted(set(names) - allowed)}")
        for name in class_names:
            if jar.read(name) != (classes / name).read_bytes():
                raise ValueError(f"Stale compiled class: {name}")
        for name, dictionary in expected.items():
            if json.loads(jar.read(name)) != dictionary:
                raise ValueError(f"Dictionary must contain exactly reviewed translations: {name}")
        index = jar.read(INDEX)
        records = [index[offset:offset + 16] for offset in range(0, len(index), 16)]
        if not records or len(index) % 16 or records != sorted(set(records)):
            raise ValueError("Known-text index must contain sorted, unique 16-byte records")
        if index != (root / "build/generated/knownIndex" / INDEX).read_bytes():
            raise ValueError("Packaged known-text index differs from the generated index")
    return sum(len(dictionary) for dictionary in expected.values())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("artifact", nargs="?", type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    candidates = [args.artifact] if args.artifact else list((root / "build/libs").glob("*-release.jar"))
    if len(candidates) != 1:
        parser.error("Expected exactly one release JAR; run a clean releaseJar build or pass its path")
    count = validate_release(root, candidates[0])
    print(f"Validated {candidates[0].name}: {count} reviewed translations and known-text index")
    if count == 0:
        print("No reviewed translations yet; this artifact is not ready for publication (issue #3).")


if __name__ == "__main__":
    main()

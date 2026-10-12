import json
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
from zipfile import ZipFile

from validate_release import CATEGORIES, INDEX, PLUGIN, validate_release


class ReleaseArtifactTests(unittest.TestCase):
    def setUp(self):
        self.directory = TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        source = self.root / "translations/dk/menu.json"
        source.parent.mkdir(parents=True)
        source.write_text(json.dumps([
            dict(category="menu", english="Use", translation="Brug", status="reviewed"),
            dict(category="menu", english="Drop", translation="Smid", status="draft"),
        ]), encoding="utf-8")
        self.entries = {
            PLUGIN: b"compiled plugin fixture",
            "META-INF/MANIFEST.MF": b"Manifest-Version: 1.0\n",
            INDEX: b"a" * 16 + b"b" * 16,
        }
        for category in CATEGORIES:
            self.entries[f"translations/dk/{category}.json"] = json.dumps(
                {"Use": "Brug"} if category == "menu" else {}).encode()
        for relative, content in {
            "build/classes/java/main/" + PLUGIN: self.entries[PLUGIN],
            "build/generated/knownIndex/" + INDEX: self.entries[INDEX],
        }.items():
            target = self.root / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(content)

    def validate(self):
        artifact = self.root / "release.jar"
        with ZipFile(artifact, "w") as jar:
            for name, content in self.entries.items():
                jar.writestr(name, content)
        return validate_release(self.root, artifact)

    def test_reviewed_only_artifact_passes(self):
        self.assertEqual(self.validate(), 1)

    def test_drafts_only_allow_empty_release_dictionaries(self):
        source = self.root / "translations/dk/menu.json"
        rows = json.loads(source.read_text(encoding="utf-8"))
        for row in rows:
            row["status"] = "draft"
        source.write_text(json.dumps(rows), encoding="utf-8")
        self.entries["translations/dk/menu.json"] = b"{}"
        self.assertEqual(self.validate(), 0)

    def test_missing_dictionary_or_index_fails(self):
        for name in (INDEX, "translations/dk/item.json", PLUGIN):
            with self.subTest(name=name):
                content = self.entries.pop(name)
                with self.assertRaisesRegex(ValueError, "missing="):
                    self.validate()
                self.entries[name] = content

    def test_draft_or_changed_translation_fails(self):
        for dictionary in ({"Use": "Brug", "Drop": "Smid"}, {}, {"Use": "Wrong"}):
            with self.subTest(dictionary=dictionary):
                self.entries["translations/dk/menu.json"] = json.dumps(dictionary).encode()
                with self.assertRaisesRegex(ValueError, "exactly reviewed"):
                    self.validate()

    def test_development_and_research_files_fail(self):
        for name in ("com/haramisi98/LingoScape/LingoScapeLauncher.class",
                     "org/junit/Test.class", "data/english/menu.jsonl",
                     "data/legacy-dictionaries/menu.json"):
            with self.subTest(name=name):
                self.entries[name] = b"unwanted"
                with self.assertRaisesRegex(ValueError, "extra="):
                    self.validate()
                del self.entries[name]

    def test_corrupt_or_stale_index_fails(self):
        for index in (b"", b"a", b"b" * 16 + b"a" * 16, b"a" * 32, b"c" * 16):
            with self.subTest(index=index):
                self.entries[INDEX] = index
                with self.assertRaisesRegex(ValueError, "index"):
                    self.validate()

    def test_stale_class_fails(self):
        self.entries[PLUGIN] = b"old plugin"
        with self.assertRaisesRegex(ValueError, "Stale compiled class"):
            self.validate()


if __name__ == "__main__":
    unittest.main()

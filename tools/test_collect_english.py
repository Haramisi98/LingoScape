import unittest
import json
from pathlib import Path
from tempfile import TemporaryDirectory

from collect_english import Collection, collect_dialogue, collect_local, extract, option_texts


class CollectionTests(unittest.TestCase):
    def test_archived_json_preserves_collected_local_text_and_provenance(self):
        root = Path(__file__).resolve().parents[1]
        actual = Collection()
        manifest = collect_local(root, actual)
        self.assertEqual(len(manifest), 10)
        expected = Collection()
        for category in ("dialogue", "item", "menu", "npc", "object"):
            with (root / "data/english" / (category + ".jsonl")).open(encoding="utf-8") as stream:
                for line in stream:
                    row = json.loads(line)
                    for source in row["sources"]:
                        if source.startswith("local:"):
                            expected.add(category, row["text"], source)
        self.assertEqual(actual.categories, expected.categories)

    def test_missing_archived_inputs_fail_instead_of_silently_losing_text(self):
        with TemporaryDirectory() as directory:
            with self.assertRaisesRegex(ValueError, "Missing or unexpected"):
                collect_local(Path(directory), Collection())

    def test_deduplicates_and_keeps_provenance_without_changing_text(self):
        result = Collection()
        for source in ("a", "b", "a"):
            result.add("dialogue", "Hello, [player name]!<br>Å", source)
        self.assertEqual(result.counts(), {"dialogue": 1})
        self.assertEqual(result.categories["dialogue"]["Hello, [player name]!<br>Å"], {"a", "b"})

    def test_ignores_absent_names_and_placeholders(self):
        result = Collection()
        for text in (None, "", " ", "null", "Hidden", "translationMissing", 12):
            result.add("item", text, "test")
        self.assertEqual(result.counts(), {})

    def test_cache_options_include_nested_and_conditional_text_only(self):
        options = {"ops": [None, {"text": "Take"}], "conditionalOps": [
            {"condition": "internal_name", "op": {"text": "Open"}}], "subOps": [["Use"]]}
        self.assertEqual(list(option_texts(options)), ["Take", "Open", "Use"])

    def test_npc_action_export_does_not_import_metadata(self):
        result = Collection()
        extract("npc-actions", {"meta": {"description": "Not game text"}, "npcs": {
            "1": {"name": "Man", "cache_name": "internal", "options": {"1": "Talk-to"}}
        }}, result)
        self.assertEqual(result.counts(), {"menu": 1, "npc": 1})
        self.assertIn("Talk-to", result.categories["menu"])

    def test_dialogue_includes_branches_but_excludes_editorial_labels(self):
        result = Collection()
        collect_dialogue({"steps": [
            {"player": "Hello."}, {"npc": "Welcome."},
            {"type": "line", "speaker": "Bob", "text": "Goodbye."},
            {"type": "choice", "prompt": "Choose:", "options": [
                {"text": "Yes", "steps": [{"type": "message", "text": "You receive a coin."}]}]},
            {"type": "random", "options": [{"text": "Dialogue 1", "steps": [{"npc": "Random line"}]}]},
            {"type": "action", "text": "Bank opens."},
            {"type": "condition", "text": "Before quest", "steps": [{"player": "Nested line"}]},
            {"type": "input", "text": "Enter amount:"},
            {"type": "reference", "text": "See another page"},
        ]}, result, "test")
        self.assertEqual(set(result.categories["dialogue"]),
                         {"Hello.", "Welcome.", "Goodbye.", "Choose:", "Yes", "Random line", "Nested line"})
        self.assertEqual(set(result.categories["interface"]), {"You receive a coin.", "Enter amount:"})


if __name__ == "__main__":
    unittest.main()

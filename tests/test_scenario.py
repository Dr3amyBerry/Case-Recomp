from __future__ import annotations
import json
from pathlib import Path
import tempfile
import unittest

from caserecomp.inspector import InspectionError
from caserecomp.scenario import (canonical_bytes, load_scenario, scenario_fingerprint,
                                 validate_scenario)

ROOT = Path(__file__).resolve().parents[1]


def valid_doc():
    return json.loads((ROOT / "fixtures/synthetic-scenario-v1.json").read_text())


class ScenarioTests(unittest.TestCase):
    def test_fixture_validates_and_canonicalizes(self):
        schema = json.loads((ROOT / "schemas/scenario-v1.schema.json").read_text())
        self.assertEqual(schema["properties"]["version"]["const"], 1)
        self.assertFalse(schema["additionalProperties"])
        doc = load_scenario(ROOT / "fixtures/synthetic-scenario-v1.json")
        self.assertEqual(doc["version"], 1)
        self.assertEqual(len(doc["scenes"]), 2)
        self.assertEqual(scenario_fingerprint(doc), scenario_fingerprint(json.loads(canonical_bytes(doc))))

    def test_canonical_order_does_not_change_fingerprint(self):
        doc = valid_doc()
        reordered = {k: doc[k] for k in reversed(list(doc))}
        self.assertEqual(scenario_fingerprint(doc), scenario_fingerprint(reordered))

    def test_rejects_schema_and_references(self):
        changes = []
        a = valid_doc(); a["version"] = 2; changes.append(a)
        a = valid_doc(); a["extra"] = 1; changes.append(a)
        a = valid_doc(); a["design"]["width"] = 0; changes.append(a)
        a = valid_doc(); a["scenes"][1]["id"] = "room-a"; changes.append(a)
        a = valid_doc(); a["scenes"][0]["targets"][1]["id"] = "shape-a"; changes.append(a)
        a = valid_doc(); a["scenes"][0]["targets"][0]["rect"] = [1, 1, 1, 2]; changes.append(a)
        a = valid_doc(); a["events"][0]["scene"] = "missing"; changes.append(a)
        a = valid_doc(); a["events"][1]["target"] = "missing"; changes.append(a)
        a = valid_doc(); a["events"][0]["frame"] = 999; changes.append(a)
        a = valid_doc(); a["events"][2]["to_scene"] = "missing"; changes.append(a)
        a = valid_doc(); a["events"][0]["kind"] = "execute-lingo"; changes.append(a)
        a = valid_doc(); a["scenes"][0]["id"] = "../bad"; changes.append(a)
        for doc in changes:
            with self.subTest(doc=doc):
                with self.assertRaises(InspectionError):
                    validate_scenario(doc)

    def test_rejects_wrong_container_types_and_ranges(self):
        cases = [None, [], {"format": "case-recomp-scenario", "version": 1}]
        for case in cases:
            with self.assertRaises(InspectionError): validate_scenario(case)
        doc = valid_doc(); doc["scenes"] = "bad"
        with self.assertRaises(InspectionError): validate_scenario(doc)
        doc = valid_doc(); doc["events"] = "bad"
        with self.assertRaises(InspectionError): validate_scenario(doc)
        doc = valid_doc(); doc["scenes"][0]["frame_end"] = 0
        with self.assertRaises(InspectionError): validate_scenario(doc)
        doc = valid_doc(); doc["scenes"][0]["targets"][0]["z"] = True
        with self.assertRaises(InspectionError): validate_scenario(doc)
        doc = valid_doc(); doc["scenes"][0]["targets"] = []
        with self.assertRaises(InspectionError): validate_scenario(doc)

    def test_file_limits_and_invalid_json(self):
        with tempfile.TemporaryDirectory() as tmp:
            p = Path(tmp) / "bad.json"
            p.write_text("{")
            with self.assertRaises(InspectionError): load_scenario(p)
            with self.assertRaises(InspectionError): load_scenario(Path(tmp) / "missing.json")


if __name__ == "__main__": unittest.main()

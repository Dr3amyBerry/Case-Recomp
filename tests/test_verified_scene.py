"""Synthetic coverage of the verified hidden-object scene contract (no original data)."""
import copy
import json
import tempfile
import unittest
from pathlib import Path

from caserecomp.__main__ import main
from caserecomp.inspector import InspectionError
from caserecomp.verified_scene import (
    encode_mask, mask_contains, validate_mask, validate_scene_observation, validate_verified_scene_proof,
    verified_scene_from_observation,
)

ROOT = Path(__file__).resolve().parent.parent
FIXTURE = ROOT / "fixtures" / "synthetic-verified-scene.crscene"
PACKAGE = "d" * 64
H = [format(n, "x") * 64 for n in range(1, 10)]


def scenario():
    return {"format": "case-recomp-scenario", "version": 1, "id": "synthetic-scene-proof",
            "design": {"width": 64, "height": 48},
            "scenes": [{"id": "room", "frame_start": 1, "frame_end": 2,
                        "targets": [{"id": "placeholder", "rect": [1, 1, 4, 4], "z": 1}]}], "events": []}


def ring(size):
    """A hollow square: clicks in the hole must miss, like a matte-ink ring sprite."""
    return [[x in (0, size - 1) or y in (0, size - 1) for x in range(size)] for y in range(size)]


def observation():
    targets = [
        {"id": "ring", "mask": encode_mask(4, 4, ring(10)),
         "structural": {"sprite_channel": 7, "cast_member": 3, "bitmap_sha256": H[0]}},
        {"id": "dot", "mask": encode_mask(40, 30, [[True] * 3] * 2)},
    ]
    steps = [
        {"kind": "miss", "point": [8, 8], "post_play_sha256": H[1], "counter_after": 5},
        {"kind": "find", "target": "ring", "point": [4, 4], "post_play_sha256": H[2], "counter_after": 4,
         "latency_ms": 120},
        {"kind": "miss", "point": [4, 4], "post_play_sha256": H[2], "counter_after": 4},
        {"kind": "find", "target": "dot", "point": [41, 31], "post_play_sha256": H[3], "counter_after": 3,
         "latency_ms": 140},
        {"kind": "acknowledge", "point": [30, 30], "post_screen": "MAP"},
    ]
    trial = {"trial_id_sha256": H[4], "initial_play_sha256": H[1], "steps": steps,
             "reentry_blocked": True, "persisted_after_restart": True}
    second = copy.deepcopy(trial)
    second["trial_id_sha256"] = H[5]
    second["persisted_after_restart"] = False
    second["steps"][1]["latency_ms"] = 109
    return {"format": "case-recomp-scene-observation", "version": 1, "runtime_kind": "native-projector",
            "evidence_kind": "independent-original-runtime", "source_sha256": H[6], "scene_id": "room",
            "frame_start": 1, "frame_end": 2, "design": {"width": 64, "height": 48}, "counter_start": 5,
            "acknowledge_region": [25, 25, 35, 35], "targets": targets, "trials": [trial, second]}


def proof():
    return verified_scene_from_observation(observation(), scenario=scenario(), package_id=PACKAGE)


class MaskTests(unittest.TestCase):
    def test_round_trip_and_ring_hole(self):
        mask = encode_mask(4, 4, ring(10))
        self.assertTrue(mask_contains(mask, 4, 4))
        self.assertFalse(mask_contains(mask, 8, 8))
        self.assertFalse(mask_contains(mask, 3, 4))
        self.assertFalse(mask_contains(mask, 14, 14))
        self.assertEqual(encode_mask(0, 0, [[True, False] * 5]), {"left": 0, "top": 0, "width": 10, "height": 1,
                                                                    "bits": "qoA="})

    def test_invalid_masks(self):
        good = encode_mask(0, 0, [[True] * 3])
        for bad in (
            {**good, "bits": "AA=="}, {**good, "bits": "!!"}, {**good, "width": 0}, {**good, "extra": 1},
            {**good, "bits": "4A=="[:-1]}, {**good, "bits": 1}, "mask", {**good, "bits": "4Q=="},
        ):
            with self.assertRaises(InspectionError):
                validate_mask(bad)
        with self.assertRaises(InspectionError):
            encode_mask(0, 0, [])
        with self.assertRaises(InspectionError):
            encode_mask(0, 0, [[True], [True, False]])
        with self.assertRaises(InspectionError):
            encode_mask(0, 0, [[False, False]])


class ObservationTests(unittest.TestCase):
    def test_consensus_observation_is_accepted(self):
        doc = observation()
        self.assertIs(validate_scene_observation(doc), doc)

    def mutated(self, change):
        doc = observation(); change(doc)
        with self.assertRaises(InspectionError):
            validate_scene_observation(doc)

    def test_rejects_weak_or_inconsistent_evidence(self):
        cases = [
            lambda d: d.update(trials=d["trials"][:1]),
            lambda d: d["trials"][1].update(trial_id_sha256=H[4]),
            lambda d: d.update(runtime_kind="emulator"),
            lambda d: d.update(evidence_kind="synthetic"),
            lambda d: d["trials"][1]["steps"][3].update(post_play_sha256=H[8]),
            lambda d: d["trials"][1].update(initial_play_sha256=H[8]),
            lambda d: [t.update(persisted_after_restart=False) for t in d["trials"]],
            lambda d: d["trials"][0].update(reentry_blocked=False),
            lambda d: d["trials"][0]["steps"][0].update(point=[4, 4]),
            lambda d: d["trials"][0]["steps"][0].update(post_play_sha256=H[7]),
            lambda d: d["trials"][0]["steps"][1].update(point=[8, 8]),
            lambda d: d["trials"][0]["steps"][1].update(counter_after=3),
            lambda d: d["trials"][0]["steps"][1].update(post_play_sha256=H[1]),
            lambda d: d["trials"][0]["steps"][1].update(target="missing"),
            lambda d: d["trials"][0]["steps"][1].update(latency_ms=-1),
            lambda d: d["trials"][0]["steps"][3].update(target="ring"),
            lambda d: d["trials"][0]["steps"][4].update(point=[0, 0]),
            lambda d: d["trials"][0]["steps"][4].update(post_screen="SCENE"),
            lambda d: d["trials"][0].update(steps=d["trials"][0]["steps"][:-1]),
            lambda d: d["trials"][0]["steps"].insert(3, {"kind": "acknowledge", "point": [30, 30], "post_screen": "MAP"}),
            lambda d: d["trials"][0]["steps"].append({"kind": "miss", "point": [1, 1], "post_play_sha256": H[3],
                                                      "counter_after": 3}),
            lambda d: d["trials"][0]["steps"][0].update(kind="hover"),
            lambda d: d["trials"][0]["steps"][0].update(extra=1),
            lambda d: d["trials"][0]["steps"][1].pop("latency_ms"),
            lambda d: d["trials"][0]["steps"][4].update(extra=1),
            lambda d: d["trials"][0].update(extra=1),
            lambda d: d["trials"][0].update(trial_id_sha256="x"),
            lambda d: d["trials"][0].update(steps="x"),
            lambda d: d["trials"][0]["steps"].__setitem__(0, "x"),
            lambda d: d.update(trials="x"),
            lambda d: d["targets"].append(copy.deepcopy(d["targets"][0])),
            lambda d: d["targets"][0]["structural"].update(bitmap_sha256="x"),
            lambda d: d["targets"][0].update(structural="x"),
            lambda d: d["targets"][0].update(extra=1),
            lambda d: d["targets"][1]["mask"].update(left=62),
            lambda d: d.update(targets=[]),
            lambda d: d.update(design={"width": 64}),
            lambda d: d.update(acknowledge_region=[5, 5, 5, 9]),
            lambda d: d.update(acknowledge_region=[1, 2]),
            lambda d: d.update(frame_end=0),
            lambda d: d.update(scene_id="a/b"),
            lambda d: d.update(source_sha256="x"),
            lambda d: d.update(version=2),
            lambda d: d.update(version=True),
            lambda d: d.update(format="other"),
            lambda d: d["trials"][0]["steps"][0].update(point=[1]),
        ]
        for change in cases:
            with self.subTest(change=change):
                self.mutated(change)


class ProofTests(unittest.TestCase):
    def test_proof_binds_package_scenario_source_and_rules(self):
        doc = proof()
        self.assertEqual(doc["package_id"], PACKAGE)
        self.assertEqual(doc["scene_id"], "room")
        self.assertEqual([t["id"] for t in doc["targets"]], ["dot", "ring"])
        self.assertEqual(doc["rules"]["completion"]["kind"], "acknowledge-then-map")
        self.assertNotIn("structural", json.dumps(doc["targets"]))
        self.assertIs(validate_verified_scene_proof(doc), doc)

    def test_fixture_is_current(self):
        self.assertEqual(json.loads(FIXTURE.read_text(encoding="utf-8")), proof())

    def test_proof_generation_rejects_bad_bindings(self):
        with self.assertRaises(InspectionError):
            verified_scene_from_observation(observation(), scenario=scenario(), package_id="x")
        other = scenario(); other["scenes"][0]["id"] = "elsewhere"
        with self.assertRaises(InspectionError):
            verified_scene_from_observation(observation(), scenario=other, package_id=PACKAGE)
        other = scenario(); other["design"] = {"width": 80, "height": 48}
        with self.assertRaises(InspectionError):
            verified_scene_from_observation(observation(), scenario=other, package_id=PACKAGE)
        with self.assertRaises(InspectionError):
            verified_scene_from_observation(observation(), scenario=[], package_id=PACKAGE)

    def test_tampered_proofs_are_rejected(self):
        def rebound(change):
            doc = copy.deepcopy(proof()); change(doc); doc.pop("binding_sha256")
            from caserecomp.vertical_slice import _digest
            doc["binding_sha256"] = _digest(doc)
            return doc
        unbound = copy.deepcopy(proof()); unbound["targets"][0]["mask"]["left"] = 1
        for doc in (
            unbound, {**proof(), "binding_sha256": "0" * 64}, {**proof(), "extra": 1}, "proof",
            rebound(lambda d: d.update(evidence_kind="synthetic")),
            rebound(lambda d: d.update(package_id="x")),
            rebound(lambda d: d.update(scene_id="")),
            rebound(lambda d: d.update(version=2)),
            rebound(lambda d: d["evidence_chain"].update(extra=H[0])),
            rebound(lambda d: d["targets"].reverse()),
            rebound(lambda d: d["targets"].append(copy.deepcopy(d["targets"][0]))),
            rebound(lambda d: d["rules"].update(miss_effect="penalty")),
            rebound(lambda d: d["rules"]["find_effect"].update(counter_delta=-2)),
            rebound(lambda d: d["rules"]["completion"].update(locks_scene=False)),
            rebound(lambda d: d["rules"]["completion"].update(kind="auto")),
            rebound(lambda d: d["rules"]["completion"].update(acknowledge_region=[1, 1, 1, 1])),
            rebound(lambda d: d["rules"].update(counter_start=-1)),
        ):
            with self.assertRaises(InspectionError):
                validate_verified_scene_proof(doc)

    def test_cli_emits_create_only_proof(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "obs.json").write_text(json.dumps(observation()), encoding="utf-8")
            (root / "scenario.json").write_text(json.dumps(scenario()), encoding="utf-8")
            out = root / "scene.crscene"
            args = ["scene-proof", str(root / "obs.json"), str(root / "scenario.json"),
                    "--package-id", PACKAGE, "--output", str(out)]
            self.assertEqual(main(args), 0)
            self.assertEqual(json.loads(out.read_text(encoding="utf-8")), proof())
            self.assertEqual(main(args), 2)


if __name__ == "__main__":
    unittest.main()

from __future__ import annotations

import json
import tempfile
import unittest
from hashlib import sha256
from pathlib import Path
from unittest.mock import patch
from types import SimpleNamespace
import struct

from caserecomp.inspector import InspectionError
from caserecomp.__main__ import main
from caserecomp.score import ScoreModel, SpriteState
from caserecomp.trace import (
    TRACE_FORMAT,
    _behavior_script_ids,
    _handler_digest_by_script,
    _script_link_map,
    _single_resource,
    build_private_trace_plan,
    compare_private_trace,
    frame_fingerprint,
    load_json,
    synthetic_fixture_from_verified_comparison,
    validate_observation_trace,
    validate_private_plan,
    write_synthetic_fixture,
)
from caserecomp.score import BehaviorRef, FrameLabel

ZERO = "0" * 64
ONE = "1" * 64
TWO = "2" * 64


def observation(**overrides):
    row = {
        "frame": 1,
        "sprite_count": 1,
        "sprite_sha256": ONE,
        "handler_set_sha256": TWO,
        "observable_state_sha256": ZERO,
        "input_kind": "tap",
        "event_kind": "target-found",
    }
    row.update(overrides)
    return {"format": TRACE_FORMAT, "version": 1, "kind": "observation", "source_sha256": ZERO, "steps": [row]}


def plan(**overrides):
    row = {"frame": 1, "sprite_count": 1, "sprite_sha256": ONE, "behavior_count": 1, "script_count": 1, "handler_set_sha256": TWO}
    row.update(overrides.pop("row", {}))
    doc = {"format": TRACE_FORMAT, "version": 1, "kind": "private-plan", "source_sha256": ZERO, "steps": [row]}
    doc.update(overrides)
    return doc


class TraceTests(unittest.TestCase):
    def test_frame_fingerprint(self):
        sprite = SpriteState(1, 3, 1, 0, False, False, 1, 2, 10, 20, 30, 40, 255, False, False)
        score = ScoreModel(2, 5, 13, 48, (sprite,), (), ())
        result = frame_fingerprint(score, 1)
        self.assertEqual(result["sprite_count"], 1)
        self.assertEqual(len(result["sprite_sha256"]), 64)
        self.assertEqual(result["behavior_count"], 0)
        with self.assertRaises(InspectionError):
            frame_fingerprint(score, 3)

    def test_validate_observation(self):
        doc = observation()
        self.assertIs(validate_observation_trace(doc), doc)

    def test_invalid_observation_variants(self):
        cases = [
            {"format": "bad"},
            {"version": 2},
            {"kind": "plan"},
            {"source_sha256": "bad"},
            {"steps": []},
        ]
        for patch_data in cases:
            doc = observation()
            doc.update(patch_data)
            with self.subTest(patch_data=patch_data), self.assertRaises(InspectionError):
                validate_observation_trace(doc)
        row_cases = [
            {"frame": 0}, {"sprite_count": -1}, {"sprite_sha256": "x"},
            {"handler_set_sha256": "x"}, {"observable_state_sha256": "x"},
            {"input_kind": "hack"}, {"event_kind": "hack"},
        ]
        for change in row_cases:
            doc = observation()
            doc["steps"][0].update(change)
            with self.subTest(change=change), self.assertRaises(InspectionError):
                validate_observation_trace(doc)

    def test_monotonic_frames_required(self):
        doc = observation()
        doc["steps"].append({**doc["steps"][0], "frame": 0})
        with self.assertRaises(InspectionError):
            validate_observation_trace(doc)


    def test_non_mapping_observation_row(self):
        doc = observation()
        doc["steps"] = ["bad"]
        with self.assertRaises(InspectionError):
            validate_observation_trace(doc)

    def test_script_link_helpers(self):
        cast_order = struct.pack(">II", 10, 0)
        info = bytearray(20)
        struct.pack_into(">i", info, 16, 5)
        cast = struct.pack(">iii", 11, len(info), 0) + bytes(info)
        lscr = bytearray(20)
        struct.pack_into(">H", lscr, 18, 5)
        entries = {
            1: SimpleNamespace(tag="CAS*"),
            10: SimpleNamespace(tag="CASt"),
            20: SimpleNamespace(tag="Lscr"),
        }
        data = {1: cast_order, 10: cast, 20: bytes(lscr)}
        archive = SimpleNamespace(entries=entries, get_resource=lambda ident: data[ident])
        order, cast_by_id, scripts = _script_link_map(archive)
        self.assertEqual(order, (10, 0))
        self.assertEqual(cast_by_id[10].script_number, 5)
        self.assertEqual(scripts, {5: 20})
        refs = [
            BehaviorRef(1, 2, 1, 1, 1, None, None),
            BehaviorRef(1, 2, 1, 2, 1, None, None),
            BehaviorRef(1, 2, 1, 1, 2, None, None),
            BehaviorRef(1, 2, 1, 1, None, None, None),
        ]
        self.assertEqual(_behavior_script_ids(refs, order, cast_by_id, scripts), [20])
        with self.assertRaises(InspectionError):
            _single_resource(SimpleNamespace(entries={}, get_resource=lambda ident: b""), "VWSC")

    def test_handler_digest_grouping(self):
        fake = {"handlers": [
            {"script_id": 9, "name": "a" * 64, "bytecode_sha256": "b" * 64, "bytecode_size": 3},
            {"script_id": 9, "name": "c" * 64, "bytecode_sha256": "d" * 64, "bytecode_size": 4},
        ]}
        with patch("caserecomp.trace.index_archive", return_value=fake):
            result = _handler_digest_by_script(object())
        self.assertEqual(list(result), [9])
        self.assertEqual(len(result[9]), 64)

    def test_build_private_trace_plan_with_mocked_structures(self):
        sprite = SpriteState(1, 3, 1, 0, False, False, 1, 1, 10, 20, 30, 40, 255, False, False)
        behavior = BehaviorRef(1, 2, 3, 1, 1, None, None)
        score = ScoreModel(2, 5, 13, 48, (sprite,), (), (behavior,))
        archive = SimpleNamespace(
            kind="movie",
            entries={1: SimpleNamespace(tag="VWSC"), 2: SimpleNamespace(tag="VWLB")},
            get_resource=lambda ident: b"score" if ident == 1 else b"labels",
        )
        with tempfile.TemporaryDirectory() as temp:
            movie = Path(temp) / "movie.dcr"
            movie.write_bytes(b"synthetic-private-input")
            with patch("caserecomp.trace.open_archive", return_value=(archive, 0)), \
                 patch("caserecomp.trace.parse_score", return_value=score), \
                 patch("caserecomp.trace.parse_frame_labels", return_value=(FrameLabel(2, ZERO),)), \
                 patch("caserecomp.trace._script_link_map", return_value=((10,), {10: SimpleNamespace(script_number=7)}, {7: 90})), \
                 patch("caserecomp.trace._handler_digest_by_script", return_value={90: ONE}):
                result = build_private_trace_plan(movie)
                self.assertEqual([row["frame"] for row in result["steps"]], [1, 2])
                self.assertEqual(result["steps"][0]["script_count"], 1)
                selected = build_private_trace_plan(movie, [2])
                self.assertEqual(len(selected["steps"]), 1)
            bad_archive = SimpleNamespace(kind="cast")
            with patch("caserecomp.trace.open_archive", return_value=(bad_archive, 0)):
                with self.assertRaises(InspectionError):
                    build_private_trace_plan(movie)
            with patch("caserecomp.trace.open_archive", return_value=(archive, 0)), \
                 patch("caserecomp.trace.parse_score", return_value=score), \
                 patch("caserecomp.trace.parse_frame_labels", return_value=()), \
                 patch("caserecomp.trace._script_link_map", return_value=((), {}, {})), \
                 patch("caserecomp.trace._handler_digest_by_script", return_value={}):
                with self.assertRaises(InspectionError):
                    build_private_trace_plan(movie, [])

    def test_private_plan_validation(self):
        doc = plan()
        self.assertIs(validate_private_plan(doc), doc)
        variants = [
            {"source_sha256": "x" * 64},
            {"steps": []},
            {"kind": "other"},
        ]
        for change in variants:
            bad = plan(); bad.update(change)
            with self.subTest(change=change), self.assertRaises(InspectionError):
                validate_private_plan(bad)
        for change in ({"sprite_count": -1}, {"behavior_count": -1}, {"script_count": -1}, {"sprite_sha256": "z" * 64}):
            bad = plan(); bad["steps"][0].update(change)
            with self.subTest(row=change), self.assertRaises(InspectionError):
                validate_private_plan(bad)

    def test_compare_verified(self):
        result = compare_private_trace(plan(), observation())
        self.assertTrue(result["verified"])
        self.assertEqual(result["planned_steps"], 1)
        self.assertEqual(result["checks"][0]["reasons"], [])

    def test_compare_mismatch(self):
        result = compare_private_trace(plan(), observation(sprite_count=2))
        self.assertFalse(result["verified"])
        self.assertIn("sprite_count", result["checks"][0]["reasons"])

    def test_compare_unplanned_and_source_mismatch(self):
        result = compare_private_trace(plan(), observation(frame=2))
        self.assertFalse(result["verified"])
        self.assertEqual(result["checks"][0]["reasons"], ["frame-not-planned"])
        bad = observation()
        bad["source_sha256"] = "f" * 64
        with self.assertRaises(InspectionError):
            compare_private_trace(plan(), bad)

    def test_invalid_plan(self):
        with self.assertRaises(InspectionError):
            compare_private_trace({"kind": "bad"}, observation())
        duplicate = plan()
        duplicate["steps"].append(dict(duplicate["steps"][0]))
        with self.assertRaises(InspectionError):
            compare_private_trace(duplicate, observation())

    def test_verified_fixture(self):
        comparison = compare_private_trace(plan(), observation())
        fixture = synthetic_fixture_from_verified_comparison(comparison)
        self.assertEqual(fixture["format"], "case-recomp-scenario")
        self.assertEqual(fixture["events"][0]["kind"], "target-found")
        self.assertNotIn(ZERO, json.dumps(fixture))

    def test_fixture_event_shapes(self):
        for kind in ("enter", "leave", "frame-reached", "scenario-complete"):
            obs = observation(event_kind=kind)
            fixture = synthetic_fixture_from_verified_comparison(compare_private_trace(plan(), obs), f"x-{kind}")
            self.assertEqual(fixture["events"][0]["kind"], kind)

    def test_verified_fixture_rejects_unknown_event_kind(self):
        comparison = compare_private_trace(plan(), observation())
        comparison["checks"][0]["event_kind"] = "unknown"
        with self.assertRaises(InspectionError):
            synthetic_fixture_from_verified_comparison(comparison)

    def test_unverified_fixture_rejected(self):
        with self.assertRaises(InspectionError):
            synthetic_fixture_from_verified_comparison({"kind": "comparison", "verified": False})
        with self.assertRaises(InspectionError):
            synthetic_fixture_from_verified_comparison({"kind": "comparison", "verified": True, "checks": []})

    def test_trace_compare_and_fixture_cli(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            plan_path = root / "plan.json"
            obs_path = root / "observation.json"
            comparison_path = root / "comparison.json"
            fixture_path = root / "fixture.json"
            plan_path.write_text(json.dumps(plan()))
            obs_path.write_text(json.dumps(observation()))
            self.assertEqual(main(["trace-compare", str(plan_path), str(obs_path), "--output", str(comparison_path)]), 0)
            self.assertEqual(main(["trace-synthetic-fixture", str(comparison_path), "--output", str(fixture_path), "--id", "cli-synthetic"]), 0)
            self.assertEqual(json.loads(fixture_path.read_text())["id"], "cli-synthetic")

    def test_load_and_write(self):
        comparison = compare_private_trace(plan(), observation())
        fixture = synthetic_fixture_from_verified_comparison(comparison)
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            p = root / "fixture.json"
            write_synthetic_fixture(p, fixture)
            self.assertEqual(load_json(p)["id"], "verified-synthetic")
            with self.assertRaises(InspectionError):
                write_synthetic_fixture(p, fixture)
            bad = root / "bad.json"
            bad.write_text("[]")
            with self.assertRaises(InspectionError):
                load_json(bad)
            bad.write_text("{")
            with self.assertRaises(InspectionError):
                load_json(bad)


if __name__ == "__main__":
    unittest.main()

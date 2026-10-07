"""Fail-closed Phase 6 vertical-slice evidence and verified-flow proofs.

Private plans may contain structural fingerprints from a locally owned Director
movie. Runtime flow proofs contain only generic navigation rules and are emitted
only after an independently observed original-runtime trace matches the static
Score/sprite/handler fingerprints.
"""
from __future__ import annotations

from hashlib import sha256
import json
import struct
from pathlib import Path
from typing import Any

from .director import open_archive
from .inspector import InspectionError
from .lingo_index import index_archive
from .score import lingo_script_number, parse_cast_member, parse_cast_order, parse_score
from .scenario import canonical_bytes, load_scenario

SPEC_FORMAT = "case-recomp-private-vertical-slice"
OBS_FORMAT = "case-recomp-vertical-slice-observation"
COMPARE_FORMAT = "case-recomp-vertical-slice-comparison"
FLOW_FORMAT = "case-recomp-verified-flow"
VERSION = 1
STAGES = ("boot", "menu", "map", "scene")
TRANSITIONS = (("boot", "menu"), ("menu", "map"), ("map", "scene"))
_HEX = set("0123456789abcdef")


def _digest(value: Any) -> str:
    return sha256(json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode()).hexdigest()


def _is_hash(value: Any) -> bool:
    return isinstance(value, str) and len(value) == 64 and set(value) <= _HEX


def _single(archive, tag: str) -> bytes:
    ids = [rid for rid, item in archive.entries.items() if item.tag == tag]
    if len(ids) != 1:
        raise InspectionError(f"expected exactly one {tag} resource")
    return archive.get_resource(ids[0])


def decode_private_frame_labels(data: bytes) -> dict[str, int]:
    """Decode VWLB labels for a private report; never publish original names."""
    if len(data) < 6:
        raise InspectionError("truncated VWLB label table")
    count = struct.unpack_from(">H", data, 0)[0]
    table_end = 2 + count * 4
    if count > 16384 or table_end + 4 > len(data):
        raise InspectionError("invalid VWLB label table")
    rows = [struct.unpack_from(">HH", data, 2 + i * 4) for i in range(count)]
    size = struct.unpack_from(">i", data, table_end)[0]
    block_start = table_end + 4
    if size < 0 or block_start + size > len(data):
        raise InspectionError("invalid VWLB string block")
    block = data[block_start:block_start + size]
    ordered = sorted(rows, key=lambda row: row[1])
    result: dict[str, int] = {}
    for i, (frame, offset) in enumerate(ordered):
        end = len(block) if i + 1 == len(ordered) else ordered[i + 1][1]
        if offset > end or end > len(block):
            raise InspectionError("invalid VWLB label offsets")
        name = block[offset:end].rstrip(b"\0").decode("mac_roman")
        if not name or name in result:
            raise InspectionError("invalid or duplicate VWLB label")
        result[name] = max(1, frame)
    return result


def _stage_fingerprint(score, frame: int, cast_order, casts, scripts, handlers) -> dict:
    sprites = sorted(item.signature() for item in score.sprites if item.frame == frame)
    behaviors = [item for item in score.behaviors if item.start_frame <= frame <= item.end_frame]
    script_ids: set[int] = set()
    for item in behaviors:
        if item.cast_member is None or item.cast_lib not in (None, 1) or not 1 <= item.cast_member <= len(cast_order):
            continue
        resource_id = cast_order[item.cast_member - 1]
        member = casts.get(resource_id) if resource_id else None
        if member and member.script_number in scripts:
            script_ids.add(scripts[member.script_number])
    selected_handlers = sorted(row for sid in script_ids for row in handlers.get(sid, ()))
    return {
        "entry_sprite_count": len(sprites),
        "entry_sprite_sha256": _digest(sprites),
        "entry_behavior_count": len(behaviors),
        "script_count": len(script_ids),
        "handler_set_sha256": _digest(selected_handlers),
    }


def build_private_vertical_slice(path: Path, *, menu_label: str, map_label: str, scene_label: str,
                                 boot_frame: int = 1) -> dict:
    archive, _ = open_archive(path)
    if archive.kind != "movie":
        raise InspectionError("vertical-slice analysis requires a Director movie")
    score = parse_score(_single(archive, "VWSC"))
    labels = decode_private_frame_labels(_single(archive, "VWLB"))
    try:
        menu_frame, map_frame, scene_frame = (labels[menu_label], labels[map_label], labels[scene_label])
    except KeyError as exc:
        raise InspectionError("requested vertical-slice marker not found") from exc
    if not (1 <= boot_frame < menu_frame < map_frame < scene_frame <= score.frame_count):
        raise InspectionError("vertical-slice markers are not strictly ordered")
    following = min((frame for frame in labels.values() if frame > scene_frame), default=score.frame_count + 1)
    starts = (boot_frame, menu_frame, map_frame, scene_frame)
    ends = (menu_frame - 1, map_frame - 1, scene_frame - 1, following - 1)
    cast_order = parse_cast_order(_single(archive, "CAS*"))
    casts = {rid: parse_cast_member(rid, archive.get_resource(rid))
             for rid, item in archive.entries.items() if item.tag == "CASt"}
    scripts = {lingo_script_number(archive.get_resource(rid)): rid
               for rid, item in archive.entries.items() if item.tag == "Lscr"}
    handler_rows: dict[int, list[tuple]] = {}
    for row in index_archive(archive, redact=True)["handlers"]:
        handler_rows.setdefault(int(row["script_id"]), []).append(
            (row["name"], row["bytecode_sha256"], int(row["bytecode_size"])))
    private_markers = (None, menu_label, map_label, scene_label)
    stages = [
        {"id": stage_id, "start_frame": start, "end_frame": end,
         "marker_sha256": sha256(marker.casefold().encode()).hexdigest() if marker else None,
         **_stage_fingerprint(score, start, cast_order, casts, scripts, handler_rows)}
        for stage_id, start, end, marker in zip(STAGES, starts, ends, private_markers)
    ]
    return {
        "format": SPEC_FORMAT, "version": VERSION, "source_sha256": sha256(path.read_bytes()).hexdigest(),
        "stages": stages,
        "transitions": [{"from": a, "to": b, "verification": "static-only"} for a, b in TRANSITIONS],
        "promotable_rules": 0,
        "verification_note": "static Score/sprite/handler evidence only; independent original-runtime observation required",
    }


def validate_private_slice(doc: Any) -> dict:
    if not isinstance(doc, dict) or doc.get("format") != SPEC_FORMAT or doc.get("version") != VERSION or not _is_hash(doc.get("source_sha256")):
        raise InspectionError("invalid private vertical-slice specification")
    stages = doc.get("stages")
    if not isinstance(stages, list) or [row.get("id") if isinstance(row, dict) else None for row in stages] != list(STAGES):
        raise InspectionError("invalid vertical-slice stages")
    previous = 0
    for row in stages:
        if not isinstance(row.get("start_frame"), int) or not isinstance(row.get("end_frame"), int) or not previous < row["start_frame"] <= row["end_frame"]:
            raise InspectionError("invalid vertical-slice frame range")
        previous = row["end_frame"]
        if any(not isinstance(row.get(k), int) or row[k] < 0 for k in ("entry_sprite_count", "entry_behavior_count", "script_count")):
            raise InspectionError("invalid vertical-slice counts")
        if any(not _is_hash(row.get(k)) for k in ("entry_sprite_sha256", "handler_set_sha256")):
            raise InspectionError("invalid vertical-slice fingerprint")
    return doc


def validate_slice_observation(doc: Any) -> dict:
    if not isinstance(doc, dict) or doc.get("format") != OBS_FORMAT or doc.get("version") != VERSION or not _is_hash(doc.get("source_sha256")):
        raise InspectionError("invalid vertical-slice observation")
    if doc.get("evidence_kind") not in {"independent-original-runtime", "synthetic-test"}:
        raise InspectionError("unsupported vertical-slice evidence kind")
    if doc.get("evidence_kind") == "independent-original-runtime":
        capture = doc.get("capture_evidence")
        if not isinstance(capture, dict) or capture.get("format") != "case-recomp-native-capture-consensus" \
                or capture.get("version") != 1 or capture.get("runtime_kind") != "native-projector":
            raise InspectionError("independent observation requires native capture consensus")
        if not isinstance(capture.get("trial_count"), int) or capture["trial_count"] < 2 \
                or capture.get("visual_consensus") is not True or capture.get("static_fingerprints_bound") is not True:
            raise InspectionError("independent observation lacks repeated visual consensus")
        stage_pixels = capture.get("stage_pixel_sha256")
        if not isinstance(stage_pixels, dict) or set(stage_pixels) != set(STAGES) \
                or any(not _is_hash(value) for value in stage_pixels.values()):
            raise InspectionError("invalid native capture stage pixel hashes")
    stages = doc.get("stages")
    if not isinstance(stages, list) or [x.get("id") if isinstance(x, dict) else None for x in stages] != list(STAGES):
        raise InspectionError("invalid observed stages")
    for row in stages:
        if not isinstance(row.get("frame"), int) or row["frame"] < 1 or any(
                not _is_hash(row.get(k)) for k in ("sprite_sha256", "handler_set_sha256", "observable_state_sha256")):
            raise InspectionError("invalid observed stage evidence")
    transitions = doc.get("transitions")
    if not isinstance(transitions, list) or len(transitions) != len(TRANSITIONS):
        raise InspectionError("invalid observed transitions")
    for row, expected, expected_input in zip(transitions, TRANSITIONS, ("none", "start", "enter-scene")):
        if (row.get("from"), row.get("to")) != expected or row.get("input_kind") != expected_input:
            raise InspectionError("invalid observed transition")
        elapsed = row.get("elapsed_ms")
        if not isinstance(elapsed, int) or elapsed < 0:
            raise InspectionError("invalid observed transition timing")
    trials = doc.get("timing_trials", 1)
    if not isinstance(trials, int) or trials < 1 or not isinstance(doc.get("controlled_timing", False), bool):
        raise InspectionError("invalid observation timing evidence")
    return doc


def compare_vertical_slice(spec: dict, observation: dict) -> dict:
    validate_private_slice(spec); validate_slice_observation(observation)
    if spec["source_sha256"] != observation["source_sha256"]:
        raise InspectionError("vertical-slice source digest mismatch")
    checks = []
    for expected, actual in zip(spec["stages"], observation["stages"]):
        reasons = []
        if expected["start_frame"] != actual["frame"]: reasons.append("frame")
        if expected["entry_sprite_sha256"] != actual["sprite_sha256"]: reasons.append("sprite-fingerprint")
        if expected["handler_set_sha256"] != actual["handler_set_sha256"]: reasons.append("handler-fingerprint")
        checks.append({"id": expected["id"], "verified": not reasons, "reasons": reasons,
                       "observable_state_sha256": actual["observable_state_sha256"]})
    structural = all(row["verified"] for row in checks)
    capture = observation.get("capture_evidence", {})
    independent = observation["evidence_kind"] == "independent-original-runtime" \
        and capture.get("visual_consensus") is True and capture.get("trial_count", 0) >= 2
    verified = structural and independent
    return {
        "format": COMPARE_FORMAT, "version": VERSION, "source_sha256": spec["source_sha256"],
        "evidence_kind": observation["evidence_kind"], "verified": verified,
        "stage_checks": checks, "transitions": observation["transitions"],
        "timing_trials": int(observation.get("timing_trials", 1)),
        "timing_verified": verified and observation.get("controlled_timing") is True
                           and int(observation.get("timing_trials", 1)) >= 2,
    }


def verified_flow_from_comparison(comparison: dict, *, scenario: dict, scene_id: str) -> dict:
    if not isinstance(comparison, dict) or comparison.get("format") != COMPARE_FORMAT or comparison.get("version") != VERSION or comparison.get("verified") is not True:
        raise InspectionError("only a verified vertical-slice comparison can produce flow rules")
    if comparison.get("evidence_kind") != "independent-original-runtime":
        raise InspectionError("verified flow requires independent original-runtime evidence")
    if not isinstance(scene_id, str) or not scene_id:
        raise InspectionError("invalid verified-flow scene binding")
    scenario_id = scenario.get("id") if isinstance(scenario, dict) else None
    scenes = scenario.get("scenes") if isinstance(scenario, dict) else None
    if not isinstance(scenario_id, str) or not scenario_id or not isinstance(scenes, list) or scene_id not in {x.get("id") for x in scenes if isinstance(x, dict)}:
        raise InspectionError("verified-flow scene is not present in scenario")
    timings = {(row["from"], row["to"]): row["elapsed_ms"] for row in comparison["transitions"]}
    use_timing = comparison.get("timing_verified") is True
    proof = {
        "format": FLOW_FORMAT, "version": VERSION, "scenario_id": scenario_id,
        "scenario_sha256": sha256(canonical_bytes(scenario)).hexdigest(),
        "source_sha256": comparison["source_sha256"], "evidence_kind": comparison["evidence_kind"],
        "boot_verified": True, "rules": [
            {"id": "menu-map", "from_screen": "MENU", "input_kind": "start", "to_screen": "MAP",
             "scene_id": None, "not_before_ms": timings[("menu", "map")] if use_timing else 0},
            {"id": "map-scene", "from_screen": "MAP", "input_kind": "enter-scene", "to_screen": "SCENE",
             "scene_id": scene_id, "not_before_ms": timings[("map", "scene")] if use_timing else 0},
        ],
    }
    return validate_verified_flow_proof(proof)


def validate_verified_flow_proof(doc: Any) -> dict:
    if not isinstance(doc, dict) or doc.get("format") != FLOW_FORMAT or doc.get("version") != VERSION or doc.get("boot_verified") is not True:
        raise InspectionError("invalid verified-flow proof")
    if doc.get("evidence_kind") != "independent-original-runtime":
        raise InspectionError("verified-flow proof requires independent original-runtime evidence")
    if not isinstance(doc.get("scenario_id"), str) or not doc["scenario_id"] or not _is_hash(doc.get("scenario_sha256")) or not _is_hash(doc.get("source_sha256")):
        raise InspectionError("invalid verified-flow proof identity")
    rules = doc.get("rules")
    if not isinstance(rules, list) or len(rules) != 2:
        raise InspectionError("verified-flow proof requires exactly two navigation rules")
    for row, triple in zip(rules, (("MENU", "start", "MAP"), ("MAP", "enter-scene", "SCENE"))):
        if not isinstance(row, dict) or (row.get("from_screen"), row.get("input_kind"), row.get("to_screen")) != triple:
            raise InspectionError("invalid verified-flow rule")
        if not isinstance(row.get("not_before_ms"), int) or row["not_before_ms"] < 0:
            raise InspectionError("invalid verified-flow timing")
    if rules[0].get("scene_id") is not None or not isinstance(rules[1].get("scene_id"), str) or not rules[1]["scene_id"]:
        raise InspectionError("invalid verified-flow scene binding")
    return doc


def load_json(path: Path) -> dict:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise InspectionError(f"cannot read vertical-slice JSON: {exc}") from exc
    if not isinstance(value, dict):
        raise InspectionError("vertical-slice JSON root must be an object")
    return value


def write_json_create_only(path: Path, document: dict) -> None:
    if path.exists() or path.is_symlink() or not path.parent.is_dir():
        raise InspectionError("vertical-slice output must be a new file in an existing directory")
    path.write_text(json.dumps(document, ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8")


def load_scenario_for_proof(path: Path) -> dict:
    return load_scenario(path)

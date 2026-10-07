"""Hash-only private behavior traces and safe synthetic promotion."""
from __future__ import annotations

from hashlib import sha256
import json
from pathlib import Path
from typing import Iterable

from .director import open_archive
from .inspector import InspectionError
from .lingo_index import index_archive
from .score import BehaviorRef, ScoreModel, lingo_script_number, parse_cast_member, parse_cast_order, parse_frame_labels, parse_score
from .scenario import canonical_bytes, validate_scenario

FORMAT = "case-recomp-behavior-trace"
TRACE_FORMAT = FORMAT
VERSION = 1
MAX_STEPS = 10_000
INPUTS = {"start", "enter-scene", "tap", "advance-frame", "back", "reset", "none"}
EVENTS = {"enter", "leave", "target-found", "frame-reached", "scenario-complete", "none"}
_HEX = set("0123456789abcdef")


def _sha(value: object) -> str:
    raw = json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode()
    return sha256(raw).hexdigest()


def _hash(value: object) -> bool:
    return isinstance(value, str) and len(value) == 64 and set(value) <= _HEX


def _single(archive, tag: str) -> bytes:
    ids = [rid for rid, entry in archive.entries.items() if entry.tag == tag]
    if len(ids) != 1:
        raise InspectionError(f"expected exactly one {tag} resource")
    return archive.get_resource(ids[0])


def _active(score: ScoreModel, frame: int) -> tuple[list[tuple], tuple[BehaviorRef, ...]]:
    sprites = sorted((s.channel, s.sprite_type, s.ink, s.trails, s.stretch, s.cast_lib, s.cast_member,
                      s.x, s.y, s.width, s.height, s.blend, s.flip_h, s.flip_v)
                     for s in score.sprites if s.frame == frame)
    behaviors = tuple(b for b in score.behaviors if b.start_frame <= frame <= b.end_frame)
    return sprites, behaviors


def frame_fingerprint(score: ScoreModel, frame: int) -> dict:
    if not 1 <= frame <= score.frame_count:
        raise InspectionError("trace frame outside score")
    sprites, behaviors = _active(score, frame)
    return {"frame": frame, "sprite_count": len(sprites), "sprite_sha256": _sha(sprites),
            "behavior_count": len(behaviors)}


def _links(archive):
    order = parse_cast_order(_single(archive, "CAS*"))
    casts = {rid: parse_cast_member(rid, archive.get_resource(rid))
             for rid, entry in archive.entries.items() if entry.tag == "CASt"}
    scripts = {lingo_script_number(archive.get_resource(rid)): rid
               for rid, entry in archive.entries.items() if entry.tag == "Lscr"}
    return order, casts, scripts


def _script_ids(behaviors: Iterable[BehaviorRef], order, casts, scripts) -> list[int]:
    result = set()
    for b in behaviors:
        if b.cast_member is None or b.cast_lib not in (None, 1) or not 1 <= b.cast_member <= len(order):
            continue
        rid = order[b.cast_member - 1]
        member = casts.get(rid) if rid else None
        number = getattr(member, "script_number", None)
        if number in scripts:
            result.add(scripts[number])
    return sorted(result)


def _handler_hashes(archive) -> dict[int, str]:
    grouped: dict[int, list[tuple]] = {}
    for row in index_archive(archive, redact=True)["handlers"]:
        grouped.setdefault(int(row["script_id"]), []).append(
            (row["name"], row["bytecode_sha256"], int(row["bytecode_size"])))
    return {key: _sha(sorted(value)) for key, value in grouped.items()}


# Backwards-compatible internal names retained for the synthetic regression suite.
_single_resource = _single
_script_link_map = _links
_behavior_script_ids = _script_ids
_handler_digest_by_script = _handler_hashes


def build_private_trace_plan(path: Path, frames: Iterable[int] | None = None) -> dict:
    archive, _ = open_archive(path)
    if archive.kind != "movie":
        raise InspectionError("trace planning requires a Director movie")
    score = parse_score(_single_resource(archive, "VWSC"))
    labels = parse_frame_labels(_single_resource(archive, "VWLB"))
    order, casts, scripts = _script_link_map(archive)
    handlers = _handler_digest_by_script(archive)
    selected = set(map(int, frames)) if frames is not None else {1, score.frame_count, *(x.frame for x in labels)}
    if not 1 <= len(selected) <= MAX_STEPS:
        raise InspectionError("invalid trace frame selection")
    steps = []
    for frame in sorted(selected):
        row = frame_fingerprint(score, frame)
        ids = _behavior_script_ids(_active(score, frame)[1], order, casts, scripts)
        row.update(script_count=len(ids), handler_set_sha256=_sha([handlers.get(i, "") for i in ids]))
        steps.append(row)
    return {"format": FORMAT, "version": VERSION, "kind": "private-plan",
            "source_sha256": sha256(path.read_bytes()).hexdigest(), "steps": steps,
            "observation_contract": {"required": ["frame", "sprite_count", "sprite_sha256",
                "handler_set_sha256", "observable_state_sha256"], "optional": ["input_kind", "event_kind"]}}


def validate_private_plan(doc: dict) -> dict:
    required_root = {"format", "version", "kind", "source_sha256", "steps"}
    allowed_root = required_root | {"observation_contract"}
    if not isinstance(doc, dict) or not required_root <= set(doc) or set(doc) - allowed_root \
            or doc.get("format") != FORMAT \
            or isinstance(doc.get("version"), bool) or not isinstance(doc.get("version"), int) \
            or doc.get("version") != VERSION or doc.get("kind") != "private-plan" \
            or not _hash(doc.get("source_sha256")):
        raise InspectionError("invalid private trace plan")
    contract = doc.get("observation_contract")
    if contract is not None and (
            not isinstance(contract, dict) or set(contract) != {"required", "optional"}
            or contract.get("required") != ["frame", "sprite_count", "sprite_sha256",
                                            "handler_set_sha256", "observable_state_sha256"]
            or contract.get("optional") != ["input_kind", "event_kind"]):
        raise InspectionError("invalid private trace observation contract")
    steps = doc.get("steps")
    if not isinstance(steps, list) or not 1 <= len(steps) <= MAX_STEPS:
        raise InspectionError("invalid private trace plan steps")
    seen = set()
    step_keys = {"frame", "sprite_count", "sprite_sha256", "behavior_count", "script_count", "handler_set_sha256"}
    for row in steps:
        if not isinstance(row, dict) or set(row) != step_keys:
            raise InspectionError("invalid private trace plan step")
        frame = row.get("frame")
        if isinstance(frame, bool) or not isinstance(frame, int) or frame < 1 or frame in seen:
            raise InspectionError("invalid private trace plan frame")
        seen.add(frame)
        if any(isinstance(row.get(k), bool) or not isinstance(row.get(k), int) or row[k] < 0
               for k in ("sprite_count", "behavior_count", "script_count")):
            raise InspectionError("invalid private trace plan count")
        if any(not _hash(row.get(k)) for k in ("sprite_sha256", "handler_set_sha256")):
            raise InspectionError("invalid private trace plan digest")
    return doc


def validate_observation_trace(doc: dict) -> dict:
    if not isinstance(doc, dict) or doc.get("format") != FORMAT or doc.get("version") != VERSION \
            or doc.get("kind") != "observation" or not _hash(doc.get("source_sha256")):
        raise InspectionError("invalid observation trace")
    steps = doc.get("steps")
    if not isinstance(steps, list) or not 1 <= len(steps) <= MAX_STEPS:
        raise InspectionError("invalid observation steps")
    previous = 0
    for row in steps:
        frame = row.get("frame") if isinstance(row, dict) else None
        if not isinstance(frame, int) or frame < 1 or frame < previous:
            raise InspectionError("observation frames must be positive and monotonic")
        previous = frame
        if not isinstance(row.get("sprite_count"), int) or row["sprite_count"] < 0:
            raise InspectionError("invalid observation sprite count")
        if any(not _hash(row.get(k)) for k in ("sprite_sha256", "handler_set_sha256", "observable_state_sha256")):
            raise InspectionError("invalid observation digest")
        if row.get("input_kind", "none") not in INPUTS or row.get("event_kind", "none") not in EVENTS:
            raise InspectionError("invalid observation event/input kind")
    return doc


def compare_private_trace(plan: dict, observation: dict) -> dict:
    validate_private_plan(plan); validate_observation_trace(observation)
    if plan["source_sha256"] != observation["source_sha256"]:
        raise InspectionError("trace source digest mismatch")
    expected = {row["frame"]: row for row in plan["steps"]}
    checks = []
    for row in observation["steps"]:
        ref = expected.get(row["frame"]); reasons = []
        if ref is None:
            reasons.append("frame-not-planned")
        else:
            reasons.extend(k for k in ("sprite_count", "sprite_sha256", "handler_set_sha256") if row.get(k) != ref.get(k))
        checks.append({"frame": row["frame"], "verified": not reasons, "reasons": reasons,
                       "input_kind": row.get("input_kind", "none"), "event_kind": row.get("event_kind", "none"),
                       "observable_state_sha256": row["observable_state_sha256"]})
    verified = len(checks) == len(expected) and all(x["verified"] for x in checks) and set(expected) == {x["frame"] for x in checks}
    return {"format": FORMAT, "version": VERSION, "kind": "comparison", "verified": verified,
            "planned_steps": len(expected), "observed_steps": len(checks), "checks": checks}


def synthetic_fixture_from_verified_comparison(comparison: dict, fixture_id: str = "verified-synthetic") -> dict:
    checks = comparison.get("checks") if isinstance(comparison, dict) and comparison.get("kind") == "comparison" \
        and comparison.get("verified") is True else None
    if not isinstance(checks, list) or not checks:
        raise InspectionError("only a fully verified trace comparison can produce a public fixture")
    kinds = [x.get("event_kind", "none") for x in checks if x.get("event_kind", "none") != "none"]
    if any(k not in EVENTS for k in kinds):
        raise InspectionError("verified comparison contains unsupported event kind")
    events = []
    for n, kind in enumerate(kinds, 1):
        row = {"id": f"event-{n}", "kind": kind}
        if kind != "scenario-complete": row["scene"] = "room-a"
        if kind == "target-found": row["target"] = "target-a"
        if kind == "frame-reached": row["frame"] = 10
        events.append(row)
    doc = {"format": "case-recomp-scenario", "version": 1, "id": fixture_id,
           "design": {"width": 320, "height": 240},
           "scenes": [{"id": "room-a", "frame_start": 1, "frame_end": 20,
                       "targets": [{"id": "target-a", "rect": [40, 50, 90, 110], "z": 1}]}], "events": events}
    return validate_scenario(doc)


def load_json(path: Path) -> dict:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise InspectionError(f"cannot read trace JSON: {exc}") from exc
    if not isinstance(value, dict):
        raise InspectionError("trace JSON root must be an object")
    return value


def write_synthetic_fixture(path: Path, document: dict) -> None:
    if path.exists():
        raise InspectionError("output already exists")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(canonical_bytes(document))

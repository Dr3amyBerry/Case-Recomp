"""Versioned, content-neutral scenario model for the Android reconstruction.

The schema is intentionally generic.  It can represent synthetic fixtures or a
privately generated reconstruction without embedding original game media,
labels, Lingo text, or recovered cast names in the public repository.
"""
from __future__ import annotations

from hashlib import sha256
import json
from pathlib import Path
from typing import Any

from .inspector import InspectionError

FORMAT = "case-recomp-scenario"
VERSION = 1
MAX_DOCUMENT_BYTES = 2 * 1024 * 1024
MAX_SCENES = 2048
MAX_TARGETS_PER_SCENE = 4096
MAX_EVENTS = 16384
MAX_DIMENSION = 16384
MAX_FRAME = 10_000_000
_ALLOWED_TOP = {"format", "version", "id", "design", "scenes", "events"}
_ALLOWED_SCENE = {"id", "frame_start", "frame_end", "targets"}
_ALLOWED_TARGET = {"id", "rect", "z"}
_ALLOWED_EVENT = {"id", "kind", "scene", "target", "frame", "to_scene"}
_EVENT_KINDS = {"enter", "leave", "target-found", "frame-reached", "scenario-complete"}


def _clean_id(value: Any, field: str) -> str:
    if not isinstance(value, str) or not value or len(value) > 128:
        raise InspectionError(f"invalid scenario {field}")
    if any(ord(ch) < 32 for ch in value) or "/" in value or "\\" in value:
        raise InspectionError(f"unsafe scenario {field}")
    return value


def _integer(value: Any, field: str, low: int, high: int) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or not low <= value <= high:
        raise InspectionError(f"invalid scenario {field}")
    return value


def _rect(value: Any) -> list[int]:
    if not isinstance(value, list) or len(value) != 4:
        raise InspectionError("scenario target rect must have four integers")
    left, top, right, bottom = [_integer(v, "rect coordinate", -MAX_DIMENSION, MAX_DIMENSION) for v in value]
    if right <= left or bottom <= top:
        raise InspectionError("invalid scenario target rectangle")
    return [left, top, right, bottom]


def validate_scenario(document: Any) -> dict:
    """Validate and normalize scenario-v1 using a strict, bounded allow-list."""
    if not isinstance(document, dict) or set(document) - _ALLOWED_TOP:
        raise InspectionError("invalid scenario top-level object")
    if document.get("format") != FORMAT or document.get("version") != VERSION:
        raise InspectionError("unsupported scenario format/version")
    scenario_id = _clean_id(document.get("id"), "id")
    design = document.get("design")
    if not isinstance(design, dict) or set(design) != {"width", "height"}:
        raise InspectionError("invalid scenario design")
    width = _integer(design["width"], "design width", 1, MAX_DIMENSION)
    height = _integer(design["height"], "design height", 1, MAX_DIMENSION)

    scenes = document.get("scenes")
    if not isinstance(scenes, list) or not 1 <= len(scenes) <= MAX_SCENES:
        raise InspectionError("invalid scenario scene count")
    normalized_scenes: list[dict] = []
    scene_ids: set[str] = set()
    target_ids_by_scene: dict[str, set[str]] = {}
    for scene in scenes:
        if not isinstance(scene, dict) or set(scene) - _ALLOWED_SCENE or set(scene) != _ALLOWED_SCENE:
            raise InspectionError("invalid scenario scene")
        sid = _clean_id(scene["id"], "scene id")
        if sid in scene_ids:
            raise InspectionError("duplicate scenario scene id")
        scene_ids.add(sid)
        start = _integer(scene["frame_start"], "frame start", 1, MAX_FRAME)
        end = _integer(scene["frame_end"], "frame end", start, MAX_FRAME)
        targets = scene["targets"]
        if not isinstance(targets, list) or not 1 <= len(targets) <= MAX_TARGETS_PER_SCENE:
            raise InspectionError("invalid scenario target count")
        seen_targets: set[str] = set()
        normalized_targets: list[dict] = []
        for target in targets:
            if not isinstance(target, dict) or set(target) != _ALLOWED_TARGET:
                raise InspectionError("invalid scenario target")
            tid = _clean_id(target["id"], "target id")
            if tid in seen_targets:
                raise InspectionError("duplicate scenario target id")
            seen_targets.add(tid)
            normalized_targets.append({"id": tid, "rect": _rect(target["rect"]),
                                       "z": _integer(target["z"], "target z", -32768, 32767)})
        target_ids_by_scene[sid] = seen_targets
        normalized_scenes.append({"id": sid, "frame_start": start, "frame_end": end,
                                  "targets": normalized_targets})

    events = document.get("events", [])
    if not isinstance(events, list) or len(events) > MAX_EVENTS:
        raise InspectionError("invalid scenario event count")
    event_ids: set[str] = set()
    normalized_events: list[dict] = []
    for event in events:
        if not isinstance(event, dict) or set(event) - _ALLOWED_EVENT:
            raise InspectionError("invalid scenario event")
        eid = _clean_id(event.get("id"), "event id")
        if eid in event_ids:
            raise InspectionError("duplicate scenario event id")
        event_ids.add(eid)
        kind = event.get("kind")
        if kind not in _EVENT_KINDS:
            raise InspectionError("unsupported scenario event kind")
        sid = event.get("scene")
        if sid is not None:
            sid = _clean_id(sid, "event scene")
            if sid not in scene_ids:
                raise InspectionError("scenario event references unknown scene")
        target = event.get("target")
        if target is not None:
            target = _clean_id(target, "event target")
            if sid is None or target not in target_ids_by_scene[sid]:
                raise InspectionError("scenario event references unknown target")
        frame = event.get("frame")
        if frame is not None:
            frame = _integer(frame, "event frame", 1, MAX_FRAME)
            if sid is not None:
                scene = next(s for s in normalized_scenes if s["id"] == sid)
                if not scene["frame_start"] <= frame <= scene["frame_end"]:
                    raise InspectionError("scenario event frame outside scene range")
        to_scene = event.get("to_scene")
        if to_scene is not None:
            to_scene = _clean_id(to_scene, "event destination")
            if to_scene not in scene_ids:
                raise InspectionError("scenario event references unknown destination")
        normalized_events.append({k: v for k, v in {"id": eid, "kind": kind, "scene": sid,
                                                    "target": target, "frame": frame,
                                                    "to_scene": to_scene}.items() if v is not None})

    return {"format": FORMAT, "version": VERSION, "id": scenario_id,
            "design": {"width": width, "height": height},
            "scenes": normalized_scenes, "events": normalized_events}


def canonical_bytes(document: Any) -> bytes:
    normalized = validate_scenario(document)
    return (json.dumps(normalized, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n").encode("utf-8")


def scenario_fingerprint(document: Any) -> str:
    return sha256(canonical_bytes(document)).hexdigest()


def load_scenario(path: Path) -> dict:
    if not path.is_file() or path.stat().st_size > MAX_DOCUMENT_BYTES:
        raise InspectionError("invalid scenario file")
    try:
        raw = path.read_bytes()
        document = json.loads(raw.decode("utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise InspectionError("cannot read scenario JSON") from exc
    return validate_scenario(document)

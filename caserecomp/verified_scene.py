"""Fail-closed hidden-object scene evidence and verified-scene proofs.

A private ``case-recomp-scene-observation`` records repeated, controlled
native-projector trials of one scene: the clickable mask of each verified
target, every click (misses included), the observable post-state hash after
each click and how the scene closes. Only an observation whose trials agree
exactly can produce a ``case-recomp-verified-scene`` proof (``.crscene``),
which is bound to a private-content package, scenario and source like the
Phase 6 ``.crflow``. Proofs carry generic rules and hit masks, never media.
"""
from __future__ import annotations

import base64
import binascii
from hashlib import sha256
from typing import Any

from .inspector import InspectionError
from .scenario import canonical_bytes
from .vertical_slice import _digest, _is_hash

OBSERVATION_FORMAT = "case-recomp-scene-observation"
PROOF_FORMAT = "case-recomp-verified-scene"
VERSION = 1
MAX_DIMENSION = 16384
MAX_TARGETS = 256
MAX_TRIALS = 64
MAX_STEPS = 4096
MAX_MASK_SIDE = 4096


def _int(value: Any, low: int, high: int, what: str) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or not low <= value <= high:
        raise InspectionError(f"invalid {what}")
    return value


def _id(value: Any, what: str) -> str:
    if not isinstance(value, str) or not value or len(value) > 128 or any(ord(ch) < 32 for ch in value) \
            or "/" in value or "\\" in value:
        raise InspectionError(f"invalid {what}")
    return value


def _point(value: Any) -> tuple[int, int]:
    if not isinstance(value, list) or len(value) != 2:
        raise InspectionError("invalid scene click point")
    return _int(value[0], 0, MAX_DIMENSION, "scene click point"), _int(value[1], 0, MAX_DIMENSION, "scene click point")


def _rect(value: Any) -> tuple[int, int, int, int]:
    if not isinstance(value, list) or len(value) != 4:
        raise InspectionError("invalid scene region")
    left, top, right, bottom = (_int(v, 0, MAX_DIMENSION, "scene region") for v in value)
    if right <= left or bottom <= top:
        raise InspectionError("invalid scene region")
    return left, top, right, bottom


def encode_mask(left: int, top: int, rows: list[list[bool]]) -> dict:
    """Pack a boolean hit mask (rows of equal width) MSB-first, one padded byte row per mask row."""
    if not rows or not rows[0] or any(len(row) != len(rows[0]) for row in rows):
        raise InspectionError("hit mask rows must be non-empty and rectangular")
    width, height = len(rows[0]), len(rows)
    packed = bytearray()
    for row in rows:
        for start in range(0, width, 8):
            byte = 0
            for offset, value in enumerate(row[start:start + 8]):
                if value:
                    byte |= 0x80 >> offset
            packed.append(byte)
    mask = {"left": left, "top": top, "width": width, "height": height,
            "bits": base64.b64encode(bytes(packed)).decode("ascii")}
    validate_mask(mask)
    return mask


def validate_mask(mask: Any) -> dict:
    if not isinstance(mask, dict) or set(mask) != {"left", "top", "width", "height", "bits"}:
        raise InspectionError("invalid hit mask")
    _int(mask["left"], 0, MAX_DIMENSION, "hit mask origin")
    _int(mask["top"], 0, MAX_DIMENSION, "hit mask origin")
    width = _int(mask["width"], 1, MAX_MASK_SIDE, "hit mask size")
    height = _int(mask["height"], 1, MAX_MASK_SIDE, "hit mask size")
    bits = mask["bits"]
    if not isinstance(bits, str):
        raise InspectionError("invalid hit mask bits")
    try:
        packed = base64.b64decode(bits.encode("ascii"), validate=True)
    except (binascii.Error, UnicodeEncodeError) as exc:
        raise InspectionError("invalid hit mask bits") from exc
    if len(packed) != ((width + 7) // 8) * height or base64.b64encode(packed).decode("ascii") != bits:
        raise InspectionError("hit mask bit length does not match its size")
    stride = (width + 7) // 8
    padding = (8 - width % 8) % 8
    if padding and any(packed[row * stride + stride - 1] & ((1 << padding) - 1) for row in range(height)):
        raise InspectionError("hit mask row padding must be zero")
    if not any(packed):
        raise InspectionError("hit mask must contain at least one clickable pixel")
    return mask


def mask_contains(mask: dict, x: int, y: int) -> bool:
    local_x, local_y = x - mask["left"], y - mask["top"]
    if not (0 <= local_x < mask["width"] and 0 <= local_y < mask["height"]):
        return False
    packed = base64.b64decode(mask["bits"])
    byte = packed[local_y * ((mask["width"] + 7) // 8) + local_x // 8]
    return bool(byte & (0x80 >> (local_x % 8)))


def _validate_targets(targets: Any, width: int, height: int) -> dict[str, dict]:
    if not isinstance(targets, list) or not 1 <= len(targets) <= MAX_TARGETS:
        raise InspectionError("invalid verified scene target count")
    out: dict[str, dict] = {}
    for target in targets:
        if not isinstance(target, dict) or not {"id", "mask"} <= set(target) or set(target) - {"id", "mask", "structural"}:
            raise InspectionError("invalid verified scene target")
        target_id = _id(target["id"], "scene target id")
        if target_id in out:
            raise InspectionError("duplicate verified scene target")
        mask = validate_mask(target["mask"])
        if mask["left"] + mask["width"] > width or mask["top"] + mask["height"] > height:
            raise InspectionError("hit mask exceeds scene design")
        structural = target.get("structural")
        if structural is not None:
            if not isinstance(structural, dict) or set(structural) != {"sprite_channel", "cast_member", "bitmap_sha256"}:
                raise InspectionError("invalid structural target link")
            _int(structural["sprite_channel"], 1, 100_000, "structural sprite channel")
            _int(structural["cast_member"], 1, 100_000, "structural cast member")
            if not _is_hash(structural["bitmap_sha256"]):
                raise InspectionError("invalid structural bitmap digest")
        out[target_id] = mask
    return out


def _step_key(step: dict) -> tuple:
    return (step["kind"], step.get("target"), tuple(step["point"]), step.get("post_play_sha256"),
            step.get("counter_after"), step.get("post_screen"))


def _validate_trial(trial: Any, masks: dict[str, dict], counter_start: int, ack: tuple) -> dict:
    allowed = {"trial_id_sha256", "initial_play_sha256", "steps", "reentry_blocked", "persisted_after_restart"}
    if not isinstance(trial, dict) or set(trial) != allowed:
        raise InspectionError("invalid scene trial")
    if not _is_hash(trial["trial_id_sha256"]) or not _is_hash(trial["initial_play_sha256"]):
        raise InspectionError("invalid scene trial identity")
    if trial["reentry_blocked"] is not True or not isinstance(trial["persisted_after_restart"], bool):
        raise InspectionError("scene trial must observe the completed scene being locked")
    steps = trial["steps"]
    if not isinstance(steps, list) or not 2 <= len(steps) <= MAX_STEPS:
        raise InspectionError("invalid scene trial step count")
    found: set[str] = set()
    state = trial["initial_play_sha256"]
    counter = counter_start
    for index, step in enumerate(steps):
        if not isinstance(step, dict):
            raise InspectionError("invalid scene trial step")
        kind = step.get("kind")
        x, y = _point(step.get("point"))
        if kind == "acknowledge":
            if set(step) != {"kind", "point", "post_screen"} or index != len(steps) - 1:
                raise InspectionError("acknowledge must be the single final scene step")
            if found != set(masks):
                raise InspectionError("scene acknowledged before every verified target was found")
            if not (ack[0] <= x < ack[2] and ack[1] <= y < ack[3]) or step["post_screen"] != "MAP":
                raise InspectionError("completion acknowledge must use the dialog region and return to MAP")
            continue
        if kind == "miss":
            if set(step) != {"kind", "point", "post_play_sha256", "counter_after"}:
                raise InspectionError("invalid scene miss step")
        elif kind == "find":
            if set(step) != {"kind", "target", "point", "post_play_sha256", "counter_after", "latency_ms"}:
                raise InspectionError("invalid scene find step")
            _int(step["latency_ms"], 0, 60_000, "scene find latency")
        else:
            raise InspectionError("unsupported scene step kind")
        if found == set(masks):
            raise InspectionError("scene clicks after completion must be the acknowledge step")
        if not _is_hash(step["post_play_sha256"]):
            raise InspectionError("invalid scene post-state digest")
        _int(step["counter_after"], 0, 1_000_000, "scene counter")
        hits = {target for target, mask in masks.items() if target not in found and mask_contains(mask, x, y)}
        if kind == "miss":
            if hits:
                raise InspectionError("a recorded miss lies inside an unfound verified target")
            if step["post_play_sha256"] != state or step["counter_after"] != counter:
                raise InspectionError("a recorded miss changed the observable scene")
        else:
            target = _id(step["target"], "scene target id")
            if target not in masks or target in found:
                raise InspectionError("find step references an unknown or already found target")
            if hits != {target}:
                raise InspectionError("find point must lie inside exactly its own unfound target mask")
            if step["post_play_sha256"] == state or step["counter_after"] != counter - 1:
                raise InspectionError("find step must change the scene and decrement the counter by one")
            found.add(target)
            state, counter = step["post_play_sha256"], step["counter_after"]
    if steps[-1].get("kind") != "acknowledge":
        raise InspectionError("scene trial must end with the completion acknowledge")
    return trial


def validate_scene_observation(doc: Any) -> dict:
    allowed = {"format", "version", "runtime_kind", "evidence_kind", "source_sha256", "scene_id", "frame_start",
               "frame_end", "design", "counter_start", "acknowledge_region", "targets", "trials"}
    if not isinstance(doc, dict) or set(doc) != allowed or doc.get("format") != OBSERVATION_FORMAT:
        raise InspectionError("invalid scene observation")
    _int(doc["version"], VERSION, VERSION, "scene observation version")
    if doc["runtime_kind"] != "native-projector" or doc["evidence_kind"] != "independent-original-runtime":
        raise InspectionError("scene observation requires independent native-projector evidence")
    if not _is_hash(doc["source_sha256"]):
        raise InspectionError("invalid scene observation source")
    _id(doc["scene_id"], "scene id")
    start = _int(doc["frame_start"], 1, 10_000_000, "scene frame range")
    _int(doc["frame_end"], start, 10_000_000, "scene frame range")
    design = doc["design"]
    if not isinstance(design, dict) or set(design) != {"width", "height"}:
        raise InspectionError("invalid scene design")
    width = _int(design["width"], 1, MAX_DIMENSION, "scene design")
    height = _int(design["height"], 1, MAX_DIMENSION, "scene design")
    counter_start = _int(doc["counter_start"], 0, 1_000_000, "scene counter")
    ack = _rect(doc["acknowledge_region"])
    masks = _validate_targets(doc["targets"], width, height)
    trials = doc["trials"]
    if not isinstance(trials, list) or not 2 <= len(trials) <= MAX_TRIALS:
        raise InspectionError("scene consensus requires at least two controlled trials")
    for trial in trials:
        _validate_trial(trial, masks, counter_start, ack)
    if len({trial["trial_id_sha256"] for trial in trials}) != len(trials):
        raise InspectionError("scene consensus requires distinct trial ids")
    reference = trials[0]
    for trial in trials[1:]:
        if trial["initial_play_sha256"] != reference["initial_play_sha256"] \
                or [_step_key(step) for step in trial["steps"]] != [_step_key(step) for step in reference["steps"]]:
            raise InspectionError("scene trials do not reach exact observable consensus")
    if not any(trial["persisted_after_restart"] for trial in trials):
        raise InspectionError("scene observation must include a restart persistence check")
    return doc


def verified_scene_from_observation(observation: dict, *, scenario: dict, package_id: str) -> dict:
    validate_scene_observation(observation)
    if not _is_hash(package_id):
        raise InspectionError("verified scene requires a private-content package id")
    scenario_id = scenario.get("id") if isinstance(scenario, dict) else None
    scenes = scenario.get("scenes") if isinstance(scenario, dict) else None
    design = scenario.get("design") if isinstance(scenario, dict) else None
    if not isinstance(scenario_id, str) or not scenario_id or not isinstance(scenes, list) \
            or observation["scene_id"] not in {scene.get("id") for scene in scenes if isinstance(scene, dict)}:
        raise InspectionError("verified scene is not present in scenario")
    if design != observation["design"]:
        raise InspectionError("verified scene design does not match scenario design")
    base = {
        "format": PROOF_FORMAT, "version": VERSION, "package_id": package_id, "scenario_id": scenario_id,
        "scenario_sha256": sha256(canonical_bytes(scenario)).hexdigest(),
        "source_sha256": observation["source_sha256"], "scene_id": observation["scene_id"],
        "evidence_kind": observation["evidence_kind"],
        "evidence_chain": {
            "observation_sha256": _digest(observation),
            "trial_set_sha256": _digest(sorted(trial["trial_id_sha256"] for trial in observation["trials"])),
        },
        "targets": sorted(({"id": t["id"], "mask": t["mask"]} for t in observation["targets"]), key=lambda t: t["id"]),
        "rules": {
            "miss_effect": "none",
            "find_effect": {"hide_target": True, "counter_delta": -1},
            "completion": {"kind": "acknowledge-then-map", "acknowledge_region": observation["acknowledge_region"],
                           "locks_scene": True},
            "counter_start": observation["counter_start"],
        },
    }
    return validate_verified_scene_proof({**base, "binding_sha256": _digest(base)})


def validate_verified_scene_proof(doc: Any) -> dict:
    allowed = {"format", "version", "package_id", "scenario_id", "scenario_sha256", "source_sha256", "scene_id",
               "evidence_kind", "evidence_chain", "targets", "rules", "binding_sha256"}
    if not isinstance(doc, dict) or set(doc) != allowed or doc.get("format") != PROOF_FORMAT:
        raise InspectionError("invalid verified-scene proof")
    _int(doc["version"], VERSION, VERSION, "verified-scene version")
    if not _is_hash(doc["binding_sha256"]) \
            or _digest({k: v for k, v in doc.items() if k != "binding_sha256"}) != doc["binding_sha256"]:
        raise InspectionError("verified-scene binding digest mismatch")
    if doc["evidence_kind"] != "independent-original-runtime":
        raise InspectionError("verified-scene proof requires independent original-runtime evidence")
    for key in ("package_id", "scenario_sha256", "source_sha256"):
        if not _is_hash(doc[key]):
            raise InspectionError("invalid verified-scene identity")
    _id(doc["scenario_id"], "scenario id")
    _id(doc["scene_id"], "scene id")
    chain = doc["evidence_chain"]
    if not isinstance(chain, dict) or set(chain) != {"observation_sha256", "trial_set_sha256"} \
            or not all(_is_hash(value) for value in chain.values()):
        raise InspectionError("invalid verified-scene evidence chain")
    targets = doc["targets"]
    if not isinstance(targets, list) or any(not isinstance(t, dict) or set(t) != {"id", "mask"} for t in targets) \
            or [t["id"] for t in targets] != sorted({t["id"] for t in targets}):
        raise InspectionError("verified-scene targets must be unique and sorted")
    _validate_targets(targets, MAX_DIMENSION, MAX_DIMENSION)
    rules = doc["rules"]
    if not isinstance(rules, dict) or set(rules) != {"miss_effect", "find_effect", "completion", "counter_start"} \
            or rules["miss_effect"] != "none" or rules["find_effect"] != {"hide_target": True, "counter_delta": -1}:
        raise InspectionError("invalid verified-scene rules")
    _int(rules["counter_start"], 0, 1_000_000, "verified-scene counter")
    completion = rules["completion"]
    if not isinstance(completion, dict) or set(completion) != {"kind", "acknowledge_region", "locks_scene"} \
            or completion["kind"] != "acknowledge-then-map" or completion["locks_scene"] is not True:
        raise InspectionError("invalid verified-scene completion rule")
    _rect(completion["acknowledge_region"])
    return doc

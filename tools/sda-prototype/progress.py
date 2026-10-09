"""Versioned experimental progress; deliberately separate from native player saves."""
from dataclasses import asdict
import hashlib
import json
import math
import os
from pathlib import Path
import tempfile
from history import HistoryMark
from motion import FoundMotion
from runtime import Scene, Score
from selection import TargetDeck

SCHEMA = "case-recomp-sda-prototype/1"


def resource_hash(resources):
    return hashlib.sha256(resources.path.read_bytes()).hexdigest()


def snapshot(scene):
    return {"schema": SCHEMA, "resources_sha256": resource_hash(scene.resources),
            "scene": scene.name, "active_sets": scene.active_sets,
            "candidate_sets": scene.candidate_sets,
            "deck": {"order": list(scene.deck.order), "cursor": scene.deck.cursor} if scene.deck else None,
            "score": asdict(scene.score), "elapsed": scene.elapsed, "since_found": scene.since_found,
            "history": [asdict(mark) for mark in scene.history], "history_variant": scene.history_variant,
            "history_pruned": scene.history_pruned, "found_order": list(scene.found_order),
            "objects": {identity: {"found": sprite.found, "hidden": sprite.hidden,
                                    "motion": dict(vars(sprite.motion)) if sprite.motion else None}
                        for identity, sprite in scene.objects.items()}}


def number(value, integer=False, low=None, high=None):
    if isinstance(value, bool) or not isinstance(value, int if integer else (int, float)):
        raise ValueError("invalid numeric state")
    if not math.isfinite(value) or low is not None and value < low or high is not None and value > high:
        raise ValueError("numeric state outside supported range")
    return value


def boolean(value):
    if not isinstance(value, bool):
        raise ValueError("invalid boolean state")
    return value


def restore(resources, state):
    if state.get("schema") != SCHEMA or state.get("resources_sha256") != resource_hash(resources):
        raise ValueError("unsupported progress schema or different original resources")
    scene = Scene(resources, state["scene"], seed=0)

    def sets(values):
        result = tuple(tuple(ids) for ids in values)
        if len(set(result)) != len(result) or any(ids not in scene.target_sets for ids in result):
            raise ValueError("invalid saved target sets")
        return result

    scene.candidate_sets = sets(state["candidate_sets"])
    scene.active_sets = sets(state["active_sets"])
    if any(ids not in scene.candidate_sets for ids in scene.active_sets):
        raise ValueError("active sets are absent from saved candidate pool")
    scene.targets = tuple(identity for ids in scene.active_sets for identity in ids)
    deck = state["deck"]
    if deck is None:
        scene.deck = None
    else:
        order = sets(deck["order"])
        if set(order) != set(scene.candidate_sets):
            raise ValueError("saved deck differs from candidate pool")
        scene.deck = TargetDeck(order)
        scene.deck.cursor = number(deck["cursor"], integer=True, low=0, high=len(order))
    score = state["score"]
    misses = [number(value, integer=True, low=0, high=0xffffffff) for value in score["misses"]]
    if len(misses) > 5:
        raise ValueError("invalid miss history")
    scene.score = Score(number(score["points"], integer=True, low=0), boolean(score["fast_chain"]),
                        number(score["fast_bonus"], integer=True, low=2500, high=15500), misses)
    scene.elapsed = number(state["elapsed"], low=0)
    scene.since_found = number(state["since_found"], low=0)
    scene.history = [HistoryMark(mark["text"], number(mark["x"], integer=True), number(mark["y"], integer=True))
                     for mark in state["history"]]
    scene.history_variant = None if state["history_variant"] is None else number(state["history_variant"], integer=True)
    scene.history_pruned = boolean(state["history_pruned"])
    scene.found_order = list(state["found_order"])
    if len(set(scene.found_order)) != len(scene.found_order) or any(item not in scene.objects for item in scene.found_order):
        raise ValueError("invalid found-object ordering")
    if set(state["objects"]) != set(scene.objects):
        raise ValueError("saved objects differ from original scene")
    for identity, saved in state["objects"].items():
        sprite = scene.objects[identity]
        sprite.found, sprite.hidden = boolean(saved["found"]), boolean(saved["hidden"])
        motion = saved["motion"]
        if motion is None:
            sprite.motion = None
        else:
            value = FoundMotion(sprite.x, sprite.y, sprite.image.width, sprite.image.height)
            if set(motion) != set(vars(value)):
                raise ValueError("invalid saved motion fields")
            for key in ("original_x", "original_y", "width", "height", "center_x", "center_y"):
                if motion[key] != getattr(value, key):
                    raise ValueError("saved motion geometry differs from original resource")
            for key, saved_value in motion.items():
                setattr(value, key, boolean(saved_value) if key == "removed" else number(saved_value))
            number(value.phase, integer=True, low=0, high=2)
            number(value.pulses, integer=True, low=0, high=2)
            number(value.draw_width, integer=True, low=1, high=sprite.image.width * 2)
            number(value.draw_height, integer=True, low=1, high=sprite.image.height * 2)
            sprite.motion = value
        if sprite.found != (sprite.motion is not None):
            raise ValueError("inconsistent found-object lifecycle")
    if any(not scene.objects[item].found for item in scene.found_order):
        raise ValueError("animation list contains an unfound object")
    if any(sprite.found and not sprite.motion.removed and identity not in scene.found_order
           for identity, sprite in scene.objects.items()):
        raise ValueError("active found motion is absent from the animation list")
    return scene


def progress_path(path):
    root = Path(__file__).resolve().parents[2]
    resolved = Path(path).resolve()
    if not resolved.is_relative_to(root / "local-output" / "sda-prototype") or resolved.suffix.lower() != ".json":
        raise ValueError("experimental progress must be JSON inside local-output/sda-prototype/")
    return resolved


def save(scene, path):
    out = progress_path(path)
    payload = json.dumps(snapshot(scene), ensure_ascii=False, allow_nan=False, indent=2).encode("utf-8")
    out.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=out.parent, suffix=".tmp", delete=False) as stream:
        temporary = Path(stream.name)
        stream.write(payload)
        stream.flush()
        os.fsync(stream.fileno())
    try:
        os.replace(temporary, out)
    finally:
        if temporary.exists():
            temporary.unlink()


def load(resources, path):
    data = progress_path(path).read_bytes()
    if len(data) > 1_000_000:
        raise ValueError("progress exceeds experimental state budget")
    return restore(resources, json.loads(data.decode("utf-8")))

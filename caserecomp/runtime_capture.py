"""Private native-projector observation capture for Phase 6B.

The capture path is intentionally local-only. It hashes screenshots and the
runtime binary, removes local paths from persisted trials, and requires repeated
controlled trials before producing an observation eligible for slice-compare.
"""
from __future__ import annotations

from hashlib import sha256
import json
from pathlib import Path
from statistics import median
from typing import Any

from .inspector import InspectionError
from .vertical_slice import STAGES, TRANSITIONS, validate_private_slice

CAPTURE_INPUT_FORMAT = "case-recomp-runtime-capture-input"
TRIAL_FORMAT = "case-recomp-runtime-capture-trial"
CONSENSUS_FORMAT = "case-recomp-native-capture-consensus"
VERSION = 1
_MAX_SCREENSHOT_BYTES = 64 * 1024 * 1024
_ALLOWED_INPUTS = ("none", "start", "enter-scene")
_HEX = set("0123456789abcdef")


def _hash_file(path: Path, cap: int | None = None) -> str:
    if not path.is_file() or path.is_symlink():
        raise InspectionError("capture input must be a regular local file")
    size = path.stat().st_size
    if cap is not None and not 0 < size <= cap:
        raise InspectionError("capture file exceeds safety limit")
    digest = sha256()
    with path.open("rb") as handle:
        while chunk := handle.read(1024 * 1024):
            digest.update(chunk)
    return digest.hexdigest()


def _is_hash(value: Any) -> bool:
    return isinstance(value, str) and len(value) == 64 and set(value) <= _HEX


def _document_digest(value: Any) -> str:
    return sha256(json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode()).hexdigest()


def _load(path: Path) -> dict:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise InspectionError(f"cannot read capture JSON: {exc}") from exc
    if not isinstance(value, dict):
        raise InspectionError("capture JSON root must be an object")
    return value


def _pixel_fingerprint(path: Path) -> dict:
    fingerprint, _ = _decoded_fingerprint(path)
    return fingerprint


def _decoded_fingerprint(path: Path) -> tuple[dict, bool]:
    """Fingerprint a PNG and report whether every decoded RGBA pixel is identical."""
    try:
        from PIL import Image
    except ImportError as exc:
        raise InspectionError("Pillow is required for runtime screenshot capture") from exc
    _hash_file(path, _MAX_SCREENSHOT_BYTES)
    try:
        with Image.open(path) as image:
            if image.format != "PNG":
                raise InspectionError("runtime screenshot must be PNG")
            rgba = image.convert("RGBA")
            width, height = rgba.size
            if width <= 0 or height <= 0 or width * height > 64_000_000:
                raise InspectionError("runtime screenshot dimensions exceed safety limit")
            pixels = rgba.tobytes()
    except InspectionError:
        raise
    except Exception as exc:
        raise InspectionError("cannot decode runtime PNG screenshot") from exc
    payload = width.to_bytes(4, "big") + height.to_bytes(4, "big") + pixels
    uniform = pixels.count(pixels[:4]) == width * height
    return {
        "width": width, "height": height,
        "png_sha256": _hash_file(path, _MAX_SCREENSHOT_BYTES),
        "pixel_sha256": sha256(payload).hexdigest(),
    }, uniform


def _require_distinct_stage_pixels(stages: list[dict]) -> None:
    if len({row["pixel_sha256"] for row in stages}) != len(stages):
        raise InspectionError("boot/menu/map/scene screenshots must be visually distinct within a trial")


def capture_desktop_png(output: Path, bbox: tuple[int, int, int, int] | None = None) -> dict:
    if output.exists() or output.is_symlink() or not output.parent.is_dir():
        raise InspectionError("screenshot output must be a new PNG in an existing directory")
    if output.suffix.lower() != ".png":
        raise InspectionError("screenshot output must end in .png")
    if bbox is not None and (len(bbox) != 4 or bbox[2] <= bbox[0] or bbox[3] <= bbox[1]):
        raise InspectionError("invalid screenshot bounding box")
    try:
        from PIL import ImageGrab
        image = ImageGrab.grab(bbox=bbox)
        image.save(output, format="PNG")
    except Exception as exc:
        output.unlink(missing_ok=True)
        raise InspectionError("desktop capture failed; run on the same desktop as the native projector") from exc
    return _pixel_fingerprint(output)


def _validate_capture_input(document: dict) -> dict:
    if document.get("format") != CAPTURE_INPUT_FORMAT or document.get("version") != VERSION:
        raise InspectionError("unsupported runtime capture input")
    if document.get("runtime_kind") != "native-projector" or document.get("controlled") is not True:
        raise InspectionError("capture must be a controlled native-projector trial")
    trial_id = document.get("trial_id")
    if not isinstance(trial_id, str) or not trial_id or len(trial_id) > 128:
        raise InspectionError("invalid capture trial id")
    stages = document.get("stages")
    if not isinstance(stages, list) or [x.get("id") if isinstance(x, dict) else None for x in stages] != list(STAGES):
        raise InspectionError("capture stages must be boot/menu/map/scene in order")
    for index, row in enumerate(stages):
        if not isinstance(row.get("frame"), int) or row["frame"] < 1:
            raise InspectionError("invalid capture stage frame")
        if not isinstance(row.get("screenshot"), str) or not row["screenshot"]:
            raise InspectionError("capture stage requires a screenshot path")
        marker = row.get("marker_observed")
        if not isinstance(marker, bool) or (index > 0 and marker is not True):
            raise InspectionError("menu/map/scene captures require an observed marker")
    transitions = document.get("transitions")
    if not isinstance(transitions, list) or len(transitions) != len(TRANSITIONS):
        raise InspectionError("invalid capture transitions")
    for row, expected, input_kind in zip(transitions, TRANSITIONS, _ALLOWED_INPUTS):
        if (row.get("from"), row.get("to"), row.get("input_kind")) != (*expected, input_kind):
            raise InspectionError("invalid capture transition order/input")
        start, visible = row.get("input_at_ms"), row.get("visible_at_ms")
        if not isinstance(start, int) or not isinstance(visible, int) or start < 0 or visible < start:
            raise InspectionError("invalid capture transition timestamps")
        probe = row.get("gate_probe")
        if probe is not None:
            if not isinstance(probe, dict):
                raise InspectionError("invalid timing gate probe")
            rejected, accepted = probe.get("rejected_before_ms"), probe.get("accepted_at_ms")
            if not isinstance(rejected, int) or not isinstance(accepted, int) or rejected < 0 or accepted <= rejected:
                raise InspectionError("invalid timing gate probe")
    return document


def build_capture_trial(plan: dict, capture_input: dict, runtime_binary: Path) -> dict:
    validate_private_slice(plan)
    _validate_capture_input(capture_input)
    source_sha = _hash_file(runtime_binary)
    if source_sha != plan["source_sha256"]:
        raise InspectionError("native runtime binary does not match vertical-slice source")
    plan_sha = _document_digest(plan)
    stages = []
    for expected, row in zip(plan["stages"], capture_input["stages"]):
        if row["frame"] != expected["start_frame"]:
            raise InspectionError("captured stage frame does not match static slice entry")
        shot, uniform = _decoded_fingerprint(Path(row["screenshot"]))
        if row["marker_observed"] and uniform:
            raise InspectionError("a stage with an observed marker cannot be a single flat color screenshot")
        stages.append({
            "id": row["id"], "frame": row["frame"], "marker_observed": row["marker_observed"],
            **shot,
        })
    _require_distinct_stage_pixels(stages)
    transitions = []
    for row in capture_input["transitions"]:
        out = {
            "from": row["from"], "to": row["to"], "input_kind": row["input_kind"],
            "input_at_ms": row["input_at_ms"], "visible_at_ms": row["visible_at_ms"],
            "latency_ms": row["visible_at_ms"] - row["input_at_ms"],
        }
        if row.get("gate_probe") is not None:
            out["gate_probe"] = dict(row["gate_probe"])
        transitions.append(out)
    return {
        "format": TRIAL_FORMAT, "version": VERSION, "source_sha256": source_sha,
        "plan_sha256": plan_sha,
        "runtime_kind": "native-projector", "trial_id_sha256": sha256(capture_input["trial_id"].encode()).hexdigest(),
        "controlled": True, "stages": stages, "transitions": transitions,
        "privacy": {"local_paths_removed": True, "original_media_embedded": False},
    }


def validate_capture_trial(document: Any) -> dict:
    if not isinstance(document, dict) or document.get("format") != TRIAL_FORMAT or document.get("version") != VERSION:
        raise InspectionError("invalid runtime capture trial")
    if document.get("runtime_kind") != "native-projector" or document.get("controlled") is not True \
            or not _is_hash(document.get("source_sha256")) or not _is_hash(document.get("plan_sha256")):
        raise InspectionError("invalid runtime capture provenance")
    if not _is_hash(document.get("trial_id_sha256")):
        raise InspectionError("invalid capture trial identifier")
    privacy = document.get("privacy")
    if not isinstance(privacy, dict) or privacy.get("local_paths_removed") is not True \
            or privacy.get("original_media_embedded") is not False:
        raise InspectionError("capture trial privacy boundary is invalid")
    stages = document.get("stages")
    if not isinstance(stages, list) or [x.get("id") if isinstance(x, dict) else None for x in stages] != list(STAGES):
        raise InspectionError("invalid captured stages")
    for index, row in enumerate(stages):
        marker = row.get("marker_observed")
        if not isinstance(row.get("frame"), int) or row["frame"] < 1 \
                or not isinstance(marker, bool) or (index > 0 and marker is not True):
            raise InspectionError("invalid captured stage")
        width, height = row.get("width"), row.get("height")
        if not isinstance(width, int) or not isinstance(height, int) or width <= 0 or height <= 0 \
                or width * height > 64_000_000:
            raise InspectionError("invalid captured dimensions")
        if not _is_hash(row.get("png_sha256")) or not _is_hash(row.get("pixel_sha256")):
            raise InspectionError("invalid captured image digest")
    _require_distinct_stage_pixels(stages)
    transitions = document.get("transitions")
    if not isinstance(transitions, list) or len(transitions) != len(TRANSITIONS):
        raise InspectionError("invalid captured transitions")
    for row, expected, input_kind in zip(transitions, TRANSITIONS, _ALLOWED_INPUTS):
        if not isinstance(row, dict) or (row.get("from"), row.get("to"), row.get("input_kind")) != (*expected, input_kind):
            raise InspectionError("invalid captured transition")
        start, visible, latency = row.get("input_at_ms"), row.get("visible_at_ms"), row.get("latency_ms")
        if not isinstance(start, int) or not isinstance(visible, int) or not isinstance(latency, int) \
                or start < 0 or visible < start or latency != visible - start:
            raise InspectionError("invalid captured transition timing")
        probe = row.get("gate_probe")
        if probe is not None:
            if not isinstance(probe, dict) or set(probe) != {"rejected_before_ms", "accepted_at_ms"}:
                raise InspectionError("invalid captured timing gate probe")
            rejected, accepted = probe["rejected_before_ms"], probe["accepted_at_ms"]
            if not isinstance(rejected, int) or not isinstance(accepted, int) or rejected < 0 or accepted <= rejected:
                raise InspectionError("invalid captured timing gate probe")
    return document

def finalize_capture_trials(plan: dict, trials: list[dict], *, timing_tolerance_ms: int = 16) -> dict:
    validate_private_slice(plan)
    if len(trials) < 2 or timing_tolerance_ms < 0:
        raise InspectionError("capture consensus requires at least two controlled trials")
    plan_sha = _document_digest(plan)
    trial_ids = []
    for trial in trials:
        validate_capture_trial(trial)
        if trial["source_sha256"] != plan["source_sha256"]:
            raise InspectionError("capture trial source mismatch")
        if trial["plan_sha256"] != plan_sha:
            raise InspectionError("capture trial vertical-slice plan mismatch")
        trial_ids.append(trial["trial_id_sha256"])
    if len(set(trial_ids)) != len(trial_ids):
        raise InspectionError("capture consensus requires distinct controlled trial ids")
    stage_pixel: dict[str, str] = {}
    stage_dimensions: dict[str, list[int]] = {}
    observation_stages = []
    for index, expected in enumerate(plan["stages"]):
        samples = [trial["stages"][index] for trial in trials]
        if any(sample["frame"] != expected["start_frame"] for sample in samples):
            raise InspectionError("capture consensus frame mismatch")
        pixels = {sample["pixel_sha256"] for sample in samples}
        dims = {(sample["width"], sample["height"]) for sample in samples}
        if len(pixels) != 1 or len(dims) != 1:
            raise InspectionError("runtime screenshots do not reach exact visual consensus")
        pixel = next(iter(pixels)); width, height = next(iter(dims))
        stage_pixel[expected["id"]] = pixel
        stage_dimensions[expected["id"]] = [width, height]
        observable = sha256(f'{expected["id"]}:{expected["start_frame"]}:{width}x{height}:{pixel}'.encode()).hexdigest()
        observation_stages.append({
            "id": expected["id"], "frame": expected["start_frame"],
            "sprite_sha256": expected["entry_sprite_sha256"],
            "handler_set_sha256": expected["handler_set_sha256"],
            "observable_state_sha256": observable,
        })

    observation_transitions = []
    latency_summary = []
    all_gate_verified = True
    for index, (expected, input_kind) in enumerate(zip(TRANSITIONS, _ALLOWED_INPUTS)):
        rows = [trial["transitions"][index] for trial in trials]
        latencies = [row["latency_ms"] for row in rows]
        probes = [row.get("gate_probe") for row in rows]
        gate_verified = index > 0 and all(isinstance(probe, dict) for probe in probes)
        not_before = 0
        if gate_verified:
            accepted = [probe["accepted_at_ms"] for probe in probes]
            rejected = [probe["rejected_before_ms"] for probe in probes]
            gate_verified = max(accepted) - min(accepted) <= timing_tolerance_ms and all(a > r for a, r in zip(accepted, rejected))
            if gate_verified:
                not_before = max(accepted)
        if index > 0 and not gate_verified:
            all_gate_verified = False
        observation_transitions.append({"from": expected[0], "to": expected[1],
                                        "input_kind": input_kind, "elapsed_ms": not_before})
        latency_summary.append({
            "from": expected[0], "to": expected[1], "samples": len(latencies),
            "min_ms": min(latencies), "max_ms": max(latencies), "median_ms": int(median(latencies)),
            "gate_verified": gate_verified,
        })
    return {
        "format": "case-recomp-vertical-slice-observation", "version": VERSION,
        "source_sha256": plan["source_sha256"], "evidence_kind": "independent-original-runtime",
        "timing_trials": len(trials), "controlled_timing": all_gate_verified,
        "stages": observation_stages, "transitions": observation_transitions,
        "capture_evidence": {
            "format": CONSENSUS_FORMAT, "version": VERSION, "runtime_kind": "native-projector",
            "trial_count": len(trials), "visual_consensus": True, "static_fingerprints_bound": True,
            "plan_sha256": plan_sha, "trial_set_sha256": _document_digest(sorted(trial_ids)),
            "stage_pixel_sha256": stage_pixel, "stage_dimensions": stage_dimensions,
            "transition_latency": latency_summary, "timing_tolerance_ms": timing_tolerance_ms,
        },
    }


def capture_trial_from_files(plan_path: Path, input_path: Path, runtime_binary: Path) -> dict:
    return build_capture_trial(_load(plan_path), _load(input_path), runtime_binary)


def finalize_capture_from_files(plan_path: Path, trial_paths: list[Path], *, timing_tolerance_ms: int = 16) -> dict:
    return finalize_capture_trials(_load(plan_path), [_load(path) for path in trial_paths],
                                   timing_tolerance_ms=timing_tolerance_ms)

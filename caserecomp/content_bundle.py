"""Versioned private-content package for local Android import.

The package is intentionally transport-only. It never grants redistribution
rights and is designed for a user-owned local export produced by Case-Recomp.
"""
from __future__ import annotations

from hashlib import sha256
from pathlib import Path, PurePosixPath
from typing import Any
import json
import mimetypes
import zipfile

from .inspector import InspectionError
from .pipeline import verify_export
from .scenario import canonical_bytes, load_scenario
from .trace import validate_private_plan

FORMAT = "case-recomp-private-content"
VERSION = 1
BINDINGS_FORMAT = "case-recomp-private-bindings"
MAX_BUNDLE_BYTES = 768 * 1024 * 1024
MAX_ENTRY_BYTES = 256 * 1024 * 1024
MAX_ENTRIES = 20_000
_SUPPORTED_MEDIA = {"image/png", "audio/wav", "audio/mpeg"}
_ZIP_TIME = (1980, 1, 1, 0, 0, 0)


def _canonical_json(value: Any) -> bytes:
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n").encode("utf-8")


def _safe_rel(value: Any) -> str:
    if not isinstance(value, str) or not value or len(value) > 512 or "\\" in value or value.startswith("/"):
        raise InspectionError("unsafe private-content path")
    parts = PurePosixPath(value).parts
    if not parts or any(part in {"", ".", ".."} for part in parts):
        raise InspectionError("unsafe private-content path")
    return value


def _hex(value: Any) -> str:
    if not isinstance(value, str) or len(value) != 64 or any(ch not in "0123456789abcdef" for ch in value):
        raise InspectionError("invalid SHA-256 in private-content manifest")
    return value


def _media_type(record: dict) -> str | None:
    fmt = record.get("format")
    file = str(record.get("file", "")).lower()
    if fmt == "png" or file.endswith(".png"):
        return "image/png"
    if fmt == "pcm-wav" or file.endswith(".wav"):
        return "audio/wav"
    if fmt == "mp3-id3" or file.endswith(".mp3"):
        return "audio/mpeg"
    return None


def _load_bindings(path: Path | None, known_files: dict[str, str], scenario: dict) -> dict:
    empty = {"scene_backgrounds": {}, "targets": {}, "audio": {}}
    if path is None:
        return empty
    try:
        doc = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise InspectionError("cannot read private bindings JSON") from exc
    if not isinstance(doc, dict) or doc.get("format") != BINDINGS_FORMAT or doc.get("version") != 1:
        raise InspectionError("unsupported private bindings format")
    if set(doc) - {"format", "version", "scene_backgrounds", "targets", "audio"}:
        raise InspectionError("unexpected private bindings fields")
    scene_ids = {s["id"] for s in scenario["scenes"]}
    targets = {s["id"]: {t["id"] for t in s["targets"]} for s in scenario["scenes"]}

    def resolve_file(value: Any, expected: str) -> str:
        rel = _safe_rel(value)
        asset_id = known_files.get(rel)
        if asset_id is None:
            raise InspectionError("private binding references asset outside conversion manifest")
        if expected == "image" and not asset_id.startswith("image:"):
            raise InspectionError("private image binding references non-image asset")
        if expected == "audio" and not asset_id.startswith("audio:"):
            raise InspectionError("private audio binding references non-audio asset")
        return asset_id.split(":", 1)[1]

    backgrounds = doc.get("scene_backgrounds", {})
    if not isinstance(backgrounds, dict):
        raise InspectionError("invalid private scene background bindings")
    normalized_backgrounds = {}
    for scene, file in backgrounds.items():
        if scene not in scene_ids:
            raise InspectionError("private binding references unknown scene")
        normalized_backgrounds[scene] = resolve_file(file, "image")

    target_doc = doc.get("targets", {})
    if not isinstance(target_doc, dict):
        raise InspectionError("invalid private target bindings")
    normalized_targets: dict[str, dict[str, str]] = {}
    for scene, rows in target_doc.items():
        if scene not in scene_ids or not isinstance(rows, dict):
            raise InspectionError("private target binding references unknown scene")
        out = {}
        for target, file in rows.items():
            if target not in targets[scene]:
                raise InspectionError("private target binding references unknown target")
            out[target] = resolve_file(file, "image")
        normalized_targets[scene] = out

    audio = doc.get("audio", {})
    if not isinstance(audio, dict):
        raise InspectionError("invalid private audio bindings")
    allowed_audio = {"START", "TARGET_FOUND", "SCENE_COMPLETE", "SCENARIO_COMPLETE"}
    normalized_audio = {}
    for cue, file in audio.items():
        if cue not in allowed_audio:
            raise InspectionError("unsupported private audio cue binding")
        normalized_audio[cue] = resolve_file(file, "audio")
    return {"scene_backgrounds": normalized_backgrounds, "targets": normalized_targets, "audio": normalized_audio}


def _zip_write(zf: zipfile.ZipFile, name: str, data: bytes) -> None:
    info = zipfile.ZipInfo(name, _ZIP_TIME)
    info.compress_type = zipfile.ZIP_DEFLATED
    info.external_attr = 0o600 << 16
    zf.writestr(info, data)


def build_private_content_bundle(conversion_dir: Path, scenario_path: Path, output: Path, *,
                                 bindings_path: Path | None = None, trace_plan_path: Path | None = None) -> dict:
    """Create a deterministic, create-only ZIP for app-private Android import."""
    if output.exists() or output.is_symlink() or not output.parent.is_dir():
        raise InspectionError("private-content output must be a new file in an existing directory")
    verified = verify_export(conversion_dir)
    scenario = load_scenario(scenario_path)
    scenario_bytes = canonical_bytes(scenario)
    manifest_path = conversion_dir / "manifest.json"
    try:
        conversion_manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise InspectionError("cannot read conversion manifest") from exc

    catalog: list[dict] = []
    known_files: dict[str, str] = {}
    stored_by_hash: dict[str, tuple[str, bytes]] = {}
    for record in conversion_manifest["assets"]:
        media_type = _media_type(record)
        if media_type not in _SUPPORTED_MEDIA:
            continue
        rel = _safe_rel(record["file"])
        source = conversion_dir / rel
        data = source.read_bytes()
        digest = sha256(data).hexdigest()
        if digest != record["sha256"] or len(data) != record["bytes"]:
            raise InspectionError("conversion asset changed after verification")
        ext = {"image/png": ".png", "audio/wav": ".wav", "audio/mpeg": ".mp3"}[media_type]
        asset_id = f"sha256-{digest}"
        stored = f"assets/{digest}{ext}"
        existing = stored_by_hash.get(digest)
        if existing is not None and existing[1] != data:
            raise InspectionError("SHA-256 collision while packaging private content")
        stored_by_hash[digest] = (stored, data)
        known_files[rel] = ("image:" if media_type.startswith("image/") else "audio:") + asset_id
        if not any(row["id"] == asset_id for row in catalog):
            catalog.append({"id": asset_id, "path": stored, "sha256": digest, "bytes": len(data),
                            "media_type": media_type})
    if not catalog:
        raise InspectionError("conversion export has no Android-supported private assets")
    catalog.sort(key=lambda row: row["id"])
    bindings = _load_bindings(bindings_path, known_files, scenario)

    trace_info = None
    trace_bytes = None
    if trace_plan_path is not None:
        try:
            trace_doc = json.loads(trace_plan_path.read_text(encoding="utf-8"))
        except (OSError, UnicodeDecodeError, json.JSONDecodeError) as exc:
            raise InspectionError("cannot read private trace plan") from exc
        validate_private_plan(trace_doc)
        trace_bytes = _canonical_json(trace_doc)
        trace_info = {"path": "trace-plan.json", "sha256": sha256(trace_bytes).hexdigest()}

    base = {
        "format": FORMAT,
        "version": VERSION,
        "scenario": {"path": "scenario.json", "sha256": sha256(scenario_bytes).hexdigest(), "id": scenario["id"]},
        "assets": catalog,
        "bindings": bindings,
        "source": {"conversion_manifest_sha256": verified["manifest_sha256"]},
    }
    if trace_info is not None:
        base["trace_plan"] = trace_info
    package_id = sha256(_canonical_json(base)).hexdigest()
    manifest = {**base, "package_id": package_id}
    manifest_bytes = _canonical_json(manifest)

    total = len(manifest_bytes) + len(scenario_bytes) + sum(len(v[1]) for v in stored_by_hash.values())
    if trace_bytes is not None:
        total += len(trace_bytes)
    if total > MAX_BUNDLE_BYTES or len(catalog) + 3 > MAX_ENTRIES:
        raise InspectionError("private-content bundle exceeds safety limit")
    try:
        with zipfile.ZipFile(output, "x", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as zf:
            _zip_write(zf, "content-manifest.json", manifest_bytes)
            _zip_write(zf, "scenario.json", scenario_bytes)
            if trace_bytes is not None:
                _zip_write(zf, "trace-plan.json", trace_bytes)
            for stored, data in sorted(stored_by_hash.values()):
                _zip_write(zf, stored, data)
    except BaseException:
        output.unlink(missing_ok=True)
        raise
    return {"format": FORMAT, "version": VERSION, "package_id": package_id,
            "asset_count": len(catalog), "bytes": total, "scenario_id": scenario["id"],
            "trace_plan_included": trace_bytes is not None}


def _read_zip_member(zf: zipfile.ZipFile, name: str, expected_size: int | None = None) -> bytes:
    try:
        info = zf.getinfo(name)
    except KeyError as exc:
        raise InspectionError(f"private-content bundle missing {name}") from exc
    if info.is_dir() or info.file_size > MAX_ENTRY_BYTES or (expected_size is not None and info.file_size != expected_size):
        raise InspectionError("invalid private-content ZIP entry size")
    data = zf.read(info)
    if len(data) != info.file_size:
        raise InspectionError("truncated private-content ZIP entry")
    return data


def validate_private_content_manifest(document: Any) -> dict:
    if not isinstance(document, dict) or document.get("format") != FORMAT or document.get("version") != VERSION:
        raise InspectionError("unsupported private-content manifest")
    allowed = {"format", "version", "package_id", "scenario", "assets", "bindings", "source", "trace_plan"}
    if set(document) - allowed or not _hex(document.get("package_id")):
        raise InspectionError("invalid private-content manifest")
    scenario = document.get("scenario")
    if not isinstance(scenario, dict) or set(scenario) != {"path", "sha256", "id"}:
        raise InspectionError("invalid private-content scenario descriptor")
    _safe_rel(scenario["path"]); _hex(scenario["sha256"])
    if not isinstance(scenario["id"], str) or not scenario["id"]:
        raise InspectionError("invalid private-content scenario id")
    assets = document.get("assets")
    if not isinstance(assets, list) or not 1 <= len(assets) <= MAX_ENTRIES:
        raise InspectionError("invalid private-content asset catalog")
    ids, paths = set(), set()
    for row in assets:
        if not isinstance(row, dict) or set(row) != {"id", "path", "sha256", "bytes", "media_type"}:
            raise InspectionError("invalid private-content asset descriptor")
        if not isinstance(row["id"], str) or not row["id"].startswith("sha256-") or row["id"] in ids:
            raise InspectionError("invalid or duplicate private-content asset id")
        ids.add(row["id"])
        path = _safe_rel(row["path"])
        if path in paths:
            raise InspectionError("duplicate private-content asset path")
        paths.add(path)
        _hex(row["sha256"])
        if row["id"] != "sha256-" + row["sha256"]:
            raise InspectionError("private-content asset id/hash mismatch")
        if isinstance(row["bytes"], bool) or not isinstance(row["bytes"], int) or not 0 <= row["bytes"] <= MAX_ENTRY_BYTES:
            raise InspectionError("invalid private-content asset byte length")
        if row["media_type"] not in _SUPPORTED_MEDIA:
            raise InspectionError("unsupported private-content media type")
    bindings = document.get("bindings")
    if not isinstance(bindings, dict) or set(bindings) != {"scene_backgrounds", "targets", "audio"}:
        raise InspectionError("invalid private-content bindings")
    bound_ids: list[str] = []
    if not isinstance(bindings["scene_backgrounds"], dict) or not isinstance(bindings["targets"], dict) or not isinstance(bindings["audio"], dict):
        raise InspectionError("invalid private-content bindings")
    bound_ids.extend(bindings["scene_backgrounds"].values())
    bound_ids.extend(bindings["audio"].values())
    for rows in bindings["targets"].values():
        if not isinstance(rows, dict):
            raise InspectionError("invalid private target bindings")
        bound_ids.extend(rows.values())
    if any(value not in ids for value in bound_ids):
        raise InspectionError("private binding references unknown asset id")
    source = document.get("source")
    if not isinstance(source, dict) or set(source) != {"conversion_manifest_sha256"}:
        raise InspectionError("invalid private-content source descriptor")
    _hex(source["conversion_manifest_sha256"])
    trace = document.get("trace_plan")
    if trace is not None:
        if not isinstance(trace, dict) or set(trace) != {"path", "sha256"}:
            raise InspectionError("invalid private-content trace descriptor")
        _safe_rel(trace["path"]); _hex(trace["sha256"])
    base = {k: v for k, v in document.items() if k != "package_id"}
    if sha256(_canonical_json(base)).hexdigest() != document["package_id"]:
        raise InspectionError("private-content package id mismatch")
    return document


def verify_private_content_bundle(path: Path) -> dict:
    if not path.is_file() or path.is_symlink() or path.stat().st_size > MAX_BUNDLE_BYTES:
        raise InspectionError("invalid private-content bundle")
    try:
        with zipfile.ZipFile(path, "r") as zf:
            infos = zf.infolist()
            if not 2 <= len(infos) <= MAX_ENTRIES or len({i.filename for i in infos}) != len(infos):
                raise InspectionError("invalid private-content ZIP entry count")
            total_declared = 0
            for info in infos:
                _safe_rel(info.filename)
                if info.file_size > MAX_ENTRY_BYTES:
                    raise InspectionError("private-content ZIP member exceeds cap")
                total_declared += info.file_size
                if total_declared > MAX_BUNDLE_BYTES:
                    raise InspectionError("private-content ZIP expands beyond cap")
            manifest_bytes = _read_zip_member(zf, "content-manifest.json")
            try:
                manifest = validate_private_content_manifest(json.loads(manifest_bytes.decode("utf-8")))
            except (UnicodeDecodeError, json.JSONDecodeError) as exc:
                raise InspectionError("invalid private-content manifest JSON") from exc
            scenario_bytes = _read_zip_member(zf, manifest["scenario"]["path"])
            if sha256(scenario_bytes).hexdigest() != manifest["scenario"]["sha256"]:
                raise InspectionError("private-content scenario hash mismatch")
            try:
                from .scenario import validate_scenario
                scenario = validate_scenario(json.loads(scenario_bytes.decode("utf-8")))
            except (UnicodeDecodeError, json.JSONDecodeError) as exc:
                raise InspectionError("invalid private-content scenario JSON") from exc
            if scenario["id"] != manifest["scenario"]["id"]:
                raise InspectionError("private-content scenario id mismatch")
            verified_bytes = 0
            for row in manifest["assets"]:
                data = _read_zip_member(zf, row["path"], row["bytes"])
                if sha256(data).hexdigest() != row["sha256"]:
                    raise InspectionError("private-content asset integrity mismatch")
                verified_bytes += len(data)
            if manifest.get("trace_plan"):
                data = _read_zip_member(zf, manifest["trace_plan"]["path"])
                if sha256(data).hexdigest() != manifest["trace_plan"]["sha256"]:
                    raise InspectionError("private-content trace-plan hash mismatch")
                try:
                    validate_private_plan(json.loads(data.decode("utf-8")))
                except (UnicodeDecodeError, json.JSONDecodeError) as exc:
                    raise InspectionError("invalid private trace-plan JSON") from exc
    except zipfile.BadZipFile as exc:
        raise InspectionError("invalid private-content ZIP") from exc
    return {"format": FORMAT, "version": VERSION, "package_id": manifest["package_id"],
            "asset_count": len(manifest["assets"]), "verified_asset_bytes": verified_bytes,
            "scenario_id": manifest["scenario"]["id"], "trace_plan_included": bool(manifest.get("trace_plan"))}

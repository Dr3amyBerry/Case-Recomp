"""Locally verify every Afterburner resource without writing any game content."""

from __future__ import annotations

from collections import Counter
from pathlib import Path
import struct
from hashlib import sha256

from .director import DirectorArchive, embedded_movie, open_archive, read_local
from .inspector import InspectionError
from .lingo_index import index_archive

SUFFIXES = {".exe", ".cct", ".cxt", ".cst", ".dcr", ".dxr", ".dir"}
MAX_DECODED_BYTES = 512 * 1024 * 1024


def verify_directory(source: Path, *, max_archives: int = 100) -> dict:
    """Report aggregate decoding compatibility for a user's local assets.

    Unsupported codecs are reported separately from corruption. Paths, hashes,
    decompiled scripts and proprietary bytes are not included in the result.
    """
    if source.is_symlink() or not source.is_dir():
        raise InspectionError("verification requires a real local directory")
    paths = []
    for path in source.rglob("*"):
        if path.is_symlink() or not path.is_file():
            continue
        if path.suffix.lower() in {".cct", ".dcr", ".dxr", ".exe", ".bin"}:
            paths.append(path)
    paths.sort()
    if len(paths) > max_archives:
        raise InspectionError("verification input count exceeds limit")

    count = Counter()
    tags = Counter()
    unsupported = Counter()
    failures = []
    invalid_archives = []
    for path in paths:
        try:
            archive, _ = open_archive(path)
        except (InspectionError, OSError) as exc:
            # A declared Director file which fails to parse is NOT quietly ignored.
            is_declared_cast = path.suffix.lower() in {".cct", ".dcr", ".dxr"} or path.name.lower().endswith(".cct.bin")
            if is_declared_cast:
                invalid_archives.append({"source_name": path.name, "error": str(exc)})
            else:
                count["unrecognized_files"] += 1
            continue
        count["archives"] += 1
        for res in archive.entries.values():
            count["resources"] += 1
            tags[res.tag] += 1
            codec = archive._codec_name(res.compression_index)
            if codec == "unsupported":
                unsupported[archive.codecs[res.compression_index].split(" from ")[0]] += 1
                continue
            try:
                archive.get_resource(res.id)
                count["decoded"] += 1
            except InspectionError as exc:
                failures.append({"source_name": path.name, "resource_id": res.id, "error": str(exc)})
    return {
        "archives": count["archives"],
        "unrecognized_files": count["unrecognized_files"],
        "resources": count["resources"],
        "decoded": count["decoded"],
        "unsupported_codecs": dict(sorted(unsupported.items())),
        "decode_failures": failures,
        "invalid_archives": invalid_archives,
        "resource_tags": dict(sorted(tags.items())),
    }


def _candidate_headers(data: bytes) -> list[dict]:
    """Diagnose bounds without repairing or accepting a malformed container."""
    found = []
    cursor = 0
    while len(found) < 16:
        position = data.find(b"XFIR", cursor)
        if position < 0:
            break
        cursor = position + 4
        if len(data) - position < 12 or data[position + 8:position + 12] not in (b"MDGF", b"CDGF"):
            continue
        declared = struct.unpack_from("<I", data, position + 4)[0]
        available = len(data) - position
        found.append({"offset": position, "kind": data[position + 8:position + 12][::-1].decode("ascii"),
                      "declared_bytes": declared + 8, "available_bytes": available,
                      "extent_valid": 4 <= declared <= available - 8})
    return found


def _audit_file(path: Path, relative: str, *, show_names: bool, decode_media: bool) -> dict:
    record = {"path": relative, "status": "failed", "errors": []}
    try:
        data = read_local(path)
        record.update(bytes=len(data), sha256=sha256(data).hexdigest())
        if path.suffix.lower() == ".exe":
            candidates = _candidate_headers(data)
            record["candidate_headers"] = candidates
            if not candidates:
                record["status"] = "not_director"
                return record
            offset, movie = embedded_movie(data)
        else:
            offset, movie = 0, data
        archive = DirectorArchive(movie)
        record.update(movie_offset=offset, archive=archive.summary())
        resource_hashes = []
        unsupported = Counter()
        decoded_bytes = 0
        failed_ils = False
        for ident, entry in sorted(archive.entries.items()):
            if entry.in_ils and failed_ils:
                continue
            if not entry.in_ils and archive._codec_name(entry.compression_index) == "unsupported":
                unsupported[entry.tag] += 1
                continue
            if decoded_bytes + entry.uncompressed_size > MAX_DECODED_BYTES:
                record["errors"].append("decoded resource byte cap exceeded")
                break
            try:
                raw = archive.get_resource(ident)
                decoded_bytes += len(raw)
                resource_hashes.append({"id": ident, "tag": entry.tag, "bytes": len(raw),
                                        "sha256": sha256(raw).hexdigest()})
            except InspectionError as exc:
                record["errors"].append(f"resource {ident}: {exc}")
                if entry.in_ils or entry.tag == "ILS ":
                    failed_ils = True
                if len(record["errors"]) >= 32:
                    record["errors"].append("resource error limit reached")
                    break
        record["resources"] = {"verified": len(resource_hashes), "decoded_bytes": decoded_bytes,
                               "unsupported_codecs": dict(unsupported), "hashes": resource_hashes}
        tags = Counter(entry.tag for entry in archive.entries.values())
        if tags["Lscr"] or tags["LctX"] or tags["Lnam"]:
            try:
                record["lingo"] = index_archive(archive, redact=not show_names)
            except InspectionError as exc:
                record["errors"].append(f"Lingo index: {exc}")
        if decode_media and not record["errors"]:
            # Reuse the package builder's media paths and decoding rules, discard bodies.
            from .director_content import _archive_media, cast_key
            skipped = Counter()
            media = []
            media_bytes = 0
            for item_path, body, key, member, kind in _archive_media(archive, cast_key(path.name), None, skipped):
                media_bytes += len(body)
                if media_bytes > MAX_DECODED_BYTES:
                    record["errors"].append("decoded media byte cap exceeded")
                    break
                media.append({"path": item_path, "member": member, "kind": kind,
                              "bytes": len(body), "sha256": sha256(body).hexdigest()})
            record["media"] = {"decoded": len(media), "decoded_bytes": media_bytes,
                               "counts": dict(Counter(item["kind"] for item in media)),
                               "skipped": dict(skipped), "hashes": media}
        record["status"] = "failed" if record["errors"] else "verified_bytes"
    except (InspectionError, OSError) as exc:
        record["errors"].append(str(exc))
    return record


def audit_directory(source: Path, *, max_files: int = 256, show_names: bool = False,
                    decode_media: bool = False) -> dict:
    """Check each archive independently; incomplete recovery stays explicit."""
    if not 1 <= max_files <= 4096:
        raise InspectionError("file cap must be in [1, 4096]")
    if source.is_symlink() or not source.is_dir():
        raise InspectionError("input must be a regular directory, not a link")
    paths = []
    for path in source.rglob("*"):
        if path.is_symlink():
            raise InspectionError("input tree includes symbolic link")
        if path.is_file() and path.suffix.lower() in SUFFIXES:
            paths.append(path)
            if len(paths) > max_files:
                raise InspectionError("input archive file cap exceeded")
    records = [_audit_file(path, path.relative_to(source).as_posix(), show_names=show_names,
                           decode_media=decode_media) for path in sorted(paths)]
    summary = {
        "files": len(records),
        "valid_archives": sum("archive" in item for item in records),
        "verified_archives": sum(item["status"] == "verified_bytes" for item in records),
        "failed_files": sum(item["status"] == "failed" for item in records),
        "not_director_files": sum(item["status"] == "not_director" for item in records),
        "scripts": sum(item.get("lingo", {}).get("script_count", 0) for item in records),
        "handlers": sum(item.get("lingo", {}).get("handler_count", 0) for item in records),
        "decoded_media": sum(item.get("media", {}).get("decoded", 0) for item in records),
        "skipped_media": sum(sum(item.get("media", {}).get("skipped", {}).values()) for item in records),
    }
    return {"format": "case-recomp-director-audit", "version": 1, "summary": summary,
            "runtime_semantics_verified": False, "recovered_lingo_source": False,
            "media_decode_requested": decode_media, "files": records}

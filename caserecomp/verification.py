"""Locally verify every Afterburner resource without writing any game content."""

from __future__ import annotations

from collections import Counter
from pathlib import Path

from .director import open_archive
from .inspector import InspectionError


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

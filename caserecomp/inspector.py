"""Read-only PE and Macromedia Director/XFIR inventory.

This module does not execute inputs, extract assets, or bypass licensing.
"""

from __future__ import annotations

import hashlib
import json
import re
from pathlib import Path

MAX_FILE_BYTES = 128 * 1024 * 1024
SUPPORTED_EXTENSIONS = frozenset({".exe", ".dll", ".cct", ".cst", ".dcr", ".dir", ".dxr", ".txt", ".ini", ".js", ".htm", ".html", ".cab", ".osd"})
ENDIAN_TAGS = {b"XFIR": "little", b"FFIR": "little", b"RIFX": "big", b"RIFF": "little"}
DIRECTOR_TYPES = {b"CDGF", b"MDGF", b"FGDC", b"FGDM", b"MV93", b"39VM"}


class InspectionError(ValueError):
    """An unsafe or malformed input cannot be inspected."""


def _u16(data: bytes, pos: int, endian: str = "little") -> int:
    if pos < 0 or pos + 2 > len(data):
        raise InspectionError("truncated 16-bit field")
    return int.from_bytes(data[pos : pos + 2], endian)


def _u32(data: bytes, pos: int, endian: str = "little") -> int:
    if pos < 0 or pos + 4 > len(data):
        raise InspectionError("truncated 32-bit field")
    return int.from_bytes(data[pos : pos + 4], endian)


def _director_header(data: bytes, offset: int = 0) -> dict | None:
    if offset < 0 or len(data) - offset < 12:
        return None
    signature = data[offset : offset + 4]
    if signature not in ENDIAN_TAGS:
        return None
    endian = ENDIAN_TAGS[signature]
    declared_size = _u32(data, offset + 4, endian)
    kind_bytes = data[offset + 8 : offset + 12]
    # Reversed four-character codes are normal for little-endian XFIR files.
    if kind_bytes not in DIRECTOR_TYPES:
        return None
    if declared_size < 4 or declared_size > len(data) - offset - 8:
        return None
    version_match = re.search(rb"(\d{1,2}\.\d{1,2}\.\d{1,3}#\d{1,5})", data[offset + 12 : offset + 64])
    return {
        "offset": offset,
        "signature": signature.decode("ascii"),
        "kind": kind_bytes[::-1].decode("ascii") if signature in (b"XFIR", b"FFIR") else kind_bytes.decode("ascii"),
        "declared_payload_bytes": declared_size,
        "endian": endian,
        "director_version": version_match.group(1).decode("ascii") if version_match else None,
    }


def _pe_header(data: bytes) -> dict | None:
    if data[:2] != b"MZ":
        return None
    if len(data) < 64:
        raise InspectionError("truncated DOS header")
    pe_offset = _u32(data, 0x3C)
    if pe_offset > len(data) - 24 or data[pe_offset : pe_offset + 4] != b"PE\x00\x00":
        raise InspectionError("missing or truncated PE signature")
    machine = _u16(data, pe_offset + 4)
    count = _u16(data, pe_offset + 6)
    optional_size = _u16(data, pe_offset + 20)
    optional_pos = pe_offset + 24
    sections_start = optional_pos + optional_size
    if count > 96 or sections_start + 40 * count > len(data):
        raise InspectionError("invalid or truncated PE section table")
    if optional_size < 2:
        raise InspectionError("missing PE optional header")
    optional_magic = _u16(data, optional_pos)
    sections = []
    max_raw_end = sections_start + 40 * count
    for index in range(count):
        pos = sections_start + index * 40
        name = data[pos : pos + 8].split(b"\0", 1)[0].decode("latin1")
        raw_size = _u32(data, pos + 16)
        raw_offset = _u32(data, pos + 20)
        if raw_size and raw_offset + raw_size > len(data):
            raise InspectionError("PE section extends beyond end of file")
        if raw_size:
            max_raw_end = max(max_raw_end, raw_offset + raw_size)
        sections.append({"name": name, "raw_offset": raw_offset, "raw_size": raw_size})
    # Certificate data may also live outside PE image sections.
    return {
        "machine": f"0x{machine:04x}",
        "bits": 32 if optional_magic == 0x10B else (64 if optional_magic == 0x20B else None),
        "sections": sections,
        "unmapped_trailing_bytes": len(data) - max_raw_end,
    }


def inspect_bytes(data: bytes, name: str = "sample.bin") -> dict:
    """Inspect bytes without execution, modification, or extraction."""
    if len(data) > MAX_FILE_BYTES:
        raise InspectionError("file exceeds bounded inspection size")
    record = {"name": Path(name).name, "size": len(data), "sha256": hashlib.sha256(data).hexdigest(), "format": "unknown"}
    director = _director_header(data)
    if director:
        record.update(format="director", director=director)
    else:
        pe = _pe_header(data)
        if pe:
            record.update(format="windows-pe", pe=pe)
            found = []
            cursor = 0
            while len(found) < 16:
                offsets = [p for sig in (b"XFIR", b"RIFX") if (p := data.find(sig, cursor)) >= 0]
                if not offsets:
                    break
                cursor = min(offsets) + 4
                item = _director_header(data, cursor - 4)
                if item:
                    found.append(item)
            if found:
                record["embedded_director"] = found
    return record


def inspect_path(path: Path) -> dict:
    if path.is_symlink() or not path.is_file():
        raise InspectionError("expected a regular file, not a link")
    if path.stat().st_size > MAX_FILE_BYTES:
        raise InspectionError("file exceeds bounded inspection size")
    return inspect_bytes(path.read_bytes(), path.name)


def scan(directory: Path, max_files: int = 1000) -> dict:
    if directory.is_symlink() or not directory.is_dir():
        raise InspectionError("directory must exist and must not be a symlink")
    files, errors = [], []
    for index, path in enumerate(sorted(directory.rglob("*"))):
        if index >= max_files:
            errors.append({"name": "<limit>", "error": "maximum entry count reached"})
            break
        if path.is_symlink() or not path.is_file() or path.suffix.lower() not in SUPPORTED_EXTENSIONS:
            continue
        rel = path.relative_to(directory).as_posix()
        try:
            files.append({"relative_path": rel, **inspect_path(path)})
        except (InspectionError, OSError) as exc:
            errors.append({"name": rel, "error": str(exc)})
    return {"schema_version": 1, "file_count": len(files), "files": files, "errors": errors}


def report_json(directory: Path) -> str:
    return json.dumps(scan(directory), indent=2, ensure_ascii=False, sort_keys=True) + "\n"

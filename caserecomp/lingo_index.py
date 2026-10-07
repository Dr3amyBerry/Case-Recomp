"""Recover validated *handler names and bytecode boundaries* from Director 8.5.

This is metadata extraction, NOT decompilation of the 80 game Lscr scripts.
Lnam indexes and LctX references are parsed with strict bounds and joined to
Lscr handler records. Bytecode bodies are never included in the report.
"""
from __future__ import annotations

from collections import Counter
from dataclasses import dataclass
from hashlib import sha256
import struct
from pathlib import Path

from .director import open_archive
from .inspector import InspectionError

MAX_SCRIPT_NAMES = 16384
MAX_HANDLERS_PER_SCRIPT = 2048


@dataclass(frozen=True)
class HandlerSymbol:
    script_id: int
    name_id: int
    name: str
    bytecode_bytes: int
    bytecode_digest: str


def read_lnam(data: bytes) -> tuple[str, ...]:
    if len(data) < 20:
        raise InspectionError("truncated Lnam header")
    offset, count = struct.unpack_from(">HH", data, 16)
    if count > MAX_SCRIPT_NAMES or offset < 20 or offset > len(data):
        raise InspectionError("invalid Lnam names offset or count")
    cursor = offset
    names = []
    for _ in range(count):
        if cursor >= len(data):
            raise InspectionError("truncated Lnam entry")
        size = data[cursor]
        cursor += 1
        if cursor + size > len(data):
            raise InspectionError("truncated Lnam name bytes")
        names.append(data[cursor:cursor + size].decode("mac_roman"))
        cursor += size
    if cursor != len(data):
        raise InspectionError("unexpected Lnam trailing bytes")
    return tuple(names)


def read_lctx(data: bytes) -> tuple[int, tuple[int, ...]]:
    if len(data) < 42:
        raise InspectionError("truncated LctX header")
    count = struct.unpack_from(">i", data, 8)[0]
    offset = struct.unpack_from(">H", data, 16)[0]
    lnam_id = struct.unpack_from(">i", data, 32)[0]
    if count < 0 or count > MAX_SCRIPT_NAMES or offset < 42 or offset + count * 12 > len(data):
        raise InspectionError("invalid LctX entry layout")
    script_ids = []
    for index in range(count):
        entry_offset = offset + index * 12
        script_id = struct.unpack_from(">i", data, entry_offset + 4)[0]
        if script_id >= 0:
            script_ids.append(script_id)
    if lnam_id < 0 or len(script_ids) != len(set(script_ids)):
        raise InspectionError("invalid LctX script identifiers")
    return lnam_id, tuple(script_ids)


def script_handlers(script_id: int, data: bytes, names: tuple[str, ...]) -> tuple[HandlerSymbol, ...]:
    if len(data) < 92:
        raise InspectionError("truncated Lscr header")
    count = struct.unpack_from(">H", data, 72)[0]
    offset = struct.unpack_from(">i", data, 74)[0]
    if count > MAX_HANDLERS_PER_SCRIPT or offset < 92 or offset + 46 * count > len(data):
        raise InspectionError("invalid Director 8.5 Lscr handler directory")
    results = []
    for i in range(count):
        pos = offset + i * 46
        name_id = struct.unpack_from(">h", data, pos)[0]
        bytecode_len, bytecode_offset = struct.unpack_from(">ii", data, pos + 4)
        if not 0 <= name_id < len(names):
            raise InspectionError("Lscr handler references unknown Lnam name")
        if (bytecode_len < 0 or bytecode_offset < offset + 46 * count
                or bytecode_offset + bytecode_len > len(data)):
            raise InspectionError("Lscr handler points outside bytecode body")
        code = data[bytecode_offset:bytecode_offset + bytecode_len]
        results.append(HandlerSymbol(script_id, name_id, names[name_id], bytecode_len, sha256(code).hexdigest()))
    return tuple(results)


def index_archive(archive, *, redact: bool = True) -> dict:
    names_ids = [ident for ident, entry in archive.entries.items() if entry.tag == "Lnam"]
    context_ids = [ident for ident, entry in archive.entries.items() if entry.tag == "LctX"]
    if len(names_ids) != 1 or len(context_ids) != 1:
        raise InspectionError("expected exactly one Lnam and LctX")
    names_id, script_ids = read_lctx(archive.get_resource(context_ids[0]))
    if names_id != names_ids[0]:
        raise InspectionError("LctX names ID disagrees with Lnam resource map")
    names = read_lnam(archive.get_resource(names_id))
    scripts = []
    for ident in script_ids:
        resource = archive.entries.get(ident)
        if resource is None:
            raise InspectionError("LctX references missing script resource")
        if resource.tag != "Lscr":
            raise InspectionError("LctX points to non-Lscr resource")
        scripts.extend(script_handlers(ident, archive.get_resource(ident), names))
    counted = Counter(item.name.casefold() for item in scripts)
    # Expose names ONLY in a deliberately local report (redacted by default).
    handler_list = [{"script_id": item.script_id, "name": sha256(item.name.casefold().encode()).hexdigest() if redact else item.name,
                     "bytecode_size": item.bytecode_bytes, "bytecode_sha256": item.bytecode_digest}
                    for item in scripts]
    return {"schema_version": 1, "script_count": len(script_ids), "symbols_in_lnam": len(names),
            "handler_count": len(scripts), "unique_handler_names": len(counted),
            "duplicate_names_across_scripts": sum(n > 1 for n in counted.values()),
            "name_redaction": "sha256" if redact else "none", "handlers": handler_list,
            "recovered_lingo_source": False,
            "description": "names and bytecode hash boundaries only; not equivalent to original Lingo source"}


def index_movie(path: Path, *, redact: bool = True) -> dict:
    archive, _ = open_archive(path)
    if archive.kind != "movie":
        raise InspectionError("requires a Director movie with LctX script context")
    return index_archive(archive, redact=redact)

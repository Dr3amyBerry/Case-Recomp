"""Bounded reader for Director 8.5 compiled Lingo (Lscr) and a private bytecode bundle.

The bundle keeps the user's own compiled scripts in a neutral JSON form for the
Kotlin Lingo VM: names, script members, properties, globals, literals and per
handler argument/local names plus base64 bytecode. It never contains decompiled
source text. Bundles built from commercial movies are private and must not be
committed or redistributed.
"""
from __future__ import annotations

import base64
import struct
from hashlib import sha256
from pathlib import Path
from typing import Any

from .director import open_archive, read_local
from .inspector import InspectionError
from .lingo_index import read_lctx, read_lnam
from .score import parse_cast_order

BUNDLE_FORMAT = "case-recomp-lingo-bundle"
VERSION = 1
SCRIPT_MEMBER_TYPE = 11
SCRIPT_TYPES = {1: "behavior", 3: "movie", 7: "parent"}
LITERAL_TYPES = {1: "string", 4: "int", 9: "float"}
MAX_ITEMS = 65535


def _u16(data: bytes, pos: int) -> int:
    return struct.unpack_from(">H", data, pos)[0]


def _u32(data: bytes, pos: int) -> int:
    return struct.unpack_from(">I", data, pos)[0]


def _name_list(data: bytes, count: int, offset: int, names: tuple[str, ...], what: str) -> list[str]:
    if count > MAX_ITEMS or offset + 2 * count > len(data):
        raise InspectionError(f"Lscr {what} table outside script")
    out = []
    for index in range(count):
        name_id = struct.unpack_from(">h", data, offset + 2 * index)[0]
        if not 0 <= name_id < len(names):
            raise InspectionError(f"Lscr {what} references unknown name")
        out.append(names[name_id])
    return out


def _extended_to_float(raw: bytes) -> float:
    """IEEE 754 80-bit extended (big endian) to a Python float."""
    exponent = struct.unpack(">H", raw[:2])[0]
    mantissa = struct.unpack(">Q", raw[2:])[0]
    sign = -1.0 if exponent & 0x8000 else 1.0
    exponent &= 0x7FFF
    if exponent == 0 and mantissa == 0:
        return 0.0 * sign
    if exponent == 0x7FFF:
        raise InspectionError("non-finite Lingo float literal")
    return sign * mantissa * 2.0 ** (exponent - 16383 - 63)


def _literals(data: bytes, count: int, offset: int, data_offset: int) -> list[dict]:
    if count > MAX_ITEMS or offset + 8 * count > len(data) or data_offset > len(data):
        raise InspectionError("Lscr literal table outside script")
    out = []
    for index in range(count):
        kind, value = struct.unpack_from(">II", data, offset + 8 * index)
        if kind not in LITERAL_TYPES:
            raise InspectionError(f"unsupported Lingo literal type {kind}")
        if kind == 4:
            out.append({"type": "int", "value": struct.unpack(">i", struct.pack(">I", value))[0]})
            continue
        start = data_offset + value
        if start + 4 > len(data):
            raise InspectionError("Lscr literal data outside script")
        length = _u32(data, start)
        raw = data[start + 4:start + 4 + length]
        if len(raw) != length:
            raise InspectionError("truncated Lscr literal data")
        if kind == 1:
            text = raw[:-1] if raw.endswith(b"\0") else raw
            out.append({"type": "string", "value": text.decode("latin-1")})
        elif length == 8:
            out.append({"type": "float", "value": struct.unpack(">d", raw)[0]})
        elif length == 10:
            out.append({"type": "float", "value": _extended_to_float(raw)})
        else:
            raise InspectionError("unsupported Lingo float literal size")
    return out


def parse_lscr(data: bytes, names: tuple[str, ...]) -> dict:
    """Parse one Director 8.5 Lscr chunk into names, literals and handler bytecode."""
    if len(data) < 92:
        raise InspectionError("truncated Lscr header")
    script_number = _u16(data, 18)
    prop_count, prop_offset = _u16(data, 60), _u32(data, 62)
    glob_count, glob_offset = _u16(data, 66), _u32(data, 68)
    handler_count, handler_offset = _u16(data, 72), _u32(data, 74)
    lit_count, lit_offset = _u16(data, 78), _u32(data, 80)
    lit_data_offset = _u32(data, 88)
    if handler_count > MAX_ITEMS or handler_offset + 46 * handler_count > len(data):
        raise InspectionError("Lscr handler table outside script")
    handlers = []
    for index in range(handler_count):
        pos = handler_offset + 46 * index
        name_id = struct.unpack_from(">h", data, pos)[0]
        code_len, code_offset = _u32(data, pos + 4), _u32(data, pos + 8)
        arg_count, arg_offset = _u16(data, pos + 12), _u32(data, pos + 14)
        local_count, local_offset = _u16(data, pos + 18), _u32(data, pos + 20)
        hglob_count, hglob_offset = _u16(data, pos + 24), _u32(data, pos + 26)
        if not 0 <= name_id < len(names):
            raise InspectionError("Lscr handler references unknown name")
        if code_offset + code_len > len(data):
            raise InspectionError("Lscr handler bytecode outside script")
        handlers.append({
            "name": names[name_id],
            "arguments": _name_list(data, arg_count, arg_offset, names, "argument"),
            "locals": _name_list(data, local_count, local_offset, names, "local"),
            "globals": _name_list(data, hglob_count, hglob_offset, names, "handler global"),
            "bytecode": base64.b64encode(data[code_offset:code_offset + code_len]).decode("ascii"),
        })
    return {
        "script_number": script_number,
        "properties": _name_list(data, prop_count, prop_offset, names, "property"),
        "globals": _name_list(data, glob_count, glob_offset, names, "global"),
        "literals": _literals(data, lit_count, lit_offset, lit_data_offset),
        "handlers": handlers,
    }


def parse_script_member(data: bytes) -> dict | None:
    """Return name, script number and script type for a CASt script member, else None."""
    if len(data) < 12:
        raise InspectionError("truncated CASt member")
    member_type, info_len, data_len = struct.unpack_from(">iii", data, 0)
    if member_type != SCRIPT_MEMBER_TYPE:
        return None
    if info_len < 20 or data_len < 2 or 12 + info_len + data_len != len(data):
        raise InspectionError("invalid script CASt lengths")
    info = data[12:12 + info_len]
    script_number = struct.unpack_from(">i", info, 16)[0]
    script_type = SCRIPT_TYPES.get(_u16(data, 12 + info_len))
    if script_type is None:
        raise InspectionError("unsupported Lingo script member type")
    name = ""
    table = _u32(info, 0)
    if table + 2 <= len(info):
        count = _u16(info, table)
        offsets_end = table + 2 + 4 * count
        if count >= 2 and offsets_end + 4 <= len(info):
            offsets = [_u32(info, table + 2 + 4 * i) for i in range(count)]
            items = offsets_end + 4
            start = items + offsets[1]
            if start < len(info):
                length = info[start]
                name = info[start + 1:start + 1 + length].decode("latin-1")
    return {"name": name, "script_number": script_number, "script_type": script_type}


def build_lingo_bundle(path: Path) -> dict:
    """Collect every compiled script of a movie (projector-embedded movies included)."""
    archive, _ = open_archive(path)
    if archive.kind != "movie":
        raise InspectionError("Lingo bundle requires a Director movie")
    entries = {tag: [rid for rid, e in archive.entries.items() if e.tag == tag] for tag in ("Lnam", "LctX", "CAS*")}
    if len(entries["Lnam"]) != 1 or len(entries["LctX"]) != 1 or len(entries["CAS*"]) != 1:
        raise InspectionError("expected exactly one Lnam, LctX and CAS* resource")
    names = read_lnam(archive.get_resource(entries["Lnam"][0]))
    _, script_ids = read_lctx(archive.get_resource(entries["LctX"][0]))
    members: dict[int, dict] = {}
    for number, rid in enumerate(parse_cast_order(archive.get_resource(entries["CAS*"][0])), start=1):
        if rid:
            meta = parse_script_member(archive.get_resource(rid))
            if meta:
                members[meta["script_number"]] = {"member": number, **meta}
    scripts = []
    for index, rid in enumerate(script_ids, start=1):
        if not rid:
            continue
        script = parse_lscr(archive.get_resource(rid), names)
        member = members.get(script["script_number"] + 1)  # see score.member_script_number
        scripts.append({"context_index": index, **script,
                        "member": None if member is None else
                        {"number": member["member"], "name": member["name"], "script_type": member["script_type"]}})
    return {
        "format": BUNDLE_FORMAT, "version": VERSION,
        "source_sha256": sha256(read_local(path)).hexdigest(),
        "names": list(names), "scripts": scripts,
        "notice": "private compiled bytecode of the user's own movie; no source text; do not redistribute",
    }

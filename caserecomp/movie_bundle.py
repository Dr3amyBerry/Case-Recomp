"""Private Director movie bundle for the Kotlin Director runtime.

The bundle describes what the runtime needs besides compiled Lingo: the stage,
frame labels, every score sprite span and behaviour (with its parameter
literal), the cast library table, and each cast member's name, type and
geometry, from the movie and from its external casts. Member media stays in the
user's local media export and is referenced by archive and resource id. Bundles
built from commercial movies hold names and text from that movie: they are
private and must not be committed or redistributed.
"""
from __future__ import annotations

import re
import struct
from hashlib import sha256
from pathlib import Path

from .director import DirectorArchive, open_archive, read_local
from .inspector import InspectionError
from .relationships import CastRelationships
from .score import (_score_entries, parse_cast_order, parse_frame_label_names, parse_movie_config,
                    parse_score)

BUNDLE_FORMAT = "case-recomp-movie-bundle"
VERSION = 1
MAX_CAST_LIBS = 1024
CAST_SUFFIXES = {".cct", ".cxt", ".cst"}
MEMBER_TYPES = {
    1: "bitmap", 2: "filmLoop", 3: "field", 4: "palette", 5: "picture", 6: "sound", 7: "button",
    8: "shape", 9: "movie", 10: "digitalVideo", 11: "script", 12: "richText", 14: "transition",
    15: "xtra", 16: "font",
}
SHAPE_TYPES = {1: "rect", 2: "roundRect", 3: "oval", 4: "line"}
MEDIA_TAGS = ("BITD", "ediM", "ALFA", "snd ", "XMED", "STXT")
FONT_ENTRY = re.compile(rb"\x00[0-9A-F]+,([\x01-\x3f])")  # font table entry: NUL, hex size, comma, name length


def _u16(data: bytes, pos: int) -> int:
    return struct.unpack_from(">H", data, pos)[0]


def _u32(data: bytes, pos: int) -> int:
    return struct.unpack_from(">I", data, pos)[0]


def _single(archive: DirectorArchive, tag: str) -> bytes | None:
    ids = [rid for rid, entry in archive.entries.items() if entry.tag == tag]
    if len(ids) > 1:
        raise InspectionError(f"expected at most one {tag} resource")
    return archive.get_resource(ids[0]) if ids else None


def info_items(info: bytes) -> list[bytes]:
    """Items of a CASt info list (item 0 script text, item 1 name, ...)."""
    if len(info) < 4:
        return []
    table = _u32(info, 0)
    if table + 2 > len(info):
        return []
    count = _u16(info, table)
    start = table + 2 + 4 * count + 4
    if start > len(info):
        raise InspectionError("CASt info list outside member")
    offsets = [_u32(info, table + 2 + 4 * i) for i in range(count)] + [len(info) - start]
    if any(a > b for a, b in zip(offsets, offsets[1:])):
        raise InspectionError("CASt info offsets are not monotonic")
    return [info[start + offsets[i]:start + offsets[i + 1]] for i in range(count)]


def _pascal(raw: bytes) -> str:
    if not raw:
        return ""
    if 1 + raw[0] > len(raw):
        raise InspectionError("truncated Pascal string")
    return raw[1:1 + raw[0]].decode("latin-1")


def parse_cast_list(data: bytes) -> list[dict]:
    """MCsL: cast library number, name and recorded file path."""
    if len(data) < 12:
        raise InspectionError("truncated MCsL cast list")
    data_offset, _unk, cast_count, items_per_cast = struct.unpack_from(">IHHH", data, 0)
    if cast_count > MAX_CAST_LIBS or not 1 <= items_per_cast <= 16 or data_offset + 2 > len(data):
        raise InspectionError("invalid MCsL header")
    count = _u16(data, data_offset)
    table = data_offset + 2
    if count < cast_count * items_per_cast + 1 or table + 4 * count + 4 > len(data):
        raise InspectionError("invalid MCsL item table")
    offsets = [_u32(data, table + 4 * i) for i in range(count)]
    items_len = _u32(data, table + 4 * count)
    base = table + 4 * count + 4
    if base + items_len > len(data) or any(o > items_len for o in offsets):
        raise InspectionError("MCsL items outside resource")
    bounds = offsets + [items_len]
    items = [data[base + bounds[i]:base + max(bounds[i], bounds[i + 1])] for i in range(count)]
    out = []
    for index in range(cast_count):
        group = items[index * items_per_cast + 1:index * items_per_cast + 1 + items_per_cast]
        entry = {"number": index + 1, "name": _pascal(group[0]) if group else "",
                 "file_path": _pascal(group[1]) if len(group) > 1 else ""}
        if len(group) > 3 and len(group[3]) >= 4:
            entry["min_member"], entry["max_member"] = struct.unpack_from(">HH", group[3], 0)
        out.append(entry)
    return out


def parse_xmed_text(data: bytes) -> dict:
    """Plain text and font names of a Director text Xtra (XMED) member."""
    sections: dict[int, bytes] = {}
    pos = data.find(b"\x03")
    while 0 <= pos and pos + 21 <= len(data):
        header = data[pos + 1:pos + 21]
        try:
            section, length = int(header[0:4], 16), int(header[4:12], 16)
        except ValueError:
            pos = data.find(b"\x03", pos + 1)
            continue
        body = data[pos + 21:pos + 21 + length]
        sections.setdefault(section, body)
        pos = data.find(b"\x03", pos + 21 + length)
    text = ""
    raw = sections.get(2)
    if raw is not None:
        if not raw.startswith(b"\x00") or b"," not in raw:
            raise InspectionError("unsupported XMED text section")
        size_hex, _, rest = raw[1:].partition(b",")
        size = int(size_hex, 16)
        if size > len(rest):
            raise InspectionError("truncated XMED text")
        text = rest[:size].decode("cp1252", errors="replace")  # lines end with RETURN, as Lingo sees them
    fonts = []
    for match in FONT_ENTRY.finditer(sections.get(8, b"")):
        name = sections[8][match.end():match.end() + match.group(1)[0]]
        if len(name) == match.group(1)[0] and all(32 <= c < 127 for c in name):
            fonts.append(name.decode("ascii"))
    return {"text": text, "fonts": list(dict.fromkeys(fonts))}


def _rect(specific: bytes, pos: int) -> tuple[int, int, int, int]:
    top, left, bottom, right = struct.unpack_from(">hhhh", specific, pos)
    return left, top, right, bottom


def _member_record(archive: DirectorArchive, relations: CastRelationships, rid: int, number: int) -> dict:
    data = archive.get_resource(rid)
    if len(data) < 12:
        raise InspectionError("truncated CASt member")
    member_type, info_len, data_len = struct.unpack_from(">iii", data, 0)
    if info_len < 0 or data_len < 0 or 12 + info_len + data_len != len(data):
        raise InspectionError("invalid CASt lengths")
    info, specific = data[12:12 + info_len], data[12 + info_len:]
    items = info_items(info)
    record: dict = {"number": number, "name": _pascal(items[1]) if len(items) > 1 else "",
                    "type": MEMBER_TYPES.get(member_type, f"unknown{member_type}")}
    if len(info) >= 20:
        script = struct.unpack_from(">i", info, 16)[0]
        if script > 0:
            record["script_number"] = script
    media = {}
    for tag in MEDIA_TAGS:
        ids = relations.resources_for(rid, tag)
        if ids:
            media[tag.strip()] = ids[0]
    if media:
        record["media"] = media
    if member_type == 1 and len(specific) >= 24:
        left, top, right, bottom = _rect(specific, 2)
        reg_y, reg_x = struct.unpack_from(">hh", specific, 18)
        record.update(width=right - left, height=bottom - top, reg_x=reg_x - left, reg_y=reg_y - top,
                      bit_depth=specific[23])
    elif member_type == 8 and len(specific) >= 17:
        left, top, right, bottom = _rect(specific, 2)
        record.update(shape=SHAPE_TYPES.get(_u16(specific, 0), "rect"), width=right - left,
                      height=bottom - top, pattern=_u16(specific, 10), fore_color=specific[12],
                      back_color=specific[13], filled=bool(specific[14]), line_size=specific[15])
    elif member_type == 11 and len(specific) >= 2:
        record["script_type"] = {1: "behavior", 3: "movie", 7: "parent"}.get(_u16(specific, 0), "unknown")
    elif member_type == 15 and len(specific) >= 4:
        size = _u32(specific, 0)
        kind = specific[4:4 + size].decode("latin-1") if size <= len(specific) - 4 else ""
        record["xtra"] = kind
        if kind == "text" and "XMED" in media:
            record.update(parse_xmed_text(archive.get_resource(media["XMED"])))
    return record


def cast_members(archive: DirectorArchive) -> list[dict]:
    cast_order = _single(archive, "CAS*")
    if cast_order is None:
        return []
    relations = CastRelationships(archive)
    return [_member_record(archive, relations, rid, number)
            for number, rid in enumerate(parse_cast_order(cast_order), start=1) if rid]


def _score_record(archive: DirectorArchive) -> dict:
    raw = _single(archive, "VWSC")
    if raw is None:
        raise InspectionError("movie has no score")
    score = parse_score(raw)
    entries, _ = _score_entries(raw)
    spans = [{"start": span.start_frame, "end": span.end_frame, "channel": span.state.channel,
              "type": span.state.sprite_type, "ink": span.state.ink, "trails": span.state.trails,
              "stretch": span.state.stretch, "cast_lib": span.state.cast_lib, "member": span.state.cast_member,
              "x": span.state.x, "y": span.state.y, "width": span.state.width, "height": span.state.height,
              "blend": span.state.blend, "flip_h": span.state.flip_h, "flip_v": span.state.flip_v}
             for span in score.sprite_spans]
    # Each span row of the score (start, end, channel) is one Director sprite span; its
    # behaviours follow it, and a span without behaviours yields a single empty reference.
    sprite_spans: dict[tuple[int, int, int], list[dict]] = {}
    for ref in score.behaviors:
        behaviors = sprite_spans.setdefault((ref.start_frame, ref.end_frame, ref.channel), [])
        if ref.cast_member is None:
            continue
        parameters = None
        if ref.parameter_entry is not None:
            parameters = entries[ref.parameter_entry].rstrip(b"\0").decode("latin-1")
        behaviors.append({"cast_lib": ref.cast_lib, "member": ref.cast_member, "parameters": parameters})
    return {"frame_count": score.frame_count, "channel_count": score.channel_count,
            "sprite_record_size": score.sprite_record_size, "sprites": spans,
            "spans": [{"start": start, "end": end, "channel": channel, "behaviors": behaviors}
                      for (start, end, channel), behaviors in sprite_spans.items()]}


def external_cast_files(directory: Path, limit: int = MAX_CAST_LIBS) -> list[Path]:
    files = sorted(path for path in directory.iterdir()
                   if path.is_file() and path.suffix.lower() in CAST_SUFFIXES)
    if len(files) > limit:
        raise InspectionError("too many external casts")
    return files


def build_movie_bundle(movie: Path, casts: list[Path] | None = None) -> dict:
    """Bundle a movie (or projector) and its external casts for the Director runtime."""
    archive, _ = open_archive(movie)
    if archive.kind != "movie":
        raise InspectionError("movie bundle requires a Director movie")
    config_raw, labels_raw, cast_list_raw = (_single(archive, tag) for tag in ("DRCF", "VWLB", "MCsL"))
    if config_raw is None:
        raise InspectionError("movie has no DRCF config")
    config = parse_movie_config(config_raw)
    libs = parse_cast_list(cast_list_raw) if cast_list_raw else [{"number": 1, "name": "Internal", "file_path": ""}]
    external = []
    for path in sorted(casts or [], key=lambda item: item.name.lower()):
        cast_archive, _ = open_archive(path)
        if cast_archive.kind != "cast":
            raise InspectionError(f"{path.name} is not a Director cast")
        external.append({"file": path.name, "sha256": sha256(read_local(path)).hexdigest(),
                         "members": cast_members(cast_archive)})
    return {
        "format": BUNDLE_FORMAT, "version": VERSION,
        "source_sha256": sha256(read_local(movie)).hexdigest(),
        "stage": {"width": config.width, "height": config.height, "tempo": config.tempo},
        "labels": [{"frame": frame, "name": name} for frame, name in
                   (parse_frame_label_names(labels_raw) if labels_raw else ())],
        "cast_libs": libs,
        "internal_members": cast_members(archive),
        "external_casts": external,
        "score": _score_record(archive),
        "notice": "private names, text and layout of the user's own movie; no media; do not redistribute",
    }

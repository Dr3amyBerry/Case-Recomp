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


XMED_HEADER = re.compile(rb"\x03([0-9A-F]{4})([0-9A-F]{8})([0-9A-F]{8})")


def _xmed_sections(data: bytes) -> dict[int, tuple[int, bytes]]:
    """Section key -> (declared count, body) of an XMED stream; the first copy of a key wins."""
    sections: dict[int, tuple[int, bytes]] = {}
    pos = 0
    while True:
        match = XMED_HEADER.search(data, pos)
        if match is None:
            return sections
        length = int(match.group(2), 16)
        # The declared length counts the 0x03 that starts the next section header.
        body = data[match.end():match.end() + length]
        if body.endswith(b"\x03"):
            body = body[:-1]
        sections.setdefault(int(match.group(1), 16), (int(match.group(3), 16), body))
        pos = match.end() + max(0, length - 1)


class _Packer:
    """Paige's packed number stream: ctrl byte, hex digits; bit 7 repeats the last value
    (with bit 6 a following byte gives the repeat count); ctrl type 1 is an unsigned short."""

    def __init__(self, data: bytes):
        self.data, self.pos, self.last, self.repeat = data, 0, 0, 0

    def remaining(self) -> int:
        return len(self.data) - self.pos

    def num(self) -> int:
        if self.repeat > 0:
            self.repeat -= 1
            return self.last
        if self.pos >= len(self.data):
            return 0
        ctrl = self.data[self.pos]
        self.pos += 1
        if ctrl & 0x80:
            if ctrl & 0x40 and self.pos < len(self.data):
                self.repeat = self.data[self.pos] - 1
                self.pos += 1
            return self.last
        start = self.pos
        while self.pos < len(self.data) and chr(self.data[self.pos]) in "0123456789ABCDEFabcdef-":
            self.pos += 1
        digits = self.data[start:self.pos].decode("ascii")
        try:
            value = int(digits, 16) if digits else 0
        except ValueError:
            value = 0
        if ctrl & 0x0F == 1:
            value &= 0xFFFF
        self.last = value
        return value

    def nums(self, count: int) -> list[int]:
        return [self.num() for _ in range(count)]


def _run_index_at(section: tuple[int, bytes] | None, offset: int) -> int:
    """Index of the (text offset, index) run covering a text offset in a run section."""
    if section is None:
        return 0
    packer = _Packer(section[1])
    found = None
    while packer.remaining() > 0:
        start, index = packer.num(), packer.num()
        if found is not None and start > offset:
            break
        found = index
    return found or 0


def _xmed_paragraph_justification(body: bytes, index: int, version: int) -> int:
    """Justification (0 left, 1 center, 2 right, 3 full) of par_info [index] (section 7)."""
    packer = _Packer(body)
    for current in range(index + 1):
        if packer.remaining() <= 0:
            return 0
        justification = packer.num()
        if current == index:
            return justification
        packer.nums(8)  # line height, box, 3 indents, border, margin, line spacing
        if version >= 65547:
            packer.num()
        packer.nums(8)
        if version >= 65552:
            packer.num()
        packer.nums(2 + 8)  # refcon, dword2E8, gap2A8
        extra = packer.num()
        for _ in range(max(0, min(extra, 32))):
            packer.nums(4)
        packer.nums(max(0, extra - 32))
        packer.nums((version >= 8) + (version >= 65548) + 4 * (version >= 65552) + (version >= 65555)
                    + 9 * (version >= 131075))
        if version >= 131090:
            packer.nums(1 + 4 + (version >= 196614) + (version >= 196615) + (version >= 196616))
    return 0


def _xmed_char_style(body: bytes, index: int, version: int) -> dict | None:
    """Font index, size, styles and colour of style_info [index] (section 6)."""
    packer = _Packer(body)
    packer.num()  # declared style count
    for current in range(index + 1):
        if packer.remaining() <= 4:
            return None
        font_index = packer.num()
        packer.nums(4)  # two words, style number, word wrap
        packer.nums(4)
        fore = packer.nums(4)
        packer.nums(4)  # back colour
        if version < 65547:
            packer.num()
        fixed = packer.nums(11)  # Paige pg_fixed block: [1] is the point size (16.16)
        packer.nums((version < 65547) + (version >= 65551) + 2 + 8)  # refcon, dword120, gapB4
        flags = packer.nums(32 if version >= 257 else 16)
        packer.nums((version >= 65536) + 4 * (version >= 65552) + (version >= 65555))
        if current == index:
            style = [name for name, on in zip(("bold", "italic", "underline"), flags) if on]
            return {"font_index": font_index, "font_size": fixed[1] // 65536,
                    "font_style": style, "color": "#%02x%02x%02x" % tuple((c >> 8) & 0xFF for c in fore[:3])}
    return None


def parse_xmed_text(data: bytes) -> dict:
    """Plain text, font names and the first run's paragraph and character style of a
    Director text Xtra (XMED) member, whose styling is stored as packed Paige records."""
    sections = _xmed_sections(data)
    text = ""
    raw = sections.get(2, (0, None))[1]
    if raw is not None:
        if not raw.startswith(b"\x00") or b"," not in raw:
            raise InspectionError("unsupported XMED text section")
        size_hex, _, rest = raw[1:].partition(b",")
        size = int(size_hex, 16)
        if size > len(rest):
            raise InspectionError("truncated XMED text")
        text = rest[:size].decode("cp1252", errors="replace")  # lines end with RETURN, as Lingo sees them
    font_table = sections.get(8, (0, b""))[1]
    fonts = []
    for match in FONT_ENTRY.finditer(font_table):
        name = font_table[match.end():match.end() + match.group(1)[0]]
        if len(name) == match.group(1)[0] and all(32 <= c < 127 for c in name):
            fonts.append(name.decode("ascii"))
    result: dict = {"text": text, "fonts": list(dict.fromkeys(fonts))}
    version = _Packer(sections[0][1]).num() if 0 in sections else 0
    if 9 in sections:  # member rect: top, left, bottom, right
        top, left, bottom, right = _Packer(sections[9][1]).nums(4)
        if right > left and bottom > top:
            result["width"], result["height"] = right - left, bottom - top
    # Member-level style is the one of the first visible character, not of leading blank lines.
    first = len(text) - len(text.lstrip("\r\n "))
    if 7 in sections:
        justification = _xmed_paragraph_justification(sections[7][1], _run_index_at(sections.get(5), first), version)
        result["alignment"] = {1: "center", 2: "right", 3: "justify"}.get(justification, "left")
    if 6 in sections:
        style = _xmed_char_style(sections[6][1], _run_index_at(sections.get(4), first), version)
        if style is not None:
            font_index = style.pop("font_index")
            if 0 <= font_index < len(fonts):
                style["font"] = fonts[font_index]
            if not 0 < style["font_size"] <= 200:
                del style["font_size"]
            result["style"] = style
    return result


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

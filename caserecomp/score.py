"""Bounded Director 8 score/cast structural analysis.

The parser exposes timeline geometry and references, never original text or
reconstructed game rules.  Reports can therefore remain local/redacted while
synthetic fixtures exercise the same binary structures in public tests.
"""
from __future__ import annotations

from collections import Counter
from dataclasses import dataclass
from hashlib import sha256
import struct
from pathlib import Path

from .director import open_archive
from .inspector import InspectionError
from .lingo_index import index_archive

MAX_SCORE_ENTRIES = 100_000
MAX_SCORE_BYTES = 64 * 1024 * 1024
MAX_FRAMES = 100_000
MAX_CHANNELS = 2_048
MAX_SPRITE_RECORD = 128
MAX_LABELS = 16_384

CAST_TYPES = {
    0: "null", 1: "bitmap", 2: "film-loop", 3: "text", 4: "palette",
    5: "picture", 6: "sound", 7: "button", 8: "shape", 9: "movie",
    10: "digital-video", 11: "script", 12: "rich-text", 14: "transition",
    15: "xtra", 16: "font", 17: "shockwave-3d",
}


def _i16(data: bytes, pos: int) -> int:
    return struct.unpack_from(">h", data, pos)[0]


def _u16(data: bytes, pos: int) -> int:
    return struct.unpack_from(">H", data, pos)[0]


def _i32(data: bytes, pos: int) -> int:
    return struct.unpack_from(">i", data, pos)[0]


@dataclass(frozen=True)
class MovieConfig:
    stage_left: int
    stage_top: int
    stage_right: int
    stage_bottom: int
    tempo: int
    platform: int
    default_palette_cast: int | None
    default_palette_member: int | None

    @property
    def width(self) -> int:
        return self.stage_right - self.stage_left

    @property
    def height(self) -> int:
        return self.stage_bottom - self.stage_top


@dataclass(frozen=True)
class CastMemberMeta:
    resource_id: int
    member_type: int
    script_number: int | None


@dataclass(frozen=True)
class SpriteState:
    frame: int
    channel: int
    sprite_type: int
    ink: int
    trails: bool
    stretch: bool
    cast_lib: int
    cast_member: int
    x: int
    y: int
    width: int
    height: int
    blend: int
    flip_h: bool
    flip_v: bool
    # Sprite colours: "#rrggbb" when the record holds RGB, else a palette index (255 black, 0 white).
    fore_color: str | int = 255
    back_color: str | int = 0

    def signature(self) -> tuple:
        return (self.channel, self.sprite_type, self.ink, self.trails, self.stretch,
                self.cast_lib, self.cast_member, self.x, self.y, self.width,
                self.height, self.blend, self.flip_h, self.flip_v, self.fore_color, self.back_color)


@dataclass(frozen=True)
class SpriteSpan:
    start_frame: int
    end_frame: int
    state: SpriteState


@dataclass(frozen=True)
class BehaviorRef:
    start_frame: int
    end_frame: int
    channel: int
    cast_lib: int | None
    cast_member: int | None
    parameter_entry: int | None
    parameter_digest: str | None


@dataclass(frozen=True)
class FrameLabel:
    frame: int
    label_digest: str


@dataclass(frozen=True)
class ScoreModel:
    frame_count: int
    channel_count: int
    frames_version: int
    sprite_record_size: int
    sprites: tuple[SpriteState, ...]
    sprite_spans: tuple[SpriteSpan, ...]
    behaviors: tuple[BehaviorRef, ...]

    @property
    def displayed_channels(self) -> int:
        if self.sprite_record_size < 48:
            if self.frames_version <= 7:
                return 48
            if self.frames_version <= 13:
                return 120
        return self.channel_count


@dataclass(frozen=True)
class SceneSegment:
    ordinal: int
    start_frame: int
    end_frame: int
    active_sprite_spans: int
    behavior_refs: int


def parse_cast_order(data: bytes) -> tuple[int, ...]:
    """Parse CAS* logical member-number -> CASt resource-id mapping.

    Entry N (1-based) is the CASt resource id for logical cast member N; zero
    is an unused slot. The mapping is structural and contains no member names.
    """
    if not data or len(data) % 4 or len(data) // 4 > MAX_SCORE_ENTRIES:
        raise InspectionError("invalid CAS* cast order table")
    values = struct.unpack(f">{len(data) // 4}I", data)
    return tuple(values)


def cast_first_member(archive) -> int:
    """Member number of a cast's first CAS* slot: the config's minMember (casts may start at 2)."""
    configs = [rid for rid, entry in archive.entries.items() if entry.tag in ("DRCF", "VWCF")]
    if not configs:
        return 1
    data = archive.get_resource(configs[0])
    if len(data) < 16:
        return 1
    first = struct.unpack_from(">h", data, 12)[0]
    return first if 1 <= first <= MAX_SCORE_ENTRIES else 1


def build_scene_segments(score: ScoreModel, labels: tuple[FrameLabel, ...]) -> tuple[SceneSegment, ...]:
    """Build anonymous marker-delimited timeline segments for private analysis."""
    starts = sorted({1, *(label.frame for label in labels if 1 <= label.frame <= score.frame_count)})
    out: list[SceneSegment] = []
    for ordinal, start in enumerate(starts, 1):
        end = (starts[ordinal] - 1) if ordinal < len(starts) else score.frame_count
        if end < start:
            continue
        span_count = sum(span.start_frame <= end and span.end_frame >= start for span in score.sprite_spans)
        behavior_count = sum(item.start_frame <= end and item.end_frame >= start for item in score.behaviors)
        out.append(SceneSegment(ordinal, start, end, span_count, behavior_count))
    return tuple(out)


def parse_movie_config(data: bytes) -> MovieConfig:
    """Parse the D6+ DRCF fields needed by the engine model."""
    if len(data) < 58:
        raise InspectionError("truncated DRCF config")
    stage_top, stage_left, stage_bottom, stage_right = struct.unpack_from(">hhhh", data, 4)
    if stage_right <= stage_left or stage_bottom <= stage_top:
        raise InspectionError("invalid Director stage rectangle")
    tempo = _i16(data, 54)
    platform = _i16(data, 56)
    palette_cast = palette_member = None
    if len(data) >= 80:
        palette_cast = _i16(data, 76)
        palette_member = _i16(data, 78)
    return MovieConfig(stage_left, stage_top, stage_right, stage_bottom, tempo, platform,
                       palette_cast, palette_member)


def parse_cast_member(resource_id: int, data: bytes) -> CastMemberMeta:
    if len(data) < 12:
        raise InspectionError("truncated CASt member")
    member_type, info_len, data_len = struct.unpack_from(">iii", data, 0)
    if info_len < 0 or data_len < 0 or 12 + info_len + data_len != len(data):
        raise InspectionError("invalid CASt lengths")
    info = data[12:12 + info_len]
    script_number = _i32(info, 16) if len(info) >= 20 else None
    return CastMemberMeta(resource_id, member_type, script_number)


def lingo_script_number(data: bytes) -> int:
    if len(data) < 20:
        raise InspectionError("truncated Lscr script number")
    return _u16(data, 18)


def member_script_number(data: bytes) -> int:
    """Number by which CASt script members reference this Lscr.

    A script member's info stores the Lscr's own script number plus one. This was
    confirmed on an owned Director 8.5 movie, where every one of 80 script members
    then matched an independent decompiler's handler list (versus 3 of 80 without
    the offset).
    """
    return lingo_script_number(data) + 1


def parse_frame_labels(data: bytes) -> tuple[FrameLabel, ...]:
    return tuple(FrameLabel(frame, sha256(raw).hexdigest()) for frame, raw in _frame_label_entries(data))


def parse_frame_label_names(data: bytes) -> tuple[tuple[int, str], ...]:
    """Frame labels with their text, for private runtime bundles only."""
    return tuple((frame, raw.decode("latin-1")) for frame, raw in _frame_label_entries(data))


def _frame_label_entries(data: bytes) -> tuple[tuple[int, bytes], ...]:
    if len(data) < 2:
        raise InspectionError("truncated VWLB label table")
    count = _u16(data, 0)
    if count > MAX_LABELS or 2 + count * 4 + 4 > len(data):
        raise InspectionError("invalid VWLB label count")
    entries = [(_u16(data, 2 + i * 4), _u16(data, 4 + i * 4)) for i in range(count)]
    pos = 2 + count * 4
    size = _i32(data, pos)
    pos += 4
    if size < 0 or pos + size > len(data):
        raise InspectionError("invalid VWLB string block")
    block = data[pos:pos + size]
    ordered = sorted(enumerate(entries), key=lambda item: item[1][1])
    out: list[tuple[int, bytes]] = []
    for order_index, (_, (frame, offset)) in enumerate(ordered):
        if offset > len(block):
            raise InspectionError("VWLB label offset outside string block")
        end = len(block) if order_index + 1 == len(ordered) else ordered[order_index + 1][1][1]
        if end < offset or end > len(block):
            raise InspectionError("VWLB label offsets are not monotonic")
        out.append((max(1, frame), block[offset:end].rstrip(b"\0")))
    return tuple(sorted(out, key=lambda item: item[0]))


def _score_entries(data: bytes) -> tuple[tuple[bytes, ...], tuple[int, ...]]:
    if len(data) < 24 or len(data) > MAX_SCORE_BYTES:
        raise InspectionError("invalid VWSC score size")
    header = struct.unpack_from(">6i", data, 0)
    entry_count = header[3]
    if entry_count < 0 or entry_count > MAX_SCORE_ENTRIES:
        raise InspectionError("invalid VWSC entry count")
    table_end = 24 + (entry_count + 1) * 4
    if table_end > len(data):
        raise InspectionError("truncated VWSC entry offsets")
    offsets = struct.unpack_from(f">{entry_count + 1}i", data, 24)
    if not offsets or offsets[0] != 0 or any(a < 0 or a > b for a, b in zip(offsets, offsets[1:])):
        raise InspectionError("invalid VWSC entry offset order")
    base = table_end
    if offsets[-1] > len(data) - base:
        raise InspectionError("VWSC entries exceed resource")
    entries = tuple(data[base + offsets[i]:base + offsets[i + 1]] for i in range(entry_count))
    return entries, header


def _channel_state(raw: bytes, pos: int, record_size: int, frame: int, channel: int) -> SpriteState | None:
    if pos < 0 or record_size < 20 or pos + record_size > len(raw):
        return None
    sprite_type = raw[pos]
    ink_byte = raw[pos + 1]
    cast_lib, cast_member = struct.unpack_from(">HH", raw, pos + 4)
    y, x = struct.unpack_from(">hh", raw, pos + 12)
    height, width = struct.unpack_from(">HH", raw, pos + 16)
    if width == 0 or height == 0:
        return None
    blend = raw[pos + 21] if record_size >= 24 else 0
    flags = raw[pos + 22] if record_size >= 24 else 0
    fore, back = raw[pos + 2], raw[pos + 3]
    if record_size >= 28:
        # Director 7+: the colour code flags RGB colours, whose green and blue follow the record head.
        code = raw[pos + 20]
        if code & 0x10:
            fore = "#%02x%02x%02x" % (raw[pos + 2], raw[pos + 24], raw[pos + 26])
        if code & 0x20:
            back = "#%02x%02x%02x" % (raw[pos + 3], raw[pos + 25], raw[pos + 27])
    return SpriteState(frame, channel, sprite_type, ink_byte & 0x3F,
                       bool(ink_byte & 0x40), bool(ink_byte & 0x80), cast_lib, cast_member,
                       x, y, width, height, blend, bool(flags & 0x20), bool(flags & 0x40), fore, back)


def _parse_frame_data(data: bytes) -> tuple[int, int, int, int, tuple[SpriteState, ...]]:
    if len(data) < 20:
        raise InspectionError("truncated VWSC frame data")
    frame_count = _i32(data, 8)
    frames_version, record_size, channels = struct.unpack_from(">HHH", data, 12)
    if not 1 <= frame_count <= MAX_FRAMES or not 20 <= record_size <= MAX_SPRITE_RECORD or not 1 <= channels <= MAX_CHANNELS:
        raise InspectionError("invalid VWSC frame header")
    # Director 7+ writes 48-byte sprite records and lays out every channel the
    # header declares (an owned Director 8.5 movie with frames version 13 writes
    # deltas up to channel 320); only older 24-byte layouts use fixed counts.
    if frames_version <= 7 and record_size < 48:
        main_size, displayed, base_channel = 48, 48, 6
    elif frames_version <= 13 and record_size < 48:
        main_size, displayed, base_channel = 144, 120, 3
    else:
        main_size, displayed, base_channel = 0, channels, 0
    frame_size = main_size + displayed * record_size
    total = frame_count * frame_size
    if frame_size <= 0 or total > MAX_SCORE_BYTES:
        raise InspectionError("VWSC expanded score exceeds safety cap")
    expanded = bytearray(total)
    cursor = 20
    decoded_frames = 0
    while cursor + 2 <= len(data) and decoded_frames < frame_count:
        length = _u16(data, cursor)
        cursor += 2
        if length == 0:
            break
        body = length - 2
        if body < 0 or cursor + body > len(data):
            raise InspectionError("truncated VWSC frame delta")
        if decoded_frames:
            prev = (decoded_frames - 1) * frame_size
            cur = decoded_frames * frame_size
            expanded[cur:cur + frame_size] = expanded[prev:prev + frame_size]
        frame_end = cursor + body
        while cursor + 4 <= frame_end:
            size, offset = struct.unpack_from(">HH", data, cursor)
            cursor += 4
            dest = decoded_frames * frame_size + offset
            if size == 0 or cursor + size > frame_end:
                raise InspectionError("invalid VWSC channel delta")
            if dest + size <= len(expanded):
                expanded[dest:dest + size] = data[cursor:cursor + size]
            cursor += size
        if cursor != frame_end:
            raise InspectionError("unparsed VWSC frame delta bytes")
        decoded_frames += 1
    if decoded_frames != frame_count:
        raise InspectionError("VWSC frame stream ended before declared frame count")

    sprites: list[SpriteState] = []
    for frame in range(frame_count):
        frame_start = frame * frame_size
        if main_size:
            for sprite in range(displayed):
                pos = frame_start + main_size + sprite * record_size
                state = _channel_state(expanded, pos, record_size, frame + 1, sprite + base_channel)
                if state:
                    sprites.append(state)
        else:
            for channel in range(channels):
                if channel in (4, 5):
                    continue
                pos = frame_start + channel * record_size
                state = _channel_state(expanded, pos, record_size, frame + 1, channel)
                if state:
                    sprites.append(state)
    return frame_count, channels, frames_version, record_size, tuple(sprites)


def _compress_sprite_spans(sprites: tuple[SpriteState, ...]) -> tuple[SpriteSpan, ...]:
    by_channel: dict[int, list[SpriteState]] = {}
    for item in sprites:
        by_channel.setdefault(item.channel, []).append(item)
    spans: list[SpriteSpan] = []
    for channel in sorted(by_channel):
        items = sorted(by_channel[channel], key=lambda s: s.frame)
        start = previous = items[0]
        for current in items[1:]:
            contiguous = current.frame == previous.frame + 1
            same = current.signature() == previous.signature()
            if not contiguous or not same:
                spans.append(SpriteSpan(start.frame, previous.frame, start))
                start = current
            previous = current
        spans.append(SpriteSpan(start.frame, previous.frame, start))
    return tuple(sorted(spans, key=lambda s: (s.start_frame, s.state.channel, s.end_frame)))


def _parse_behaviors(entries: tuple[bytes, ...], frame_count: int, channel_count: int) -> tuple[BehaviorRef, ...]:
    out: list[BehaviorRef] = []
    index = 2
    while index < len(entries):
        row = entries[index]
        if 44 <= len(row) <= 48:
            start, end, _u0, _u1, channel = struct.unpack_from(">5i", row, 0)
            parameter_anchor = None
            if 1 <= start <= end <= frame_count and 0 <= channel < max(channel_count, 1):
                second = index + 1
                found = False
                while second < len(entries) and len(entries[second]) >= 8 and len(entries[second]) % 8 == 0:
                    valid_here = False
                    for pos in range(0, len(entries[second]), 8):
                        cast_lib, cast_member, parameter_entry = struct.unpack_from(">HHi", entries[second], pos)
                        if cast_lib > 0 and cast_member > 0:
                            digest = None
                            if 0 < parameter_entry < len(entries):
                                digest = sha256(entries[parameter_entry]).hexdigest()
                            out.append(BehaviorRef(start, end, channel, cast_lib, cast_member,
                                                   parameter_entry if parameter_entry > 0 else None, digest))
                            valid_here = found = True
                    if not valid_here:
                        break
                    second += 1
                if not found:
                    out.append(BehaviorRef(start, end, channel, None, None, parameter_anchor, None))
                index = max(index + 1, second)
                continue
        index += 1
    return tuple(out)


def parse_score(data: bytes) -> ScoreModel:
    entries, _header = _score_entries(data)
    if not entries:
        raise InspectionError("VWSC score has no frame entry")
    frame_count, channels, version, record_size, sprites = _parse_frame_data(entries[0])
    spans = _compress_sprite_spans(sprites)
    behaviors = _parse_behaviors(entries, frame_count, channels)
    return ScoreModel(frame_count, channels, version, record_size, sprites, spans, behaviors)


def analyze_movie_structure(path: Path) -> dict:
    """Produce a redacted private structural report suitable for Phase 4 modeling."""
    archive, _ = open_archive(path)
    if archive.kind != "movie":
        raise InspectionError("structural score analysis requires a Director movie")

    def single(tag: str) -> bytes:
        ids = [rid for rid, item in archive.entries.items() if item.tag == tag]
        if len(ids) != 1:
            raise InspectionError(f"expected exactly one {tag} resource")
        return archive.get_resource(ids[0])

    config = parse_movie_config(single("DRCF"))
    score = parse_score(single("VWSC"))
    labels = parse_frame_labels(single("VWLB"))
    cast: list[CastMemberMeta] = []
    for rid, item in sorted(archive.entries.items()):
        if item.tag == "CASt":
            cast.append(parse_cast_member(rid, archive.get_resource(rid)))
    cast_by_id = {item.resource_id: item for item in cast}
    cast_order = parse_cast_order(single("CAS*"))
    script_resource_by_number: dict[int, int] = {}
    for rid, item in archive.entries.items():
        if item.tag == "Lscr":
            script_resource_by_number[member_script_number(archive.get_resource(rid))] = rid

    handler_index = index_archive(archive, redact=True)
    behavior_script_links = 0
    behavior_script_resources: set[int] = set()
    referenced_logical_members: set[int] = set()
    for behavior in score.behaviors:
        if behavior.cast_member is None or behavior.cast_lib not in (None, 1):
            continue
        logical = behavior.cast_member
        if 1 <= logical <= len(cast_order) and cast_order[logical - 1] != 0:
            referenced_logical_members.add(logical)
            member = cast_by_id.get(cast_order[logical - 1])
            if member and member.script_number is not None and member.script_number in script_resource_by_number:
                behavior_script_links += 1
                behavior_script_resources.add(script_resource_by_number[member.script_number])
    for sprite in score.sprites:
        if sprite.cast_lib == 1 and 1 <= sprite.cast_member <= len(cast_order) and cast_order[sprite.cast_member - 1] != 0:
            referenced_logical_members.add(sprite.cast_member)
    segments = build_scene_segments(score, labels)
    type_counts = Counter(CAST_TYPES.get(item.member_type, f"unknown-{item.member_type}") for item in cast)
    return {
        "schema_version": 1,
        "source_sha256": sha256(path.read_bytes()).hexdigest(),
        "stage": {"width": config.width, "height": config.height, "tempo": config.tempo,
                  "platform": config.platform, "default_palette_member": config.default_palette_member},
        "score": {"frames": score.frame_count, "channels": score.channel_count,
                  "displayed_channels": score.displayed_channels,
                  "frames_version": score.frames_version, "sprite_record_size": score.sprite_record_size,
                  "active_sprite_records": len(score.sprites), "sprite_spans": len(score.sprite_spans),
                  "behavior_refs": len(score.behaviors), "anonymous_marker_segments": len(segments)},
        "frame_labels": {"count": len(labels), "frames": [label.frame for label in labels],
                         "label_hashes": [label.label_digest for label in labels]},
        "cast": {"members": len(cast), "logical_slots": len(cast_order),
                 "referenced_logical_members": len(referenced_logical_members),
                 "types": dict(sorted(type_counts.items()))},
        "lingo": {"scripts": handler_index["script_count"], "handlers": handler_index["handler_count"],
                  "unique_handler_names": handler_index["unique_handler_names"],
                  "score_behavior_refs_resolved_to_script": behavior_script_links,
                  "score_behavior_script_resources": len(behavior_script_resources)},
        "fidelity_claim": "structural only; no original text, media, operands, or game rules are embedded",
    }

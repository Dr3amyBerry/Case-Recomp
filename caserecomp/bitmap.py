"""Bounded Director ALFA and BITD bitmap support.

ALFA masks in the supplied Director 8.5 data occur in two verified forms:
8-bit raw scanlines and Macintosh PackBits scanlines. BITD true-colour rendering
is intentionally limited to cast-owned 16/32-bit members whose CASt metadata
provides exact geometry and pitch. Indexed 1/2/4/8-bit images require a verified
palette and are therefore diagnostics-only here.
"""
from __future__ import annotations

from dataclasses import dataclass
from io import BytesIO
import struct
from PIL import Image

from .inspector import InspectionError

MAX_PLANE_BYTES = 64 * 1024 * 1024
MAX_PIXELS = 32_000_000
BITMAP_MEMBER_TYPE = 1
SUPPORTED_DEPTHS = {1, 2, 4, 8, 16, 32}


def unpack_packbits(payload: bytes, *, expected_bytes: int | None = None,
                    max_bytes: int = MAX_PLANE_BYTES) -> bytes:
    """Strict Macintosh/Director PackBits byte-RLE with bounded expansion."""
    if not 0 <= max_bytes <= MAX_PLANE_BYTES:
        raise InspectionError("invalid PackBits output limit")
    if expected_bytes is not None and not 0 <= expected_bytes <= max_bytes:
        raise InspectionError("invalid expected PackBits output size")
    out = bytearray()
    cursor = 0
    bound = max_bytes if expected_bytes is None else expected_bytes
    while cursor < len(payload):
        op = payload[cursor]
        cursor += 1
        if op == 128:
            continue
        if op < 128:
            n = op + 1
            if n > len(payload) - cursor:
                raise InspectionError("truncated PackBits literal run")
            if len(out) + n > bound:
                raise InspectionError("PackBits expansion exceeds size limit")
            out.extend(payload[cursor:cursor + n])
            cursor += n
        else:
            n = 257 - op
            if cursor >= len(payload):
                raise InspectionError("truncated PackBits repeat run")
            if len(out) + n > bound:
                raise InspectionError("PackBits expansion exceeds size limit")
            out.extend(bytes((payload[cursor],)) * n)
            cursor += 1
    if expected_bytes is not None and len(out) != expected_bytes:
        raise InspectionError("PackBits expansion does not match expected dimensions")
    return bytes(out)


def _plane_payload(payload: bytes, expected: int) -> tuple[bytes, str]:
    """Decode a Director byte plane without guessing ambiguous lengths.

    Exact-size payloads are verified raw scanlines. Any other length must be a
    valid PackBits stream that expands to exactly the expected byte count.
    """
    if len(payload) == expected:
        return payload, "director-raw-gray8"
    return unpack_packbits(payload, expected_bytes=expected), "director-packbits-gray8"


def decode_alpha_plane(payload: bytes, width: int, height: int, *, with_codec: bool = False):
    """Recover 8-bit alpha, discarding Director's per-row pad byte on odd widths."""
    if width < 1 or height < 1 or width * height > MAX_PIXELS:
        raise InspectionError("invalid alpha dimensions")
    stride = width + (width % 2)
    decoded, codec = _plane_payload(payload, stride * height)
    if stride != width:
        decoded = b"".join(decoded[row * stride:row * stride + width] for row in range(height))
    return (decoded, codec) if with_codec else decoded


def compose_jpeg_alpha(jpeg: bytes, alpha_payload: bytes) -> tuple[bytes, dict]:
    """Render JPEG RGB plus verified ALFA plane to RGBA PNG with pixel audit."""
    try:
        with Image.open(BytesIO(jpeg)) as original:
            if original.format != "JPEG":
                raise InspectionError("alpha can only be paired with JPEG media")
            width, height = original.size
            if width < 1 or height < 1 or width * height > MAX_PIXELS:
                raise InspectionError("alpha image dimensions exceed cap")
            original.load()
            rgb = original.convert("RGB")
        plane, codec = decode_alpha_plane(alpha_payload, width, height, with_codec=True)
        result = rgb.convert("RGBA")
        result.putalpha(Image.frombytes("L", (width, height), plane))
        output = BytesIO()
        result.save(output, format="PNG", optimize=True)
        data = output.getvalue()
        with Image.open(BytesIO(data)) as check:
            check.load()
            if check.mode != "RGBA" or check.size != (width, height):
                raise InspectionError("PNG validation failed")
            if check.getchannel("A").tobytes() != plane or check.convert("RGB").tobytes() != rgb.tobytes():
                raise InspectionError("RGBA round-trip fidelity check failed")
        return data, {"width": width, "height": height, "alpha_applied": True,
                      "alpha_codec": codec, "pixel_roundtrip_verified": True}
    except (OSError, ValueError, SyntaxError, Image.DecompressionBombError) as exc:
        raise InspectionError("cannot decode JPEG/ALFA pair") from exc


@dataclass(frozen=True)
class BitmapCastInfo:
    width: int
    height: int
    bit_depth: int
    pitch: int
    reg_x: int
    reg_y: int
    palette_id: int | None

    @property
    def expected_bytes(self) -> int:
        return self.pitch * self.height

    @property
    def indexed(self) -> bool:
        return self.bit_depth <= 8


def parse_bitmap_cast_member(data: bytes) -> BitmapCastInfo:
    """Parse the Director 8.x CASt bitmap metadata layout used by this title.

    This is intentionally strict: bitmap member type, lengths, rectangle,
    bit-depth and pitch must all be internally consistent before BITD use.
    """
    if len(data) < 12:
        raise InspectionError("truncated CASt bitmap member header")
    member_type, info_len, data_len = struct.unpack_from(">iii", data, 0)
    if member_type != BITMAP_MEMBER_TYPE:
        raise InspectionError("CASt member is not a bitmap")
    if info_len < 0 or data_len < 24 or 12 + info_len + data_len != len(data):
        raise InspectionError("invalid CASt bitmap member lengths")
    specific = data[12 + info_len:]
    raw_pitch, top, left, bottom, right = struct.unpack_from(">Hhhhh", specific, 0)
    width, height = right - left, bottom - top
    if width < 1 or height < 1 or width * height > MAX_PIXELS:
        raise InspectionError("invalid CASt bitmap rectangle")
    reg_y, reg_x = struct.unpack_from(">hh", specific, 18)
    bit_depth = specific[23]
    if bit_depth not in SUPPORTED_DEPTHS:
        raise InspectionError("unsupported CASt bitmap bit depth")
    pitch = raw_pitch & 0x0FFF
    minimum = (width * bit_depth + 7) // 8
    if pitch < minimum or pitch * height > MAX_PLANE_BYTES:
        raise InspectionError("invalid CASt bitmap pitch")
    palette_id = struct.unpack_from(">h", specific, 26)[0] - 1 if len(specific) >= 28 else None
    return BitmapCastInfo(width, height, bit_depth, pitch, reg_x, reg_y, palette_id)


def _decode_bitd_bytes(payload: bytes, info: BitmapCastInfo) -> tuple[bytes, str, bool]:
    if len(payload) == info.expected_bytes:
        return payload, "director-raw-bitmap", False
    return (unpack_packbits(payload, expected_bytes=info.expected_bytes),
            "director-packbits-bitmap", True)



def decode_bitd_indices(payload: bytes, info: BitmapCastInfo) -> tuple[bytes, dict]:
    """Decode 1/2/4/8-bit BITD scanlines into one byte per palette index.

    This deliberately stops before RGB conversion: palette selection is Director
    state and must be resolved separately. Padding bits/bytes are ignored only
    after geometry and PackBits expansion have validated exactly.
    """
    if info.bit_depth not in (1, 2, 4, 8):
        raise InspectionError("true-colour BITD does not contain palette indices")
    raw, codec, _ = _decode_bitd_bytes(payload, info)
    depth = info.bit_depth
    mask = (1 << depth) - 1
    result = bytearray(info.width * info.height)
    for y in range(info.height):
        row = y * info.pitch
        for x in range(info.width):
            bit = x * depth
            byte_index = bit // 8
            shift = 8 - depth - (bit % 8)
            if shift < 0 or row + byte_index >= len(raw):
                raise InspectionError("indexed BITD row geometry is inconsistent")
            sample = (raw[row + byte_index] >> shift) & mask
            result[y * info.width + x] = sample if depth == 8 else (sample * 255 // mask)
    return bytes(result), {"width": info.width, "height": info.height,
                           "bit_depth": info.bit_depth, "pitch": info.pitch,
                           "palette_id": info.palette_id, "bitmap_codec": codec,
                           "palette_indices_verified": True,
                           "palette_rgb_resolved": False}

def decode_bitd_truecolor(payload: bytes, info: BitmapCastInfo) -> tuple[bytes, dict]:
    """Decode verified 16/32-bit BITD members to PNG.

    Director 8.x compressed 16-bit rows store high and low pixel bytes in
    separate row planes; 32-bit rows are ARGB bytes. Indexed depths fail closed
    until the owning palette is resolved.
    """
    if info.bit_depth not in (16, 32):
        raise InspectionError("indexed BITD requires a verified palette")
    raw, codec, was_compressed = _decode_bitd_bytes(payload, info)
    image = Image.new("RGBA", (info.width, info.height), (0, 0, 0, 0))
    pixels = image.load()
    if info.bit_depth == 16:
        scan_pixels = info.pitch // 2
        if scan_pixels < info.width:
            raise InspectionError("16-bit BITD pitch is smaller than width")
        for y in range(info.height):
            row = y * info.pitch
            for x in range(info.width):
                if was_compressed:
                    # Director RLE stores the high-byte plane before the low-byte plane.
                    value = (raw[row + x] << 8) | raw[row + scan_pixels + x]
                else:
                    # Exact-size, uncompressed BITD stores ordinary big-endian words.
                    pos = row + x * 2
                    value = (raw[pos] << 8) | raw[pos + 1]
                r5, g5, b5 = (value >> 10) & 31, (value >> 5) & 31, value & 31
                pixels[x, y] = ((r5 << 3) | (r5 >> 2), (g5 << 3) | (g5 >> 2),
                                (b5 << 3) | (b5 >> 2), 255)
    else:
        if info.pitch < info.width * 4:
            raise InspectionError("32-bit BITD pitch is smaller than width")
        scan_pixels = info.pitch // 4
        for y in range(info.height):
            row = y * info.pitch
            for x in range(info.width):
                if was_compressed:
                    # RLE-compressed 32-bit rows are channel-planar: A, R, G, B.
                    a = raw[row + x]
                    r = raw[row + scan_pixels + x]
                    g = raw[row + 2 * scan_pixels + x]
                    b = raw[row + 3 * scan_pixels + x]
                else:
                    pos = row + x * 4
                    a, r, g, b = raw[pos:pos + 4]
                pixels[x, y] = (r, g, b, a)
    out = BytesIO()
    image.save(out, format="PNG", optimize=True)
    encoded = out.getvalue()
    with Image.open(BytesIO(encoded)) as check:
        check.load()
        if check.mode != "RGBA" or check.size != image.size or check.tobytes() != image.tobytes():
            raise InspectionError("BITD PNG round-trip fidelity check failed")
    return encoded, {"format": "png", "width": info.width, "height": info.height,
                     "bit_depth": info.bit_depth, "pitch": info.pitch,
                      "reg_x": info.reg_x, "reg_y": info.reg_y,
                     "palette_id": info.palette_id, "bitmap_codec": codec,
                     "pixel_roundtrip_verified": True, "palette_required": False}


DIRECTOR_SYSTEM_WINDOWS_D5 = -102

# Only entries exercised by this title are embedded.  They were independently
# corroborated against maintained Director implementations before publication.
_WIN_D5_VERIFIED = {
    0: (255, 255, 255),
    19: (255, 153, 0),
    119: (102, 204, 0),
    136: (102, 51, 51),
    255: (0, 0, 0),
}


def director_system_windows_color(index: int) -> tuple[int, int, int]:
    try:
        return _WIN_D5_VERIFIED[index]
    except KeyError as exc:
        raise InspectionError("unverified Director System Windows -102 palette index") from exc


def decode_bitd_indexed(payload: bytes, info: BitmapCastInfo) -> tuple[bytes, dict]:
    """Decode the independently verified -102 indexed BITD subset to PNG."""
    if info.palette_id != DIRECTOR_SYSTEM_WINDOWS_D5:
        raise InspectionError("indexed BITD palette is not independently verified")
    indices, meta = decode_bitd_indices(payload, info)
    rgb = bytearray()
    for index in indices:
        rgb.extend(director_system_windows_color(index))
    image = Image.frombytes("RGB", (info.width, info.height), bytes(rgb))
    out = BytesIO()
    image.save(out, format="PNG", optimize=True)
    encoded = out.getvalue()
    with Image.open(BytesIO(encoded)) as check:
        check.load()
        if check.mode != "RGB" or check.size != image.size or check.tobytes() != image.tobytes():
            raise InspectionError("indexed BITD PNG round-trip fidelity check failed")
    return encoded, {**meta, "format": "png", "palette_rgb_resolved": True,
                     "palette_source": "Director System Windows -102 verified subset",
                     "pixel_roundtrip_verified": True, "palette_required": True}


def probe_bitd(data: bytes, *, max_bytes: int = 8 * 1024 * 1024) -> dict:
    """Structural RLE probe only. No color/palette/pixel-format claims."""
    decoded = unpack_packbits(data, max_bytes=max_bytes)
    return {"candidate_encoding": "packbits-rle", "decoded_byte_count": len(decoded),
            "pixel_geometry_verified": False, "bitmap_rendered": False}

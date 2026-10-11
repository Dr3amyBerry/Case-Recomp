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

# Entries on which two maintained Director implementations agree (dirplayer-rs
# SYSTEM_WIN_PALETTE and LibreShockwave systemWinColors); the original five were
# corroborated before. Indices 246, 249, 250 and 252-254 differ between them and
# stay unverified.
_WIN_D5_VERIFIED = {
    0: (255, 255, 255), 1: (0, 255, 255), 2: (255, 0, 255), 3: (0, 0, 255), 4: (255, 255, 0), 5: (0, 255, 0),
    6: (255, 0, 0), 7: (128, 128, 128), 8: (160, 160, 164), 9: (255, 251, 240), 10: (51, 51, 51), 11: (153, 102, 0),
    12: (51, 102, 51), 13: (0, 51, 153), 14: (204, 0, 255), 15: (136, 0, 0), 16: (255, 204, 102), 17: (255, 153, 204),
    18: (221, 221, 221), 19: (255, 153, 0), 20: (255, 102, 255), 21: (255, 102, 204), 22: (255, 102, 153), 23: (255, 102, 102),
    24: (255, 102, 51), 25: (255, 102, 0), 26: (255, 51, 255), 27: (255, 51, 204), 28: (255, 51, 153), 29: (255, 51, 102),
    30: (255, 51, 51), 31: (255, 51, 0), 32: (255, 0, 204), 33: (255, 0, 153), 34: (255, 0, 102), 35: (255, 0, 51),
    36: (204, 255, 255), 37: (204, 255, 204), 38: (204, 255, 153), 39: (204, 255, 102), 40: (204, 255, 51), 41: (204, 255, 0),
    42: (204, 204, 255), 43: (204, 204, 204), 44: (204, 204, 153), 45: (204, 204, 102), 46: (204, 204, 51), 47: (204, 204, 0),
    48: (204, 153, 255), 49: (204, 153, 204), 50: (204, 153, 153), 51: (204, 153, 102), 52: (204, 153, 51), 53: (204, 153, 0),
    54: (204, 102, 255), 55: (204, 102, 204), 56: (204, 102, 153), 57: (204, 102, 102), 58: (204, 102, 51), 59: (204, 102, 0),
    60: (204, 51, 255), 61: (204, 51, 204), 62: (204, 51, 153), 63: (204, 51, 102), 64: (204, 51, 51), 65: (204, 51, 0),
    66: (212, 8, 255), 67: (204, 0, 204), 68: (204, 0, 153), 69: (204, 0, 102), 70: (204, 0, 51), 71: (204, 0, 0),
    72: (153, 255, 255), 73: (153, 255, 204), 74: (153, 255, 153), 75: (153, 255, 102), 76: (153, 255, 51), 77: (153, 255, 0),
    78: (153, 204, 255), 79: (153, 204, 204), 80: (153, 204, 153), 81: (153, 204, 102), 82: (153, 204, 51), 83: (153, 204, 0),
    84: (153, 153, 255), 85: (153, 153, 204), 86: (153, 153, 153), 87: (153, 153, 102), 88: (153, 153, 51), 89: (153, 153, 0),
    90: (153, 102, 255), 91: (153, 102, 204), 92: (153, 102, 153), 93: (153, 102, 102), 94: (153, 102, 51), 95: (161, 102, 0),
    96: (153, 51, 255), 97: (153, 51, 204), 98: (153, 51, 153), 99: (153, 51, 102), 100: (153, 51, 51), 101: (153, 51, 0),
    102: (153, 0, 255), 103: (153, 0, 204), 104: (153, 0, 153), 105: (153, 0, 102), 106: (153, 0, 51), 107: (153, 0, 0),
    108: (102, 255, 255), 109: (102, 255, 204), 110: (102, 255, 153), 111: (102, 255, 102), 112: (102, 255, 51), 113: (102, 255, 0),
    114: (102, 204, 255), 115: (102, 204, 204), 116: (102, 204, 153), 117: (102, 204, 102), 118: (102, 204, 51), 119: (102, 204, 0),
    120: (102, 153, 255), 121: (102, 153, 204), 122: (102, 153, 153), 123: (102, 153, 102), 124: (102, 153, 51), 125: (102, 153, 0),
    126: (102, 102, 255), 127: (102, 102, 204), 128: (102, 102, 153), 129: (102, 102, 102), 130: (102, 102, 51), 131: (102, 102, 0),
    132: (102, 51, 255), 133: (102, 51, 204), 134: (102, 51, 153), 135: (102, 51, 102), 136: (102, 51, 51), 137: (102, 51, 0),
    138: (102, 0, 255), 139: (102, 0, 204), 140: (102, 0, 153), 141: (102, 0, 102), 142: (102, 0, 51), 143: (102, 0, 0),
    144: (51, 255, 255), 145: (51, 255, 204), 146: (51, 255, 153), 147: (51, 255, 102), 148: (51, 255, 51), 149: (51, 255, 0),
    150: (51, 204, 255), 151: (51, 204, 204), 152: (51, 204, 153), 153: (51, 204, 102), 154: (51, 204, 51), 155: (51, 204, 0),
    156: (51, 153, 255), 157: (51, 153, 204), 158: (51, 153, 153), 159: (51, 153, 102), 160: (51, 153, 51), 161: (51, 153, 0),
    162: (51, 102, 255), 163: (51, 102, 204), 164: (51, 102, 153), 165: (51, 102, 102), 166: (51, 110, 51), 167: (51, 102, 0),
    168: (51, 51, 255), 169: (51, 51, 204), 170: (51, 51, 153), 171: (51, 51, 102), 172: (51, 51, 59), 173: (51, 51, 0),
    174: (51, 0, 255), 175: (51, 0, 204), 176: (51, 0, 153), 177: (51, 0, 102), 178: (51, 0, 51), 179: (51, 0, 0),
    180: (0, 255, 204), 181: (0, 255, 153), 182: (0, 255, 102), 183: (0, 255, 51), 184: (0, 204, 255), 185: (0, 204, 204),
    186: (0, 204, 153), 187: (0, 204, 102), 188: (0, 204, 51), 189: (0, 204, 0), 190: (0, 153, 255), 191: (0, 153, 204),
    192: (0, 153, 153), 193: (0, 153, 102), 194: (0, 153, 51), 195: (0, 153, 0), 196: (0, 102, 255), 197: (0, 102, 204),
    198: (0, 102, 153), 199: (0, 102, 102), 200: (0, 102, 51), 201: (0, 102, 0), 202: (0, 51, 255), 203: (0, 51, 204),
    204: (0, 51, 161), 205: (0, 51, 102), 206: (0, 51, 51), 207: (0, 51, 0), 208: (0, 0, 204), 209: (0, 0, 153),
    210: (0, 0, 102), 211: (0, 0, 51), 212: (238, 0, 0), 213: (221, 0, 0), 214: (170, 0, 0), 215: (144, 0, 0),
    216: (119, 0, 0), 217: (85, 0, 0), 218: (68, 0, 0), 219: (34, 0, 0), 220: (17, 0, 0), 221: (0, 238, 0),
    222: (0, 221, 0), 223: (0, 170, 0), 224: (0, 136, 0), 225: (0, 119, 0), 226: (0, 85, 0), 227: (0, 68, 0),
    228: (0, 34, 0), 229: (0, 17, 0), 230: (0, 0, 238), 231: (0, 0, 221), 232: (0, 0, 170), 233: (0, 0, 136),
    234: (0, 0, 119), 235: (0, 0, 85), 236: (0, 0, 68), 237: (0, 0, 34), 238: (0, 0, 17), 239: (34, 34, 48),
    240: (255, 153, 153), 241: (255, 204, 255), 242: (153, 212, 255), 243: (153, 212, 153), 244: (255, 255, 153), 245: (240, 240, 240),
    247: (192, 220, 192), 248: (192, 192, 192), 251: (0, 0, 128), 255: (0, 0, 0),
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

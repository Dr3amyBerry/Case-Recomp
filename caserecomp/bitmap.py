"""Bounded Director grayscale alpha PackBits support and BITD diagnostics.

An ALFA plane is an 8-bit transparency plane, with rows padded to even width.
BITD is *not* automatically a viewable image: cast geometry, color depth,
palette, and row alignment must be resolved before any BITD render is trusted.
"""
from __future__ import annotations

from io import BytesIO
from PIL import Image

from .inspector import InspectionError

MAX_PLANE_BYTES = 64 * 1024 * 1024


def unpack_packbits(payload: bytes, *, expected_bytes: int | None = None,
                    max_bytes: int = MAX_PLANE_BYTES) -> bytes:
    """Strict Macintosh/Director PackBits byte-RLE, no implicit trailing data.

    00..7f = literal run of (n+1) bytes; 81..ff = next byte repeated
    (257-n) times; 80 = no-op. The decoder bounds output *before* append.
    If expected_bytes is set, both expansion and final length must match.
    """
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


def decode_alpha_plane(payload: bytes, width: int, height: int) -> bytes:
    """Recover 8-bit alpha, discarding Director's per-row pad byte on odd widths."""
    if width < 1 or height < 1 or width * height > 32_000_000:
        raise InspectionError("invalid alpha dimensions")
    stride = width + (width % 2)
    decoded = unpack_packbits(payload, expected_bytes=stride * height)
    if stride == width:
        return decoded
    return b"".join(decoded[row * stride:row * stride + width] for row in range(height))


def compose_jpeg_alpha(jpeg: bytes, compressed_alpha: bytes) -> tuple[bytes, dict]:
    """Render JPEG RGB plus verified ALFA plane to RGBA PNG, with a pixel audit.

    Raw image and mask dimensions must agree exactly; unknown formats fail.
    This is NOT validation against Director's ink, sprites or palette rules.
    """
    try:
        with Image.open(BytesIO(jpeg)) as original:
            if original.format != "JPEG":
                raise InspectionError("alpha can only be paired with JPEG media")
            width, height = original.size
            if width < 1 or height < 1 or width * height > 32_000_000:
                raise InspectionError("alpha image dimensions exceed cap")
            original.load()
            rgb = original.convert("RGB")
        plane = decode_alpha_plane(compressed_alpha, width, height)
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
                      "alpha_codec": "director-packbits-gray8", "pixel_roundtrip_verified": True}
    except (OSError, ValueError, SyntaxError, Image.DecompressionBombError) as exc:
        raise InspectionError("cannot decode JPEG/ALFA pair") from exc


def probe_bitd(data: bytes, *, max_bytes: int = 8 * 1024 * 1024) -> dict:
    """Structural RLE probe only. No color/palette/pixel-format claims."""
    decoded = unpack_packbits(data, max_bytes=max_bytes)
    return {"candidate_encoding": "packbits-rle", "decoded_byte_count": len(decoded),
            "pixel_geometry_verified": False, "bitmap_rendered": False}

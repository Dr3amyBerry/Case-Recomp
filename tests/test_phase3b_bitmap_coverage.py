"""Focused synthetic coverage for Phase 3B BITD/ALFA code; no game data."""
from __future__ import annotations

from io import BytesIO
from pathlib import Path
from tempfile import TemporaryDirectory
import struct
import unittest

from PIL import Image

from caserecomp.bitmap import (
    BitmapCastInfo, compose_jpeg_alpha, decode_alpha_plane, decode_bitd_indices,
    decode_bitd_truecolor, parse_bitmap_cast_member, unpack_packbits,
)
from caserecomp.inspector import InspectionError
from caserecomp.pipeline import convert_local, verify_export
from tests.test_pipeline import generate_jpeg, media_cast


def literal(data: bytes) -> bytes:
    return bytes((len(data) - 1,)) + data


def cast_bitmap(depth: int, width: int, height: int, pitch: int, palette_stored: int = -101) -> bytes:
    specific = bytearray(28)
    struct.pack_into(">Hhhhh", specific, 0, 0x8000 | pitch, 0, 0, height, width)
    struct.pack_into(">hh", specific, 18, height // 2, width // 2)
    specific[23] = depth
    struct.pack_into(">h", specific, 26, palette_stored)
    return struct.pack(">iii", 1, 0, len(specific)) + bytes(specific)


class Phase3BBitmapCoverage(unittest.TestCase):
    def test_raw_alpha_and_packbits_alpha(self):
        raw = bytes((0, 10, 255, 99, 25, 50, 75, 88))
        plane, codec = decode_alpha_plane(raw, 3, 2, with_codec=True)
        self.assertEqual(plane, bytes((0, 10, 255, 25, 50, 75)))
        self.assertEqual(codec, "director-raw-gray8")
        packed = literal(raw)
        plane2, codec2 = decode_alpha_plane(packed, 3, 2, with_codec=True)
        self.assertEqual(plane2, plane)
        self.assertEqual(codec2, "director-packbits-gray8")

    def test_bitmap_cast_metadata_and_rejections(self):
        info = parse_bitmap_cast_member(cast_bitmap(32, 2, 1, 8, -3))
        self.assertEqual(info, BitmapCastInfo(2, 1, 32, 8, 1, 0, -4))
        for bad in (
            b"",
            struct.pack(">iii", 2, 0, 24) + bytes(24),
            struct.pack(">iii", 1, -1, 24) + bytes(24),
        ):
            with self.subTest(bad=bad[:12]), self.assertRaises(InspectionError):
                parse_bitmap_cast_member(bad)

    def test_32bit_raw_and_16bit_compressed_pixels(self):
        info32 = BitmapCastInfo(2, 1, 32, 8, 0, 0, -102)
        png, meta = decode_bitd_truecolor(bytes((255, 10, 20, 30, 128, 40, 50, 60)), info32)
        self.assertTrue(meta["pixel_roundtrip_verified"])
        with Image.open(BytesIO(png)) as image:
            self.assertEqual(list(image.getdata()), [(10, 20, 30, 255), (40, 50, 60, 128)])

        info16 = BitmapCastInfo(2, 1, 16, 4, 0, 0, -102)
        png16, meta16 = decode_bitd_truecolor(literal(bytes((0x7C, 0x03, 0x00, 0xE0))), info16)
        self.assertEqual(meta16["bit_depth"], 16)
        with Image.open(BytesIO(png16)) as image:
            self.assertEqual(list(image.convert("RGB").getdata()), [(255, 0, 0), (0, 255, 0)])

    def test_indexed_indices_and_truecolor_refusal(self):
        info = BitmapCastInfo(3, 1, 4, 2, 0, 0, -102)
        indices, meta = decode_bitd_indices(bytes((0x12, 0x30)), info)
        self.assertEqual(indices, bytes((1, 2, 3)))
        self.assertTrue(meta["palette_indices_verified"])
        self.assertFalse(meta["palette_rgb_resolved"])
        with self.assertRaises(InspectionError):
            decode_bitd_indices(b"\0\0", BitmapCastInfo(1, 1, 16, 2, 0, 0, -102))
        with self.assertRaisesRegex(InspectionError, "palette"):
            decode_bitd_truecolor(b"x", BitmapCastInfo(1, 1, 8, 1, 0, 0, -102))

    def test_alpha_png_roundtrip(self):
        mask = literal(bytes((0, 50, 255, 0, 255, 0, 128, 0)))
        png, meta = compose_jpeg_alpha(generate_jpeg(), mask)
        self.assertTrue(meta["pixel_roundtrip_verified"])
        with Image.open(BytesIO(png)) as image:
            self.assertEqual(image.mode, "RGBA")
            self.assertEqual(image.getchannel("A").tobytes(), bytes((0, 50, 255, 255, 0, 128)))

    def test_pipeline_truecolor_and_indexed_palette_boundary(self):
        with TemporaryDirectory() as tmp:
            root = Path(tmp)
            src = root / "true.cct"
            out = root / "out"
            src.write_bytes(media_cast(
                assets=[("CASt", cast_bitmap(32, 2, 1, 8), 1),
                        ("BITD", bytes((255, 10, 20, 30, 128, 40, 50, 60)), 1)],
                keys=[(101, 100, "BITD")],
            ))
            result = convert_local(src, out, decode_bitd=True)
            self.assertEqual(result["asset_count"], 1)
            self.assertEqual(result["assets"][0]["bit_depth"], 32)
            self.assertEqual(result["assets"][0]["cast_member_id"], 100)
            self.assertEqual(verify_export(out)["verified_files"], 1)

        with TemporaryDirectory() as tmp:
            root = Path(tmp)
            src = root / "indexed.cct"
            out = root / "out"
            src.write_bytes(media_cast(
                assets=[("CASt", cast_bitmap(8, 2, 1, 2), 1), ("BITD", b"\0\1", 1)],
                keys=[(101, 100, "BITD")],
            ))
            result = convert_local(src, out, decode_bitd=True)
            self.assertEqual(result["asset_count"], 0)
            self.assertEqual(result["skipped"]["indexed_BITD_requires_palette"], 1)

    def test_packbits_bounds(self):
        with self.assertRaises(InspectionError):
            unpack_packbits(b"\x01A")
        with self.assertRaises(InspectionError):
            unpack_packbits(b"\xff")
        with self.assertRaises(InspectionError):
            unpack_packbits(b"\x00A", expected_bytes=2)

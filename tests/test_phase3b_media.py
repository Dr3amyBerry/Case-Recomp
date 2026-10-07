"""Source-only synthetic ALFA, BITD, and SWA fixture tests; no game data."""
from __future__ import annotations

from io import BytesIO
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest import TestCase, skipUnless
from unittest.mock import patch
import shutil
import subprocess

from PIL import Image

from caserecomp.audio import decode_swa, resolve_ffmpeg, swa_encoded_resource
from caserecomp.bitmap import compose_jpeg_alpha, decode_alpha_plane, probe_bitd, unpack_packbits
from caserecomp.director import DirectorArchive
from caserecomp.inspector import InspectionError
from caserecomp.pipeline import convert_local, verify_export
from tests.test_pipeline import media_cast, generate_jpeg


def packbits_literal(data):
    return bytes((len(data)-1,)) + data


class PackBitsTests(TestCase):
    def test_literal_repeat_and_noop(self):
        self.assertEqual(unpack_packbits(b"\x01AB\xfdZ\x80", expected_bytes=6), b"ABZZZZ")
        self.assertEqual(unpack_packbits(b"", expected_bytes=0), b"")
        self.assertEqual(unpack_packbits(b"\x80\x80", expected_bytes=0), b"")

    def test_decode_odd_row_padding(self):
        payload = packbits_literal(bytes((0, 127, 255, 99, 42, 88, 43, 11)))
        self.assertEqual(decode_alpha_plane(payload, 3, 2), bytes((0, 127, 255, 42, 88, 43)))

    def test_truncated_runs_and_mismatched_lengths(self):
        for payload in (b"\x01A", b"\xff"):
            with self.subTest(payload=payload), self.assertRaises(InspectionError):
                unpack_packbits(payload)
        with self.assertRaisesRegex(InspectionError, "match expected"):
            unpack_packbits(b"\x00A", expected_bytes=2)
        with self.assertRaisesRegex(InspectionError, "exceeds"):
            unpack_packbits(b"\x00A", expected_bytes=0)
        with self.assertRaisesRegex(InspectionError, "exceeds"):
            unpack_packbits(b"\xffX", expected_bytes=1)

    def test_size_limits(self):
        for limit in (-1, 999999999):
            with self.assertRaises(InspectionError):
                unpack_packbits(b"", max_bytes=limit)
        with self.assertRaises(InspectionError):
            unpack_packbits(b"", expected_bytes=-1)
        with self.assertRaises(InspectionError):
            unpack_packbits(b"", expected_bytes=200, max_bytes=100)
        with self.assertRaises(InspectionError):
            decode_alpha_plane(b"", -1, 10)
        with self.assertRaises(InspectionError):
            decode_alpha_plane(b"", 6400, 6000)

    def test_png_composition_pixel_fidelity(self):
        mask = packbits_literal(bytes((0, 50, 255, 0, 255, 0, 128, 0)))
        encoded, metadata = compose_jpeg_alpha(generate_jpeg(), mask)
        self.assertTrue(metadata["pixel_roundtrip_verified"])
        self.assertEqual(metadata["alpha_codec"], "director-packbits-gray8")
        with Image.open(BytesIO(encoded)) as im:
            self.assertEqual(im.mode, "RGBA")
            self.assertEqual(list(im.getchannel("A").getdata()), [0, 50, 255, 255, 0, 128])

    def test_invalid_jpeg_or_alpha(self):
        with self.assertRaises(InspectionError):
            compose_jpeg_alpha(b"unknown", packbits_literal(b"1234"))
        with self.assertRaises(InspectionError):
            compose_jpeg_alpha(generate_jpeg(), b"\xff")

    def test_bitd_probe_not_a_pixel_image(self):
        self.assertEqual(probe_bitd(b"\xfdA")["decoded_byte_count"], 4)
        self.assertFalse(probe_bitd(b"\xfdA")["bitmap_rendered"])
        with self.assertRaises(InspectionError):
            probe_bitd(b"\xfdA", max_bytes=3)


class AlphaConversionTests(TestCase):
    def setUp(self):
        self.tmp = TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self.src = self.root / "synthetic.cct"
        self.out = self.root / "private"

    def tearDown(self):
        self.tmp.cleanup()

    def make_cast(self, mask):
        self.src.write_bytes(media_cast(
            assets=[("ediM", generate_jpeg(), 1), ("ALFA", mask, 1), ("CASt", b"synthetic", 1)],
            keys=[(100, 102, "ediM"), (101, 102, "ALFA")]))

    def test_strict_rgba_manifest_and_verification(self):
        self.make_cast(packbits_literal(bytes((0, 25, 255, 0, 255, 0, 128, 0))))
        result = convert_local(self.src, self.out, image_format="png", alpha_mode="strict")
        self.assertEqual(result["asset_count"], 1)
        self.assertTrue(result["assets"][0]["alpha_applied"])
        self.assertEqual(result["assets"][0]["alpha_resource_id"], 101)
        self.assertEqual(verify_export(self.out)["verified_files"], 1)

    def test_best_effort_falls_back_with_reason(self):
        self.make_cast(b"not packbits")
        result = convert_local(self.src, self.out, image_format="png", alpha_mode="best-effort")
        self.assertEqual(result["skipped"]["invalid_ALFA"], 1)
        self.assertFalse(result["assets"][0]["alpha_applied"])
        self.assertEqual(result["assets"][0]["alpha_decode_status"], "unsupported-mask")
        self.assertEqual(verify_export(self.out)["verified_files"], 1)

    def test_strict_invalid_mask_rolls_back(self):
        self.make_cast(b"not packbits")
        with self.assertRaises(InspectionError):
            convert_local(self.src, self.out, image_format="png", alpha_mode="strict")
        self.assertFalse(self.out.exists())

    def test_off_preserves_unmodified_workflow(self):
        self.make_cast(b"not packbits")
        output = convert_local(self.src, self.out, image_format="png")
        self.assertFalse(output["assets"][0]["alpha_applied"])
        self.assertNotIn("invalid_ALFA", output["skipped"])

    def test_invalid_alpha_mode_fails_before_output(self):
        self.make_cast(b"test")
        for options in ({"alpha_mode": "guess"}, {"image_format": "jpg", "alpha_mode": "strict"}):
            with self.assertRaises(InspectionError):
                convert_local(self.src, self.out, **options)
        self.assertFalse(self.out.exists())

    def test_strict_unlinked_image_keeps_opaque(self):
        self.src.write_bytes(media_cast(assets=[("ediM", generate_jpeg(), 1)], keys=[]))
        result = convert_local(self.src, self.out, image_format="png", alpha_mode="strict")
        self.assertFalse(result["assets"][0]["alpha_applied"])


class SwaDecodeTests(TestCase):
    def test_reject_non_swa_chunk(self):
        archive = DirectorArchive(media_cast(assets=[("snd ", b"not-swa", 1)]))
        with self.assertRaises(InspectionError):
            swa_encoded_resource(archive, archive.entries[100])
        archive = DirectorArchive(media_cast(assets=[("snd ", b"x" * 40, 2)]))
        self.assertEqual(len(swa_encoded_resource(archive, archive.entries[100])), 40)

    def test_bad_binary_or_missing_decoder(self):
        with TemporaryDirectory() as directory:
            root = Path(directory)
            with self.assertRaises(InspectionError):
                resolve_ffmpeg(root / "missing")
            f = root / "ffmpeg"
            f.write_text("stub")
            with self.assertRaises(InspectionError):
                resolve_ffmpeg(f)
            f.chmod(0o700)
            self.assertEqual(resolve_ffmpeg(f), f)
            link = root / "link"
            link.symlink_to(f)
            with self.assertRaises(InspectionError):
                resolve_ffmpeg(link)

    def test_reject_small_source_and_non_audio(self):
        with self.assertRaises(InspectionError):
            decode_swa(b"x", Path("/bin/false"))
        with self.assertRaises(InspectionError):
            decode_swa(b"not an MPEG stream" * 50, Path("/bin/false"))

    @skipUnless(shutil.which("ffmpeg"), "FFmpeg not installed in test environment")
    def test_synthetic_ffmpeg_roundtrip(self):
        original = subprocess.run([
            "ffmpeg", "-hide_banner", "-loglevel", "error", "-f", "lavfi",
            "-i", "sine=frequency=500:sample_rate=22050:duration=0.25", "-ac", "1",
            "-c:a", "libmp3lame", "-f", "mp3", "pipe:1",
        ], check=True, capture_output=True, timeout=10).stdout
        source = b"\x00\x02\x00\x00" + original
        wav, meta = decode_swa(source, Path(shutil.which("ffmpeg")))
        self.assertEqual(wav[:4], b"RIFF")
        self.assertGreater(meta["frames"], 0)
        self.assertEqual(meta["rate"], 22050)
        self.assertFalse(meta["historical_swa_equivalence_verified"])
        with TemporaryDirectory() as d:
            src = Path(d) / "sound.cct"
            out = Path(d) / "decoded"
            src.write_bytes(media_cast(assets=[("snd ", source, 2)]))
            result = convert_local(src, out, decode_swa=True, ffmpeg=Path(shutil.which("ffmpeg")))
            self.assertEqual(result["asset_count"], 1)
            self.assertEqual(result["assets"][0]["format"], "swa-mpeg-to-wav-ffmpeg")
            self.assertEqual(verify_export(out)["verified_files"], 1)

    def test_failing_decoder_rolls_back(self):
        with TemporaryDirectory() as d:
            root = Path(d)
            tool = root / "fake_ffmpeg"
            tool.write_text("#!/bin/sh\nexit 2\n")
            tool.chmod(0o700)
            input_file = root / "sound.cct"
            out = root / "decoded"
            input_file.write_bytes(media_cast(assets=[("snd ", b"BAD" * 80, 2)]))
            with self.assertRaises(InspectionError):
                convert_local(input_file, out, decode_swa=True, ffmpeg=tool)
            self.assertFalse(out.exists())

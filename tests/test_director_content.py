"""Synthetic archives for the private Director content package."""
import io
import json
import struct
import tempfile
import unittest
import zipfile
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

from PIL import Image

from caserecomp.__main__ import main
from caserecomp.director_content import build_director_content, cast_key
from caserecomp.inspector import InspectionError
from tests.test_movie_bundle import bitmap_specific, member, xtra_specific
from tests.test_phase3b_media import packbits_literal
from tests.test_pipeline import generate_jpeg


def jpeg(width=4, height=2) -> bytes:
    out = io.BytesIO()
    Image.new("RGB", (width, height), (200, 10, 10)).save(out, format="JPEG")
    return out.getvalue()


SWF = b"FWS\x06" + struct.pack("<I", 12) + b"\x00\x00\x00\x00"


class FakeRelations:
    def __init__(self, archive):
        self.archive = archive

    def resources_for(self, owner, tag=None):
        return self.archive.links.get(owner, {}).get(tag, ())


def archive(kind, resources, links, codec_unsupported=()):
    entries = {rid: SimpleNamespace(tag=tag, compression_index=1 if rid in codec_unsupported else 0)
               for rid, (tag, _) in resources.items()}
    return SimpleNamespace(kind=kind, entries=entries, links=links,
                           get_resource=lambda rid: resources[rid][1],
                           _codec_name=lambda index: "unsupported" if index else "zlib")


def movie_archive():
    return archive("movie", {
        1: ("CAS*", struct.pack(">IIII", 11, 12, 13, 14)),
        11: ("CASt", member(1, b"photo", bitmap_specific(0, 0, 4, 2, 2, 1))),
        12: ("CASt", member(15, b"dialog", xtra_specific(b"flash"))),
        13: ("CASt", member(6, b"click", b"")),
        14: ("CASt", member(6, b"theme", b"")),
        21: ("ediM", jpeg()), 22: ("XMED", b"\x00\x01junk" + SWF + b"tail"), 23: ("ediM", b"ID3\x03mp3"),
        24: ("snd ", b"swa-bytes"),
    }, {11: {"ediM": (21,)}, 12: {"XMED": (22,)}, 13: {"ediM": (23,)}, 14: {"snd ": (24,)}}, codec_unsupported=(24,))


def extra_archive():
    """Alpha, BITD and WAV members plus three undecodable ones."""
    return archive("cast", {
        1: ("CAS*", struct.pack(">IIIIII", 11, 12, 13, 14, 15, 16)),
        11: ("CASt", member(1, b"masked", bitmap_specific(0, 0, 3, 2, 1, 1))),
        12: ("CASt", member(1, b"raw", bitmap_specific(0, 0, 4, 2, 2, 1, depth=16))),
        13: ("CASt", member(6, b"beep", b"")),
        14: ("CASt", member(15, b"broken", xtra_specific(b"flash"))),
        15: ("CASt", member(1, b"notjpeg", bitmap_specific(0, 0, 4, 2, 2, 1))),
        16: ("CASt", member(6, b"noise", b"")),
        21: ("ediM", generate_jpeg()), 22: ("ALFA", packbits_literal(bytes((0, 50, 255, 0, 255, 0, 128, 0)))),
        23: ("BITD", bytes(320)), 24: ("snd ", b"RIFF....WAVE"), 25: ("XMED", b"no swf here"),
        26: ("ediM", b"GIF89a"), 27: ("ediM", b"\x00\x01"),
    }, {11: {"ediM": (21,), "ALFA": (22,)}, 12: {"BITD": (23,)}, 13: {"snd ": (24,)}, 14: {"XMED": (25,)},
        15: {"ediM": (26,)}, 16: {"ediM": (27,)}})


def cast_archive():
    return archive("cast", {1: ("CAS*", struct.pack(">I", 11)), 11: ("CASt", member(1, b"tile", bitmap_specific(0, 0, 4, 2, 2, 1))),
                            21: ("ediM", jpeg())}, {11: {"ediM": (21,)}})


class DirectorContentTests(unittest.TestCase):
    def build(self, root: Path, **kwargs):
        archives = {"movie.dir": movie_archive(), "01.cct": cast_archive()}
        with patch("caserecomp.director_content.open_archive", side_effect=lambda p: (archives[p.name], 0)), \
                patch("caserecomp.director_content.CastRelationships", FakeRelations), \
                patch("caserecomp.movie_bundle.CastRelationships", FakeRelations), \
                patch("caserecomp.director_content.build_movie_bundle", return_value={"format": "case-recomp-movie-bundle"}), \
                patch("caserecomp.director_content.build_lingo_bundle", return_value={"format": "case-recomp-lingo-bundle"}), \
                patch("caserecomp.director_content.read_local", return_value=b"source"), \
                patch("caserecomp.audio.swa_encoded_resource", return_value=b"not mpeg audio at all"):
            return build_director_content(Path("movie.dir"), [Path("01.cct")], root / "out.zip", **kwargs)

    def test_package_holds_bundles_and_member_media(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            manifest = self.build(root)
            self.assertEqual((manifest["format"], manifest["version"]), ("case-recomp-director-content", 1))
            paths = [e["path"] for e in manifest["entries"]]
            self.assertEqual(paths, ["movie.json", "lingo.json", "media/internal/1.png", "media/internal/2.swf",
                                     "media/internal/3.mp3", "media/01/1.png"])
            self.assertEqual(manifest["counts"], {"bitmap": 2, "bundle": 2, "flash": 1, "sound": 1})
            # Without FFmpeg an SWA keeps its MPEG frames; this fake one holds none, so it is skipped.
            self.assertEqual(manifest["skipped"], {"sound: SWA stream holds no MPEG audio frames": 1})
            self.assertEqual(set(manifest["source_sha256"]), {"internal", "01"})
            with zipfile.ZipFile(root / "out.zip") as zf:
                self.assertEqual(zf.read("media/internal/2.swf"), SWF)
                with Image.open(io.BytesIO(zf.read("media/internal/1.png"))) as png:
                    self.assertEqual(png.size, (4, 2))
                listed = json.loads(zf.read("manifest.json"))
                self.assertEqual(listed["entries"], manifest["entries"])
            with self.assertRaises(InspectionError):
                self.build(root)

    def test_cover_names_a_packaged_bitmap(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self.assertNotIn("cover", self.build(root))
            (root / "out.zip").unlink()
            self.assertEqual(self.build(root, cover="01:1")["cover"], "media/01/1.png")
            with zipfile.ZipFile(root / "out.zip") as zf:
                self.assertEqual(json.loads(zf.read("manifest.json"))["cover"], "media/01/1.png")
            (root / "out.zip").unlink()
            for bad in ("2", "x", "0"):  # 2 is Flash, not a bitmap
                with self.assertRaises(InspectionError):
                    self.build(root, cover=bad)
            self.assertFalse((root / "out.zip").exists())

    def test_alpha_bitd_wav_and_skipped_members(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            with patch("caserecomp.director_content.open_archive", return_value=(extra_archive(), 0)),                     patch("caserecomp.director_content.CastRelationships", FakeRelations),                     patch("caserecomp.movie_bundle.CastRelationships", FakeRelations),                     patch("caserecomp.director_content.build_movie_bundle", return_value={}),                     patch("caserecomp.director_content.build_lingo_bundle", return_value={}),                     patch("caserecomp.director_content.read_local", return_value=b"source"):
                manifest = build_director_content(Path("movie.dir"), [], root / "x.zip")
            self.assertEqual([e["path"] for e in manifest["entries"][2:]],
                             ["media/internal/1.png", "media/internal/2.png", "media/internal/3.wav"])
            self.assertEqual(manifest["skipped"], {"bitmap: bitmap ediM is not JPEG": 1,
                                                   "flash: Flash member holds no SWF": 1,
                                                   "sound: unrecognised sound media": 1})
            with zipfile.ZipFile(root / "x.zip") as zf, Image.open(io.BytesIO(zf.read("media/internal/1.png"))) as png:
                self.assertEqual(png.mode, "RGBA")
                self.assertEqual(list(png.getchannel("A").getdata()), [0, 50, 255, 255, 0, 128])

    def test_cast_keys_and_cli(self):
        self.assertEqual(cast_key(""), "internal")
        self.assertEqual(cast_key("Dat12.CCT"), "dat12")
        manifest = {"format": "case-recomp-director-content", "entries": [], "counts": {}, "skipped": {}}
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = root / "movie.dir"; source.write_bytes(b"x")
            with patch("caserecomp.director_content.build_director_content", return_value=manifest) as build:
                self.assertEqual(main(["director-content", str(source), "--output", str(root / "o.zip")]), 0)
                self.assertEqual(build.call_args[0][1], [])
                self.assertIsNone(build.call_args.kwargs["ffmpeg"])
                self.assertEqual(main(["director-content", str(source), "--cover", "7",
                                       "--output", str(root / "c.zip")]), 0)
                self.assertEqual(build.call_args.kwargs["cover"], "7")


if __name__ == "__main__":
    unittest.main()

"""Synthetic fixtures for the private Director movie bundle."""
import json
import struct
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

from caserecomp.__main__ import main
from caserecomp.inspector import InspectionError
from caserecomp.movie_bundle import (build_movie_bundle, external_cast_files, info_items, parse_cast_list,
                                     parse_xmed_text)
from tests.test_score import labels, movie_config, score_fixture


def pascal(text: bytes) -> bytes:
    return bytes([len(text)]) + text


def info_list(*items: bytes, script_number: int = 0) -> bytes:
    header = bytearray(20)
    struct.pack_into(">I", header, 0, 20)
    struct.pack_into(">i", header, 16, script_number)
    offsets, cursor = [], 0
    for item in items:
        offsets.append(cursor); cursor += len(item)
    table = struct.pack(">H", len(items)) + b"".join(struct.pack(">I", o) for o in offsets)
    return bytes(header) + table + struct.pack(">I", cursor) + b"".join(items)


def member(member_type: int, name: bytes, specific: bytes, script_number: int = 0) -> bytes:
    info = info_list(b"", pascal(name), script_number=script_number)
    return struct.pack(">iii", member_type, len(info), len(specific)) + info + specific


def bitmap_specific(left=10, top=20, right=50, bottom=80, reg_x=30, reg_y=50, depth=32) -> bytes:
    data = bytearray(28)
    struct.pack_into(">Hhhhh", data, 0, 0x8000 | 160, top, left, bottom, right)
    struct.pack_into(">hh", data, 18, reg_y, reg_x)
    data[23] = depth
    return bytes(data)


def shape_specific() -> bytes:
    return struct.pack(">Hhhhh", 3, 0, 0, 20, 200) + struct.pack(">H", 1) + bytes([255, 0, 1, 2, 5])


def xtra_specific(kind: bytes) -> bytes:
    return struct.pack(">I", len(kind)) + kind + bytes(8)


def xmed_section(section: int, body: bytes) -> bytes:
    return b"\x03" + b"%04X%08X%08X" % (section, len(body), 0) + body


def xmed(text: bytes, fonts=(b"Arial",)) -> bytes:
    font_table = b"".join(b"\x0040," + pascal(font) + bytes(0x40 - 1 - len(font)) for font in fonts)
    return (b"FFFF0000000600040001" + xmed_section(0, b"00") + xmed_section(2, b"\x00%X," % len(text) + text)
            + xmed_section(8, font_table) + b"\x00\x7f\xff")


def cast_list(*libs: tuple[bytes, bytes, int, int]) -> bytes:
    items = [b""]
    for name, path, low, high in libs:
        items += [pascal(name), pascal(path), b"\x00\x00", struct.pack(">HHi", low, high, 1024)]
    offsets, cursor = [], 0
    for item in items:
        offsets.append(cursor); cursor += len(item)
    header = struct.pack(">IHHHH", 12, 0, len(libs), 4, 0)
    return (header + struct.pack(">H", len(items)) + b"".join(struct.pack(">I", o) for o in offsets)
            + struct.pack(">I", cursor) + b"".join(items))


class FakeRelations:
    links = {11: {"ediM": (91,), "ALFA": (92,)}, 13: {"XMED": (93,)}}

    def __init__(self, archive):
        pass

    def resources_for(self, owner, tag=None):
        return self.links.get(owner, {}).get(tag, ())


def fake_archive(kind: str, resources: dict[int, tuple[str, bytes]]):
    return SimpleNamespace(kind=kind, entries={rid: SimpleNamespace(tag=tag) for rid, (tag, _) in resources.items()},
                           get_resource=lambda rid: resources[rid][1])


class ParserTests(unittest.TestCase):
    def test_info_items_and_cast_list(self):
        self.assertEqual(info_items(info_list(b"", pascal(b"logo"))), [b"", b"\x04logo"])
        self.assertEqual(info_items(b""), [])
        libs = parse_cast_list(cast_list((b"Internal", b"", 1, 40), (b"dat1", b"C:\\x\\empty.cst", 1, 0)))
        self.assertEqual(libs[0], {"number": 1, "name": "Internal", "file_path": "", "min_member": 1, "max_member": 40})
        self.assertEqual((libs[1]["number"], libs[1]["name"], libs[1]["file_path"]), (2, "dat1", "C:\\x\\empty.cst"))

    def test_xmed_text_and_fonts(self):
        parsed = parse_xmed_text(xmed(b"Caf\xe9\rOpen", fonts=(b"Arial", b"Courier")))
        self.assertEqual(parsed, {"text": "Café\nOpen", "fonts": ["Arial", "Courier"]})
        self.assertEqual(parse_xmed_text(b"FFFF"), {"text": "", "fonts": []})

    def test_rejects_malformed_tables(self):
        bad = [
            lambda: parse_cast_list(b"x"),
            lambda: parse_cast_list(struct.pack(">IHHHH", 12, 0, 1, 0, 0) + bytes(8)),
            lambda: parse_cast_list(cast_list((b"a", b"b", 1, 1))[:-3]),
            lambda: parse_xmed_text(xmed_section(2, b"9,ab")),
            lambda: parse_xmed_text(xmed_section(2, b"\x00FF,ab")),
            lambda: info_items(struct.pack(">I", 4) + struct.pack(">H", 9)),
        ]
        for fn in bad:
            with self.subTest(fn=fn), self.assertRaises(InspectionError):
                fn()


class BundleTests(unittest.TestCase):
    def movie(self):
        return fake_archive("movie", {
            1: ("DRCF", movie_config()), 2: ("VWLB", labels()), 3: ("VWSC", score_fixture()),
            4: ("MCsL", cast_list((b"Internal", b"", 1, 3), (b"01", b"empty.cst", 1, 0))),
            5: ("CAS*", struct.pack(">III", 11, 12, 13)),
            11: ("CASt", member(1, b"logo", bitmap_specific(), script_number=4)),
            12: ("CASt", member(8, b"box", shape_specific())),
            13: ("CASt", member(15, b"title", xtra_specific(b"text"))),
            93: ("XMED", xmed(b"Hello")),
        })

    def cast(self):
        return fake_archive("cast", {1: ("CAS*", struct.pack(">II", 0, 11)),
                                     11: ("CASt", member(6, b"click", b""))})

    def build(self, movie, cast=None):
        archives = {"movie.dir": movie, "01.cct": cast or self.cast()}
        with patch("caserecomp.movie_bundle.open_archive", side_effect=lambda p: (archives[p.name], 0)), \
                patch("caserecomp.movie_bundle.CastRelationships", FakeRelations), \
                patch("caserecomp.movie_bundle.read_local", return_value=b"bytes"):
            return build_movie_bundle(Path("movie.dir"), [Path("01.cct")])

    def test_bundle_has_stage_labels_cast_and_score(self):
        bundle = self.build(self.movie())
        self.assertEqual((bundle["format"], bundle["version"]), ("case-recomp-movie-bundle", 1))
        self.assertEqual(bundle["stage"], {"width": 800, "height": 600, "tempo": 30})
        self.assertEqual(bundle["labels"], [{"frame": 1, "name": "first"}, {"frame": 2, "name": "second"}])
        self.assertEqual([lib["name"] for lib in bundle["cast_libs"]], ["Internal", "01"])
        logo, box, title = bundle["internal_members"]
        self.assertEqual(logo, {"number": 1, "name": "logo", "type": "bitmap", "script_number": 4,
                                "media": {"ediM": 91, "ALFA": 92}, "width": 40, "height": 60,
                                "reg_x": 20, "reg_y": 30, "bit_depth": 32})
        self.assertEqual((box["shape"], box["width"], box["height"], box["fore_color"], box["filled"]),
                         ("oval", 200, 20, 255, True))
        self.assertEqual((title["xtra"], title["text"], title["fonts"]), ("text", "Hello", ["Arial"]))
        self.assertEqual(bundle["external_casts"][0]["file"], "01.cct")
        self.assertEqual(bundle["external_casts"][0]["members"], [{"number": 2, "name": "click", "type": "sound", "media": {"ediM": 91, "ALFA": 92}}])
        score = bundle["score"]
        self.assertEqual((score["frame_count"], len(score["sprites"])), (2, 1))
        self.assertEqual(score["sprites"][0]["member"], 2)
        self.assertEqual(score["behaviors"], [{"start": 1, "end": 2, "channel": 6, "cast_lib": 1, "member": 2,
                                               "parameters": "[x:1]"}])

    def test_bundle_rejects_wrong_archive_kinds(self):
        with self.assertRaises(InspectionError):
            self.build(self.cast())
        with self.assertRaises(InspectionError):
            self.build(self.movie(), cast=self.movie())
        no_score = self.movie(); del no_score.entries[3]
        with self.assertRaises(InspectionError):
            self.build(no_score)

    def test_external_cast_files_and_cli(self):
        bundle = {"format": "case-recomp-movie-bundle", "labels": [], "external_casts": [],
                  "score": {"sprites": []}}
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "data").mkdir()
            for name in ("02.cct", "01.CXT", "notes.txt"):
                (root / "data" / name).write_bytes(b"x")
            self.assertEqual([p.name for p in external_cast_files(root / "data")], ["01.CXT", "02.cct"])
            with self.assertRaises(InspectionError):
                external_cast_files(root / "data", limit=1)
            source = root / "movie.dir"; source.write_bytes(b"x")
            out = root / "bundle.json"
            with patch("caserecomp.movie_bundle.build_movie_bundle", return_value=bundle) as build:
                self.assertEqual(main(["movie-bundle", str(source), "--cast-dir", str(root / "data"),
                                       "--output", str(out)]), 0)
                self.assertEqual([p.name for p in build.call_args[0][1]], ["01.CXT", "02.cct"])
                self.assertEqual(json.loads(out.read_text(encoding="utf-8")), bundle)
                self.assertEqual(main(["movie-bundle", str(source), "--output", str(out)]), 2)


if __name__ == "__main__":
    unittest.main()

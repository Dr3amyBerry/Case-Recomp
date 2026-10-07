"""Synthetic Afterburner fixtures: no proprietary files, media or scripts."""

import json
import struct
import os
import tempfile
import unittest
import zlib
from pathlib import Path

from caserecomp.__main__ import main
from caserecomp.director import (
    DirectorArchive,
    _bounded_zlib,
    _varint,
    embedded_movie,
    extract_movie,
    extract_resources,
)
from caserecomp.inspector import InspectionError


def varint(value):
    if not 0 <= value <= 0xffffffff:
        raise ValueError(value)
    digits = [value & 0x7f]
    value >>= 7
    while value:
        digits.insert(0, (value & 0x7f) | 0x80)
        value >>= 7
    return bytes(digits)


def file_chunk(tag, payload):
    return tag + varint(len(payload)) + payload


def afterburner_fixture(kind=b"CDGF", broken_ils=False):
    codec_names = b"Macromedia ziplib compression\0Macromedia null Compressor\0"
    codecs = struct.pack("<H", 2) + bytes(32) + codec_names
    fver = varint(0x501) + varint(1) + varint(0x73A) + b"\x09" + b"8.5.1#104"
    ils_entries = {3: ("KEY*", b"private-map"), 4: ("Lscr", b"synthetic Lingo test bytes")}
    ils_bytes = b"".join(varint(resource_id) + raw for resource_id, (_, raw) in ils_entries.items())
    if broken_ils:
        ils_bytes = ils_bytes[:-1]
    ils_zip = zlib.compress(ils_bytes)
    versions = b"some-version-data"
    version_zip = zlib.compress(versions)
    raw_media = b"\x01\x02not-a-real-image"
    resources = [
        (2, 0, len(ils_zip), len(ils_bytes), 0, "ILS "),
        (3, 0xffffffff, len(ils_entries[3][1]), len(ils_entries[3][1]), 0, "KEY*"),
        (4, 0xffffffff, len(ils_entries[4][1]), len(ils_entries[4][1]), 0, "Lscr"),
        (5, len(ils_zip), len(version_zip), len(versions), 0, "VERS"),
        (6, len(ils_zip) + len(version_zip), len(raw_media), len(raw_media), 1, "BITD"),
    ]
    mapping = varint(1) + varint(12) + varint(len(resources))
    for ident, offset, clen, ulen, codec, tag in resources:
        mapping += b"".join(varint(x) for x in (ident, offset, clen, ulen, codec)) + tag[::-1].encode("ascii")
    abmp = varint(0) + varint(len(mapping)) + zlib.compress(mapping)
    chunk_data = file_chunk(b"revF", fver) + file_chunk(b"rdcF", zlib.compress(codecs)) + file_chunk(b"PMBA", abmp) + b"IEGF\x00" + ils_zip + version_zip + raw_media
    body = kind + chunk_data
    return b"XFIR" + struct.pack("<I", len(body)) + body


def pe_with_movie(movie):
    data = bytearray(0x200)
    data[:2] = b"MZ"
    struct.pack_into("<I", data, 0x3c, 0x80)
    data[0x80:0x84] = b"PE\x00\x00"
    struct.pack_into("<HH", data, 0x84, 0x14c, 1)
    struct.pack_into("<H", data, 0x94, 0xe0)
    struct.pack_into("<H", data, 0x98, 0x10b)
    sh = 0x80 + 24 + 0xe0
    data[sh:sh + 5] = b".text"
    struct.pack_into("<II", data, sh + 16, 16, 0x1f0)
    return bytes(data) + movie + b"\0\0\0\0"


class DirectorTests(unittest.TestCase):
    def setUp(self):
        self.raw = afterburner_fixture()
        self.archive = DirectorArchive(self.raw)

    def test_varint_roundtrip_bounds(self):
        for n in (0, 1, 127, 128, 20000, 0xffffffff):
            value, pos = _varint(varint(n), 0)
            self.assertEqual((value, pos), (n, len(varint(n))))
        with self.assertRaises(InspectionError):
            _varint(b"\x80" * 6, 0)
        with self.assertRaises(InspectionError):
            _varint(b"\x80", 0)

    def test_header_map_tag_codec(self):
        self.assertEqual(self.archive.kind, "cast")
        self.assertEqual(self.archive.version, "8.5.1#104")
        self.assertEqual(self.archive.summary()["resource_count"], 5)
        self.assertEqual(self.archive.summary()["initial_load_resource_count"], 2)
        self.assertIn("Lscr", self.archive.summary()["tags"])

    def test_ils_lingo_and_raw_media(self):
        self.assertEqual(self.archive.get_resource(4), b"synthetic Lingo test bytes")
        self.assertEqual(self.archive.get_resource(5), b"some-version-data")
        self.assertEqual(self.archive.get_resource(6), b"\x01\x02not-a-real-image")

    def test_bad_ils_raises(self):
        broken = DirectorArchive(afterburner_fixture(broken_ils=True))
        with self.assertRaises(InspectionError):
            broken.get_resource(4)

    def test_bad_map_and_wrong_kind_rejected(self):
        with self.assertRaises(InspectionError):
            DirectorArchive(self.raw[:-5])
        with self.assertRaises(InspectionError):
            DirectorArchive(afterburner_fixture(b"ABCD"))

    def test_decompression_bomb_rejected(self):
        with self.assertRaises(InspectionError):
            _bounded_zlib(zlib.compress(b"A" * 100000), 10, 100)
        with self.assertRaises(InspectionError):
            _bounded_zlib(zlib.compress(b"abcd") + b"tail", 4, 100)

    def test_projector_recovery_without_patching_source(self):
        movie = afterburner_fixture(b"MDGF")
        executable = pe_with_movie(movie)
        pos, bytes_movie = embedded_movie(executable)
        self.assertEqual(pos, 0x200)
        self.assertEqual(bytes_movie, movie)
        self.assertEqual(DirectorArchive(bytes_movie).kind, "movie")

    def test_projector_fails_closed_on_missing_movie(self):
        with self.assertRaises(InspectionError):
            embedded_movie(pe_with_movie(b"not-a-movie"))

    def test_filesystem_create_only_extract_movie(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            src, dst = root / "fixture.exe", root / "private.dcr"
            source = pe_with_movie(afterburner_fixture(b"MDGF"))
            src.write_bytes(source)
            result = extract_movie(src, dst)
            self.assertEqual(result["offset"], 0x200)
            self.assertEqual(dst.read_bytes(), afterburner_fixture(b"MDGF"))
            self.assertEqual(src.read_bytes(), source)
            with self.assertRaises(FileExistsError):
                extract_movie(src, dst)

    def test_selective_resource_extract_and_no_overwrite(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            src, dest = root / "fixture.cct", root / "private_resources"
            src.write_bytes(self.raw)
            info = extract_resources(src, dest, tags={"Lscr", "VERS"})
            self.assertEqual(info["count"], 2)
            self.assertEqual(sorted(x["tag"] for x in info["files"]), ["Lscr", "VERS"])
            self.assertEqual(len(list(dest.iterdir())), 2)
            with self.assertRaises(InspectionError):
                extract_resources(src, dest, tags={"Lscr"})
            with self.assertRaises(InspectionError):
                extract_resources(src, root / "bad", tags=set())
            self.assertFalse((root / "bad").exists())

    def test_cli_integration_and_errors(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            src = root / "fixture.cct"
            src.write_bytes(self.raw)
            self.assertEqual(main(["director-map", str(src), "--details"]), 0)
            self.assertEqual(main(["extract-resources", str(src), "--output", str(root / 'only_lingo'), "--tag", "Lscr"]), 0)
            self.assertEqual(main(["extract-resources", str(src), "--output", str(root / 'bad'), "--tag", "hi"]), 2)
            self.assertFalse((root / 'bad').exists())


if __name__ == "__main__":
    unittest.main()


class VerificationTests(unittest.TestCase):
    def test_verify_directory_uses_only_synthetic_sources(self):
        from caserecomp.verification import verify_directory
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "cast.cct").write_bytes(afterburner_fixture())
            (root / "projector.exe").write_bytes(pe_with_movie(afterburner_fixture(b"MDGF")))
            (root / "text.txt").write_text("non-container")
            result = verify_directory(root)
            self.assertEqual(result["archives"], 2)
            self.assertEqual(result["resources"], 10)
            self.assertEqual(result["decoded"], 10)
            self.assertEqual(result["decode_failures"], [])
            self.assertEqual(result["resource_tags"]["Lscr"], 2)
            self.assertNotIn(str(root), json.dumps(result))

    def test_verify_caps_number_of_local_files(self):
        from caserecomp.verification import verify_directory
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "a.cct").write_bytes(afterburner_fixture())
            (root / "b.cct").write_bytes(afterburner_fixture())
            with self.assertRaises(InspectionError):
                verify_directory(root, max_archives=1)

class SafetyTests(unittest.TestCase):
    def test_corrupted_cast_reported_not_ignored(self):
        from caserecomp.verification import verify_directory
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "broken.cct").write_bytes(b"fake")
            result = verify_directory(root)
            self.assertEqual(result["archives"], 0)
            self.assertEqual(len(result["invalid_archives"]), 1)

    def test_input_symlink_rejected(self):
        from caserecomp.director import read_local
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            file = root / "fixture.cct"
            link = root / "link.cct"
            file.write_bytes(afterburner_fixture())
            link.symlink_to(file)
            with self.assertRaises((InspectionError, OSError)):
                read_local(link)

    def test_input_symlink_rejected_without_o_nofollow(self):
        from unittest.mock import patch
        from caserecomp import director
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            file = root / "fixture.cct"
            other = root / "other.cct"
            link = root / "link.cct"
            file.write_bytes(afterburner_fixture())
            other.write_bytes(afterburner_fixture())
            link.symlink_to(file)
            with patch.object(director, "_O_NOFOLLOW", 0):
                self.assertEqual(director.read_local(file), afterburner_fixture())
                with self.assertRaises(InspectionError):
                    director.read_local(link)
                with patch.object(director, "_same_unlinked_file", return_value=False),                         self.assertRaises(InspectionError):
                    director.read_local(file)
            opened = os.stat(file)
            self.assertTrue(director._same_unlinked_file(file, opened))
            self.assertFalse(director._same_unlinked_file(other, opened))
            self.assertFalse(director._same_unlinked_file(link, opened))
            self.assertFalse(director._same_unlinked_file(root / "missing.cct", opened))

    def test_destinations_do_not_replace_existing_data(self):
        from caserecomp.director import exclusive_write
        with tempfile.TemporaryDirectory() as tmp:
            dest = Path(tmp) / "result.bin"
            dest.write_bytes(b"existing")
            with self.assertRaises(FileExistsError):
                exclusive_write(dest, b"new")
            self.assertEqual(dest.read_bytes(), b"existing")

    def test_unknown_resource_id(self):
        with self.assertRaises(InspectionError):
            DirectorArchive(afterburner_fixture()).get_resource(999999)

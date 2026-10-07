"""Synthetic 8.5 Lnam/LctX/Lscr handler-directory tests; no commercial bytes."""
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest import TestCase
from unittest.mock import patch
from types import SimpleNamespace
import struct

from caserecomp.inspector import InspectionError
from caserecomp.lingo_index import read_lnam, read_lctx, script_handlers, index_archive, index_movie
from caserecomp.__main__ import main


def names_fixture(names=("start", "onButton")):
    binary = b"".join(bytes((len(s),)) + s.encode("mac_roman") for s in names)
    return bytes(16) + struct.pack(">HH", 20, len(names)) + binary


def context_fixture(lnam_id=4, script_ids=(9,)):
    offset = 96
    data = bytearray(offset + len(script_ids) * 12)
    struct.pack_into(">i", data, 8, len(script_ids))
    struct.pack_into(">H", data, 16, offset)
    struct.pack_into(">i", data, 32, lnam_id)
    for i, ident in enumerate(script_ids):
        struct.pack_into(">iiHh", data, offset + 12 * i, 0, ident, 0, 0)
    return bytes(data)


def script_fixture(handler_name_ids=(0, 1)):
    code = b"\x41\x01\x02"  # synthetic 3-byte bytecode body
    offset = 92
    output = bytearray(offset + len(handler_name_ids) * 46 + len(code))
    struct.pack_into(">H", output, 72, len(handler_name_ids))
    struct.pack_into(">i", output, 74, offset)
    payload = offset + len(handler_name_ids) * 46
    output[payload:payload + len(code)] = code
    for idx, name_id in enumerate(handler_name_ids):
        struct.pack_into(">hHii", output, offset + idx * 46, name_id, 0, len(code), payload)
    return bytes(output)


class DummyMovie:
    kind = "movie"
    def __init__(self, *, named=None, context=None, script=None):
        self.resources = {4: named or names_fixture(), 6: context or context_fixture(),
                          9: script or script_fixture()}
        self.entries = {4: SimpleNamespace(tag="Lnam"), 6: SimpleNamespace(tag="LctX"),
                        9: SimpleNamespace(tag="Lscr")}

    def get_resource(self, ident):
        return self.resources[ident]


class LingoMetadataTests(TestCase):
    def test_valid_names_and_context(self):
        self.assertEqual(read_lnam(names_fixture()), ("start", "onButton"))
        self.assertEqual(read_lctx(context_fixture()), (4, (9,)))

    def test_valid_scripts_and_owners(self):
        names = read_lnam(names_fixture())
        handlers = script_handlers(9, script_fixture(), names)
        self.assertEqual(len(handlers), 2)
        self.assertEqual(handlers[0].name, "start")
        self.assertEqual(handlers[0].bytecode_bytes, 3)
        self.assertEqual(len(handlers[0].bytecode_digest), 64)
        index = index_archive(DummyMovie())
        self.assertEqual(index["handler_count"], 2)
        self.assertEqual(index["symbols_in_lnam"], 2)
        self.assertEqual(index["name_redaction"], "sha256")
        self.assertEqual(len(index["handlers"][0]["name"]), 64)
        self.assertFalse(index["recovered_lingo_source"])
        exposed = index_archive(DummyMovie(), redact=False)
        self.assertEqual(exposed["handlers"][0]["name"], "start")

    def test_lnam_truncations_and_size_limits(self):
        for blob in (b"", names_fixture()[:-1], bytes(16) + struct.pack(">HH", 20, 20000),
                     names_fixture() + b"extra", bytes(16) + struct.pack(">HH", 50, 2)):
            with self.subTest(blob=blob), self.assertRaises(InspectionError):
                read_lnam(blob)

    def test_lctx_rejects_missing_entries_and_duplicates(self):
        for blob in (b"", context_fixture()[:-1], context_fixture(script_ids=(9, 9)),
                     context_fixture(lnam_id=-1)):
            with self.subTest(blob=blob), self.assertRaises(InspectionError):
                read_lctx(blob)
        many = bytearray(context_fixture())
        struct.pack_into(">i", many, 8, 999999)
        with self.assertRaises(InspectionError):
            read_lctx(many)

    def test_reject_bad_script_structures(self):
        names = ("start",)
        for blob in (b"", script_fixture()[:-8], script_fixture((5,))):
            with self.subTest(blob=blob), self.assertRaises(InspectionError):
                script_handlers(9, blob, names)
        bad = bytearray(script_fixture((0,)))
        struct.pack_into(">i", bad, 92 + 8, 80000)
        with self.assertRaises(InspectionError):
            script_handlers(9, bad, names)
        bad = bytearray(script_fixture((0,)))
        # A plausible bytecode offset inside the handler directory is invalid.
        struct.pack_into(">i", bad, 92 + 8, 92)
        with self.assertRaises(InspectionError):
            script_handlers(9, bad, names)
        bad = bytearray(script_fixture((0,)))
        struct.pack_into(">H", bad, 72, 2500)
        with self.assertRaises(InspectionError):
            script_handlers(9, bad, names)

    def test_index_requires_consistent_map(self):
        data = DummyMovie(context=context_fixture(lnam_id=111))
        with self.assertRaises(InspectionError):
            index_archive(data)
        data = DummyMovie()
        data.entries[9] = SimpleNamespace(tag="ALFA")
        with self.assertRaises(InspectionError):
            index_archive(data)
        data = DummyMovie()
        del data.entries[4]
        with self.assertRaises(InspectionError):
            index_archive(data)
        data = DummyMovie(context=context_fixture(script_ids=(19,)))
        with self.assertRaises(InspectionError):
            index_archive(data)

    def test_movie_only_and_cli_rejects_repo_output(self):
        with patch("caserecomp.lingo_index.open_archive", return_value=(SimpleNamespace(kind="cast"), 0)):
            with self.assertRaises(InspectionError):
                index_movie(Path("fake.cct"))
        with TemporaryDirectory() as path:
            root = Path(path)
            source = root / "movie.dcr"
            source.write_bytes(b"synthetic placeholder")
            out = root / "names.json"
            with patch("caserecomp.lingo_index.open_archive", return_value=(DummyMovie(), 0)):
                self.assertEqual(main(["lingo-index", str(source), "--output", str(out), "--show-names"]), 0)
                self.assertIn("onButton", out.read_text())
                self.assertEqual(main(["lingo-index", str(source), "--output", str(out)]), 2)
            (root / ".git").mkdir()
            with self.assertRaises(InspectionError):
                from caserecomp.pipeline import guard_destination
                guard_destination(source, root / "forbidden.json")

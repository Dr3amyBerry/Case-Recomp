"""Whole-folder audit fixtures contain only synthetic Director resources."""
import contextlib
import io
import json
import struct
import tempfile
import unittest
import zlib
from pathlib import Path

from caserecomp.__main__ import main
from caserecomp.inspector import InspectionError
from tests.test_director import file_chunk, varint, pe_with_movie
from tests.test_lingo_index import names_fixture, context_fixture, script_fixture


def audit_archive(resources, kind=b"MDGF"):
    registry = struct.pack("<H", 2) + bytes(32) + b"ziplib\0null\0"
    version = varint(0x501) + varint(1) + varint(1) + b"\x078.5.1#1"
    # Everything is in the initial-load segment, with synthetic resource IDs.
    body = b"".join(varint(rid) + data for rid, (_, data) in resources.items())
    compressed = zlib.compress(body)
    mapping = varint(1) + varint(0) + varint(len(resources) + 1)
    mapping += b"".join(varint(x) for x in (2, 0, len(compressed), len(body), 0)) + b" SLI"
    for rid, (tag, data) in resources.items():
        mapping += b"".join(varint(x) for x in (rid, 0xffffffff, len(data), len(data), 0))
        mapping += tag[::-1].encode("ascii")
    chunks = (file_chunk(b"revF", version) + file_chunk(b"rdcF", zlib.compress(registry))
              + file_chunk(b"PMBA", varint(0) + varint(len(mapping)) + zlib.compress(mapping))
              + b"IEGF\0" + compressed)
    payload = kind + chunks
    return b"XFIR" + struct.pack("<I", len(payload)) + payload


class DirectorAuditTests(unittest.TestCase):
    def audit(self, root, **kwargs):
        from caserecomp.verification import audit_directory
        return audit_directory(root, **kwargs)

    def test_mixed_folder_recovers_scripts_without_accepting_bad_projector(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "movie.dcr").write_bytes(audit_archive({
                4: ("Lnam", names_fixture()), 6: ("LctX", context_fixture()),
                9: ("Lscr", script_fixture()),
            }))
            (root / "empty.cct").write_bytes(audit_archive({}, kind=b"CDGF"))
            (root / "setup.exe").write_bytes(pe_with_movie(b"not a Director movie"))
            malformed = bytearray(audit_archive({}))
            struct.pack_into("<I", malformed, 4, len(malformed) + 1000)
            (root / "bad.exe").write_bytes(pe_with_movie(malformed))
            report = self.audit(root)
            self.assertEqual(report["summary"]["valid_archives"], 2)
            self.assertEqual(report["summary"]["failed_files"], 1)
            self.assertEqual(report["summary"]["scripts"], 1)
            self.assertEqual(report["summary"]["handlers"], 2)
            self.assertFalse(report["runtime_semantics_verified"])
            bad = next(r for r in report["files"] if r["path"] == "bad.exe")
            self.assertFalse(bad["candidate_headers"][0]["extent_valid"])
            movie = next(r for r in report["files"] if r["path"] == "movie.dcr")
            self.assertEqual(movie["lingo"]["name_redaction"], "sha256")
            self.assertEqual(len(movie["lingo"]["handlers"][0]["name"]), 64)

    def test_bad_resource_and_missing_script_are_reported_without_aborting(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            corrupt = bytearray(audit_archive({3: ("ediM", b"synthetic")}))
            corrupt[-1] ^= 1
            (root / "broken.cct").write_bytes(corrupt)
            (root / "missing.dcr").write_bytes(audit_archive({
                4: ("Lnam", names_fixture()), 6: ("LctX", context_fixture()),
            }))
            report = self.audit(root)
            self.assertEqual(report["summary"]["failed_files"], 2)
            self.assertTrue(all(r["errors"] for r in report["files"]))
            self.assertEqual(report["summary"]["handlers"], 0)

    def test_file_cap_and_output_create_only(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp) / "input"
            root.mkdir()
            for name in ("a.cct", "b.cct"):
                (root / name).write_bytes(audit_archive({}, kind=b"CDGF"))
            with self.assertRaises(InspectionError):
                self.audit(root, max_files=1)
            output = Path(tmp) / "audit.json"
            with contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(main(["verify-local", str(root), "--output", str(output)]), 0)
                self.assertEqual(main(["verify-local", str(root), "--output", str(output)]), 2)
            self.assertEqual(json.loads(output.read_text())["summary"]["valid_archives"], 2)

    def test_symbolic_input_is_refused(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            target = root / "a.cct"
            target.write_bytes(audit_archive({}, kind=b"CDGF"))
            link = root / "alias.cct"
            try:
                link.symlink_to(target)
            except OSError:
                self.skipTest("symlink creation unavailable")
            with self.assertRaises(InspectionError):
                self.audit(root)

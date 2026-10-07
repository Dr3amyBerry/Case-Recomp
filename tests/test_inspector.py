import hashlib
import json
import struct
import tempfile
import unittest
from pathlib import Path

from caserecomp.inspector import InspectionError, inspect_bytes, inspect_path, scan


def pe_fixture(payload=b""):
    data = bytearray(0x200 + len(payload))
    data[:2] = b"MZ"
    struct.pack_into("<I", data, 0x3C, 0x80)
    data[0x80:0x84] = b"PE\0\0"
    struct.pack_into("<HH", data, 0x84, 0x14C, 1)
    struct.pack_into("<H", data, 0x94, 0xE0)
    struct.pack_into("<H", data, 0x98, 0x10B)
    section = 0x80 + 24 + 0xE0
    data[section:section + 5] = b".text"
    struct.pack_into("<II", data, section + 16, 16, 0x1F0)
    data[0x200:] = payload
    return bytes(data)


def cct_fixture():
    content = b"revF" + b"\x0f\x8a\x01\x01\x8e\x3a\x09" + b"8.5.1#104" + b"more"
    return b"XFIR" + struct.pack("<I", len(content) + 4) + b"CDGF" + content


class StaticInspectorTests(unittest.TestCase):
    def test_cct_reports_container_and_version(self):
        result = inspect_bytes(cct_fixture(), "01.cct")
        self.assertEqual(result["format"], "director")
        self.assertEqual(result["director"]["endian"], "little")
        self.assertEqual(result["director"]["director_version"], "8.5.1#104")
        self.assertEqual(result["director"]["declared_payload_bytes"], len(cct_fixture()) - 8)

    def test_pe_overlay_and_embedded_cast(self):
        result = inspect_bytes(pe_fixture(cct_fixture()), "launch.exe")
        self.assertEqual(result["format"], "windows-pe")
        self.assertEqual(result["pe"]["bits"], 32)
        self.assertEqual(result["pe"]["unmapped_trailing_bytes"], len(cct_fixture()))
        self.assertEqual(result["embedded_director"][0]["offset"], 0x200)

    def test_invalid_pe_rejected(self):
        with self.assertRaises(InspectionError):
            inspect_bytes(b"MZ" + b"\0" * 62)

    def test_corrupt_cct_does_not_claim_success(self):
        invalid = b"XFIR" + struct.pack("<I", 999999) + b"CDGF"
        self.assertEqual(inspect_bytes(invalid)["format"], "unknown")

    def test_no_symlink_follows_and_valid_json(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            (root / "01.cct").write_bytes(cct_fixture())
            (root / "README.txt").write_text("hello")
            (root / "link.cct").symlink_to(root / "01.cct")
            result = scan(root)
            self.assertEqual(result["file_count"], 2)
            self.assertEqual(result["errors"], [])
            json.dumps(result)
            with self.assertRaises(InspectionError):
                inspect_path(root / "link.cct")

    def test_hash_is_deterministic(self):
        data = cct_fixture()
        self.assertEqual(inspect_bytes(data)["sha256"], hashlib.sha256(data).hexdigest())

    def test_limits(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            for i in range(3):
                (root / f"{i}.txt").write_bytes(b"a")
            self.assertEqual(scan(root, max_files=2)["errors"][0]["name"], "<limit>")


if __name__ == "__main__":
    unittest.main()

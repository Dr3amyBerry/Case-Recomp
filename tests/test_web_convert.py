import importlib.util
import json
import struct
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools" / "sda-prototype"))
spec = importlib.util.spec_from_file_location("web_convert", Path(__file__).resolve().parents[1] / "web" / "convert.py")
web_convert = importlib.util.module_from_spec(spec)
spec.loader.exec_module(web_convert)


class WebConvertScanTests(unittest.TestCase):
    def test_detects_movie_casts_and_title(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "data").mkdir()
            for name in ("dat1.cct", "DAT16.CCT", "01.cct", "21.cct", "notes.txt"):
                (root / "data" / name).write_bytes(b"x")
            (root / "setup.exe").write_bytes(b"not a projector")
            report = json.loads(web_convert.scan(str(root)))
            self.assertIsNone(report["movie"])
            self.assertEqual(report["title"]["id"], "huntsville")
            self.assertEqual(len(report["casts"]), 4)
            (root / "game.dir").write_bytes(b"x")
            self.assertEqual(json.loads(web_convert.scan(str(root)))["movie_name"], "game.dir")

    def test_unknown_folder(self):
        with tempfile.TemporaryDirectory() as tmp:
            (Path(tmp) / "other.cct").write_bytes(b"x")
            report = json.loads(web_convert.scan(tmp))
            self.assertEqual((report["movie"], report["title"]), (None, None))
            with self.assertRaises(web_convert.InspectionError):
                web_convert.convert(tmp, str(Path(tmp) / "out.zip"))

    def test_prime_suspects_casts_do_not_identify_as_huntsville(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            for name in ("dat1.cct", "dat16.cct", "01.cct", "21.cct", "29.cct", "dat22.cct"):
                (root / name).write_bytes(b"synthetic")
            report = json.loads(web_convert.scan(tmp))
            self.assertEqual(report["title"]["id"], "prime_suspects")
            self.assertTrue(report["title"]["ready"])

    def test_ravenhearst_casts_across_data_folders(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "data" / "game").mkdir(parents=True)
            (root / "data" / "language").mkdir()
            for name in ("room1.cct", "room32.cct", "puzz.cct", "sfx.cct"):
                (root / "data" / "game" / name).write_bytes(b"synthetic")
            (root / "data" / "language" / "interface_us.cct").write_bytes(b"synthetic")
            report = json.loads(web_convert.scan(tmp))
            self.assertEqual(report["title"]["id"], "ravenhearst")
            self.assertTrue(report["title"]["ready"])

    def test_vegas_resources_dll_is_detected_as_sda(self):
        with tempfile.TemporaryDirectory() as tmp:
            Path(tmp, "Resources.dll").write_bytes(synthetic_pe({"LEVELS_1.XUI": b"<xui/>", "UNLIMITED.XUI": b"<xui/>", "ENVS.MSE": b"x"}))
            report = json.loads(web_convert.scan(tmp))
            self.assertEqual((report["engine"], report["title"]["id"], report["resources"]), ("sda", "vegas_heist", 3))
            from runtime import Resources
            self.assertEqual(Resources(Path(tmp, "Resources.dll")).read("levels_1.xui"), b"<xui/>")

    def test_other_dll_is_not_a_game(self):
        with tempfile.TemporaryDirectory() as tmp:
            Path(tmp, "Resources.dll").write_bytes(synthetic_pe({"OTHER.XUI": b"x"}))
            self.assertIsNone(json.loads(web_convert.scan(tmp))["title"])


def synthetic_pe(resources):
    """A PE32 image whose only section holds a RAWDATA/<name>/1033 resource tree."""
    names = sorted(resources)
    section_rva, section_raw = 0x1000, 0x200
    root = bytearray()

    def directory(count):
        return struct.pack("<IIHHHH", 0, 0, 0, 0, count, 0)

    # Layout: root dir, type dir, name dir, language dirs, data entries, strings, data.
    root_size = 16 + 8
    type_size = 16 + 8 * len(names)
    lang_size = 16 + 8
    lang_start = root_size + type_size
    leaf_start = lang_start + lang_size * len(names)
    strings_start = leaf_start + 16 * len(names)
    strings = bytearray()
    string_at = {}
    for label in ["RAWDATA"] + names:
        string_at[label] = strings_start + len(strings)
        strings += struct.pack("<H", len(label)) + label.encode("utf-16-le")
    data_start = strings_start + len(strings)
    blob = bytearray()
    data_at = {}
    for name in names:
        data_at[name] = data_start + len(blob)
        blob += resources[name]
    root += directory(1) + struct.pack("<II", 0x80000000 | string_at["RAWDATA"], 0x80000000 | root_size)
    root += directory(len(names))
    for i, name in enumerate(names):
        root += struct.pack("<II", 0x80000000 | string_at[name], 0x80000000 | (lang_start + lang_size * i))
    for i, name in enumerate(names):
        root += directory(1) + struct.pack("<II", 1033, leaf_start + 16 * i)
    for name in names:
        root += struct.pack("<IIII", section_rva + data_at[name], len(resources[name]), 0, 0)
    root += strings + blob
    pe = 0x40
    header = bytearray(section_raw)
    header[0:2] = b"MZ"
    struct.pack_into("<I", header, 0x3C, pe)
    header[pe:pe + 4] = b"PE\0\0"
    optional_size = 96 + 16 * 8
    struct.pack_into("<HHIIIHH", header, pe + 4, 0x14C, 1, 0, 0, 0, optional_size, 0)
    struct.pack_into("<H", header, pe + 24, 0x10B)
    struct.pack_into("<II", header, pe + 24 + 96 + 2 * 8, section_rva, len(root))
    table = pe + 24 + optional_size
    struct.pack_into("<8sIIIIIIHHI", header, table, b".rsrc", len(root), section_rva, len(root), section_raw, 0, 0, 0, 0, 0)
    return bytes(header + root)


if __name__ == "__main__":
    unittest.main()

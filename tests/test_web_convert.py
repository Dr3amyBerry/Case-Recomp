import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

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


if __name__ == "__main__":
    unittest.main()

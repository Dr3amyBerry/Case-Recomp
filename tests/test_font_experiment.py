"""Synthetic contour conversion checks; no commercial font fixtures."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

try:
    import fontTools
except ImportError:
    fontTools = None


@unittest.skipUnless(fontTools, "optional font-research dependencies not installed")
class FontExperimentTests(unittest.TestCase):
    def setUp(self):
        spec = importlib.util.spec_from_file_location("font_experiment", Path(__file__).resolve().parents[1] / "tools/font-experiment/build_font.py")
        self.module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.module)
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.source = Path(self.directory.name) / "source.json"
        self.output = Path(self.directory.name) / "font.otf"
        codes = set(map(ord, "Agente: Dream Caso 12345 \u00e1\u00e9\u00ed\u00f3\u00fa\u00f1\u00fc\u00a1\u00bf")) | {128}
        contour = [[0, 0, 0, 0, 0, 0, 0], [2, 500, 0, 0, 700, 500, 700], [1, 0, 0, 0, 0, 0, 0]]
        self.data = {"units": 1000, "metric_units": 2000, "ascender": 800, "descender": -200,
            "glyphs": [{"code": code, "width": 1200, "contours": [] if code == 32 else [contour]} for code in sorted(codes)]}

    def convert(self):
        self.source.write_text(json.dumps(self.data))
        return self.module.build_font(self.source, self.output, "Synthetic Test")

    def test_cubic_contours_unicode_and_advance_survive_roundtrip(self):
        from fontTools.ttLib import TTFont
        from fontTools.pens.recordingPen import RecordingPen
        self.convert()
        with TTFont(self.output, checkChecksums=2) as font:
            font.ensureDecompiled()
            cmap = font.getBestCmap()
            self.assertIn(0x20ac, cmap)
            self.assertIn(0x00f1, cmap)
            self.assertEqual(font["hmtx"][cmap[65]][0], 600)
            pen = RecordingPen()
            font.getGlyphSet()[cmap[65]].draw(pen)
            self.assertIn(("curveTo", ((0, 700), (500, 700), (500, 0))), pen.value)

    def test_refuses_to_replace_existing_output(self):
        self.output.write_bytes(b"preserved")
        with self.assertRaisesRegex(ValueError, "already exists"):
            self.convert()
        self.assertEqual(self.output.read_bytes(), b"preserved")

    def test_rejects_empty_non_space_glyph(self):
        next(g for g in self.data["glyphs"] if g["code"] == 65)["contours"] = []
        with self.assertRaisesRegex(ValueError, "empty non-space"):
            self.convert()
        self.assertFalse(self.output.exists())

    def test_rejects_contour_with_no_move(self):
        self.data["glyphs"][1]["contours"] = [[[1, 5, 5, 0, 0, 0, 0]]]
        with self.assertRaisesRegex(ValueError, "moveTo"):
            self.convert()
        self.assertFalse(self.output.exists())


if __name__ == "__main__":
    unittest.main()

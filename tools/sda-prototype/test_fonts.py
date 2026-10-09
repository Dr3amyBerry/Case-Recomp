"""Own synthetic fixtures: no original game media or captions."""
import unittest
from PIL import Image
from fonts import AtlasFont, parse_strings, resolve_caption, scan_atlas


def atlas():
    im = Image.new("RGBA", (10, 4))
    for x in (1, 2, 4, 5, 6):
        im.putpixel((x, 1), (200, 100, 50, 255))
    return im


class FontTests(unittest.TestCase):
    def test_alpha_threshold_and_trailing_run(self):
        im = Image.new("RGBA", (7, 2))
        for x, alpha in ((0, 4), (1, 5), (2, 255), (3, 4), (6, 255)):
            im.putpixel((x, 0), (1, 2, 3, alpha))
        self.assertEqual(scan_atlas(im), [(1, 2)])

    def test_scan_caps_native_arrays(self):
        im = Image.new("RGBA", (600, 1))
        for x in range(0, 600, 2):
            im.putpixel((x, 0), (1, 2, 3, 255))
        self.assertEqual(len(scan_atlas(im)), 256)

    def test_duplicate_map_and_space_metrics(self):
        font = AtlasFont(atlas(), "AA", spacing=0.68, space_width=6)
        self.assertEqual(font.glyph_run(ord("A")), (4, 3))
        self.assertEqual(font.advance(ord("A")), 2)
        self.assertEqual(font.advance(32), 4)
        self.assertEqual(font.advance(ord("Z")), 0)

    def test_crop_width_and_alignment_omit_kern(self):
        font = AtlasFont(atlas(), "AB", spacing=1, kern=[(65, 66, -1.9)])
        canvas = Image.new("RGBA", (20, 8))
        font.draw(canvas, "AB", 10, 4, halign=1)
        # Metric width 2+3 centers at x8. Pair spacing truncates -1.9 to -1.
        self.assertEqual(canvas.getpixel((8, 1))[3], 255)
        self.assertEqual(canvas.getpixel((11, 1))[3], 255)
        self.assertEqual(canvas.getpixel((12, 1))[3], 0)

    def test_spacing_changes_advance_without_resizing(self):
        font = AtlasFont(atlas(), "AB", spacing=0.5)
        canvas = Image.new("RGBA", (10, 4))
        font.draw(canvas, "B", 0, 4)
        self.assertEqual(font.advance(66), 1)
        self.assertEqual(canvas.getbbox(), (0, 1, 3, 2))

    def test_baseline_newline_and_unsupported_style(self):
        font = AtlasFont(atlas(), "AB", baseline=1)
        canvas = Image.new("RGBA", (20, 12))
        font.draw(canvas, "A\\nB", 0, 5, valign=1)
        self.assertEqual(canvas.getpixel((0, 3))[3], 255)
        self.assertEqual(canvas.getpixel((0, 6))[3], 255)
        with self.assertRaises(ValueError):
            font.draw(canvas, "A\\sbB", 0, 5)

    def test_vertical_advance_and_top_anchor(self):
        font = AtlasFont(atlas(), "AB")
        canvas = Image.new("RGBA", (20, 24))
        font.draw(canvas, "A\\sa12B", 0, 2, valign=3)
        self.assertEqual(canvas.getpixel((0, 3))[3], 255)
        self.assertEqual(canvas.getpixel((0, 15))[3], 255)
        with self.assertRaises(ValueError):
            font.draw(canvas, "A\\sa123B", 0, 2)

    def test_localization_quotes_bom_and_missing_lookup(self):
        raw = '\ufeff@ID_TEST = "A \"quoted\" value"\nID_NEXT = "B\\nC"\ninvalid'.encode("utf-8")
        strings = parse_strings(raw)
        self.assertEqual(strings["ID_TEST"], 'A "quoted" value')
        self.assertEqual(resolve_caption("@ID_NEXT", strings), "B\\nC")
        self.assertEqual(resolve_caption("@ID_ABSENT", strings), "@ID_ABSENT")
        self.assertEqual(resolve_caption("literal", strings), "literal")


if __name__ == "__main__":
    unittest.main()

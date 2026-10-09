import unittest
from PIL import Image
from runtime import Score, Sprite, parse_xui


class RecoveredRules(unittest.TestCase):
    def test_fast_streak_caps_and_slow_hit_resets(self):
        score = Score()
        gains = [score.found(True) for _ in range(20)]
        self.assertEqual(gains[:3], [7500, 8500, 9500])
        self.assertEqual(gains[-1], 20500)
        self.assertEqual(score.found(False), 5000)
        self.assertEqual(score.found(True), 7500)

    def test_first_five_misses_do_not_penalize(self):
        score = Score(points=10000)
        self.assertFalse(any(score.miss(x) for x in [0, 100, 200, 300, 400]))
        self.assertTrue(score.miss(500))
        self.assertEqual(score.points, 7500)
        self.assertEqual(score.misses, [])

    def test_miss_boundary_uses_shifted_window_and_gate(self):
        for now, expected in [(2100, True), (2101, False)]:
            score = Score(points=10000)
            for x in [0, 100, 200, 300, 400]:
                score.miss(x)
            self.assertEqual(score.miss(now), expected)
        score = Score(points=10000)
        for x in range(5):
            score.miss(x)
        self.assertFalse(score.miss(5, penalty_allowed=False))
        self.assertTrue(score.miss(6))

    def test_clock_wrap_and_score_floor(self):
        score = Score(points=1000)
        for x in range(0xfffffffc, 0x100000001):
            score.miss(x)
        self.assertTrue(score.miss(1))
        self.assertEqual(score.points, 0)
        score.hint()
        self.assertEqual(score.points, 0)

    def test_alpha_hit_ignores_transparent_pixels_and_edges(self):
        image = Image.new("RGBA", (3, 2), (255, 255, 255, 0))
        image.putpixel((1, 0), (0, 0, 0, 1))
        sprite = Sprite("target", 10, 20, image)
        self.assertTrue(sprite.hit(11, 20))
        for x, y in [(10, 20), (13, 20), (11, 22), (9, 20)]:
            self.assertFalse(sprite.hit(x, y))
        sprite.found = True
        self.assertFalse(sprite.hit(11, 20))

    def test_xui_prefix_and_bom(self):
        root = parse_xui(b"\xef\xbb\xbf<xui><mpi:eyespyobjects/></xui>")
        self.assertEqual(root[0].tag, "{urn:spintop}eyespyobjects")
        with self.assertRaises(ValueError):
            parse_xui(b'<!DOCTYPE xui><xui/>')


if __name__ == "__main__":
    unittest.main()

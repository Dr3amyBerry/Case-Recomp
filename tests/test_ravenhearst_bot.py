import importlib.util
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("ravenhearst_android_qa", Path(__file__).resolve().parents[1] / "tools" / "ravenhearst_android_qa.py")
bot = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bot)


class RavenhearstBotTests(unittest.TestCase):
    def test_fresh_seed_clears_all_five_agents(self):
        seed = bot.fresh_seed()
        self.assertEqual(len(seed), 5)
        self.assertTrue(all(value == "0" and key.endswith("_dat") for key, value in seed.items()))
        self.assertTrue(all("ravenhearst" in key for key in seed))

    def test_points_parse_from_lingo_text(self):
        self.assertEqual(bot.point('"point(91, 389)"'), (91, 389))
        self.assertEqual(bot.point("point(-4.0, 12.5)"), (-4, 12))
        with self.assertRaises(ValueError):
            bot.point("rect(0, 0, 1, 1)")


if __name__ == "__main__":
    unittest.main()

"""Recovered RNG and set interaction verified with independent synthetic fixtures."""
import unittest
from PIL import Image
from runtime import Scene
from selection import NativeRandom, TargetDeck, native_shuffle


class FixtureResources:
    def read(self, name):
        if name == "STRINGS.TXT":
            return b'ID_PAIR = "Two samples,One sample"\nID_OFF = "Spare"'
        if name.endswith(".TXT"):
            return b""
        return b'''<xui><texture id="tex" uri="fixture"/>
          <eyespyimage id="a" x="20" y="20" tex="tex"/>
          <eyespyimage id="b" x="30" y="20" tex="tex"/>
          <eyespyimage id="off" x="40" y="20" tex="tex"/>
          <eyespyset objects="a,b" itemnamelist="@ID_PAIR"/>
          <eyespyset objects="off" itemnamelist="@ID_OFF"/></xui>'''

    def image(self, name):
        return Image.new("RGBA", (3, 3), (1, 2, 3, 255))


class SelectionTests(unittest.TestCase):
    def test_native_random_reference_sequence_and_u32_wrap(self):
        random = NativeRandom(1)
        self.assertEqual([random.next() for _ in range(5)], [41, 18467, 6334, 26500, 19169])
        random = NativeRandom(0xffffffff)
        self.assertEqual(random.next(), 35)
        with self.assertRaises(ValueError):
            NativeRandom(-1)

    def test_forward_shuffle_reference_and_suffix(self):
        original = list(range(5))
        self.assertEqual(native_shuffle(original, 1), [1, 4, 3, 2, 0])
        self.assertEqual(original, list(range(5)))
        shuffled = native_shuffle(list(range(20)), 1, start=4)
        self.assertEqual(shuffled[:4], [0, 1, 2, 3])
        self.assertEqual(sorted(shuffled[4:]), list(range(4, 20)))

    def test_first_batch_and_unshuffled_second_batch(self):
        deck = TargetDeck(range(30))
        first = deck.next_batch(1)
        order = tuple(deck.order)
        self.assertEqual(first, order[:10])
        self.assertEqual(deck.next_batch(999), order[10:20])
        self.assertEqual(deck.cursor, 20)

    def test_native_equal_count_boundary_is_not_silently_wrapped(self):
        deck = TargetDeck(range(10))
        self.assertEqual(len(deck.next_batch(1)), 10)
        self.assertEqual(deck.cursor, 10)
        with self.assertRaisesRegex(ValueError, "null child"):
            deck.next_batch(2)
        self.assertEqual(deck.cursor, 10)

    def test_restore_matches_variants_and_pins_prefix(self):
        deck = TargetDeck(range(15))
        variants = {identity: (str(identity), "part-" + str(identity)) for identity in range(15)}
        restored = deck.restore_batch(variants, ["part-8", "not present", "2"], 1)
        self.assertEqual(restored, (8, 2))
        self.assertEqual(deck.order[:2], [8, 2])
        self.assertEqual(sorted(deck.order[2:]), [i for i in range(15) if i not in (8, 2)])
        self.assertEqual(deck.cursor, 10)

    def test_restore_last_match_and_short_pool_cursor(self):
        deck = TargetDeck(["first", "last", "other"])
        variants = {"first": ("same",), "last": ("same",), "other": ("different",)}
        self.assertEqual(deck.restore_batch(variants, ["same"], 1), ("last",))
        self.assertEqual(deck.order[0], "last")
        self.assertEqual(deck.cursor, 0)
        with self.assertRaises(ValueError):
            deck.restore_batch(variants, ["same"] * 11, 1)

    def test_compound_partial_and_complete_each_hit_scores(self):
        scene = Scene(FixtureResources(), "SCENE_FIXTURE.MSL", [("a", "b")])
        self.assertEqual(scene.remaining_captions(), ["Two samples"])
        self.assertEqual(scene.click(20, 20), {"kind": "found", "id": "a", "gain": 7500})
        self.assertEqual(scene.remaining_captions(), ["One sample"])
        self.assertEqual(scene.click(20, 20)["kind"], "miss")
        self.assertEqual(scene.click(40, 20)["kind"], "miss")
        self.assertFalse(scene.objects["off"].found)
        self.assertEqual(scene.click(30, 20)["gain"], 8500)
        self.assertEqual(scene.remaining_captions(), [])
        self.assertEqual(scene.score.points, 16000)

    def test_invalid_set_or_ambiguous_selection_fails(self):
        with self.assertRaises(ValueError):
            Scene(FixtureResources(), "SCENE_FIXTURE.MSL", ["a"])
        with self.assertRaises(ValueError):
            Scene(FixtureResources(), "SCENE_FIXTURE.MSL", [("a", "b")], seed=1)


if __name__ == "__main__":
    unittest.main()

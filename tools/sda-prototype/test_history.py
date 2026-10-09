"""Native history matching and geometry with synthetic scene data."""
import unittest
from history import HistoryMark, replay_history, strict_point_inside, prune_scene_history
from runtime import Scene
from test_selection import FixtureResources


class HistoryTests(unittest.TestCase):
    def test_scoped_caption_and_first_bracket_parse(self):
        mark = HistoryMark.create("Sample", "room", 3, 11, 21)
        self.assertEqual(mark.text, "Sample (room) [3]")
        self.assertEqual(mark.split_variant(), ("Sample (room)", 3))
        self.assertEqual(HistoryMark("Sample (room)", 0, 0).split_variant(), ("Sample (room)", -1))
        self.assertEqual(HistoryMark("Sample (room) [bad]", 0, 0).split_variant(), ("Sample (room)", -1))

    def test_history_geometry_excludes_all_edges(self):
        rect = (10, 20, 3, 3)
        self.assertTrue(strict_point_inside(rect, 11, 21))
        for point in ((10, 21), (13, 21), (11, 20), (11, 23)):
            self.assertFalse(strict_point_inside(rect, *point))

    def test_compound_retires_matching_component_without_score(self):
        ids = ("a", "b")
        report = replay_history([ids], {ids: ("Two", "One")},
                                {"a": (10, 20, 3, 3), "b": (30, 20, 3, 3)},
                                [HistoryMark.create("Two", "room", 0, 11, 21)], "room", 0)
        self.assertEqual(report.candidates, (ids,))
        self.assertEqual(report.retired, frozenset({"a"}))

    def test_compound_final_mark_removes_set_and_hides_last(self):
        ids = ("a", "b")
        marks = [HistoryMark.create("Two", "room", 0, 11, 21),
                 HistoryMark.create("One", "room", 0, 31, 21)]
        report = replay_history([ids], {ids: ("Two", "One")},
                                {"a": (10, 20, 3, 3), "b": (30, 20, 3, 3)}, marks, "room", 0)
        self.assertEqual(report.candidates, ())
        self.assertEqual(report.retired, frozenset({"a"}))
        self.assertEqual(report.hidden, frozenset({"a", "b"}))

    def test_single_set_does_not_require_point_and_shifted_child_is_skipped(self):
        sets = [("a",), ("b",), ("c",)]
        variants = {ids: (ids[0],) for ids in sets}
        rects = {identity: (10, 20, 3, 3) for identity in "abc"}
        marks = [HistoryMark.create(identity, "room", 0, 100, 100) for identity in "abc"]
        report = replay_history(sets, variants, rects, marks, "room", 0)
        self.assertEqual(report.candidates, (("b",),))
        self.assertEqual(report.hidden, frozenset({"a", "c"}))

    def test_scene_scope_and_zero_variant_exceptions(self):
        ids = ("a",)
        variants, rects = {ids: ("Sample",)}, {"a": (10, 20, 3, 3)}
        for scene, variant, expected in (("other", 2, False), ("room", 0, False), ("room", 3, True)):
            report = replay_history([ids], variants, rects,
                                    [HistoryMark.create("Sample", scene, variant, 11, 21)], "room", 2)
            self.assertEqual(bool(report.removed_sets), expected)
        report = replay_history([ids], variants, rects,
                                [HistoryMark.create("Sample", "room", 2, 11, 21)], "room", 0)
        self.assertFalse(report.removed_sets)

    def test_pruning_threshold_counts_affected_sets_and_keeps_zero_scope(self):
        sets = [(str(i),) for i in range(11)]
        variants = {ids: ("sample-" + ids[0] + "!",) for ids in sets}
        one = HistoryMark.create("sample-0!", "room", 1, 0, 0)
        two = HistoryMark.create("sample-1!", "room", 2, 0, 0)
        zero = HistoryMark.create("sample-2!", "room", 0, 0, 0)
        other = HistoryMark.create("sample-3!", "elsewhere", 1, 0, 0)
        original = [one, zero, other]
        retained, reset = prune_scene_history(sets, variants, original, "room")
        self.assertFalse(reset)  # Exactly ten unaffected sets.
        self.assertEqual(retained, tuple(original))
        retained, reset = prune_scene_history(sets, variants, [one, two, zero, other], "room")
        self.assertTrue(reset)
        self.assertEqual(retained, (zero, other))

    def test_pruning_uses_native_substrings_not_exact_caption(self):
        sets = [(str(i),) for i in range(10)]
        variants = {ids: ("Token" if ids == ("0",) else "unused-" + ids[0],) for ids in sets}
        mark = HistoryMark.create("Long Token name", "room", 1, 0, 0)
        self.assertEqual(prune_scene_history(sets, variants, [mark], "room"), ((), True))

    def test_scene_restores_partial_compound_and_records_next_click(self):
        mark = HistoryMark.create("Two samples", "fixture", 0, 21, 21)
        scene = Scene(FixtureResources(), "SCENE_FIXTURE.MSL", seed=1, history=[mark],
                      history_variant=0, saved_captions=["One sample"])
        self.assertEqual(scene.active_sets, (("a", "b"),))
        self.assertTrue(scene.objects["a"].motion.removed)
        self.assertEqual(scene.click(21, 21)["kind"], "miss")
        self.assertEqual(scene.remaining_captions(), ["One sample"])
        self.assertEqual(scene.saved_captions(), ["One sample"])
        self.assertEqual(scene.click(31, 21)["kind"], "found")
        self.assertEqual(scene.history[-1].text, "One sample (fixture) [0]")
        self.assertEqual(scene.score.points, 7500)


if __name__ == "__main__":
    unittest.main()

"""Target-row lifecycle/render/persistence verified with own data and glyphs."""
import copy
from pathlib import Path
from types import SimpleNamespace
import tempfile
import unittest
from PIL import Image
from target_rows import TargetRow
from motion import single
from runtime import Scene
from progress import snapshot, restore
from test_selection import FixtureResources


class RowTests(unittest.TestCase):
    def test_four_update_fade_and_deferred_removal(self):
        row = TargetRow()
        row.update(False)
        self.assertEqual(row, TargetRow())
        row.update(True)
        self.assertEqual(row.phase, 1)
        self.assertEqual(row.alpha, single(1 - single(.34)))
        row.update(True)
        self.assertGreater(row.alpha, 0)
        row.update(True)
        self.assertEqual((row.phase, row.alpha, row.removed), (2, 0, False))
        row.update(True)
        self.assertEqual((row.phase, row.alpha, row.removed), (0, 0, True))
        row.update(True)
        self.assertTrue(row.removed)

    def test_caption_uses_retirement_and_row_compacts_after_fade(self):
        resources = FixtureResources()
        scene = Scene(resources, "SCENE_FIXTURE.MSL", [("a", "b"), ("off",)])
        for definition in scene.set_definitions.values():
            definition.update(font="own", h="20")
        calls = []
        def draw(im, text, x, y, **kwargs):
            calls.append(text)
            im.putpixel((0, 0), (255, 0, 0, 255))
        scene.fonts = SimpleNamespace(get=lambda font: SimpleNamespace(draw=draw))
        def render():
            calls.clear()
            im = Image.new("RGBA", (800, 600))
            scene.draw_target_list(im)
            return im
        render()
        self.assertEqual(calls, ["Two samples", "Spare"])
        scene.click(20, 20)
        render()
        self.assertEqual(calls[0], "Two samples")
        scene.objects["a"].motion.removed = True
        render()
        self.assertEqual(calls[0], "One sample")
        scene.click(30, 20)
        scene.objects["b"].motion.removed = True
        scene.advance(0)
        im = render()
        self.assertEqual(calls[0], "One sample")
        self.assertTrue(0 < im.getpixel((0, 121))[3] < 255)
        scene.advance(0)
        scene.advance(0)
        im = render()
        self.assertEqual(im.getpixel((0, 121))[3], 0)
        self.assertEqual(im.getpixel((0, 141))[3], 255)
        scene.advance(0)
        im = render()
        self.assertEqual(calls, ["Spare"])
        self.assertEqual(im.getpixel((0, 121))[3], 255)
        self.assertEqual(im.getpixel((0, 141))[3], 0)

    def test_partial_fade_restores_and_legacy_rows_are_inferred(self):
        root = Path(__file__).resolve().parents[2] / "local-output/sda-prototype/tests"
        root.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=root) as directory:
            resources = FixtureResources()
            resources.path = Path(directory) / "own.dat"
            resources.path.write_bytes(b"own row fixture")
            scene = Scene(resources, "SCENE_FIXTURE.MSL", [("off",)])
            scene.click(40, 20)
            scene.objects["off"].motion.removed = True
            scene.advance(0)
            state = snapshot(scene)
            resumed = restore(resources, state)
            self.assertEqual(snapshot(resumed), state)
            for _ in range(3):
                scene.advance(.04)
                resumed.advance(.04)
            self.assertEqual(snapshot(resumed), snapshot(scene))
            self.assertTrue(resumed.batch_retired)
            legacy = copy.deepcopy(state)
            del legacy["rows"]
            self.assertTrue(restore(resources, legacy).batch_retired)
            for key,value in (("phase",3),("alpha",float("nan")),("removed",True)):
                bad = copy.deepcopy(state)
                bad["rows"][0][key] = value
                with self.assertRaises(ValueError):
                    restore(resources,bad)


if __name__ == "__main__":
    unittest.main()

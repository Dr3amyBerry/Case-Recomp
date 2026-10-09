"""Two batches and a real file round trip using only synthetic resources."""
import copy
from pathlib import Path
import tempfile
import unittest
from test_selection import FixtureResources
from runtime import Scene
from progress import snapshot, restore, save, load


class ManyResources(FixtureResources):
    def read(self, name):
        if name == "STRINGS.TXT":
            return "\n".join(f'ID_{i} = "Sample {i}"' for i in range(22)).encode()
        if name.endswith(".TXT"):
            return b""
        objects = "".join(f'<eyespyimage id="o{i}" x="{20+i*5}" y="20" tex="tex"/>' for i in range(22))
        sets = "".join(f'<eyespyset objects="o{i}" itemnamelist="@ID_{i}"/>' for i in range(22))
        return ('<xui><texture id="tex" uri="fixture"/>' + objects + sets + '</xui>').encode()


class ProgressTests(unittest.TestCase):
    def fixture(self, directory):
        resources = ManyResources()
        resources.path = Path(directory) / "synthetic-resources.dat"
        resources.path.write_bytes(b"own synthetic fixture v1")
        return resources

    def test_complete_batch_advance_and_recover_file_progress(self):
        root = Path(__file__).resolve().parents[2] / "local-output" / "sda-prototype" / "tests"
        root.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=root) as directory:
            self.assertTrue(Path(directory).resolve().is_relative_to(root.resolve()))
            resources = self.fixture(directory)
            scene = Scene(resources, "SCENE_FIXTURE.MSL", seed=1)
            first = scene.active_sets
            with self.assertRaises(ValueError):
                scene.next_batch(2)
            for identity in scene.targets:
                sprite = scene.objects[identity]
                self.assertEqual(scene.click(sprite.x+1, sprite.y+1)["kind"], "found")
            for _ in range(100):
                scene.advance(.04)
            points, elapsed = scene.score.points, scene.elapsed
            second = scene.next_batch(2)
            self.assertFalse(set(first) & set(second))
            self.assertEqual(len(second), 10)
            self.assertEqual(scene.score.points, points)
            out = Path(directory) / "progress.json"
            save(scene, out)
            resumed = load(resources, out)
            self.assertEqual(snapshot(resumed), snapshot(scene))
            self.assertEqual(resumed.score.points, points)
            self.assertEqual(resumed.elapsed, elapsed)
            sprite = resumed.objects[resumed.targets[0]]
            self.assertEqual(resumed.click(sprite.x+1, sprite.y+1)["kind"], "found")
            self.assertGreater(resumed.score.points, points)

    def test_mid_animation_state_is_frozen_and_survives_resume(self):
        root = Path(__file__).resolve().parents[2] / "local-output" / "sda-prototype" / "tests"
        root.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=root) as directory:
            self.assertTrue(Path(directory).resolve().is_relative_to(root.resolve()))
            resources = self.fixture(directory)
            scene = Scene(resources, "SCENE_FIXTURE.MSL", seed=1)
            sprite = scene.objects[scene.targets[0]]
            scene.click(sprite.x+1, sprite.y+1)
            scene.advance(.04)
            state = snapshot(scene)
            frozen = copy.deepcopy(state)
            scene.advance(.04)
            self.assertEqual(state, frozen)
            resumed = restore(resources, state)
            self.assertEqual(snapshot(resumed), state)
            resumed.advance(.04)
            self.assertEqual(snapshot(resumed), snapshot(scene))
            wrong = copy.deepcopy(state)
            wrong["resources_sha256"] = "different"
            with self.assertRaises(ValueError):
                restore(resources, wrong)
            wrong = copy.deepcopy(state)
            wrong["elapsed"] = float("nan")
            with self.assertRaises(ValueError):
                restore(resources, wrong)


if __name__ == "__main__":
    unittest.main()

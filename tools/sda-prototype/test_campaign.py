"""Campaign accounting/clock/resume against own, synthetic multi-scene resources."""
import copy
from pathlib import Path
import tempfile
import unittest
from clock import LevelClock
from campaign import Session, read_levels
from test_progress import ManyResources
from progress import write_state, read_state


class CampaignResources(ManyResources):
    def read(self, name):
        if name == "LEVELS_1.XUI":
            return b'<xui><mpi:levels><mpi:level clue="1" time="1320" objects="18" scenes="one,two" levelname="Sample" bonus="sample"/></mpi:levels></xui>'
        return super().read(name)


class CampaignTests(unittest.TestCase):
    def test_native_timer_preupdate_gate_pause_and_display(self):
        clock = LevelClock(1320, 1139)
        self.assertEqual(clock.advance(1), (3,))
        self.assertEqual(clock.text(), "00:03:00")
        clock = LevelClock(1320, 1319.5)
        self.assertEqual(clock.advance(.6), ())
        self.assertEqual(clock.display_seconds(), 0)
        self.assertEqual(clock.advance(1), ("timeout",))
        clock = LevelClock(1320, 1310, True)
        self.assertEqual(clock.advance(1), (4,))
        self.assertEqual(clock.elapsed, 1310)
        self.assertEqual(clock.advance(1, suppress_events=True), ())
        # The remainder comparison can skip a 60-second jump, as in the native code.
        self.assertEqual(clock.advance(60), ())
        clock = LevelClock(10.5, 1.9)
        self.assertEqual(clock.display_seconds(), 9)
        self.assertEqual(clock.display_seconds(unlimited=True), 1)

    def test_two_scene_level_counts_retired_sets_once_and_resumes(self):
        root = Path(__file__).resolve().parents[2] / "local-output" / "sda-prototype" / "tests"
        root.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=root) as directory:
            self.assertTrue(Path(directory).resolve().is_relative_to(root.resolve()))
            resources = CampaignResources()
            resources.path = Path(directory) / "fixture.dat"
            resources.path.write_bytes(b"own campaign fixture")
            self.assertEqual(read_levels(resources)[0].scenes, ("one", "two"))
            session = Session(resources, 8)
            session.enter("one")
            for identity in session.scene.targets:
                sprite = session.scene.objects[identity]
                self.assertEqual(session.click(sprite.x+1, sprite.y+1)["kind"], "found")
            self.assertEqual(session.remaining, 18)
            for _ in range(100):
                session.advance(.04)
            self.assertEqual(session.remaining, 8)
            session.advance(1)
            self.assertEqual(session.remaining, 8)
            elapsed, points = session.clock.elapsed, session.points
            session.to_map()
            session.advance(10)
            self.assertEqual(session.clock.elapsed, elapsed)
            out = Path(directory) / "campaign.json"
            write_state(session.state(), out)
            recovered = Session.restore(resources, read_state(out))
            self.assertEqual(recovered.state(), session.state())
            recovered.enter("two")
            self.assertEqual(recovered.points, points)
            for identity in recovered.scene.targets[:8]:
                sprite = recovered.scene.objects[identity]
                self.assertEqual(recovered.click(sprite.x+1, sprite.y+1)["kind"], "found")
            for _ in range(100):
                recovered.advance(.04)
            self.assertEqual(recovered.phase, "objects_complete")
            self.assertEqual(recovered.remaining, 0)
            self.assertEqual(recovered.completed, 18)
            before = recovered.state()
            recovered.advance(60)
            self.assertEqual(recovered.state(), before)
            self.assertEqual(Session.restore(resources, before).state(), before)
            bad = copy.deepcopy(before)
            bad["completed"] = 17
            with self.assertRaises(ValueError):
                Session.restore(resources, bad)


if __name__ == "__main__":
    unittest.main()

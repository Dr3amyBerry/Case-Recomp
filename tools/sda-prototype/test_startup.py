"""Startup route and independent profile persistence using own fixtures."""
import copy
from pathlib import Path
import tempfile
import unittest
from startup import Startup, Player, name_error, trimmed_name
from progress import read_state, write_state
from test_campaign import CampaignResources


class StartupTests(unittest.TestCase):
    def test_space_only_trim_empty_and_case_sensitive_duplicate(self):
        self.assertEqual(trimmed_name("  Dream  "), "Dream")
        self.assertEqual(name_error("   "), "empty")
        self.assertEqual(name_error(" Dream ", ("Dream",)), "duplicate")
        self.assertIsNone(name_error("dream", ("Dream",)))
        # The recovered trim set is a space, not Python's entire whitespace set.
        self.assertEqual(trimmed_name(" \tDream\t "), "\tDream\t")
        with self.assertRaises(ValueError):
            trimmed_name("Dream\x00other")
        for icon in (True, -1, 3):
            with self.assertRaises(ValueError):
                Player("Dream", icon)

    def test_creation_returns_to_menu_without_starting_a_campaign(self):
        flow = Startup(CampaignResources(), 8)
        self.assertEqual(flow.action(299), "newplayer")
        self.assertIsNone(flow.session)
        self.assertEqual(flow.action(1, "   "), "empty")
        self.assertEqual(flow.phase, "newplayer")
        self.assertIsNone(flow.player)
        self.assertEqual(flow.action(9), "cancelled")
        self.assertEqual(flow.phase, "menu")
        self.assertEqual(flow.action(299), "newplayer")
        self.assertEqual(flow.action(1, " Dream ", 2), "player_created")
        self.assertEqual(flow.player, Player("Dream", 2))
        self.assertEqual(flow.phase, "menu")
        self.assertIsNone(flow.session)
        self.assertEqual(flow.action(299), "campaign")
        self.assertEqual(flow.session.phase, "map")
        self.assertEqual(flow.session.points, 0)
        with self.assertRaises(ValueError):
            flow.action(299)

    def test_profile_and_playable_progress_round_trip_do_not_restart(self):
        root = Path(__file__).resolve().parents[2] / "local-output/sda-prototype/tests"
        root.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=root) as directory:
            resources = CampaignResources()
            resources.path = Path(directory) / "fixture.dat"
            resources.path.write_bytes(b"own player fixture")
            flow = Startup(resources, 8)
            flow.action(299)
            flow.action(1, "Dream", 1)
            out = Path(directory) / "player.json"
            write_state(flow.state(), out)
            flow = Startup.restore(resources, read_state(out))
            self.assertEqual(flow.phase, "menu")
            self.assertIsNone(flow.session)
            flow.action(299)
            flow.session.enter("one")
            for identity in flow.session.scene.targets:
                sprite = flow.session.scene.objects[identity]
                flow.session.click(sprite.x+1, sprite.y+1)
            for _ in range(100):
                flow.session.advance(.04)
            self.assertEqual(flow.session.completed, 10)
            flow.session.to_map()
            write_state(flow.state(), out)
            restored = Startup.restore(resources, read_state(out))
            self.assertEqual(restored.state(), flow.state())
            restored.session.enter("two")
            self.assertEqual(restored.session.completed, 10)
            self.assertGreater(restored.session.clock.elapsed, 0)
            self.assertGreater(restored.session.points, 0)
            self.assertEqual(restored.player, Player("Dream", 1))
            bad = copy.deepcopy(flow.state())
            bad["player"] = None
            with self.assertRaises(ValueError):
                Startup.restore(resources, bad)
            bad = copy.deepcopy(flow.state())
            bad["seed"] += 1
            with self.assertRaises(ValueError):
                Startup.restore(resources, bad)
            bad = copy.deepcopy(flow.state())
            bad["player"]["name"] = " Dream "
            with self.assertRaises(ValueError):
                Startup.restore(resources, bad)

    def test_draft_is_not_committed_and_invalid_game_without_campaign_rejected(self):
        root = Path(__file__).resolve().parents[2] / "local-output/sda-prototype/tests"
        root.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=root) as directory:
            resources = CampaignResources()
            resources.path = Path(directory) / "fixture.dat"
            resources.path.write_bytes(b"own draft fixture")
            flow = Startup(resources)
            flow.action(299)
            state = flow.state()
            self.assertEqual(Startup.restore(resources, state).phase, "menu")
            state["phase"] = "game"
            with self.assertRaises(ValueError):
                Startup.restore(resources, state)


if __name__ == "__main__":
    unittest.main()

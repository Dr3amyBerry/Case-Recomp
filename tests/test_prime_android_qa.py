import argparse
import tempfile
import unittest
from pathlib import Path
from unittest.mock import Mock

from caserecomp.inspector import InspectionError
from tools.prime_android_qa import Bot, UnreachableSprite, resume_progress


class PrimeBotOutputTests(unittest.TestCase):
    def test_resumed_scene_does_not_click_a_main_menu_button(self):
        bot = Bot.__new__(Bot)
        bot.label = Mock(return_value="loc12")
        bot.named = Mock()
        bot.resume_game()
        bot.named.assert_not_called()

    def test_search_leaves_blocked_location_and_selects_another(self):
        bot = Bot.__new__(Bot)
        bot.lifecycle = ["location"]
        bot.label = Mock(side_effect=["loc12", "loc12", "map", "metagame1"])
        bot.dialog = Mock()
        bot.bridge = Mock(return_value="")
        bot.prop = Mock(side_effect=[12, [12, [29, 0]]])
        bot.call = Mock(side_effect=[["missing", 62], [0, 1]])
        bot.click = Mock(side_effect=[UnreachableSprite("missing"), None])
        bot.inventory = Mock(return_value=[[12, [29, 0]], [5, [1, 0]]])
        bot.batch = Mock(return_value=["5" if n == 42 else "0" for n in range(30, 115, 3)])
        bot.named = Mock()
        bot.wait = Mock()
        bot.event = Mock()
        bot.gameplay_wait = Mock()
        bot.search()
        bot.named.assert_called_once_with("mapButtonA")
        self.assertEqual([62, 42], [call.args[0] for call in bot.click.call_args_list])

    def test_resume_rejects_gaps_and_unverified_isolation(self):
        valid = {"cases": [1, 2], "lifecycle": ["location", "puzzle-1", "puzzle-2"], "isolation": True}
        self.assertEqual((3, [1, 2], valid["lifecycle"]), resume_progress(valid))
        for changed in ({**valid, "cases": [1, 3]}, {**valid, "isolation": False}):
            with self.assertRaises(ValueError):
                resume_progress(changed)

    def test_modal_obstruction_is_dismissed_before_retrying_same_touch(self):
        bot = Bot.__new__(Bot)
        bot.bridge = Mock(side_effect=["none", "100 200"])
        bot.dialog = Mock()
        bot.touch = Mock()
        bot.click(62)
        bot.dialog.assert_called_once_with()
        bot.touch.assert_called_once_with(100, 200)

    def test_speed_only_shortens_gameplay_waits(self):
        bot = Bot.__new__(Bot)
        bot.args = argparse.Namespace(speed=2)
        bot.sleep = Mock()
        bot.gameplay_wait(3.8)
        bot.sleep.assert_called_once_with(1.9)

    def test_commercial_output_inside_git_is_rejected_before_creation(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "owned.zip"
            source.write_bytes(b"private")
            repo = root / "repo"
            repo.mkdir()
            (repo / ".git").mkdir()
            output = repo / "captures"
            with self.assertRaises(InspectionError):
                Bot(argparse.Namespace(package=source, save=source, out=output))
            self.assertFalse(output.exists())

    def test_existing_output_is_never_overwritten(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "owned.zip"
            source.write_bytes(b"private")
            output = root / "captures"
            output.mkdir()
            marker = output / "result.json"
            marker.write_text("previous run")
            with self.assertRaises(InspectionError):
                Bot(argparse.Namespace(package=source, save=source, out=output))
            self.assertEqual(marker.read_text(), "previous run")

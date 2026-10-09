"""PDA rendering and map action tests with only own synthetic resources."""
from types import SimpleNamespace
import unittest
from unittest.mock import patch
from PIL import Image
from clock import LevelClock
from pda_view import PdaView, format_score
from test_map_view import FixtureCatalog


class FixtureResources:
    def read(self, name):
        if name == "STRINGS.TXT":
            return b'ID_TIME = "TIME: "\nID_SCORE = "SCORE: "\nID_MAP = "MAP"\nID_LEVEL = "LEVEL"\nID_TOTAL = "TOTAL:"'
        return b'<xui><pda id="pdacontrol" w="174" h="600"><image tex="side"/><clock id="clock" x="9" y="56" w="132" h="20" caption="@ID_TIME" font="clock" halign="center"/><score id="score" x="8" y="68" w="132" h="20" caption="@ID_SCORE" font="score" halign="center"/><label id="totalitems" x="2" y="337" w="148" h="26" caption="@ID_TOTAL" font="total" halign="center"/><label id="cluelabel" x="10" y="42" w="132" h="22" caption="@ID_LEVEL" font="level" halign="center"/><allbutton id="mapbutton" x="16" y="388" value="301" caption="@ID_MAP" font="map" fonthover="hoverfont" fontpushed="pushfont" texnormal="normal" texhover="hover" texpushed="pushed" texdisabled="disabled"/></pda><container id="maptext"/><container id="eyespytext"/></xui>'

    def image(self, name):
        return Image.new("RGBA", (174, 600) if name == "side" else (120, 28), (10, 20, 30, 255))


class Catalog(FixtureCatalog):
    textures = {name: name for name in ("side", "normal", "hover", "pushed", "disabled")}


class PdaTests(unittest.TestCase):
    def fixture(self):
        with patch("pda_view.FontCatalog", Catalog):
            return PdaView(FixtureResources())

    def test_original_clock_split_score_grouping_and_remaining_labels(self):
        view = self.fixture()
        session = SimpleNamespace(phase="scene", points=175500, remaining=8,
                                  clock=LevelClock(1320, 125), level=SimpleNamespace(clue=1))
        stage = Image.new("RGBA", (800, 600), (0, 0, 0, 255))
        view.render(stage, session)
        calls = view.catalog.calls
        self.assertIn(("clock", "TIME: ", 16, 9, 1, 2), calls)
        self.assertIn(("clock", "00:19:55", 0, 9, 0, 2), calls)
        self.assertIn(("score", "SCORE: 175,500", 65, 9, 1, 2), calls)
        self.assertIn(("total", "TOTAL: 8", 73, 12, 1, 2), calls)
        self.assertEqual(stage.getpixel((173, 599)), (10, 20, 30, 255))
        self.assertEqual(stage.getpixel((174, 599)), (0, 0, 0, 255))
        self.assertEqual(format_score(0), "0")
        self.assertEqual(format_score(1000000), "1,000,000")
        with self.assertRaises(ValueError):
            format_score(-1)

    def test_map_action_is_enabled_only_in_scene_and_consumed_once(self):
        view = self.fixture()
        session = SimpleNamespace(phase="map", points=0, remaining=18,
                                  clock=LevelClock(1320), level=SimpleNamespace(clue=1))
        stage = Image.new("RGBA", (800, 600))
        view.render(stage, session)
        self.assertIn(("level", "LEVEL: 1", 65, 10, 1, 2), view.catalog.calls)
        for kind in ("move", "down", "up"):
            view.pointer(kind, 30, 400)
        self.assertIsNone(view.consume_activation())
        session.phase = "scene"
        view.render(stage, session)
        for kind in ("move", "down", "up"):
            view.pointer(kind, 30, 400)
        self.assertEqual(view.consume_activation(), 301)
        self.assertIsNone(view.consume_activation())
        self.assertTrue(view.owns(173, 599))
        self.assertFalse(view.owns(174, 599))


if __name__ == "__main__":
    unittest.main()

"""Play button interaction/drawing with independent, noncommercial fixtures."""
from types import SimpleNamespace
import unittest
from unittest.mock import patch
from PIL import Image
from menu_view import MenuView
from startup import Player


class Resources:
    def read(self, name):
        if name == "STRINGS.TXT":
            return b'ID_PLAY = "Play"'
        return b'''<xui><mainmenu><image id="img_mm_idgeneric" tex="generic" x="80" y="50"/><image id="img_mm_idmale" tex="male" x="80" y="50"/><image id="img_mm_idfemale" tex="female" x="80" y="50"/><label id="playername" font="name" x="40" y="20" w="50" h="25" _valign="top"/><allbutton value="299" x="10" y="100" caption="@ID_PLAY" font="normal" fonthover="hover" fontpushed="pushed" texnormal="normal" texhover="hover" texpushed="pushed" globalcaptionoffsety="-10" captionoffsetx="2" captionoffsety="2"/></mainmenu></xui>'''

    def image(self, name):
        return Image.new("RGBA", (30, 40), {"normal": (10, 0, 0, 255),
            "hover": (20, 0, 0, 255), "pushed": (30, 0, 0, 255),
            "generic": (40, 0, 0, 255), "male": (50, 0, 0, 255),
            "female": (60, 0, 0, 255)}[name])


class Catalog:
    textures = {key: key for key in ("normal", "hover", "pushed", "generic", "male", "female")}

    def __init__(self, resources):
        self.calls = []

    def get(self, name):
        return SimpleNamespace(draw=lambda im, text, x, y, halign, valign:
            self.calls.append((name, text, x, y, halign, valign)))


class MenuTests(unittest.TestCase):
    def view(self):
        with patch("menu_view.FontCatalog", Catalog), patch("menu_view.render_menu") as base:
            base.return_value = (Image.new("RGBA", (800, 600)), {})
            view = MenuView(Resources())
            base.assert_called_once_with(view.resources, exclude_action=299)
            return view

    def test_play_activation_and_drag_cancellation(self):
        view = self.view()
        view.pointer("down", 11, 101)
        self.assertEqual(view.button.state, 0)
        view.pointer("move", 11, 101)
        view.pointer("down", 11, 101)
        view.pointer("move", 40, 101, held=True)
        view.pointer("up", 40, 101)
        self.assertIsNone(view.consume_activation())
        view.pointer("move", 11, 101)
        view.pointer("down", 11, 101)
        view.pointer("up", 11, 101)
        self.assertEqual(view.consume_activation(), 299)
        self.assertIsNone(view.consume_activation())

    def test_state_fonts_offsets_and_committed_name(self):
        view = self.view()
        view.render()
        self.assertIn(("normal", "Play", 24, 109, 1, 2), view.catalog.calls)
        view.button.state = 2
        im = view.render(Player("Dream", 2))
        self.assertEqual(im.getpixel((10, 100)), (30, 0, 0, 255))
        self.assertEqual(im.getpixel((80, 50)), (60, 0, 0, 255))
        self.assertIn(("pushed", "Play", 26, 111, 1, 2), view.catalog.calls)
        self.assertIn(("name", "Dream", 40, 31, 0, 2), view.catalog.calls)
        view.button.state = 3
        self.assertEqual(view.render().getpixel((10, 100)), (20, 0, 0, 255))


if __name__ == "__main__":
    unittest.main()

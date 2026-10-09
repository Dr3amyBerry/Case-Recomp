"""Native map layout and activation tests using own images/fonts/XUI."""
from types import SimpleNamespace
import unittest
from unittest.mock import patch
from PIL import Image
from map_view import MapView, SceneCard, LAYOUTS


class FixtureResources:
    def read(self, name):
        if name == "STRINGS.TXT":
            return b'ID_TITLE = "Sample Map"\nID_ONE = "One"\nID_TWO = "Two"'
        return b'<xui><map id="mapunderlay" background="background"><image id="background" tex="bg" x="144"/><scenebutton name="two" w="198" h="160" caption="@ID_TWO" texscene="thumb" texnormal="normal" texhover="hover" texpushed="pushed" fontidle="name" fontitems="count"/><scenebutton name="one" w="198" h="160" caption="@ID_ONE" texscene="thumb" texnormal="normal" texhover="hover" texpushed="pushed" fontidle="name" fontitems="count"/></map></xui>'

    def image(self, name):
        if name == "bg":
            return Image.new("RGBA", (656, 600), (1, 2, 3, 255))
        if name == "thumb":
            return Image.new("RGBA", (171, 107), (50, 60, 70, 255))
        im = Image.new("RGBA", (198, 160), (0, 0, 0, 0))
        im.putpixel((0, 0), {"normal": (10, 0, 0, 255), "hover": (20, 0, 0, 255), "pushed": (30, 0, 0, 255)}[name])
        return im


class FixtureCatalog:
    textures = {key: key for key in ("bg", "thumb", "normal", "hover", "pushed")}

    def __init__(self, resources):
        self.calls = []

    def get(self, name):
        return SimpleNamespace(draw=lambda stage, text, x, y, halign, valign: self.calls.append((name, text, x, y, halign, valign)))


class MapTests(unittest.TestCase):
    def view(self):
        with patch("map_view.FontCatalog", FixtureCatalog):
            return MapView(FixtureResources(), SimpleNamespace(scenes=("one", "two"), title="@ID_TITLE"))

    def test_layout_follows_graph_order_and_saved_partial_count(self):
        view = self.view()
        self.assertEqual([(c.name, c.x, c.y) for c in view.cards], [("two", 278, 211), ("one", 477, 211)])
        session = SimpleNamespace(scenes={"one": SimpleNamespace(saved_captions=lambda: ["partial", "other"])})
        self.assertEqual(view.count(session, "two"), 10)
        self.assertEqual(view.count(session, "one"), 2)
        im = view.render(session)
        self.assertEqual(im.getpixel((291, 222)), (50, 60, 70, 255))
        self.assertEqual(im.getpixel((278, 211)), (10, 0, 0, 255))
        self.assertIn(("count", "2", 10, 9, 1, 2), view.catalog.calls)
        self.assertEqual(len(LAYOUTS[9]), 9)

    def test_pointer_capture_drag_cancel_and_deferred_transition(self):
        view = self.view()
        view.pointer("down", 300, 230)
        self.assertEqual(view.cards[0].state, 0)  # Native requires hover before down.
        view.pointer("move", 300, 230)
        view.pointer("down", 300, 230)
        self.assertEqual(view.cards[0].state, 2)
        view.pointer("move", 0, 0, held=True)
        self.assertEqual(view.cards[0].state, 3)
        view.pointer("up", 0, 0)
        self.assertIsNone(view.consume_activation())
        view.pointer("move", 300, 230)
        view.pointer("down", 300, 230)
        view.pointer("move", 0, 0, held=True)
        view.pointer("move", 300, 230, held=True)
        view.pointer("up", 300, 230)
        self.assertEqual(view.consume_activation(), {"value": 302, "scene": "two"})
        self.assertIsNone(view.consume_activation())
        view.pointer("up", 300, 230)
        self.assertIsNone(view.consume_activation())
        self.assertFalse(view.cards[0].inside(476, 230))
        disabled = SceneCard({"name": "sample", "w": "2", "h": "2"}, 0, 0, state=4)
        for event in ("move", "down", "up"):
            disabled.pointer(event, 1, 1)
        self.assertFalse(disabled.activation)

    def test_pushed_thumbnail_offset_and_dragged_hover_frame(self):
        view = self.view()
        session = SimpleNamespace(scenes={})
        view.cards[0].state = 2
        im = view.render(session)
        self.assertEqual(im.getpixel((291, 222)), (1, 2, 3, 255))
        self.assertEqual(im.getpixel((293, 224)), (50, 60, 70, 255))
        self.assertEqual(im.getpixel((278, 211)), (30, 0, 0, 255))
        view.cards[0].state = 3
        self.assertEqual(view.render(session).getpixel((278, 211)), (20, 0, 0, 255))


if __name__ == "__main__":
    unittest.main()

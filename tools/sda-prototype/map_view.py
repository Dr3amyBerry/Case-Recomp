"""Recovered level-map cards and pointer states, over the experimental session.

No native profile/PDA/marker/fader graph or desktop input is used here.
"""
from dataclasses import dataclass
from PIL import Image
from runtime import parse_xui, local_name
from fonts import FontCatalog, parse_strings, resolve_caption

# 0043eec0 positions visible scene buttons in graph declaration order.
LAYOUTS = {
    1: ((378, 211),),
    2: ((278, 211), (477, 211)),
    3: ((379, 133), (282, 293), (480, 293)),
    4: ((278, 131), (477, 131), (278, 291), (477, 291)),
    5: ((179, 131), (377, 131), (575, 131), (278, 291), (477, 291)),
    6: ((179, 131), (377, 131), (575, 131), (179, 291), (377, 291), (575, 291)),
    7: ((278, 51), (477, 51), (179, 211), (377, 211), (575, 211), (278, 371), (477, 371)),
    8: ((179, 51), (377, 51), (575, 51), (179, 211), (377, 211), (575, 211), (278, 371), (477, 371)),
    9: ((179, 51), (377, 51), (575, 51), (179, 211), (377, 211), (575, 211), (179, 371), (377, 371), (575, 371)),
}


@dataclass
class SceneCard:
    attributes: dict
    x: int
    y: int
    state: int = 0
    activation: bool = False

    @property
    def name(self):
        return self.attributes["name"]

    def inside(self, x, y):
        return self.x <= x < self.x + int(self.attributes["w"]) and self.y <= y < self.y + int(self.attributes["h"])

    def pointer(self, kind, x, y, held=False):
        # 004884f9: idle=0, hover=1, pushed=2, dragged outside=3, disabled=4.
        if self.state == 4:
            return
        inside = self.inside(x, y)
        if kind == "move":
            if not held:
                if self.state == 0 and inside:
                    self.state = 1
                elif self.state == 1 and not inside:
                    self.state = 0
            elif self.state == 3 and inside:
                self.state = 2
            elif self.state == 2 and not inside:
                self.state = 3
        elif kind == "down":
            if self.state == 1 and inside:
                self.state = 2
        elif kind == "up":
            if self.state == 2:
                self.state = 1 if inside else 0
                if inside:
                    self.activation = True
            else:
                self.state = 1 if inside else 0
        else:
            raise ValueError("unknown pointer event")


class MapView:
    def __init__(self, resources, level):
        self.resources = resources
        tree = parse_xui(resources.read("ENVS.MSE"))
        self.node = next(n for n in tree.iter() if n.attrib.get("id") == "mapunderlay")
        self.catalog = FontCatalog(resources)
        self.strings = parse_strings(resources.read("STRINGS.TXT"))
        selected = [dict(n.attrib) for n in self.node if local_name(n.tag) == "scenebutton"
                    and n.attrib.get("name") in level.scenes]
        if len(selected) != len(level.scenes) or len({a["name"] for a in selected}) != len(selected):
            raise ValueError("level scenes do not match original map buttons")
        if len(selected) not in LAYOUTS:
            raise ValueError("unsupported native map layout")
        self.cards = [SceneCard(a, x, y, 4 if a.get("disabled") == "true" else 0)
                      for a, (x, y) in zip(selected, LAYOUTS[len(selected)])]
        self.level = level

    def pointer(self, kind, x, y, held=False):
        for card in self.cards:
            card.pointer(kind, x, y, held)

    def consume_activation(self):
        # 00452b40 -> 00405b70 stores the scene name and requests transition 302.
        activated = [card.name for card in self.cards if card.activation]
        for card in self.cards:
            card.activation = False
        if len(activated) > 1:
            raise ValueError("multiple map activations in one frame")
        return {"value": 302, "scene": activated[0]} if activated else None

    def count(self, session, name):
        # 00452220 defaults to ten; saved remaining captions update +0x120.
        return len(session.scenes[name].saved_captions()) if name in session.scenes else 10

    def render(self, session):
        stage = Image.new("RGBA", (800, 600), (0, 0, 0, 255))
        for node in self.node:
            a = node.attrib
            if (local_name(node.tag) == "image" and "tex" in a
                    and a.get("id") == self.node.attrib.get("background")):
                stage.alpha_composite(self.resources.image(self.catalog.textures[a["tex"]]),
                                      (int(a.get("x", 0)), int(a.get("y", 0))))
            elif local_name(node.tag) == "label" and a.get("id") == "mapscreencaption":
                x, y, w, h = (int(a.get(k, 0)) for k in ("x", "y", "w", "h"))
                self.catalog.get(a["font"]).draw(stage, resolve_caption(self.level.title, self.strings),
                                                 x - 1 + w // 2, y - 1 + h // 2, 1, 2)
        for card in self.cards:
            a = card.attributes
            w, h = int(a["w"]), int(a["h"])
            x, y = card.x, card.y
            shift = 2 if card.state == 2 else 0
            # 004524d0 draws the thumbnail BEFORE the normal/hover/pushed frame.
            thumbnail = self.resources.image(self.catalog.textures[a["texscene"]])
            stage.alpha_composite(thumbnail.crop((0, 0, min(w, thumbnail.width), min(h, thumbnail.height))),
                                  (x + 13 + shift, y + 11 + shift))
            key = {0: "texnormal", 1: "texhover", 2: "texpushed", 3: "texhover", 4: "texdisabled"}[card.state]
            texture = a.get(key)
            if texture:
                frame = self.resources.image(self.catalog.textures[texture])
                stage.alpha_composite(frame.crop((0, 0, min(w, frame.width), min(h, frame.height))), (x, y))
            # Temporary native labels: name at (19,115), remaining at (160,97).
            # Clip the text through its own label rectangle, as the native graph does.
            label = Image.new("RGBA", (w - 19, h - 115), (0, 0, 0, 0))
            self.catalog.get(a["fontidle"]).draw(label, resolve_caption(a.get("caption", ""), self.strings),
                                                0, -1 + label.height // 2, 0, 2)
            stage.alpha_composite(label, (x + 19 + shift, y + 115 + shift))
            count = Image.new("RGBA", (22, 20), (0, 0, 0, 0))
            self.catalog.get(a["fontitems"]).draw(count, str(self.count(session, card.name)), 10, 9, 1, 2)
            stage.alpha_composite(count, (x + 160 + shift, y + 97 + shift))
        return stage

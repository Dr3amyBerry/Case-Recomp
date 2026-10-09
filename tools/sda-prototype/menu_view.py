"""Original menu play button plus experimental first-player binding.

Only action 299 is interactive. Modal/profile widgets, other menu actions, logo,
faders and native transition passes remain outside this bounded view.
"""
from runtime import parse_xui, local_name
from fonts import FontCatalog, parse_strings, resolve_caption
from map_view import SceneCard
from menu_preview import render_menu


class MenuView:
    def __init__(self, resources):
        self.resources = resources
        tree = parse_xui(resources.read("ENVS.MSE"))
        self.node = next(n for n in tree.iter() if local_name(n.tag) == "mainmenu")
        self.catalog = FontCatalog(resources)
        self.strings = parse_strings(resources.read("STRINGS.TXT"))
        self.base, self.report = render_menu(resources, exclude_action=299)
        a = dict(next(n for n in self.node if n.attrib.get("value") == "299").attrib)
        states = [resources.image(self.catalog.textures[a[k]]) for k in
                  ("texnormal", "texhover", "texpushed")]
        a["name"] = "play"
        a.setdefault("w", str(max(im.width for im in states)))
        a.setdefault("h", str(max(im.height for im in states)))
        self.button = SceneCard(a, int(a["x"]), int(a["y"]))

    def pointer(self, kind, x, y, held=False):
        self.button.pointer(kind, x, y, held)

    def consume_activation(self):
        active = self.button.activation
        self.button.activation = False
        return 299 if active else None

    def render(self, player=None):
        stage = self.base.copy()
        if player:
            picture = ("img_mm_idgeneric", "img_mm_idmale", "img_mm_idfemale")[player.icon]
            for node in self.node:
                a = node.attrib
                if a.get("id") == picture:
                    stage.alpha_composite(self.resources.image(self.catalog.textures[a["tex"]]),
                                          (int(a["x"]), int(a["y"])))
                elif a.get("id") == "playername":
                    # Menu name binding: 0040ece0 -> 0043de70 -> label setter.
                    # The declared _valign is ignored; constructor default is middle.
                    self.catalog.get(a["font"]).draw(stage, player.name, int(a["x"]),
                        int(a["y"]) - 1 + int(a["h"]) // 2, 0, 2)
        a, state = self.button.attributes, self.button.state
        texture = {0: "texnormal", 1: "texhover", 2: "texpushed", 3: "texhover"}[state]
        image = self.resources.image(self.catalog.textures[a[texture]])
        image = image.crop((0, 0, min(int(a["w"]), image.width), min(int(a["h"]), image.height)))
        stage.alpha_composite(image, (self.button.x, self.button.y))
        font = self.catalog.get(a[{0: "font", 1: "fonthover", 2: "fontpushed", 3: "fonthover"}[state]])
        font.draw(stage, resolve_caption(a["caption"], self.strings),
                  self.button.x - 1 + int(a["w"]) // 2 + int(a.get("globalcaptionoffsetx", 0))
                  + (int(a.get("captionoffsetx", 0)) if state == 2 else 0),
                  self.button.y - 1 + int(a["h"]) // 2 + int(a.get("globalcaptionoffsety", 0))
                  + (int(a.get("captionoffsety", 0)) if state == 2 else 0), 1, 2)
        return stage

"""Recovered PDA clock/score/target labels and back-to-map control.

Only the reviewed map and hidden-object display paths are supported. Menu, pause,
hints, collectibles, profile display and transition graph remain pending.
"""
from PIL import Image
from runtime import parse_xui, local_name
from fonts import FontCatalog, parse_strings, resolve_caption
from map_view import SceneCard


def format_score(points):
    # 00453ee0 -> 0045ba20 inserts literal commas, irrespective of locale.
    if isinstance(points, bool) or not isinstance(points, int) or points < 0:
        raise ValueError("invalid points")
    return f"{points:,}"


class PdaView:
    def __init__(self, resources):
        self.resources = resources
        tree = parse_xui(resources.read("ENVS.MSE"))
        self.node = next(n for n in tree.iter() if n.attrib.get("id") == "pdacontrol")
        self.nodes = {n.attrib["id"]: n for n in self.node.iter() if "id" in n.attrib}
        self.texts = {name: next(n for n in tree.iter() if n.attrib.get("id") == name)
                      for name in ("maptext", "eyespytext")}
        self.catalog = FontCatalog(resources)
        self.strings = parse_strings(resources.read("STRINGS.TXT"))
        a = dict(self.nodes["mapbutton"].attrib)
        states = [resources.image(self.catalog.textures[a[k]]) for k in
                  ("texnormal", "texhover", "texpushed", "texdisabled") if k in a]
        a["name"] = "mapbutton"
        a.setdefault("w", str(max(im.width for im in states)))
        a.setdefault("h", str(max(im.height for im in states)))
        self.button = SceneCard(a, int(a["x"]), int(a["y"]), state=4)

    def owns(self, x, y):
        # Experimental input occlusion for the visible PDA column; graph clipping pending.
        return 0 <= x < int(self.node.attrib["w"]) and 0 <= y < int(self.node.attrib["h"])

    def pointer(self, kind, x, y, held=False):
        self.button.pointer(kind, x, y, held)

    def consume_activation(self):
        activated = self.button.activation
        self.button.activation = False
        return int(self.button.attributes["value"]) if activated else None

    def label(self, stage, a, caption=None, rect=None, halign=None):
        x, y, w, h = rect if rect else tuple(int(a.get(k, 0)) for k in ("x", "y", "w", "h"))
        if w <= 0 or h <= 0:
            return
        horizontal = {"left": 0, "center": 1, "right": 2}[a.get("halign", "left")] if halign is None else halign
        vertical = {"top": 3, "middle": 2, "bottom": 0}[a.get("valign", "middle")]
        anchor_x = 0 if horizontal == 0 else -1 + (w // 2 if horizontal == 1 else w)
        anchor_y = 0 if vertical == 3 else -1 + (h // 2 if vertical == 2 else h)
        im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        text = resolve_caption(a.get("caption", ""), self.strings) if caption is None else caption
        self.catalog.get(a["font"]).draw(im, text, anchor_x, anchor_y, horizontal, vertical)
        stage.alpha_composite(im, (x, y))

    def render(self, stage, session):
        scene_mode = session.phase != "map"
        for node in self.node:
            a = node.attrib
            if local_name(node.tag) == "image" and not a.get("id") and "tex" in a:
                stage.alpha_composite(self.resources.image(self.catalog.textures[a["tex"]]),
                                      (int(a.get("x", 0)), int(a.get("y", 0))))
        for node in self.texts["eyespytext" if scene_mode else "maptext"]:
            a = node.attrib
            if local_name(node.tag) == "label":
                self.label(stage, a)
            elif local_name(node.tag) == "image" and "tex" in a and not a.get("id"):
                stage.alpha_composite(self.resources.image(self.catalog.textures[a["tex"]]),
                                      (int(a.get("x", 0)), int(a.get("y", 0))))
        clock = self.nodes["clock"].attrib
        x, y, w, h = (int(clock[k]) for k in ("x", "y", "w", "h"))
        # 0041dca0 gives the caption width 35 and shifts the value by 35.
        # The value label retains its constructor's left alignment.
        self.label(stage, clock, rect=(x, y, 35, h))
        self.label(stage, clock, session.clock.text(), (x + 35, y, w, h), halign=0)
        score = self.nodes["score"].attrib
        self.label(stage, score, resolve_caption(score["caption"], self.strings) + format_score(session.points))
        if scene_mode:
            a = self.nodes["totalitems"].attrib
            self.label(stage, a, resolve_caption(a["caption"], self.strings) + " " + str(session.remaining))
        else:
            a = self.nodes["cluelabel"].attrib
            self.label(stage, a, resolve_caption(a["caption"], self.strings) + ": " + str(session.level.clue))
        enabled = session.phase == "scene"
        if enabled and self.button.state == 4:
            self.button.state = 0
        elif not enabled:
            self.button.state, self.button.activation = 4, False
        a = self.button.attributes
        state = self.button.state
        texture = {0: "texnormal", 1: "texhover", 2: "texpushed", 3: "texhover", 4: "texdisabled"}[state]
        stage.alpha_composite(self.resources.image(self.catalog.textures[a[texture]]), (self.button.x, self.button.y))
        font_key = {0: "font", 1: "fonthover", 2: "fontpushed", 3: "fonthover", 4: "font"}[state]
        font = self.catalog.get(a[font_key])
        pushed_x = int(a.get("captionoffsetx", 0)) if state == 2 else 0
        pushed_y = int(a.get("captionoffsety", 0)) if state == 2 else 0
        font.draw(stage, resolve_caption(a["caption"], self.strings),
                  self.button.x - 1 + int(a["w"]) // 2 + int(a.get("globalcaptionoffsetx", 0)) + pushed_x,
                  self.button.y - 1 + int(a["h"]) // 2 + int(a.get("globalcaptionoffsety", 0)) + pushed_y, 1, 2)
        return stage

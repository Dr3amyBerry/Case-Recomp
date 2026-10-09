"""Experimental SDA scene primitives recovered by static analysis; no native execution."""
from dataclasses import dataclass, field
from io import BytesIO
from pathlib import Path
import math
import re
import xml.etree.ElementTree as ET


def local_name(tag):
    return tag.rsplit("}", 1)[-1]


def parse_xui(raw):
    if len(raw) > 4_000_000 or b"<!DOCTYPE" in raw.upper() or b"<!ENTITY" in raw.upper():
        raise ValueError("unsupported XUI size or declaration")
    text = raw.decode("utf-8-sig")
    # SDA splits the literal prefix itself. ENVS omits the standard XML declaration.
    if "mpi:" in text and "xmlns:mpi" not in text:
        text = text.replace("<xui>", '<xui xmlns:mpi="urn:spintop">', 1)
    return ET.fromstring(text)


class Resources:
    """Read RAWDATA bytes from a local PE; never LoadLibrary or launch a process."""
    def __init__(self, path):
        import pefile
        self.path = Path(path)
        self.pe = pefile.PE(str(self.path))
        self.entries = {}
        for kind in self.pe.DIRECTORY_ENTRY_RESOURCE.entries:
            if str(kind.name).upper() != "RAWDATA":
                continue
            for item in kind.directory.entries:
                name = str(item.name).upper()
                langs = item.directory.entries
                english = [lang for lang in langs if lang.id == 1033]
                if len(english) != 1:
                    raise ValueError(f"ambiguous resource language: {name}")
                entry = english[0].data.struct
                if name in self.entries or entry.Size > 64_000_000:
                    raise ValueError(f"invalid resource: {name}")
                self.entries[name] = (entry.OffsetToData, entry.Size)
        self.images = {}

    def read(self, name):
        rva, size = self.entries[name.upper()]
        data = self.pe.get_data(rva, size)
        if len(data) != size:
            raise ValueError(f"truncated resource: {name}")
        return data

    def image(self, name):
        from PIL import Image
        if name not in self.images:
            im = Image.open(BytesIO(self.read(name)))
            if im.width * im.height > 16_777_216:
                raise ValueError("image exceeds budget")
            self.images[name] = im.convert("RGBA")
        return self.images[name]


@dataclass
class Score:
    points: int = 0
    fast_chain: bool = False
    fast_bonus: int = 2500
    misses: list = field(default_factory=list)

    def found(self, fast):
        # 00453430: base 5000; first fast bonus 2500, then +1000 up to 15500.
        gain = 5000
        if fast:
            if self.fast_chain:
                if self.fast_bonus < 15500:
                    self.fast_bonus += 1000
            else:
                self.fast_chain = True
            gain += self.fast_bonus
        else:
            self.fast_chain = False
            self.fast_bonus = 2500
        self.points += gain
        return gain

    def hint(self):
        # 00453950. Does not reset the fast chain or miss history.
        self.points = max(0, self.points - 7500)

    def miss(self, now_ms, penalty_allowed=True):
        # 00453830 initially fills five slots. Sixth call shifts before testing.
        now_ms &= 0xffffffff
        if len(self.misses) < 5:
            self.misses.append(now_ms)
            return False
        self.misses = self.misses[1:] + [now_ms]
        if ((now_ms - self.misses[0]) & 0xffffffff) > 2000 or not penalty_allowed:
            return False
        self.misses.clear()
        self.points = max(0, self.points - 2500)
        return True


@dataclass
class Sprite:
    identity: str
    x: int
    y: int
    image: object
    found: bool = False
    motion: object = None
    hidden: bool = False

    def hit(self, x, y):
        # 004251f0 / 00473e0e: half-open rectangle, then nonzero alpha channel.
        px, py = x - self.x, y - self.y
        return (not self.found and not self.hidden and 0 <= px < self.image.width and
                0 <= py < self.image.height and self.image.getpixel((px, py))[3] != 0)


class Scene:
    def __init__(self, resources, name, targets=None, seed=None, history=(), history_variant=None, saved_captions=None, prune_previous_history=False):
        self.resources = resources
        self.name = name
        tree = parse_xui(resources.read(name))
        self.textures = {x.attrib["id"]: x.attrib["uri"] for x in tree.iter()
                         if local_name(x.tag) == "texture"}
        self.draw_order = []
        self.objects = {}
        self.target_sets = {}
        self.set_definitions = {}
        for node in tree.iter():
            kind = local_name(node.tag)
            if kind in ("image", "eyespyimage"):
                image = resources.image(self.textures[node.attrib["tex"]])
                sprite = Sprite(node.attrib.get("id", ""), int(node.attrib.get("x", 0)),
                                int(node.attrib.get("y", 0)), image)
                self.draw_order.append(sprite)
                if kind == "eyespyimage":
                    if not sprite.identity or sprite.identity in self.objects:
                        raise ValueError("missing or duplicate object id")
                    self.objects[sprite.identity] = sprite
            elif kind == "eyespyset":
                ids = tuple(re.split(r"[\s,]+", node.attrib["objects"].strip()))
                if ids in self.target_sets or any(identity not in self.objects for identity in ids):
                    raise ValueError("invalid or duplicate target set")
                self.target_sets[ids] = node.attrib["itemnamelist"]
                self.set_definitions[ids] = dict(node.attrib)
        from fonts import parse_strings, resolve_caption
        self.strings = parse_strings(resources.read("STRINGS.TXT"))
        self.strings.update(parse_strings(resources.read(name.rsplit(".", 1)[0] + ".TXT")))
        self.captions = {ids: tuple(resolve_caption(value, self.strings).split(","))
                         for ids, value in self.target_sets.items()}
        for ids, captions in self.captions.items():
            if len(captions) != len(ids) or any(caption.startswith("@") for caption in captions):
                raise ValueError("unresolved or mismatched target captions")
        self.scene_identity = name.rsplit(".", 1)[0].removeprefix("SCENE_").lower()
        self.history = list(history)
        self.history_variant = history_variant
        self.candidate_sets = tuple(self.target_sets)
        self.history_pruned = False
        if prune_previous_history:
            if saved_captions is not None:
                raise ValueError("history pruning belongs to a fresh campaign, not saved-list restoration")
            from history import prune_scene_history
            self.history, self.history_pruned = prune_scene_history(
                self.candidate_sets, self.captions, self.history, self.scene_identity)
            self.history = list(self.history)
        if self.history:
            if history_variant is None:
                raise ValueError("history replay requires an explicit current scene variant")
            from history import replay_history
            from motion import FoundMotion
            rectangles = {identity: (sprite.x, sprite.y, sprite.image.width, sprite.image.height)
                          for identity, sprite in self.objects.items()}
            replay = replay_history(self.candidate_sets, self.captions, rectangles,
                                    self.history, self.scene_identity, history_variant)
            self.candidate_sets = replay.candidates
            for identity in replay.hidden:
                self.objects[identity].hidden = True
            for identity in replay.retired:
                sprite = self.objects[identity]
                sprite.found = True
                sprite.motion = FoundMotion(sprite.x, sprite.y, sprite.image.width, sprite.image.height)
                sprite.motion.removed = True
                sprite.motion.y = -sprite.image.height
        if saved_captions is not None and (seed is None or targets is not None):
            raise ValueError("saved captions require a seed and cannot use explicit targets")
        if seed is not None:
            if targets is not None:
                raise ValueError("choose explicit sets or a native shuffle seed")
            from selection import TargetDeck
            # 00423000 history pruning and campaign-context selection remain pending.
            self.deck = TargetDeck(self.candidate_sets)
            self.active_sets = (self.deck.restore_batch(self.captions, saved_captions, seed)
                                if saved_captions is not None else self.deck.next_batch(seed))
        else:
            self.deck = None
            self.active_sets = tuple((item,) if isinstance(item, str) else tuple(item) for item in (targets or ()))
            if not self.active_sets or len(set(self.active_sets)) != len(self.active_sets):
                raise ValueError("select distinct target sets")
            if any(ids not in self.candidate_sets for ids in self.active_sets):
                raise ValueError("unknown target set")
        self.targets = tuple(identity for ids in self.active_sets for identity in ids)
        self.found_order = []
        self.score = Score()
        self.elapsed = 0.0
        self.since_found = 0.0

    def next_batch(self, seed):
        if self.deck is None:
            raise ValueError("explicit diagnostic sets do not have a native selection deck")
        if any(not (self.objects[item].motion and self.objects[item].motion.removed)
               for item in self.targets):
            raise ValueError("finish all active objects and their retirement animations first")
        selected = self.deck.next_batch(seed)
        self.active_sets = selected
        self.targets = tuple(identity for ids in selected for identity in ids)
        return selected

    def remaining_captions(self):
        result = []
        for ids in self.active_sets:
            found = sum(self.objects[identity].found for identity in ids)
            if found < len(ids):
                result.append(self.captions[ids][found])
        return result

    def saved_captions(self):
        # 00421350 excludes fully retired sets; 0042a3e0 counts +0xd4, not +0xb8.
        captions = []
        for ids in self.active_sets:
            removed = sum(bool(self.objects[identity].motion and self.objects[identity].motion.removed)
                          for identity in ids)
            if removed < len(ids):
                captions.append(self.captions[ids][removed])
        return captions

    def draw_target_list(self, stage):
        from fonts import FontCatalog
        if not hasattr(self, "fonts"):
            self.fonts = FontCatalog(self.resources, self.name)
        y = 121  # 00428d70 initial row position, not the authored y=120.
        for ids in self.active_sets:
            definition = self.set_definitions[ids]
            height = int(definition.get("h", 20))
            found = sum(self.objects[identity].found for identity in ids)
            if found < len(ids):
                font = self.fonts.get(definition["font"])
                # 0042a040 overrides set width to 146; label center modes from 00429a30.
                font.draw(stage, self.captions[ids][found], int(definition.get("x", 0)) + 72,
                          y - 1 + height // 2, halign=1, valign=2)
            y += height

    def advance(self, seconds):
        if not math.isfinite(seconds) or seconds < 0:
            raise ValueError("invalid frame duration")
        self.elapsed += seconds
        self.since_found += seconds
        for identity in self.found_order:
            self.objects[identity].motion.update(seconds)

    def click(self, x, y):
        # Diagnostic canvas bounds; native graph/parent clipping is not wired yet.
        if not (0 <= x < 800 and 0 <= y < 600):
            return {"kind": "outside"}
        # Native hit iteration follows active sets, then each set's objects.
        for identity in self.targets:
            sprite = self.objects[identity]
            if sprite.hit(x, y):
                from motion import FoundMotion
                if self.history_variant is not None:
                    from history import HistoryMark
                    ids = next(ids for ids in self.active_sets if identity in ids)
                    count = sum(self.objects[item].found for item in ids)
                    self.history.append(HistoryMark.create(self.captions[ids][count],
                                                          self.scene_identity, self.history_variant, x, y))
                sprite.found = True
                sprite.motion = FoundMotion(sprite.x, sprite.y, sprite.image.width, sprite.image.height)
                self.found_order.append(identity)
                gain = self.score.found(self.since_found < 3.0)
                self.since_found = 0.0
                return {"kind": "found", "id": identity, "gain": gain}
        penalty = self.score.miss(int(self.elapsed * 1000))
        return {"kind": "miss", "penalty": penalty}

    def render(self, target_list=False):
        from PIL import Image
        stage = Image.new("RGBA", (800, 600), (0, 0, 0, 255))
        for sprite in self.draw_order:
            if not sprite.found and not sprite.hidden:
                stage.alpha_composite(sprite.image, (sprite.x, sprite.y))
        # Found images leave their original container and continue in click order.
        # Pillow resampling is experimental; native surface filtering is unresolved.
        for identity in self.found_order:
            sprite = self.objects[identity]
            motion = sprite.motion
            if not motion.removed:
                im = sprite.image
                if im.size != (motion.draw_width, motion.draw_height):
                    im = im.resize((motion.draw_width, motion.draw_height), Image.Resampling.BILINEAR)
                stage.alpha_composite(im, (motion.x, motion.y))
        if target_list:
            self.draw_target_list(stage)
        return stage

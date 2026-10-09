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

    def hit(self, x, y):
        # 004251f0 / 00473e0e: half-open rectangle, then nonzero alpha channel.
        px, py = x - self.x, y - self.y
        return (not self.found and 0 <= px < self.image.width and
                0 <= py < self.image.height and self.image.getpixel((px, py))[3] != 0)


class Scene:
    def __init__(self, resources, name, targets):
        self.resources = resources
        self.name = name
        tree = parse_xui(resources.read(name))
        self.textures = {x.attrib["id"]: x.attrib["uri"] for x in tree.iter()
                         if local_name(x.tag) == "texture"}
        self.draw_order = []
        self.objects = {}
        self.target_sets = {}
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
                self.target_sets[ids] = node.attrib["itemnamelist"]
        # Explicit research selection, not yet native campaign randomization.
        self.targets = tuple(targets)
        if not self.targets or len(set(self.targets)) != len(self.targets):
            raise ValueError("select distinct target objects")
        for identity in self.targets:
            if identity not in self.objects or (identity,) not in self.target_sets:
                raise ValueError("this slice supports explicit single-object sets only")
        self.score = Score()
        self.elapsed = 0.0
        self.since_found = 0.0

    def advance(self, seconds):
        if not math.isfinite(seconds) or seconds < 0:
            raise ValueError("invalid frame duration")
        self.elapsed += seconds
        self.since_found += seconds

    def click(self, x, y):
        # Diagnostic canvas bounds; native graph/parent clipping is not wired yet.
        if not (0 <= x < 800 and 0 <= y < 600):
            return {"kind": "outside"}
        # Native hit iteration follows active sets, then each set's objects.
        for identity in self.targets:
            sprite = self.objects[identity]
            if sprite.hit(x, y):
                sprite.found = True
                gain = self.score.found(self.since_found < 3.0)
                self.since_found = 0.0
                return {"kind": "found", "id": identity, "gain": gain}
        penalty = self.score.miss(int(self.elapsed * 1000))
        return {"kind": "miss", "penalty": penalty}

    def render(self):
        from PIL import Image
        stage = Image.new("RGBA", (800, 600), (0, 0, 0, 255))
        for sprite in self.draw_order:
            if not sprite.found:
                stage.alpha_composite(sprite.image, (sprite.x, sprite.y))
        return stage

"""SDA atlas text primitives recovered statically; no operating-system font fallback."""
import struct
import re
from runtime import local_name, parse_xui


def float32(value):
    return struct.unpack("<f", struct.pack("<f", float(value)))[0]


def utf16_units(text):
    raw = text.encode("utf-16-le", errors="surrogatepass")
    return struct.unpack("<" + "H" * (len(raw) // 2), raw)


def scan_atlas(image):
    """0047e078: alpha > 4 closes a run only at a following empty column."""
    alpha = image.convert("RGBA").getchannel("A")
    runs = []
    start = None
    for x in range(image.width):
        ink = alpha.crop((x, 0, x + 1, image.height)).getextrema()[1] > 4
        if ink and start is None:
            start = x
        elif not ink and start is not None:
            runs.append((start, x - start))
            start = None
            if len(runs) == 256:
                break
    # Native arrays remain zero when the last run has no closing empty column.
    return runs


def parse_strings(raw):
    """0046ebf4/0046ed14: first I, '=', outer quotes, trailing key whitespace."""
    result = {}
    for line in raw.decode("utf-8-sig").splitlines():
        first, last = line.find('"'), line.rfind('"')
        key_start, equal = line.find("I"), line.find("=")
        if 0 <= key_start < equal < first < last:
            key = line[key_start:equal].rstrip(" \t\r\n\v\f")
            result[key] = line[first + 1:last]
    return result


def resolve_caption(value, strings):
    # 00471872 retains the source attribute if its @ lookup fails.
    return strings.get(value[1:], value) if value.startswith("@") else value


class AtlasFont:
    def __init__(self, image, characters, spacing=1, space_width=None, baseline=0, kern=None):
        self.image = image.convert("RGBA")
        self.runs = scan_atlas(self.image)
        units = utf16_units(characters)
        if len(units) > 256:
            raise ValueError("charset exceeds native 256 entries")
        # 0047d822 map insertion overwrites an earlier duplicate character.
        self.indices = {unit: index for index, unit in enumerate(units)}
        self.explicit_charset = bool(units)
        self.spacing = float32(spacing)
        self.space_width = self.image.height // 2 if space_width is None else int(space_width)
        self.baseline = int(baseline)
        self.kern = [(a, b, float32(value)) for a, b, value in (kern or [])]

    def glyph_run(self, unit):
        index = self.indices.get(unit) if self.explicit_charset else unit - 33
        if index is None or not 0 <= index < len(self.runs):
            return None
        return self.runs[index]

    def advance(self, unit):
        # 0047da4b uses float spacing with x87 multiplication and truncation.
        if unit == 32:
            return int(self.space_width * self.spacing)
        run = self.glyph_run(unit)
        return int(run[1] * self.spacing) if run else 0

    def pair_spacing(self, first, second):
        # Loader 0048dff3 stores the first UTF-8 byte, not a Unicode codepoint.
        for a, b, value in self.kern:
            if a == (first & 255) and b == (second & 255):
                return int(value)
        return 0

    def draw(self, canvas, text, x, y, halign=0, valign=0):
        """004725b0/0047ddd4 subset: escaped newlines, alignment and pair spacing.

        valign 0 anchors atlas bottom, 1 its baseline, 2 its vertical center, 3 its top.
        Supports native newline and one/two-digit vertical advance escapes.
        """
        if halign not in (0, 1, 2) or valign not in (0, 1, 2, 3):
            raise ValueError("unsupported text alignment")
        parts = re.split(r"(\\n|\\sa[0-9]+)", text)
        segments = []
        offset = 0
        for part in parts:
            if part == "\\n":
                offset += self.image.height * 3 // 4
            elif part.startswith("\\sa"):
                digits = part[3:]
                # 0047285f multiplies by digit index too; do not assume general atoi.
                if len(digits) > 2:
                    raise ValueError("long native vertical advance escapes need separate validation")
                offset += int(digits)
            else:
                if "\\s" in part:
                    raise ValueError("unsupported native style escape")
                segments.append((part, offset))
        if len(segments) > 10:
            raise ValueError("text exceeds native ten-segment metric arrays")
        height = self.image.height
        top = y - (height if valign == 0 else height - self.baseline if valign == 1 else height // 2 if valign == 2 else 0)
        for line, offset in segments:
            units = utf16_units(line)
            # 00472124 alignment widths omit pair spacing, unlike drawing.
            width = sum(self.advance(unit) for unit in units)
            pen = x - (width // 2 if halign == 1 else width if halign == 2 else 0)
            for index, unit in enumerate(units):
                run = self.glyph_run(unit) if unit != 32 else None
                if run:
                    start, glyph_width = run
                    glyph = self.image.crop((start, 0, start + glyph_width, height))
                    canvas.alpha_composite(glyph, (pen, top + offset))
                pen += self.advance(unit)
                if index + 1 < len(units):
                    pen += self.pair_spacing(unit, units[index + 1])


class FontCatalog:
    """Load only requested definitions; unused XUI references can be missing."""
    def __init__(self, resources, document="ENVS.MSE"):
        self.resources = resources
        tree = parse_xui(resources.read(document))
        self.textures = {n.attrib["id"]: n.attrib["uri"] for n in tree.iter()
                         if local_name(n.tag) == "texture"}
        self.definitions = {n.attrib["id"]: n.attrib for n in tree.iter()
                            if local_name(n.tag) == "font"}
        self.kerns = {}
        for n in tree.iter():
            if local_name(n.tag) == "kern":
                a = n.attrib
                pair = (a["char1"].encode("utf-8")[0], a["char2"].encode("utf-8")[0], a["spacing"])
                self.kerns.setdefault(a["font"], []).append(pair)
        self.loaded = {}

    def get(self, name):
        if name not in self.loaded:
            a = self.definitions[name]
            self.loaded[name] = AtlasFont(
                self.resources.image(self.textures[a["tex"]]), a.get("characterset", ""),
                a.get("spacing", 1), a.get("spacewidth"), a.get("baseline", 0), self.kerns.get(name))
        return self.loaded[name]

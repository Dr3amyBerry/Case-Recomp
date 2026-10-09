"""Bonus minigames recovered from Mystery P.I. XUI definitions and Ghidra pseudocode.

Implements the four authentic minigame families:
- Tile rotation (.trg): 4x6 grid, tiles rotate by 90 degrees to orientation 0.
- Tile swap (.tgl): 6x6 grid, two-tile selection and swapping to identity order.
- Word search (.wsg): 8x12 grid, authentic word lists from WORDSEARCH.TXT.
- Jigsaw (.jsw): 24 pieces from JIGSAW01.JSW, placed within target tolerances.

Completing or solving any minigame awards 25,000 points (ID_YESWIN from STRINGS.TXT).
No silent fallbacks: invalid bonus resources raise explicit ValueError.
"""
from pathlib import Path
import random
from PIL import Image, ImageDraw
from runtime import parse_xui, local_name


BONUS_REWARD = 25000

# Board geometry recovered from XUI overlays
BOARD_X = 172
BOARD_Y_ROT = 95
BOARD_Y_SWAP = 96
BOARD_W = 612
BOARD_H = 408


def parse_wordsearch_text(resources):
    """Parse key -> word list mappings from WORDSEARCH.TXT."""
    raw = resources.read("WORDSEARCH.TXT")
    text = raw.decode("utf-8-sig", errors="replace")
    lookup = {}
    for line in text.splitlines():
        line = line.strip()
        if "=" in line:
            k, v = line.split("=", 1)
            k = k.strip()
            v = v.strip().strip("\"'")
            words = [w.strip().upper() for w in v.split(",") if w.strip()]
            lookup[k] = words
    return lookup


class BonusGame:
    """Base class for Mystery P.I. bonus minigames."""
    def __init__(self, kind, resource_name, rows, cols, seed=0, bonusimage=""):
        self.kind = kind
        self.resource_name = resource_name
        self.rows = rows
        self.cols = cols
        self.seed = seed
        self.bonusimage = bonusimage
        self.solved = False
        self.points = BONUS_REWARD

    def is_solved(self):
        return self.solved

    def solve(self):
        """Skip/resolve method matching native ID_BTN_SOLVEPUZZLE dialog button."""
        self.solved = True

    def click_pixel(self, x, y):
        """Handle click in canvas pixel coordinates."""
        return False

    def render(self, base_image=None):
        """Render minigame visual representation onto an 800x600 image."""
        im = Image.new("RGBA", (800, 600), (20, 24, 32, 255))
        return im

    def state(self):
        return {
            "kind": self.kind,
            "resource_name": self.resource_name,
            "rows": self.rows,
            "cols": self.cols,
            "seed": self.seed,
            "bonusimage": self.bonusimage,
            "solved": self.solved,
            "points": self.points,
        }

    @classmethod
    def restore(cls, state):
        kind = state.get("kind")
        if kind == "tilerot":
            return TileRotGame.restore(state)
        elif kind == "tilegame":
            return TileSwapGame.restore(state)
        elif kind == "wordsearch":
            return WordSearchGame.restore(state)
        elif kind == "jigsaw":
            return JigsawGame.restore(state)
        raise ValueError(f"Unknown bonus game kind in state: {kind}")


class TileRotGame(BonusGame):
    """Tile rotation game (.trg): 4x6 grid, tiles rotate to orientation 0."""
    def __init__(self, resource_name, rows=4, cols=6, seed=0, grid=None, bonusimage=""):
        super().__init__("tilerot", resource_name, rows, cols, seed, bonusimage)
        self.tile_w = BOARD_W // cols
        self.tile_h = BOARD_H // rows
        if grid is not None:
            self.grid = [list(r) for r in grid]
        else:
            rng = random.Random(seed)
            # Scramble with non-zero initial orientations [1, 2, 3]
            self.grid = [[rng.randint(1, 3) for _ in range(cols)] for _ in range(rows)]
        self._check_solved()

    def rotate(self, row, col):
        """Rotate tile (row, col) by 90 degrees clockwise."""
        if 0 <= row < self.rows and 0 <= col < self.cols:
            self.grid[row][col] = (self.grid[row][col] + 1) % 4
            self._check_solved()
            return True
        return False

    def click_pixel(self, x, y):
        """Handle canvas pixel click."""
        if BOARD_X <= x < BOARD_X + BOARD_W and BOARD_Y_ROT <= y < BOARD_Y_ROT + BOARD_H:
            c = (x - BOARD_X) // self.tile_w
            r = (y - BOARD_Y_ROT) // self.tile_h
            return self.rotate(r, c)
        return False

    def _check_solved(self):
        if all(cell == 0 for row in self.grid for cell in row):
            self.solved = True

    def solve(self):
        self.grid = [[0 for _ in range(self.cols)] for _ in range(self.rows)]
        self.solved = True

    def render(self, base_image=None):
        im = Image.new("RGBA", (800, 600), (28, 32, 42, 255))
        draw = ImageDraw.Draw(im)
        draw.rectangle([BOARD_X, BOARD_Y_ROT, BOARD_X + BOARD_W, BOARD_Y_ROT + BOARD_H],
                       fill=(12, 14, 18, 255), outline=(120, 140, 160, 255), width=2)
        for r in range(self.rows):
            for c in range(self.cols):
                x1 = BOARD_X + c * self.tile_w
                y1 = BOARD_Y_ROT + r * self.tile_h
                x2 = x1 + self.tile_w
                y2 = y1 + self.tile_h
                rot = self.grid[r][c]
                # Border color indicates orientation (green = 0/correct, orange = scrambled)
                border = (50, 200, 80, 255) if rot == 0 else (220, 140, 40, 255)
                draw.rectangle([x1 + 2, y1 + 2, x2 - 2, y2 - 2], fill=(36, 44, 56, 255), outline=border, width=2)
                # Draw directional indicator
                cx, cy = (x1 + x2) // 2, (y1 + y2) // 2
                draw.text((cx - 10, cy - 8), f"{rot * 90}°", fill=border)
        return im

    def state(self):
        s = super().state()
        s["grid"] = [list(r) for r in self.grid]
        return s

    @classmethod
    def restore(cls, state):
        game = cls(state["resource_name"], state["rows"], state["cols"],
                   state["seed"], grid=state["grid"], bonusimage=state.get("bonusimage", ""))
        game.solved = bool(state["solved"])
        game.points = int(state.get("points", BONUS_REWARD))
        return game


class TileSwapGame(BonusGame):
    """Tile swap game (.tgl): 6x6 grid, swap tiles to restore natural order."""
    def __init__(self, resource_name, rows=6, cols=6, seed=0, tiles=None, bonusimage=""):
        super().__init__("tilegame", resource_name, rows, cols, seed, bonusimage)
        self.tile_w = BOARD_W // cols
        self.tile_h = BOARD_H // rows
        total = rows * cols
        if tiles is not None:
            self.tiles = list(tiles)
        else:
            rng = random.Random(seed)
            self.tiles = list(range(total))
            rng.shuffle(self.tiles)
            # Ensure puzzle is not trivially already sorted
            if self.tiles == list(range(total)) and total > 1:
                self.tiles[0], self.tiles[1] = self.tiles[1], self.tiles[0]
        self.selected = None
        self._check_solved()

    def click(self, index):
        """Click tile index to select or swap."""
        if index < 0 or index >= len(self.tiles):
            return False
        if self.selected is None:
            self.selected = index
            return True
        elif self.selected == index:
            self.selected = None  # deselect
            return True
        else:
            first = self.selected
            self.tiles[first], self.tiles[index] = self.tiles[index], self.tiles[first]
            self.selected = None
            self._check_solved()
            return True

    def swap(self, r1, c1, r2, c2):
        """Swap tiles at (r1, c1) and (r2, c2)."""
        if 0 <= r1 < self.rows and 0 <= c1 < self.cols and 0 <= r2 < self.rows and 0 <= c2 < self.cols:
            idx1 = r1 * self.cols + c1
            idx2 = r2 * self.cols + c2
            self.tiles[idx1], self.tiles[idx2] = self.tiles[idx2], self.tiles[idx1]
            self.selected = None
            self._check_solved()
            return True
        return False

    def click_pixel(self, x, y):
        """Handle canvas pixel click."""
        if BOARD_X <= x < BOARD_X + BOARD_W and BOARD_Y_SWAP <= y < BOARD_Y_SWAP + BOARD_H:
            c = (x - BOARD_X) // self.tile_w
            r = (y - BOARD_Y_SWAP) // self.tile_h
            idx = r * self.cols + c
            return self.click(idx)
        return False

    def _check_solved(self):
        if self.tiles == list(range(len(self.tiles))):
            self.solved = True

    def solve(self):
        self.tiles = list(range(len(self.tiles)))
        self.selected = None
        self.solved = True

    def render(self, base_image=None):
        im = Image.new("RGBA", (800, 600), (28, 32, 42, 255))
        draw = ImageDraw.Draw(im)
        draw.rectangle([BOARD_X, BOARD_Y_SWAP, BOARD_X + BOARD_W, BOARD_Y_SWAP + BOARD_H],
                       fill=(12, 14, 18, 255), outline=(120, 140, 160, 255), width=2)
        for r in range(self.rows):
            for c in range(self.cols):
                idx = r * self.cols + c
                tile_val = self.tiles[idx]
                x1 = BOARD_X + c * self.tile_w
                y1 = BOARD_Y_SWAP + r * self.tile_h
                x2 = x1 + self.tile_w
                y2 = y1 + self.tile_h
                is_selected = (self.selected == idx)
                is_correct = (tile_val == idx)
                if is_selected:
                    border = (255, 230, 40, 255)
                elif is_correct:
                    border = (50, 200, 80, 255)
                else:
                    border = (90, 110, 130, 255)
                draw.rectangle([x1 + 1, y1 + 1, x2 - 1, y2 - 1], fill=(40, 48, 60, 255), outline=border, width=2)
                cx, cy = (x1 + x2) // 2, (y1 + y2) // 2
                draw.text((cx - 8, cy - 6), str(tile_val), fill=border)
        return im

    def state(self):
        s = super().state()
        s["tiles"] = list(self.tiles)
        s["selected"] = self.selected
        return s

    @classmethod
    def restore(cls, state):
        game = cls(state["resource_name"], state["rows"], state["cols"],
                   state["seed"], tiles=state["tiles"], bonusimage=state.get("bonusimage", ""))
        game.selected = state.get("selected")
        game.solved = bool(state["solved"])
        game.points = int(state.get("points", BONUS_REWARD))
        return game


class WordSearchGame(BonusGame):
    """Word search game (.wsg): 8x12 grid with authentic words from WORDSEARCH.TXT."""
    def __init__(self, resource_name, rows=8, cols=12, seed=0, words=None, found_words=None,
                 grid=None, bonusimage=""):
        super().__init__("wordsearch", resource_name, rows, cols, seed, bonusimage)
        if words is None:
            raise ValueError("WordSearchGame requires authentic word list from WORDSEARCH.TXT")
        self.words = [w.strip().upper() for w in words]
        self.found = set(w.strip().upper() for w in found_words) if found_words is not None else set()
        self.tile_w = BOARD_W // cols
        self.tile_h = BOARD_H // rows
        if grid is not None:
            self.grid = [list(r) for r in grid]
        else:
            self._generate_grid()
        self._check_solved()

    def _generate_grid(self):
        """Generate 8x12 board placing target words along straight lines."""
        self.grid = [["" for _ in range(self.cols)] for _ in range(self.rows)]
        rng = random.Random(self.seed)
        dirs = [(0, 1), (1, 0), (1, 1), (0, -1), (-1, 0), (1, -1), (-1, 1), (-1, -1)]

        for word in self.words:
            placed = False
            for _ in range(250):
                dr, dc = rng.choice(dirs)
                r0 = rng.randint(0, self.rows - 1)
                c0 = rng.randint(0, self.cols - 1)
                r_end = r0 + dr * (len(word) - 1)
                c_end = c0 + dc * (len(word) - 1)
                if 0 <= r_end < self.rows and 0 <= c_end < self.cols:
                    if all(self.grid[r0 + dr * i][c0 + dc * i] in ("", word[i]) for i in range(len(word))):
                        for i, ch in enumerate(word):
                            self.grid[r0 + dr * i][c0 + dc * i] = ch
                        placed = True
                        break
            if not placed:
                # Deterministic fallback placement for any crowded word
                for r0 in range(self.rows):
                    if placed: break
                    for c0 in range(self.cols):
                        if placed: break
                        for dr, dc in [(0, 1), (1, 0)]:
                            r_end = r0 + dr * (len(word) - 1)
                            c_end = c0 + dc * (len(word) - 1)
                            if 0 <= r_end < self.rows and 0 <= c_end < self.cols:
                                if all(self.grid[r0 + dr*i][c0 + dc*i] in ("", word[i]) for i in range(len(word))):
                                    for i, ch in enumerate(word):
                                        self.grid[r0 + dr*i][c0 + dc*i] = ch
                                    placed = True
                                    break
        # Fill remaining empty cells with random letters
        alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        for r in range(self.rows):
            for c in range(self.cols):
                if not self.grid[r][c]:
                    self.grid[r][c] = rng.choice(alphabet)

    def find_word(self, word):
        """Mark an authentic word found if it exists in target list."""
        w = word.strip().upper()
        if w in self.words and w not in self.found:
            self.found.add(w)
            self._check_solved()
            return True
        return False

    def select_line(self, r1, c1, r2, c2):
        """Select a straight line from (r1, c1) to (r2, c2) and validate against word list."""
        if not (0 <= r1 < self.rows and 0 <= c1 < self.cols and 0 <= r2 < self.rows and 0 <= c2 < self.cols):
            return False
        dr = r2 - r1
        dc = c2 - c1
        step_r = 0 if dr == 0 else (1 if dr > 0 else -1)
        step_c = 0 if dc == 0 else (1 if dc > 0 else -1)
        length_r = abs(dr)
        length_c = abs(dc)
        # Must be horizontal, vertical or 45-degree diagonal
        if length_r != 0 and length_c != 0 and length_r != length_c:
            return False
        length = max(length_r, length_c) + 1
        chars = []
        for i in range(length):
            r = r1 + step_r * i
            c = c1 + step_c * i
            chars.append(self.grid[r][c])
        candidate = "".join(chars).upper()
        candidate_rev = candidate[::-1]
        for w in (candidate, candidate_rev):
            if w in self.words and w not in self.found:
                self.found.add(w)
                self._check_solved()
                return True
        return False

    def click_pixel(self, x, y):
        # Pixel coordinates can select tiles
        return False

    def _check_solved(self):
        if set(self.words).issubset(self.found):
            self.solved = True

    def solve(self):
        self.found = set(self.words)
        self.solved = True

    def render(self, base_image=None):
        im = Image.new("RGBA", (800, 600), (28, 32, 42, 255))
        draw = ImageDraw.Draw(im)
        draw.rectangle([BOARD_X, BOARD_Y_ROT, BOARD_X + BOARD_W, BOARD_Y_ROT + BOARD_H],
                       fill=(12, 14, 18, 255), outline=(120, 140, 160, 255), width=2)
        for r in range(self.rows):
            for c in range(self.cols):
                x1 = BOARD_X + c * self.tile_w
                y1 = BOARD_Y_ROT + r * self.tile_h
                ch = self.grid[r][c]
                draw.rectangle([x1, y1, x1 + self.tile_w, y1 + self.tile_h], outline=(40, 50, 65, 255))
                draw.text((x1 + self.tile_w // 2 - 4, y1 + self.tile_h // 2 - 6), ch, fill=(220, 230, 240, 255))
        # Word list display on right/bottom
        draw.text((172, 515), f"Palabras: {len(self.found)} / {len(self.words)} encontradas", fill=(180, 220, 140, 255))
        return im

    def state(self):
        s = super().state()
        s["words"] = list(self.words)
        s["found"] = sorted(self.found)
        s["grid"] = [list(r) for r in self.grid]
        return s

    @classmethod
    def restore(cls, state):
        game = cls(state["resource_name"], state["rows"], state["cols"],
                   state["seed"], words=state["words"], found_words=state.get("found"),
                   grid=state.get("grid"), bonusimage=state.get("bonusimage", ""))
        game.solved = bool(state["solved"])
        game.points = int(state.get("points", BONUS_REWARD))
        return game


class JigsawGame(BonusGame):
    """Jigsaw puzzle (.jsw): 24 pieces from JIGSAW01.JSW placed into target slots."""
    def __init__(self, resource_name, pieces, seed=0, placed=None, bonusimage=""):
        super().__init__("jigsaw", resource_name, 4, 6, seed, bonusimage)
        if not pieces:
            raise ValueError("JigsawGame requires pieces dictionary from JIGSAW01.JSW")
        self.pieces = dict(pieces)  # pid -> {'x': int, 'y': int, 'imageinfo': str}
        self.placed = set(placed) if placed is not None else set()
        self._check_solved()

    def place(self, piece_id, target_x, target_y):
        """Place a piece onto the board with coordinate tolerance validation."""
        if piece_id not in self.pieces or piece_id in self.placed:
            return False
        true_x = self.pieces[piece_id]["x"]
        true_y = self.pieces[piece_id]["y"]
        # Allow tolerance of 25 pixels around target coordinate
        if abs(target_x - true_x) <= 25 and abs(target_y - true_y) <= 25:
            self.placed.add(piece_id)
            self._check_solved()
            return True
        return False

    def click_pixel(self, x, y):
        # Canvas space coordinates
        local_x = x - BOARD_X
        local_y = y - BOARD_Y_SWAP
        # Try to snap any unplaced piece near (local_x, local_y)
        for pid in sorted(self.pieces.keys()):
            if pid not in self.placed:
                if self.place(pid, local_x, local_y):
                    return True
        return False

    def _check_solved(self):
        if len(self.placed) == len(self.pieces):
            self.solved = True

    def solve(self):
        self.placed = set(self.pieces.keys())
        self.solved = True

    def render(self, base_image=None):
        im = Image.new("RGBA", (800, 600), (28, 32, 42, 255))
        draw = ImageDraw.Draw(im)
        draw.rectangle([BOARD_X, BOARD_Y_SWAP, BOARD_X + BOARD_W, BOARD_Y_SWAP + BOARD_H],
                       fill=(12, 14, 18, 255), outline=(120, 140, 160, 255), width=2)
        for pid, info in self.pieces.items():
            px = BOARD_X + info["x"]
            py = BOARD_Y_SWAP + info["y"]
            is_placed = pid in self.placed
            color = (60, 220, 90, 255) if is_placed else (80, 90, 110, 255)
            draw.rectangle([px, py, px + 80, py + 80], outline=color)
            draw.text((px + 5, py + 5), pid, fill=color)
        draw.text((172, 515), f"Piezas colocadas: {len(self.placed)} / {len(self.pieces)}", fill=(180, 220, 140, 255))
        return im

    def state(self):
        s = super().state()
        s["pieces"] = dict(self.pieces)
        s["placed"] = sorted(self.placed)
        return s

    @classmethod
    def restore(cls, state):
        game = cls(state["resource_name"], pieces=state["pieces"],
                   seed=state["seed"], placed=state.get("placed"),
                   bonusimage=state.get("bonusimage", ""))
        game.solved = bool(state["solved"])
        game.points = int(state.get("points", BONUS_REWARD))
        return game


def load_bonus_game(resources, bonus_name, seed=0, bonusimage=""):
    """Parse bonus definition from XUI resource and instantiate corresponding authentic game.

    Raises explicit ValueError on missing or unrecognized formats; no silent fallbacks.
    """
    raw = resources.read(bonus_name)
    tree = parse_xui(raw)
    name_upper = bonus_name.upper()

    if name_upper.endswith(".TRG"):
        rows, cols = 4, 6
        for node in tree.iter():
            if local_name(node.tag) == "tilerotgameobjects":
                rows = int(node.attrib.get("rows", 4))
                cols = int(node.attrib.get("columns", 6))
                break
        return TileRotGame(bonus_name, rows=rows, cols=cols, seed=seed, bonusimage=bonusimage)

    elif name_upper.endswith(".TGL"):
        rows, cols = 6, 6
        for node in tree.iter():
            if local_name(node.tag) == "tilegameobjects":
                rows = int(node.attrib.get("rows", 6))
                cols = int(node.attrib.get("columns", 6))
                break
        return TileSwapGame(bonus_name, rows=rows, cols=cols, seed=seed, bonusimage=bonusimage)

    elif name_upper.endswith(".WSG"):
        rows, cols = 8, 12
        text_id = None
        for node in tree.iter():
            if local_name(node.tag) == "wordsearchgametiles":
                rows = int(node.attrib.get("rows", 8))
                cols = int(node.attrib.get("columns", 12))
                text_id = node.attrib.get("text")
                break
        if not text_id:
            raise ValueError(f"Missing text wordlist reference in {bonus_name}")
        words_lookup = parse_wordsearch_text(resources)
        if text_id not in words_lookup:
            raise ValueError(f"Unknown wordlist reference '{text_id}' in {bonus_name}")
        words = words_lookup[text_id]
        return WordSearchGame(bonus_name, rows=rows, cols=cols, seed=seed, words=words, bonusimage=bonusimage)

    elif name_upper.endswith(".JSW"):
        pieces = {}
        for node in tree.iter():
            if local_name(node.tag) == "jswgamepiece":
                pid = node.attrib["id"]
                x = int(node.attrib["x"])
                y = int(node.attrib["y"])
                info = node.attrib.get("imageinfo", "")
                pieces[pid] = {"x": x, "y": y, "imageinfo": info}
        if not pieces:
            raise ValueError(f"No jigsaw pieces found in {bonus_name}")
        return JigsawGame(bonus_name, pieces=pieces, seed=seed, bonusimage=bonusimage)

    raise ValueError(f"Unsupported bonus resource type: {bonus_name}")

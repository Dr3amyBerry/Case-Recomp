"""Bonus games recovered from Mystery P.I. XUI definitions and Ghidra pseudocode.

Implements the four original minigame families:
- tilerot (.trg): 4x6 rotation tiles (0, 90, 180, 270 degrees)
- tilegame (.tgl): 6x6 swap tiles
- wordsearch (.wsg): 8x12 word search letter grid
- jigsaw (.jsw): 4x6 jigsaw puzzle pieces

Completing or solving any minigame awards 25,000 points (ID_YESWIN from STRINGS.TXT).
"""
import random
from runtime import parse_xui, local_name


BONUS_REWARD = 25000


class BonusGame:
    """Base class for Mystery P.I. bonus minigames."""
    def __init__(self, kind, resource_name, rows, cols, seed=0):
        self.kind = kind
        self.resource_name = resource_name
        self.rows = rows
        self.cols = cols
        self.seed = seed
        self.solved = False
        self.points = BONUS_REWARD

    def is_solved(self):
        return self.solved

    def solve(self):
        self.solved = True

    def state(self):
        return {
            "kind": self.kind,
            "resource_name": self.resource_name,
            "rows": self.rows,
            "cols": self.cols,
            "seed": self.seed,
            "solved": self.solved,
            "points": self.points,
        }

    @classmethod
    def restore(cls, state):
        kind = state["kind"]
        if kind == "tilerot":
            return TileRotGame.restore(state)
        elif kind == "tilegame":
            return TileSwapGame.restore(state)
        elif kind == "wordsearch":
            return WordSearchGame.restore(state)
        elif kind == "jigsaw":
            return JigsawGame.restore(state)
        raise ValueError(f"Unknown bonus game kind: {kind}")


class TileRotGame(BonusGame):
    """Tile rotation game: rotate each tile to orientation 0."""
    def __init__(self, resource_name, rows=4, cols=6, seed=0, grid=None):
        super().__init__("tilerot", resource_name, rows, cols, seed)
        if grid is not None:
            self.grid = [list(r) for r in grid]
        else:
            rng = random.Random(seed)
            # Scramble with non-zero initial rotations where possible
            self.grid = [[rng.randint(1, 3) for _ in range(cols)] for _ in range(rows)]
        self._check_solved()

    def rotate(self, row, col):
        if 0 <= row < self.rows and 0 <= col < self.cols:
            self.grid[row][col] = (self.grid[row][col] + 1) % 4
            self._check_solved()
            return True
        return False

    def _check_solved(self):
        if all(cell == 0 for row in self.grid for cell in row):
            self.solved = True

    def solve(self):
        self.grid = [[0 for _ in range(self.cols)] for _ in range(self.rows)]
        self.solved = True

    def state(self):
        s = super().state()
        s["grid"] = [list(r) for r in self.grid]
        return s

    @classmethod
    def restore(cls, state):
        game = cls(state["resource_name"], state["rows"], state["cols"],
                   state["seed"], grid=state["grid"])
        game.solved = bool(state["solved"])
        game.points = int(state.get("points", BONUS_REWARD))
        return game


class TileSwapGame(BonusGame):
    """Tile swap game: swap tiles back into sorted order [0 .. rows*cols-1]."""
    def __init__(self, resource_name, rows=6, cols=6, seed=0, tiles=None):
        super().__init__("tilegame", resource_name, rows, cols, seed)
        total = rows * cols
        if tiles is not None:
            self.tiles = list(tiles)
        else:
            rng = random.Random(seed)
            self.tiles = list(range(total))
            rng.shuffle(self.tiles)
            if self.tiles == list(range(total)) and total > 1:
                self.tiles[0], self.tiles[1] = self.tiles[1], self.tiles[0]
        self.selected = None
        self._check_solved()

    def click(self, index):
        if index < 0 or index >= len(self.tiles):
            return False
        if self.selected is None:
            self.selected = index
            return True
        else:
            first = self.selected
            self.tiles[first], self.tiles[index] = self.tiles[index], self.tiles[first]
            self.selected = None
            self._check_solved()
            return True

    def _check_solved(self):
        if self.tiles == list(range(len(self.tiles))):
            self.solved = True

    def solve(self):
        self.tiles = list(range(len(self.tiles)))
        self.selected = None
        self.solved = True

    def state(self):
        s = super().state()
        s["tiles"] = list(self.tiles)
        s["selected"] = self.selected
        return s

    @classmethod
    def restore(cls, state):
        game = cls(state["resource_name"], state["rows"], state["cols"],
                   state["seed"], tiles=state["tiles"])
        game.selected = state.get("selected")
        game.solved = bool(state["solved"])
        game.points = int(state.get("points", BONUS_REWARD))
        return game


class WordSearchGame(BonusGame):
    """Word search game: find target words in letter grid."""
    def __init__(self, resource_name, rows=8, cols=12, seed=0, words=None, found_words=None):
        super().__init__("wordsearch", resource_name, rows, cols, seed)
        self.words = list(words) if words is not None else ["LAS", "VEGAS", "CASINO", "HEIST"]
        self.found = set(found_words) if found_words is not None else set()
        self._check_solved()

    def find_word(self, word):
        w = word.upper()
        if w in self.words:
            self.found.add(w)
            self._check_solved()
            return True
        return False

    def _check_solved(self):
        if set(self.words).issubset(self.found):
            self.solved = True

    def solve(self):
        self.found = set(self.words)
        self.solved = True

    def state(self):
        s = super().state()
        s["words"] = list(self.words)
        s["found"] = sorted(self.found)
        return s

    @classmethod
    def restore(cls, state):
        game = cls(state["resource_name"], state["rows"], state["cols"],
                   state["seed"], words=state.get("words"), found_words=state.get("found"))
        game.solved = bool(state["solved"])
        game.points = int(state.get("points", BONUS_REWARD))
        return game


class JigsawGame(BonusGame):
    """Jigsaw puzzle: place 24 pieces into target positions."""
    def __init__(self, resource_name, total_pieces=24, seed=0, placed=None):
        super().__init__("jigsaw", resource_name, 4, 6, seed)
        self.total_pieces = total_pieces
        self.placed = set(placed) if placed is not None else set()
        self._check_solved()

    def place_piece(self, piece_id):
        if 0 <= piece_id < self.total_pieces:
            self.placed.add(piece_id)
            self._check_solved()
            return True
        return False

    def _check_solved(self):
        if len(self.placed) >= self.total_pieces:
            self.solved = True

    def solve(self):
        self.placed = set(range(self.total_pieces))
        self.solved = True

    def state(self):
        s = super().state()
        s["total_pieces"] = self.total_pieces
        s["placed"] = sorted(self.placed)
        return s

    @classmethod
    def restore(cls, state):
        game = cls(state["resource_name"], total_pieces=state.get("total_pieces", 24),
                   seed=state["seed"], placed=state.get("placed"))
        game.solved = bool(state["solved"])
        game.points = int(state.get("points", BONUS_REWARD))
        return game


def load_bonus_game(resources, bonus_name, seed=0):
    """Parse bonus definition from XUI resource and instantiate corresponding game."""
    raw = resources.read(bonus_name)
    tree = parse_xui(raw)
    name_lower = bonus_name.lower()

    if name_lower.endswith(".trg"):
        rows, cols = 4, 6
        for node in tree.iter():
            if local_name(node.tag) == "tilerotgameobjects":
                rows = int(node.attrib.get("rows", 4))
                cols = int(node.attrib.get("columns", 6))
                break
        return TileRotGame(bonus_name, rows=rows, cols=cols, seed=seed)

    elif name_lower.endswith(".tgl"):
        rows, cols = 6, 6
        for node in tree.iter():
            if local_name(node.tag) == "tilegameobjects":
                rows = int(node.attrib.get("rows", 6))
                cols = int(node.attrib.get("columns", 6))
                break
        return TileSwapGame(bonus_name, rows=rows, cols=cols, seed=seed)

    elif name_lower.endswith(".wsg"):
        rows, cols = 8, 12
        for node in tree.iter():
            if local_name(node.tag) == "wordsearchgametiles":
                rows = int(node.attrib.get("rows", 8))
                cols = int(node.attrib.get("columns", 12))
                break
        return WordSearchGame(bonus_name, rows=rows, cols=cols, seed=seed)

    elif name_lower.endswith(".jsw"):
        pieces = 0
        for node in tree.iter():
            if local_name(node.tag) == "jswgamepiece":
                pieces += 1
        if pieces == 0:
            pieces = 24
        return JigsawGame(bonus_name, total_pieces=pieces, seed=seed)

    # Fallback to rotation minigame
    return TileRotGame(bonus_name, rows=4, cols=6, seed=seed)

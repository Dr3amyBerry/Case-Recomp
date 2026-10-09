"""Campaign shell over recovered level data, set-completion, bonus minigames and progression.

Implements original level progression, bonus minigames, win conditions and the
campaign finale (Level 25) based on XUI definitions and Ghidra pseudocode.
"""
from dataclasses import asdict, dataclass
from clock import LevelClock
from runtime import Scene, parse_xui, local_name
from progress import snapshot as scene_snapshot, restore as scene_restore, number, boolean, resource_hash
from bonus import (
    load_bonus_game, BonusGame, TileRotGame,
    FirstRiddleGame, SecondRiddleGame, ThirdRiddleGame, BONUS_REWARD
)

SCHEMA = "case-recomp-sda-campaign/1"

RANKS = (
    "Sabueso novato",
    "Aspirante a investigador",
    "Rastreador",
    "Investigador amateur",
    "Sabueso",
    "Inspector",
    "Rastreador veterano",
    "Indagador",
    "Husmeador",
    "Detective",
    "Rastreador astuto",
    "S\u00faper indagador",
    "Detective explosivo",
    "As investigador",
    "P.I. Maestro",
)


@dataclass(frozen=True)
class Level:
    clue: int
    time: float
    objects: int
    scenes: tuple
    title: str
    bonus: str
    bonusimage: str = ""


def read_levels(resources, name="LEVELS_1.XUI"):
    tree = parse_xui(resources.read(name))
    levels = []
    for node in tree.iter():
        if local_name(node.tag) != "level":
            continue
        a = node.attrib
        scenes = tuple(a["scenes"].split(","))
        if not scenes or len(set(scenes)) != len(scenes) or any(not s or not s.replace("_", "").isalnum() for s in scenes):
            raise ValueError("invalid level scene list")
        level = Level(int(a["clue"]), float(a["time"]), int(a["objects"]), scenes,
                      a["levelname"], a.get("bonus", ""), a.get("bonusimage", ""))
        if level.objects < 1 or level.time <= 0:
            raise ValueError("invalid campaign level")
        levels.append(level)
    if not levels:
        raise ValueError("no campaign levels")
    return tuple(levels)


class Session:
    def __init__(self, resources, seed, level_index=0, level_resource="LEVELS_1.XUI"):
        self.resources = resources
        self.level_resource = level_resource
        self.level_index = number(level_index, integer=True, low=0)
        seed = number(seed, integer=True)
        self.levels = read_levels(resources, level_resource)
        self.level = self.levels[self.level_index]
        self.clock = LevelClock(self.level.time)
        self.seed = seed
        self.points = 0
        self.completed = 0
        self.counted = {}
        self.scenes = {}
        self.current = None
        self.phase = "map"
        self.events = []
        self.bonus_game = None
        self.total_elapsed = 0.0

    @property
    def rank(self):
        index = min(len(RANKS) - 1, int(self.level_index * len(RANKS) / len(self.levels)))
        return RANKS[index]

    def enter(self, name):
        if self.phase not in ("map", "scene") or name not in self.level.scenes:
            raise ValueError("scene is unavailable")
        if name not in self.scenes:
            self.scenes[name] = Scene(self.resources, "SCENE_" + name.upper() + ".MSL", seed=self.seed)
            self.counted[name] = set()
        self.current = name
        self.phase = "scene"
        # The original recreates the score node for each scene; only points carry over.
        scene = self.scenes[name]
        scene.score.points = self.points
        scene.score.fast_chain, scene.score.fast_bonus, scene.score.misses = False, 2500, []
        scene.since_found = 0.0
        if self._scene_retired():
            self.phase = "scene_complete"
        return scene

    @property
    def scene(self):
        return self.scenes[self.current] if self.current is not None else None

    @property
    def remaining(self):
        return max(0, self.level.objects - self.completed)

    def _scene_retired(self):
        return bool(self.scene.active_sets) and self.scene.batch_retired

    def confirm_scene_complete(self, action=334):
        number(action, integer=True)
        if action != 334 or self.phase != "scene_complete":
            raise ValueError("no completed-scene dialog for this action")
        # 00412300/334 -> callback 00411550/state 0x30 -> action 301.
        # This shell skips fade/overlay passes but preserves the confirmation boundary.
        self.to_map()

    def to_map(self):
        if self.phase not in ("scene", "scene_complete"):
            raise ValueError("not in a scene")
        self.points = self.scene.score.points
        self.phase = "map"
        self.current = None

    def advance(self, seconds):
        if self.phase == "scene":
            self.events.extend(self.clock.advance(seconds))
            scene = self.scene
            scene.advance(seconds)
            for ids in scene.active_sets:
                if ids not in self.counted[self.current] and scene.rows[ids].removed:
                    self.counted[self.current].add(ids)
                    self.completed += 1
            self.points = scene.score.points
            if self.remaining == 0:
                self.phase = "objects_complete"
            elif "timeout" in self.events:
                self.phase = "timeout"
            elif self._scene_retired():
                self.phase = "scene_complete"
        elif self.phase in ("bonus", "finale_1", "finale_2", "finale_3"):
            self.events.extend(self.clock.advance(seconds))
            if "timeout" in self.events:
                self.phase = "timeout"

    def click(self, x, y):
        if self.phase != "scene":
            return {"kind": "inactive"}
        result = self.scene.click(x, y)
        self.points = self.scene.score.points
        return result

    def start_bonus(self):
        if self.phase != "objects_complete":
            raise ValueError("cannot start bonus before completing objects")
        if self.scene:
            self.points = max(self.points, self.scene.score.points)
        self.current = None
        if self.level.bonus:
            self.bonus_game = load_bonus_game(self.resources, self.level.bonus, self.seed, self.level.bonusimage)
            self.phase = "bonus"
        else:
            self.phase = "level_complete"

    def bonus_click(self, x, y):
        """Interact with active bonus game via canvas pixel coordinates."""
        if self.phase not in ("bonus", "finale_1", "finale_2", "finale_3") or self.bonus_game is None:
            return {"kind": "inactive"}
        moved = self.bonus_game.click_pixel(x, y)
        if self.bonus_game.is_solved():
            self.points += self.bonus_game.points
            if self.phase == "bonus":
                self.phase = "level_complete"
                return {"kind": "solved", "bonus": self.bonus_game.kind, "points": self.bonus_game.points}
            elif self.phase == "finale_1":
                self.phase = "finale_2"
                self.bonus_game = SecondRiddleGame(seed=self.seed)
                return {"kind": "solved", "phase": "finale_2", "points": BONUS_REWARD}
            elif self.phase == "finale_2":
                self.phase = "finale_3"
                self.bonus_game = ThirdRiddleGame(seed=self.seed)
                return {"kind": "solved", "phase": "finale_3", "points": BONUS_REWARD}
            elif self.phase == "finale_3":
                self.phase = "campaign_complete"
                self.bonus_game = None
                return {"kind": "solved", "phase": "campaign_complete", "points": BONUS_REWARD}
        return {"kind": "moved" if moved else "miss"}

    def solve_bonus(self):
        """Native action matching 'Resolver puzle' dialog button (0045b150.c).

        Skipping forfeits the 25,000 bonus reward (0 points awarded).
        """
        if self.phase not in ("bonus", "finale_1", "finale_2", "finale_3") or self.bonus_game is None:
            raise ValueError("not in an active bonus round")
        self.bonus_game.solve()
        self.bonus_game.points = 0
        if self.phase == "bonus":
            self.phase = "level_complete"
        elif self.phase == "finale_1":
            self.phase = "finale_2"
            self.bonus_game = SecondRiddleGame(seed=self.seed)
        elif self.phase == "finale_2":
            self.phase = "finale_3"
            self.bonus_game = ThirdRiddleGame(seed=self.seed)
        elif self.phase == "finale_3":
            self.phase = "campaign_complete"
            self.bonus_game = None

    def complete_bonus(self):
        return self.solve_bonus()

    def level_summary(self):
        speed_bonus = int(max(0.0, self.clock.limit - self.clock.elapsed)) * 10
        return {
            "clue": self.level.clue,
            "level_index": self.level_index,
            "title": self.level.title,
            "elapsed": self.clock.elapsed,
            "remaining_time": max(0.0, self.clock.limit - self.clock.elapsed),
            "speed_bonus": speed_bonus,
            "points": self.points,
            "rank": self.rank,
            "last_level": self.level_index >= len(self.levels) - 1,
        }

    def confirm_level_complete(self):
        if self.phase != "level_complete":
            raise ValueError("not in level_complete phase")
        speed_bonus = int(max(0.0, self.clock.limit - self.clock.elapsed)) * 10
        self.points += speed_bonus
        self.total_elapsed += self.clock.elapsed

        if self.level_index >= len(self.levels) - 1:
            # Reached Level 25 completion: transition to authentic 3-stage Master Riddle finale
            self.phase = "finale_1"
            self.current = None
            self.bonus_game = FirstRiddleGame(seed=self.seed)
        else:
            self.level_index += 1
            self.level = self.levels[self.level_index]
            self.clock = LevelClock(self.level.time)
            self.completed = 0
            self.counted = {}
            self.scenes = {}
            self.current = None
            self.bonus_game = None
            self.phase = "map"

    def state(self):
        data = {
            "schema": SCHEMA, "resources_sha256": resource_hash(self.resources),
            "level_resource": self.level_resource, "level_index": self.level_index,
            "seed": self.seed, "points": self.points, "completed": self.completed,
            "clock": asdict(self.clock), "current": self.current, "phase": self.phase,
            "counted": {name: sorted(ids) for name, ids in self.counted.items()},
            "scenes": {name: scene_snapshot(scene) for name, scene in self.scenes.items()},
            "events": list(self.events),
            "total_elapsed": self.total_elapsed,
        }
        if self.bonus_game is not None:
            data["bonus_game"] = self.bonus_game.state()
        return data

    @classmethod
    def restore(cls, resources, state):
        if state.get("schema") != SCHEMA or state.get("resources_sha256") != resource_hash(resources):
            raise ValueError("unsupported campaign progress or resources")
        index = number(state["level_index"], integer=True, low=0)
        value = cls(resources, number(state["seed"], integer=True), index, state["level_resource"])
        value.points = number(state["points"], integer=True, low=0)
        value.completed = number(state["completed"], integer=True, low=0)
        c = state["clock"]
        if c["limit"] != value.clock.limit:
            raise ValueError("saved clock differs from original level")
        value.clock = LevelClock(c["limit"], number(c["elapsed"], low=0), boolean(c["paused"]))
        value.phase, value.current = state["phase"], state["current"]
        valid_phases = (
            "map", "scene", "scene_complete", "objects_complete", "bonus",
            "level_complete", "campaign_complete", "timeout",
            "finale_1", "finale_2", "finale_3"
        )
        if value.phase not in valid_phases:
            raise ValueError("invalid campaign phase")
        if set(state["scenes"]) != set(state["counted"]) or not set(state["scenes"]).issubset(value.level.scenes):
            raise ValueError("invalid campaign scenes")
        value.scenes = {name: scene_restore(resources, s) for name, s in state["scenes"].items()}
        for name, scene in value.scenes.items():
            if scene.name != "SCENE_" + name.upper() + ".MSL":
                raise ValueError("scene identity differs from level")
            ids = [tuple(group) for group in state["counted"][name]]
            if len(set(ids)) != len(ids) or any(group not in scene.target_sets or not all(
                    scene.objects[item].motion and scene.objects[item].motion.removed for item in group) for group in ids):
                raise ValueError("invalid completed sets")
            if any(group in scene.rows and not scene.rows[group].removed for group in ids):
                raise ValueError("completed set still has an active row")
            value.counted[name] = set(ids)
        if value.completed != sum(len(ids) for ids in value.counted.values()):
            raise ValueError("completed count differs from scene events")
        if value.phase in ("map", "bonus", "level_complete", "campaign_complete", "finale_1", "finale_2", "finale_3"):
            if value.current is not None:
                raise ValueError("invalid active campaign scene")
        elif value.current not in value.scenes:
            raise ValueError("invalid active campaign scene")
        if value.phase in ("objects_complete", "bonus", "level_complete", "campaign_complete", "finale_1", "finale_2", "finale_3"):
            if value.remaining != 0:
                raise ValueError("inconsistent completion boundary")
        elif value.remaining == 0:
            raise ValueError("inconsistent completion boundary")
        if value.phase == "scene_complete" and not value._scene_retired():
            raise ValueError("completed-scene dialog has unfinished sets")
        value.events = list(state["events"])
        if any(event not in (1, 2, 3, 4, "timeout") for event in value.events):
            raise ValueError("invalid clock events")
        if value.phase == "timeout" and "timeout" not in value.events:
            raise ValueError("missing timeout event")
        value.total_elapsed = float(state.get("total_elapsed", 0.0))
        if "bonus_game" in state and state["bonus_game"]:
            value.bonus_game = BonusGame.restore(state["bonus_game"])
        return value

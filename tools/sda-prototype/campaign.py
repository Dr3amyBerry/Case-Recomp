"""Experimental campaign shell over recovered level data and set-completion events.

Native profile dialogs, map transitions, scene reconstruction and bonus rounds are
still pending. This shell caches scene instances to support comparison and resume.
"""
from dataclasses import asdict, dataclass
from clock import LevelClock
from runtime import Scene, parse_xui, local_name
from progress import snapshot as scene_snapshot, restore as scene_restore, number, boolean, resource_hash

SCHEMA = "case-recomp-sda-campaign/1"


@dataclass(frozen=True)
class Level:
    clue: int
    time: float
    objects: int
    scenes: tuple
    title: str
    bonus: str


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
                      a["levelname"], a.get("bonus", ""))
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
        self.level = read_levels(resources, level_resource)[level_index]
        self.clock = LevelClock(self.level.time)
        self.seed = seed
        self.points = 0
        self.completed = 0
        self.counted = {}
        self.scenes = {}
        self.current = None
        self.phase = "map"
        self.events = []

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
        return scene

    @property
    def scene(self):
        return self.scenes[self.current] if self.current is not None else None

    @property
    def remaining(self):
        return max(0, self.level.objects - self.completed)

    def to_map(self):
        if self.phase != "scene":
            raise ValueError("not in a scene")
        self.points = self.scene.score.points
        self.phase = "map"
        self.current = None

    def advance(self, seconds):
        if self.phase != "scene":
            return
        self.events.extend(self.clock.advance(seconds))
        scene = self.scene
        scene.advance(seconds)
        for ids in scene.active_sets:
            if ids not in self.counted[self.current] and all(
                    scene.objects[item].motion and scene.objects[item].motion.removed for item in ids):
                self.counted[self.current].add(ids)
                self.completed += 1
        self.points = scene.score.points
        if self.remaining == 0:
            # Shell boundary; original completion/bonus dialogs are not implemented.
            self.phase = "objects_complete"
        elif "timeout" in self.events:
            self.phase = "timeout"

    def click(self, x, y):
        if self.phase != "scene":
            return {"kind": "inactive"}
        result = self.scene.click(x, y)
        self.points = self.scene.score.points
        return result

    def state(self):
        return {"schema": SCHEMA, "resources_sha256": resource_hash(self.resources),
                "level_resource": self.level_resource, "level_index": self.level_index,
                "seed": self.seed, "points": self.points, "completed": self.completed,
                "clock": asdict(self.clock), "current": self.current, "phase": self.phase,
                "counted": {name: sorted(ids) for name, ids in self.counted.items()},
                "scenes": {name: scene_snapshot(scene) for name, scene in self.scenes.items()},
                "events": list(self.events)}

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
        if value.phase not in ("map", "scene", "objects_complete", "timeout"):
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
            value.counted[name] = set(ids)
        if value.completed != sum(len(ids) for ids in value.counted.values()):
            raise ValueError("completed count differs from scene events")
        if value.phase == "map" and value.current is not None or value.phase != "map" and value.current not in value.scenes:
            raise ValueError("invalid active campaign scene")
        if (value.phase == "objects_complete") != (value.remaining == 0):
            raise ValueError("inconsistent completion boundary")
        value.events = list(state["events"])
        if any(event not in (1, 2, 3, 4, "timeout") for event in value.events):
            raise ValueError("invalid clock events")
        if value.phase == "timeout" and "timeout" not in value.events:
            raise ValueError("missing timeout event")
        return value

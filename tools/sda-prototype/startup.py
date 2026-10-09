"""Reviewed first-player startup route; own profile format, no native save access.

Models 299 -> create player -> menu -> 299 -> campaign. Native multi-player
selection, completion/restart dialogs and transitions are not implemented.
"""
from dataclasses import asdict, dataclass
from campaign import Session
from progress import number, resource_hash

SCHEMA = "case-recomp-sda-player/1"


def trimmed_name(text):
    # 004106b0/004104a0/00406dc0 trim the literal space at 005193c0.
    # Input encoding, editbox length limits and glyph filtering remain separate.
    if not isinstance(text, str) or "\x00" in text:
        raise ValueError("invalid player name")
    return text.strip(" ")


def name_error(text, existing_names=()):
    name = trimmed_name(text)
    if not name:
        return "empty"
    # 004104a0 performs a bytewise, case-sensitive comparison after trimming.
    if name in existing_names:
        return "duplicate"
    return None


@dataclass(frozen=True)
class Player:
    name: str
    icon: int = 0

    def __post_init__(self):
        if name_error(self.name) or self.name != trimmed_name(self.name):
            raise ValueError("invalid stored player name")
        number(self.icon, integer=True, low=0, high=2)


class Startup:
    def __init__(self, resources, seed=8):
        self.resources = resources
        self.seed = number(seed, integer=True)
        self.player = None
        self.session = None
        self.phase = "menu"
        self.error = None

    def action(self, value, name="", icon=0):
        number(value, integer=True)
        self.error = None
        if self.phase == "menu" and value == 299:
            if self.player is None:
                self.phase = "newplayer"
                return "newplayer"
            if self.session is None:
                self.session = Session(self.resources, self.seed)
            self.phase = "game"
            return "campaign"
        if self.phase == "newplayer" and value == 1:
            self.error = name_error(name)
            if self.error:
                return self.error
            self.player = Player(trimmed_name(name), icon)
            # 00412300/action 1 updates the menu; it does not dispatch 299.
            self.phase = "menu"
            return "player_created"
        if self.phase == "newplayer" and value == 9:
            self.phase = "menu"
            return "cancelled"
        raise ValueError("unsupported startup action or phase")

    def state(self):
        # Draft input/modal visibility is not original save data. Closing a draft
        # restores the menu; only a committed player and campaign are persisted.
        return {"schema": SCHEMA, "resources_sha256": resource_hash(self.resources),
                "seed": self.seed, "player": asdict(self.player) if self.player else None,
                "campaign": self.session.state() if self.session else None,
                "phase": "game" if self.phase == "game" else "menu"}

    @classmethod
    def restore(cls, resources, state):
        if state.get("schema") != SCHEMA or state.get("resources_sha256") != resource_hash(resources):
            raise ValueError("unsupported player schema or different resources")
        flow = cls(resources, state["seed"])
        player = state["player"]
        if player is not None:
            if not isinstance(player, dict) or set(player) != {"name", "icon"}:
                raise ValueError("invalid stored player")
            flow.player = Player(**player)
        campaign = state["campaign"]
        if campaign is not None:
            if flow.player is None:
                raise ValueError("campaign without a player")
            flow.session = Session.restore(resources, campaign)
            if flow.session.seed != flow.seed:
                raise ValueError("inconsistent player/campaign seed")
        phase = state["phase"]
        if phase not in ("menu", "game") or phase == "game" and flow.session is None:
            raise ValueError("invalid stored startup phase")
        flow.phase = phase
        return flow

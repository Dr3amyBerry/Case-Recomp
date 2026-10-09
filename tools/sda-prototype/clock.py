"""SDA timer scalar semantics from 0041dc40, 0041db80 and 0041dca0."""
from dataclasses import dataclass
import math
from motion import single


@dataclass
class LevelClock:
    limit: float
    elapsed: float = 0.0
    paused: bool = False

    def __post_init__(self):
        if not math.isfinite(self.limit) or self.limit <= 0 or not math.isfinite(self.elapsed) or self.elapsed < 0:
            raise ValueError("invalid level clock")
        self.limit, self.elapsed = single(self.limit), single(self.elapsed)

    def advance(self, seconds, suppress_events=False):
        if not math.isfinite(seconds) or seconds < 0:
            raise ValueError("invalid frame duration")
        seconds = single(seconds)
        # Native checks BEFORE storing elapsed, and compares integer seconds modulo 60.
        events = []
        if int(self.elapsed + seconds) % 60 != int(self.elapsed) % 60 and not suppress_events:
            remaining = int(self.limit) - int(self.elapsed)
            if self.elapsed >= self.limit:
                events.append("timeout")
            elif remaining in (181, 120, 60, 10):
                events.append({181: 3, 120: 2, 60: 1, 10: 4}[remaining])
        # The native pause flag suppresses storage, not the event check above.
        if not self.paused:
            self.elapsed = single(self.elapsed + seconds)
        return tuple(events)

    def display_seconds(self, unlimited=False):
        return int(self.elapsed) if unlimited else max(0, int(self.limit - int(self.elapsed)))

    def text(self):
        seconds = self.display_seconds()
        return f"{seconds // 3600:02d}:{seconds // 60 % 60:02d}:{seconds % 60:02d}"

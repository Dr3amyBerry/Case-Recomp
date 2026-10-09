"""Recovered target-row fade scalar; graph pass ordering remains experimental."""
from dataclasses import dataclass
from motion import single


@dataclass
class TargetRow:
    alpha: float = 1.0
    phase: int = 0
    removed: bool = False

    def update(self, objects_retired):
        if self.removed:
            return
        if self.phase == 0 and objects_retired:
            self.phase = 1
        if self.phase == 1:
            # 0042a600 uses 005076e8 per update, not elapsed seconds.
            self.alpha = single(self.alpha - single(0.34))
            if self.alpha <= 0:
                self.alpha, self.phase = 0.0, 2
        elif self.phase == 2:
            # Removal/compaction occurs on the update after reaching zero.
            self.removed, self.phase = True, 0

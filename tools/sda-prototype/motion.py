"""Scalar found-object lifecycle recovered from 004278e0/00427bd0."""
from dataclasses import dataclass
import math
import struct


def single(value):
    return struct.unpack("<f", struct.pack("<f", value))[0]


def native_round(value):
    # 00428040 uses truncation, then increments only if the remainder >= 0.5.
    truncated = int(value)
    return truncated + int(value - truncated >= 0.5)


@dataclass
class FoundMotion:
    original_x: int
    original_y: int
    width: int
    height: int
    delay: float = single(0.85)
    velocity: float = 0.0
    scale: float = 1.0
    target: float = 1.25
    step: float = single(0.25 / (single(0.2) / single(0.04)))
    phase: int = 1
    pulses: int = 0
    removed: bool = False

    def __post_init__(self):
        self.x, self.y = self.original_x, self.original_y
        self.draw_width, self.draw_height = self.width, self.height
        # 004279e1 loads the same saved rect height used for Y, not its width.
        self.center_x = self.original_x + self.height // 2
        self.center_y = self.original_y + self.height // 2

    def update(self, seconds):
        """One native update call. Scale/velocity increments are per frame, not dt."""
        if not math.isfinite(seconds) or seconds < 0:
            raise ValueError("invalid frame duration")
        if self.removed:
            return
        self.delay = single(self.delay - single(seconds))
        if self.delay <= 0:
            self.velocity = max(-10.0, single(self.velocity - 0.5))
            self.y += int(self.velocity)
            if self.y < -self.draw_height:
                self.removed = True
                return
        if self.phase == 0:
            return
        if self.phase == 1:
            self.scale = min(self.target, single(self.scale + self.step))
            if self.scale >= self.target:
                self.phase, self.target, self.step = 2, 1.0, single(-0.25 / (single(0.2) / single(0.04)))
        elif self.phase == 2:
            self.scale = max(self.target, single(self.scale + self.step))
            if self.scale <= self.target:
                self.pulses += 1
                if self.pulses < 2:
                    self.phase, self.target, self.step = 1, 1.25, single(0.25 / (single(0.2) / single(0.04)))
                else:
                    self.phase = 0
        self.draw_width = native_round(single(self.width * self.scale))
        self.draw_height = native_round(single(self.height * self.scale))
        # Active pulse updates recenter even if upward motion ran earlier this frame.
        self.x = native_round(single(self.center_x - single(self.width * self.scale) * 0.5))
        self.y = native_round(single(self.center_y - single(self.height * self.scale) * 0.5))

"""Recovered in-memory history marks and 00423680 scene filtering.

This is not the native player-file serializer. Rectangles are the scene's original
object bounds; a history label's top-left represents the original click point.
"""
from dataclasses import dataclass
import re


@dataclass(frozen=True)
class HistoryMark:
    text: str
    x: int
    y: int

    @classmethod
    def create(cls, caption, scene, variant, x, y):
        return cls(f"{caption} ({scene}) [{variant}]", int(x), int(y))

    def split_variant(self):
        # 00423680 uses the first '[', removes its preceding character, sscanf [%d].
        start = self.text.find("[")
        if start > 1:
            match = re.match(r"\[\s*([+-]?[0-9]+)", self.text[start:])
            return self.text[:start - 1], int(match.group(1)) if match else -1
        return self.text, -1


def strict_point_inside(rect, x, y):
    left, top, width, height = rect
    return left < x < left + width and top < y < top + height


@dataclass
class HistoryReplay:
    candidates: tuple
    retired: frozenset
    hidden: frozenset
    removed_sets: tuple


def replay_history(sets, variants, rectangles, marks, scene, current_variant):
    """00423680 common scope/variant paths; no original file IO or score changes.

    Removing a child increments the outer index as in the native routine, so the
    shifted successor is not revisited in that pass. Preserve this behavior.
    """
    pool = list(sets)
    retired, hidden, removed_sets = set(), set(), []
    index = 0
    while index < len(pool):
        ids = pool[index]
        removed_set = False
        for mark in marks:
            count = sum(identity in retired for identity in ids)
            remaining = len(ids) - count
            caption = variants[ids][count] if count < len(variants[ids]) else ""
            base, variant = mark.split_variant()
            if base != f"{caption} ({scene})":
                continue
            if current_variant != variant:
                if current_variant != 0 and variant != 0:
                    removed_set = True
                else:
                    continue
            elif remaining > 1:
                for identity in ids:
                    if identity not in retired and strict_point_inside(rectangles[identity], mark.x, mark.y):
                        retired.add(identity)
                        hidden.add(identity)
                continue
            else:
                # Single-object sets hide their object without a geometry test.
                # For compound sets native starts with map element zero, then
                # substitutes the first remaining object containing the point.
                chosen = ids[0]
                if len(ids) > 1:
                    chosen = next((identity for identity in ids if identity not in retired
                                   and strict_point_inside(rectangles[identity], mark.x, mark.y)), chosen)
                hidden.add(chosen)
                removed_set = True
            if removed_set:
                removed_sets.append(ids)
                pool.pop(index)
                break
        index += 1
    return HistoryReplay(tuple(pool), frozenset(retired), frozenset(hidden), tuple(removed_sets))


def prune_scene_history(sets, variants, marks, scene):
    """00423000: clear non-[0] scene history if fewer than ten sets remain fresh.

    Native matching is substring-based, including variant captions; do not replace
    it with exact matching or a count of found objects. Caller supplies the fresh
    campaign context; resumed/unlimited routes do not automatically call this.
    """
    scope = f" ({scene})"
    scoped = [mark for mark in marks if scope in mark.text and "[0]" not in mark.text]
    affected = sum(any(caption in mark.text for caption in variants[ids] for mark in scoped)
                   for ids in sets)
    reset = len(sets) - affected < 10
    if not reset:
        return tuple(marks), False
    return tuple(mark for mark in marks if not (scope in mark.text and "[0]" not in mark.text)), True

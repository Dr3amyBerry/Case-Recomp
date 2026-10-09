"""Recovered SDA target ordering, distinct from campaign/persistent pool filtering."""


class NativeRandom:
    """004f0d25/004f0d32: Visual C runtime's thread-local 32-bit state."""
    def __init__(self, seed):
        if not isinstance(seed, int) or not 0 <= seed <= 0xffffffff:
            raise ValueError("seed must be an unsigned 32-bit clock sample")
        self.state = seed

    def next(self):
        self.state = (self.state * 0x343fd + 0x269ec3) & 0xffffffff
        return (self.state >> 16) & 0x7fff


def native_shuffle(items, seed, start=0):
    """004291c0; 00429210 uses the same suffix loop after restoring active prefix."""
    result = list(items)
    if not 0 <= start <= len(result):
        raise ValueError("invalid shuffle prefix")
    random = NativeRandom(seed)
    for index in range(start, len(result) - 1):
        other = index + random.next() % (len(result) - index)
        result[index], result[other] = result[other], result[index]
    return result


class TargetDeck:
    """00428d70 batching for an explicitly supplied, already-filtered set pool.

    Pool filtering by original history/overlap and restoration remain separate.
    Native strict 'count < index' wrapping exposes a null at index == count;
    stop with a diagnostic instead of silently inventing modulo wrapping.
    """
    def __init__(self, sets):
        self.order = list(sets)
        if not self.order:
            raise ValueError("empty candidate pool")
        self.cursor = 0

    def next_batch(self, seed):
        order = native_shuffle(self.order, seed) if self.cursor == 0 else list(self.order)
        size = min(10, len(order))
        indices = [self.cursor]
        for step in range(1, size):
            index = self.cursor + step
            if len(order) < index:
                index -= len(order)
            indices.append(index)
        if any(not 0 <= index < len(order) for index in indices):
            raise ValueError("native batch boundary reaches null child; transition needs validation")
        selected = tuple(order[index] for index in indices)
        self.order = order
        self.cursor += size
        if len(order) < self.cursor:
            self.cursor = 0
        return selected

    def restore_batch(self, variants, saved_captions, seed):
        """00428540/00429210: match captions, compact ten slots, pin active prefix.

        This restores ordering only. Original object/history rectangles are needed
        to determine which components of a partially completed set were removed.
        """
        if len(saved_captions) > 10:
            raise ValueError("saved active list exceeds native ten slots")
        slots = [None] * 10
        for identity in self.order:
            captions = variants[identity]
            for index, saved in enumerate(saved_captions):
                if saved in captions:
                    # Native outer loop continues: later matching set wins.
                    slots[index] = identity
        selected = tuple(identity for identity in slots if identity is not None)
        order = list(self.order)
        for index, identity in enumerate(selected):
            if index >= len(order):
                raise ValueError("restored prefix exceeds candidate pool")
            other = order.index(identity)
            order[index], order[other] = order[other], order[index]
        order = native_shuffle(order, seed, start=len(selected))
        self.order = order
        self.cursor += 10  # Independent of compacted active count in 00428540.
        if len(order) < self.cursor:
            self.cursor = 0
        return selected

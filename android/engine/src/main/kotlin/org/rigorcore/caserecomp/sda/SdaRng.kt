package org.rigorcore.caserecomp.sda

/**
 * Visual C++ runtime LCG 32-bit thread-local state (0x004f0d25 / 0x004f0d32).
 * Reproduces native target ordering and shuffle sequences.
 */
class SdaRng(seed: Long) {
    var state: Long = seed and 0xFFFFFFFFL
        private set

    init {
        require(seed in 0..0xFFFFFFFFL) { "seed must be an unsigned 32-bit clock sample" }
    }

    /** 0046e990: x87 keeps the product wider than float after loading this float constant. */
    fun nextScaled(): Double = next().toDouble() * Float.fromBits(0x38000100).toDouble()

    fun next(): Int {
        state = (state * 0x343fdL + 0x269ec3L) and 0xFFFFFFFFL
        return ((state ushr 16) and 0x7fffL).toInt()
    }
}

/**
 * 0x004291c0; 0x00429210 suffix shuffle after restoring active prefix.
 */
fun <T> sdaShuffle(items: List<T>, seed: Long, start: Int = 0): List<T> {
    require(start in 0..items.size) { "invalid shuffle prefix" }
    val result = items.toMutableList()
    val rng = SdaRng(seed)
    for (index in start until result.size - 1) {
        val range = result.size - index
        val other = index + (rng.next() % range)
        val tmp = result[index]
        result[index] = result[other]
        result[other] = tmp
    }
    return result
}

/**
 * 0x00428d70 batching for an explicitly supplied set pool.
 */
class SdaTargetDeck<T>(sets: Collection<T>) {
    var order: List<T> = sets.toList()
        private set
    var cursor: Int = 0
        private set

    init {
        require(order.isNotEmpty()) { "empty candidate pool" }
    }

    fun nextBatch(seed: Long): List<T> {
        val currentOrder = if (cursor == 0) sdaShuffle(order, seed) else order
        val size = minOf(10, currentOrder.size)
        val indices = mutableListOf(cursor)
        for (step in 1 until size) {
            var index = cursor + step
            if (currentOrder.size < index) {
                index -= currentOrder.size
            }
            indices.add(index)
        }
        if (indices.any { it !in currentOrder.indices }) {
            throw IllegalArgumentException("native batch boundary reaches null child; transition needs validation")
        }
        val selected = indices.map { currentOrder[it] }
        order = currentOrder
        cursor += size
        if (order.size < cursor) {
            cursor = 0
        }
        return selected
    }

    fun restoreBatch(variants: Map<T, List<String>>, savedCaptions: List<String>, seed: Long): List<T> {
        require(savedCaptions.size <= 10) { "saved active list exceeds native ten slots" }
        val slots = arrayOfNulls<Any?>(10)
        for (identity in order) {
            val captions = variants[identity] ?: emptyList()
            for (index in savedCaptions.indices) {
                if (savedCaptions[index] in captions) {
                    slots[index] = identity
                }
            }
        }
        @Suppress("UNCHECKED_CAST")
        val selected = slots.filterNotNull().map { it as T }
        val newOrder = order.toMutableList()
        for ((index, identity) in selected.withIndex()) {
            if (index >= newOrder.size) {
                throw IllegalArgumentException("restored prefix exceeds candidate pool")
            }
            val other = newOrder.indexOf(identity)
            val tmp = newOrder[index]
            newOrder[index] = newOrder[other]
            newOrder[other] = tmp
        }
        order = sdaShuffle(newOrder, seed, start = selected.size)
        cursor += 10
        if (order.size < cursor) {
            cursor = 0
        }
        return selected
    }
}

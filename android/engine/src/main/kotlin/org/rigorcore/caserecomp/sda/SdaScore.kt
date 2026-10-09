package org.rigorcore.caserecomp.sda

/**
 * SDA scoring logic recovered from 0x00453430, 0x00453950, 0x00453830.
 * Tracks base points, fast streak chain bonuses, hint deduction, and miss penalty window.
 */
class SdaScore(
    var points: Int = 0,
    var fastChain: Boolean = false,
    var fastBonus: Int = 2500,
    val misses: MutableList<Long> = mutableListOf(),
) {
    /**
     * 0x00453430: Base 5000; first fast bonus 2500, then +1000 per fast hit up to 15500 cap.
     * Slow hit resets the chain and returns base 5000.
     */
    fun found(fast: Boolean): Int {
        var gain = 5000
        if (fast) {
            if (fastChain) {
                if (fastBonus < 15500) {
                    fastBonus += 1000
                }
            } else {
                fastChain = true
            }
            gain += fastBonus
        } else {
            fastChain = false
            fastBonus = 2500
        }
        points += gain
        return gain
    }

    /**
     * 0x00453950: Hint deducts 7500 points (floor 0).
     * Does not reset the fast chain or miss history.
     */
    fun hint() {
        points = maxOf(0, points - 7500)
    }

    /**
     * 0x00453830: Rapid miss detector.
     * Initially fills five slots. The sixth call shifts the window before testing.
     * If the delta between the 5th and 1st miss is <= 2000 ms, deducts 2500 points and clears misses.
     */
    fun miss(nowMs: Long, penaltyAllowed: Boolean = true): Boolean {
        val u32Now = nowMs and 0xFFFFFFFFL
        if (misses.size < 5) {
            misses.add(u32Now)
            return false
        }
        misses.removeAt(0)
        misses.add(u32Now)
        val elapsed = (u32Now - misses[0]) and 0xFFFFFFFFL
        if (elapsed > 2000 || !penaltyAllowed) {
            return false
        }
        misses.clear()
        points = maxOf(0, points - 2500)
        return true
    }
}

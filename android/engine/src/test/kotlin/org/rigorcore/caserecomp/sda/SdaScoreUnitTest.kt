package org.rigorcore.caserecomp.sda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SdaScoreUnitTest {

    @Test
    fun fast_streak_caps_and_slow_hit_resets() {
        val score = SdaScore()
        val gains = (1..20).map { score.found(true) }
        assertEquals(listOf(7500, 8500, 9500), gains.subList(0, 3))
        assertEquals(20500, gains.last())
        assertEquals(5000, score.found(false))
        assertEquals(7500, score.found(true))
    }

    @Test
    fun first_five_misses_do_not_penalize() {
        val score = SdaScore(points = 10000)
        listOf(0L, 100L, 200L, 300L, 400L).forEach {
            assertFalse(score.miss(it))
        }
        assertTrue(score.miss(500L))
        assertEquals(7500, score.points)
        assertTrue(score.misses.isEmpty())
    }

    @Test
    fun miss_boundary_uses_shifted_window_and_gate() {
        for ((now, expected) in listOf(2100L to true, 2101L to false)) {
            val score = SdaScore(points = 10000)
            listOf(0L, 100L, 200L, 300L, 400L).forEach { score.miss(it) }
            assertEquals(expected, score.miss(now))
        }

        val score = SdaScore(points = 10000)
        (0L..4L).forEach { score.miss(it) }
        assertFalse(score.miss(5L, penaltyAllowed = false))
        assertTrue(score.miss(6L))
    }

    @Test
    fun clock_wrap_and_score_floor() {
        val score = SdaScore(points = 1000)
        for (x in 0xFFFFFFFCL..0x100000000L) {
            score.miss(x)
        }
        assertTrue(score.miss(1L))
        assertEquals(0, score.points)

        score.hint()
        assertEquals(0, score.points)
    }
}

package org.rigorcore.caserecomp.sda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SdaMotionUnitTest {

    @Test
    fun native_rounding_negative_remainder() {
        assertEquals(3, sdaNativeRound(2.5f))
        assertEquals(-2, sdaNativeRound(-2.7f))
    }

    @Test
    fun delay_is_time_based_but_scale_step_is_per_update() {
        val quick = SdaFoundMotion(10, 100, 8, 8)
        val slow = SdaFoundMotion(10, 100, 8, 8)
        quick.update(0.01f)
        slow.update(0.2f)
        assertEquals(quick.scale, slow.scale, 0.0001f)
        assertEquals(0.0f, quick.velocity, 0.0001f)
        assertNotEquals(quick.delay, slow.delay)
    }

    @Test
    fun two_pulses_then_upward_retirement() {
        val motion = SdaFoundMotion(20, 200, 8, 8)
        val scales = mutableListOf<Float>()
        for (i in 0 until 200) {
            motion.update(0.04f)
            scales.add(motion.scale)
            if (motion.removed) break
        }
        assertTrue(motion.removed)
        assertEquals(2, motion.pulses)
        assertEquals(0, motion.phase)
        assertEquals(1.0f, motion.scale, 0.0001f)
        assertEquals(1.25f, scales.maxOrNull() ?: 0f, 0.0001f)
        assertTrue(motion.velocity >= -10.0f)
        assertTrue(motion.y < -motion.drawHeight)

        val beforeX = motion.x
        val beforeY = motion.y
        val beforeDelay = motion.delay
        motion.update(1.0f)
        assertEquals(beforeX, motion.x)
        assertEquals(beforeY, motion.y)
        assertEquals(beforeDelay, motion.delay, 0.0001f)
    }

    @Test
    fun saved_rect_height_is_used_for_both_center_coordinates() {
        val motion = SdaFoundMotion(10, 20, 20, 8)
        assertEquals(14, motion.centerX)
        assertEquals(24, motion.centerY)
        motion.update(0.0f)
        assertTrue(motion.x < 10)
    }
}

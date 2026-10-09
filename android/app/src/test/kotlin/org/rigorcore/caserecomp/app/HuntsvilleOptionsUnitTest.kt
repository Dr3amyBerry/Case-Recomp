package org.rigorcore.caserecomp.app
import org.junit.Assert.*
import org.junit.Test
class HuntsvilleOptionsUnitTest {
    @Test fun dragging_past_either_end_cannot_produce_an_invalid_volume() {
        assertEquals(0, HuntsvilleVolume.fromSlider(-500, 100, 300, 40))
        assertEquals(255, HuntsvilleVolume.fromSlider(900, 100, 300, 40))
        assertEquals(127, HuntsvilleVolume.fromSlider(230, 100, 300, 40))
        assertEquals(0, HuntsvilleVolume.fromSlider(Int.MIN_VALUE, Int.MAX_VALUE, 300, 40))
    }
    @Test fun readout_clamps_corrupt_saved_values_and_preserves_original_two_digits() {
        assertEquals("00", HuntsvilleVolume.readout(-300))
        assertEquals("00", HuntsvilleVolume.readout(0))
        assertEquals("50", HuntsvilleVolume.readout(128))
        assertEquals("99", HuntsvilleVolume.readout(255))
        assertEquals("99", HuntsvilleVolume.readout(Int.MAX_VALUE))
    }
    @Test fun restored_thumb_position_uses_actual_travel_instead_of_half_thumb_width() {
        assertEquals(239, HuntsvilleVolume.thumbLeft(239, 321, 92, 0))
        assertEquals(468, HuntsvilleVolume.thumbLeft(239, 321, 92, 255))
        val left = HuntsvilleVolume.thumbLeft(239, 321, 92, 128)
        assertEquals(354, left)
        assertTrue(kotlin.math.abs(128 - HuntsvilleVolume.fromSlider(left,239,321,92)) <= 1)
    }
    @Test fun invalid_slider_geometry_is_silent() {
        assertEquals(0, HuntsvilleVolume.fromSlider(99, 10, 20, 20))
        assertEquals(0, HuntsvilleVolume.fromSlider(99, 10, 10, 20))
    }
}

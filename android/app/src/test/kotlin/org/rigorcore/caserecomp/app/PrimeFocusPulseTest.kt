package org.rigorcore.caserecomp.app

import org.junit.Assert.*
import org.junit.Test

class PrimeFocusPulseTest {
    @Test fun pulse_expires_blinks_and_cannot_be_extended_by_repeated_taps() {
        val pulse = PrimeFocusPulse()
        assertTrue(pulse.start(100, true))
        assertFalse(pulse.start(200, true))
        assertTrue(pulse.illuminated(4099))
        assertFalse(pulse.illuminated(4350))
        assertTrue(pulse.illuminated(4600))
        assertFalse(pulse.illuminated(5100))
        assertFalse(pulse.start(5200, false))
        assertTrue(pulse.start(5200, true))
        pulse.cancel()
        assertFalse(pulse.illuminated(5201))
    }
}

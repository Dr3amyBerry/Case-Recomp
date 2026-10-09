package org.rigorcore.caserecomp.sda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SdaClockUnitTest {

    @Test
    fun native_timer_preupdate_gate_pause_and_display() {
        // limit 1320, elapsed 1139 -> remaining 181s -> warning 3
        val clock = SdaClock(limit = 1320f, elapsed = 1139f)
        val events1 = clock.advance(1f)
        assertEquals(listOf(SdaClockEvent.Warning(3)), events1)
        assertEquals("00:03:00", clock.text())

        // limit 1320, elapsed 1319.5 -> advance 0.6 -> display 0s -> advance 1 -> timeout
        val clockTimeout = SdaClock(limit = 1320f, elapsed = 1319.5f)
        assertEquals(emptyList<SdaClockEvent>(), clockTimeout.advance(0.6f))
        assertEquals(0, clockTimeout.displaySeconds())
        assertEquals(listOf(SdaClockEvent.Timeout), clockTimeout.advance(1f))

        // paused clock: limit 1320, elapsed 1310 -> remaining 10s -> warning 4
        val clockPaused = SdaClock(limit = 1320f, elapsed = 1310f, paused = true)
        val eventsWarning4 = clockPaused.advance(1f)
        assertEquals(listOf(SdaClockEvent.Warning(4)), eventsWarning4)
        assertEquals(1310f, clockPaused.elapsed, 0.001f) // elapsed not incremented due to pause

        // suppressed events
        assertEquals(emptyList<SdaClockEvent>(), clockPaused.advance(1f, suppressEvents = true))
        // 60-second jump skip
        assertEquals(emptyList<SdaClockEvent>(), clockPaused.advance(60f))

        // display seconds calculation with fractional limit/elapsed
        val smallClock = SdaClock(limit = 10.5f, elapsed = 1.9f)
        assertEquals(9, smallClock.displaySeconds())
        assertEquals(1, smallClock.displaySeconds(unlimited = true))
    }

    @Test
    fun display_seconds_and_formatted_text() {
        val clock = SdaClock(limit = 3665f, elapsed = 65f)
        assertEquals(3600, clock.displaySeconds())
        assertEquals("01:00:00", clock.text())

        assertEquals(65, clock.displaySeconds(unlimited = true))
        assertEquals("00:01:05", clock.text(unlimited = true))
    }

    @Test
    fun pause_suppresses_elapsed_storage() {
        val clock = SdaClock(limit = 100f, elapsed = 10f, paused = true)
        val events = clock.advance(5f)
        assertTrue(events.isEmpty())
        assertEquals(10f, clock.elapsed, 0.001f)
    }
}

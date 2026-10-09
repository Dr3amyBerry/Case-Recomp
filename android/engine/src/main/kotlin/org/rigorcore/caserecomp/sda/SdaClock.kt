package org.rigorcore.caserecomp.sda

sealed class SdaClockEvent {
    object Timeout : SdaClockEvent()
    data class Warning(val level: Int) : SdaClockEvent()
}

/**
 * SDA level countdown clock recovered from 0x0041dc40, 0x0041db80, 0x0041dca0.
 */
class SdaClock(
    val limit: Float,
    var elapsed: Float = 0f,
    var paused: Boolean = false,
) {
    init {
        require(limit.isFinite() && limit > 0f) { "invalid clock limit" }
        require(elapsed.isFinite() && elapsed >= 0f) { "invalid clock elapsed" }
    }

    /**
     * Advance elapsed time. Emits events on integer second changes modulo 60.
     */
    fun advance(seconds: Float, suppressEvents: Boolean = false): List<SdaClockEvent> {
        require(seconds.isFinite() && seconds >= 0f) { "invalid frame duration" }
        val events = mutableListOf<SdaClockEvent>()
        val currentMod = elapsed.toInt() % 60
        val nextMod = (elapsed + seconds).toInt() % 60

        if (nextMod != currentMod && !suppressEvents) {
            val remaining = limit.toInt() - elapsed.toInt()
            if (elapsed >= limit) {
                events.add(SdaClockEvent.Timeout)
            } else {
                when (remaining) {
                    181 -> events.add(SdaClockEvent.Warning(3))
                    120 -> events.add(SdaClockEvent.Warning(2))
                    60 -> events.add(SdaClockEvent.Warning(1))
                    10 -> events.add(SdaClockEvent.Warning(4))
                }
            }
        }

        if (!paused) {
            elapsed += seconds
        }
        return events
    }

    fun displaySeconds(unlimited: Boolean = false): Int =
        if (unlimited) elapsed.toInt() else maxOf(0, limit.toInt() - elapsed.toInt())

    fun text(unlimited: Boolean = false): String {
        val total = displaySeconds(unlimited)
        val hours = total / 3600
        val minutes = (total / 60) % 60
        val secs = total % 60
        return "%02d:%02d:%02d".format(hours, minutes, secs)
    }
}

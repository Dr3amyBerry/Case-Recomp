package org.rigorcore.caserecomp.app

/** Five seconds including a one-second warning blink. Repeated taps cannot extend it. */
internal class PrimeFocusPulse {
    private var started: Long? = null
    fun start(now: Long, allowed: Boolean): Boolean {
        if (!allowed || busy(now)) return false
        started = now
        return true
    }
    fun busy(now: Long): Boolean = started?.let { now - it in 0 until 5000 } == true
    fun illuminated(now: Long): Boolean {
        val elapsed = started?.let { now - it } ?: return false
        return elapsed in 0 until 5000 && (elapsed < 4000 || (elapsed - 4000) / 250 % 2 == 0L)
    }
    fun cancel() { started = null }
}

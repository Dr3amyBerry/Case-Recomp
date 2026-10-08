package org.rigorcore.caserecomp.app

/** Monotonic playback time excludes time spent outside the activity. */
internal class DirectorSessionClock(private val source: () -> Long) {
    private var excluded = 0L
    private var pausedAt: Long? = null
    fun now(): Long = (pausedAt ?: source()) - excluded
    fun pause() { if (pausedAt == null) pausedAt = source() }
    fun resume() {
        pausedAt?.let { excluded += source() - it }
        pausedAt = null
    }
}

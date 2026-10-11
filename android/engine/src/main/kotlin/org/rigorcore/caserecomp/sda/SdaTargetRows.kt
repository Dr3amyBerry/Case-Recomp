package org.rigorcore.caserecomp.sda

/**
 * 0x0042a600: Target row fade-out scalar.
 */
class SdaTargetRow(
    var alpha: Float = 1.0f,
    var phase: Int = 0,
    var removed: Boolean = false,
) {
    fun update(objectsRetired: Boolean) {
        if (removed) return
        if (phase == FADE_IN) {
            // Refilled unlimited rows fade in at the same step the native list fades rows out.
            alpha = minOf(1f, alpha + 0.34f)
            if (alpha >= 1f) phase = 0
            if (!objectsRetired) return
            phase = 0
        }
        if (phase == 0 && objectsRetired) {
            phase = 1
        }
        if (phase == 1) {
            alpha -= 0.34f
            if (alpha <= 0f) {
                alpha = 0.0f
                phase = 2
            }
        } else if (phase == 2) {
            removed = true
            phase = 0
        }
    }
    companion object {
        const val FADE_IN = 3
    }
}

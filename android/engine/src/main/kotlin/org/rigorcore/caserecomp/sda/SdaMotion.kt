package org.rigorcore.caserecomp.sda

/**
 * 0x00428040: truncation, then increment only if remainder >= 0.5.
 */
fun sdaNativeRound(value: Float): Int {
    val truncated = value.toInt()
    return truncated + if (value - truncated >= 0.5f) 1 else 0
}

/**
 * Scalar found-object lifecycle recovered from 0x004278e0 / 0x00427bd0.
 * Two pulse scaling cycles, followed by upward velocity retirement.
 */
class SdaFoundMotion(
    val originalX: Int,
    val originalY: Int,
    val width: Int,
    val height: Int,
    var delay: Float = 0.85f,
    var velocity: Float = 0.0f,
    var scale: Float = 1.0f,
    var target: Float = 1.25f,
    var step: Float = 0.25f / (0.2f / 0.04f),
    var phase: Int = 1,
    var pulses: Int = 0,
    var removed: Boolean = false,
) {
    var x: Int = originalX
    var y: Int = originalY
    var drawWidth: Int = width
    var drawHeight: Int = height
    // 0x004279e1 loads rect height for both X and Y center calculation:
    val centerX: Int = originalX + height / 2
    val centerY: Int = originalY + height / 2

    fun update(seconds: Float) {
        require(seconds.isFinite() && seconds >= 0f) { "invalid frame duration" }
        if (removed) return

        delay -= seconds
        if (delay <= 0f) {
            velocity = maxOf(-10.0f, velocity - 0.5f)
            y += velocity.toInt()
            if (y < -drawHeight) {
                removed = true
                return
            }
        }

        if (phase == 0) return

        if (phase == 1) {
            scale = minOf(target, scale + step)
            if (scale >= target) {
                phase = 2
                target = 1.0f
                step = -0.25f / (0.2f / 0.04f)
            }
        } else if (phase == 2) {
            scale = maxOf(target, scale + step)
            if (scale <= target) {
                pulses++
                if (pulses < 2) {
                    phase = 1
                    target = 1.25f
                    step = 0.25f / (0.2f / 0.04f)
                } else {
                    phase = 0
                }
            }
        }

        drawWidth = sdaNativeRound(width * scale)
        drawHeight = sdaNativeRound(height * scale)
        x = sdaNativeRound(centerX - width * scale * 0.5f)
        y = sdaNativeRound(centerY - height * scale * 0.5f)
    }
}

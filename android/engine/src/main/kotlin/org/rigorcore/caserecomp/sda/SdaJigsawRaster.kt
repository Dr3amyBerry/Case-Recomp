package org.rigorcore.caserecomp.sda

class SdaArgbPixelSource(override val width: Int, override val height: Int, pixels: IntArray) : SdaPixelSource {
    private val data = pixels.copyOf()
    init { require(width in 1..4096 && height in 1..4096 && width.toLong() * height == data.size.toLong()) }
    override fun getArgb(px: Int, py: Int): Int = if (px in 0 until width && py in 0 until height) data[py * width + px] else 0
    override fun getAlpha(px: Int, py: Int): Int = getArgb(px, py) ushr 24
    fun copyPixels(): IntArray = data.copyOf()
}

/** 00438000 crops background RGB and takes alpha from the grayscale mask byte. */
object SdaJigsawRaster {
    fun crop(background: SdaPixelSource, mask: SdaPixelSource, x: Int, y: Int): SdaArgbPixelSource {
        require(x >= 0 && y >= 0 && x.toLong() + mask.width <= background.width && y.toLong() + mask.height <= background.height) {
            "jigsaw crop outside background"
        }
        require(mask.width in 1..2048 && mask.height in 1..2048)
        return SdaArgbPixelSource(mask.width, mask.height, IntArray(mask.width * mask.height) { i ->
            val px = i % mask.width; val py = i / mask.width
            val alpha = mask.getArgb(px, py) and 255
            if (alpha == 0) 0 else (background.getArgb(x + px, y + py) and 0xffffff) or (alpha shl 24)
        })
    }
    /** Explicit prototype raster policy: nearest sampling/truncated dimensions, not certified SDA graphics-library parity. */
    fun transform(source: SdaPixelSource, quarter: Int, scale: Float): SdaArgbPixelSource {
        require(quarter in 0..3 && scale.isFinite() && scale > 0f && scale <= 1f)
        val rw = if (quarter % 2 == 0) source.width else source.height
        val rh = if (quarter % 2 == 0) source.height else source.width
        val w = maxOf(1, (rw * scale).toInt()); val h = maxOf(1, (rh * scale).toInt())
        return SdaArgbPixelSource(w, h, IntArray(w * h) { i ->
            val rx = (i % w).toLong().times(rw).div(w).toInt()
            val ry = (i / w).toLong().times(rh).div(h).toInt()
            val (sx, sy) = when (quarter) {
                0 -> rx to ry
                1 -> ry to source.height - 1 - rx
                2 -> source.width - 1 - rx to source.height - 1 - ry
                else -> source.width - 1 - ry to rx
            }
            source.getArgb(sx, sy)
        })
    }
}

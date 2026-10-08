package org.rigorcore.caserecomp.director

import org.rigorcore.caserecomp.lingo.LingoError
import org.rigorcore.caserecomp.lingo.LingoValue
import org.rigorcore.caserecomp.lingo.LingoValue.LInt
import org.rigorcore.caserecomp.lingo.LingoValue.LPropList
import org.rigorcore.caserecomp.lingo.LingoValue.LRect
import org.rigorcore.caserecomp.lingo.LingoValue.LSymbol
import org.rigorcore.caserecomp.lingo.LingoValue.Void
import org.rigorcore.caserecomp.lingo.isTruthy
import org.rigorcore.caserecomp.lingo.lingoEquals
import org.rigorcore.caserecomp.lingo.toInt

/** Lingo `rgb(r, g, b)` colour. */
class LColor(val red: Int, val green: Int, val blue: Int) : LingoValue.LHost {
    override val ilk = "color"
    val argb: Int get() = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
    override fun getProp(name: String): LingoValue = when (name.lowercase()) {
        "red" -> LInt(red); "green" -> LInt(green); "blue" -> LInt(blue)
        "ilk" -> LSymbol("color")
        else -> throw LingoError("color has no property $name")
    }
    override fun setProp(name: String, value: LingoValue) = throw LingoError("color is immutable")
    override fun call(method: String, args: List<LingoValue>): LingoValue? = null
    override fun equals(other: Any?) = other is LColor && other.argb == argb
    override fun hashCode() = argb
    override fun toString() = "rgb($red, $green, $blue)"

    companion object {
        /** Colour argument: rgb() value, palette index (8-bit greyscale approximation) or #RRGGBB string. */
        fun of(value: LingoValue): LColor = when (value) {
            is LColor -> value
            is LingoValue.LString -> value.value.removePrefix("#").toIntOrNull(16)
                ?.let { LColor((it shr 16) and 0xFF, (it shr 8) and 0xFF, it and 0xFF) } ?: LColor(0, 0, 0)
            else -> (255 - value.toInt().coerceIn(0, 255)).let { LColor(it, it, it) }
        }
    }
}

/**
 * Lingo image object: ARGB pixels plus the `useAlpha` flag. Supports the subset of
 * imaging Lingo used by Director 8.5 titles: fill, copyPixels (scaled, with alpha and
 * blend), duplicate, crop and per-pixel access.
 */
class LingoImage(val width: Int, val height: Int, val depth: Int = 32, val pixels: IntArray = IntArray(width * height)) :
    LingoValue.LHost {
    init { if (width < 0 || height < 0 || pixels.size != width * height) throw LingoError("invalid image size") }

    override val ilk = "image"
    var useAlpha: Boolean = depth == 32

    fun rectValue(): LRect = LRect(LInt(0), LInt(0), LInt(width), LInt(height))

    override fun getProp(name: String): LingoValue = when (name.lowercase()) {
        "width" -> LInt(width); "height" -> LInt(height); "depth" -> LInt(depth)
        "rect" -> rectValue()
        "usealpha" -> LingoValue.bool(useAlpha)
        "ilk" -> LSymbol("image")
        else -> throw LingoError("image has no property $name")
    }

    override fun setProp(name: String, value: LingoValue) {
        when (name.lowercase()) {
            "usealpha" -> useAlpha = value.isTruthy()
            else -> throw LingoError("cannot set image property $name")
        }
    }

    override fun call(method: String, args: List<LingoValue>): LingoValue? = when (method.lowercase()) {
        "fill" -> {
            val (rect, color) = if (args.size >= 5) intRect(LRect(args[0], args[1], args[2], args[3])) to args[4]
                else intRect(args.getOrElse(0) { rectValue() }) to args.getOrElse(1) { Void }
            fill(rect, colorArgb(color)); Void
        }
        "copypixels" -> {
            val source = args.getOrNull(0) as? LingoImage ?: throw LingoError("copyPixels needs a source image")
            val dest = intRect(args.getOrElse(1) { Void })
            val src = intRect(args.getOrElse(2) { source.rectValue() })
            copyPixels(source, dest, src, args.getOrNull(3) as? LPropList); Void
        }
        "duplicate" -> duplicate()
        "crop" -> crop(intRect(args.getOrElse(0) { rectValue() }))
        "getpixel" -> {
            val x = args.getOrElse(0) { Void }.toInt(); val y = args.getOrElse(1) { Void }.toInt()
            if (x !in 0 until width || y !in 0 until height) Void
            else pixels[y * width + x].let { LColor((it shr 16) and 0xFF, (it shr 8) and 0xFF, it and 0xFF) }
        }
        "setpixel" -> {
            val x = args.getOrElse(0) { Void }.toInt(); val y = args.getOrElse(1) { Void }.toInt()
            if (x in 0 until width && y in 0 until height) { pixels[y * width + x] = colorArgb(args.getOrElse(2) { Void }); LingoValue.TRUE }
            else LingoValue.FALSE
        }
        else -> null
    }

    fun duplicate(): LingoImage = LingoImage(width, height, depth, pixels.copyOf()).also { it.useAlpha = useAlpha }

    fun crop(r: IntArray): LingoImage {
        val left = r[0].coerceIn(0, width); val top = r[1].coerceIn(0, height)
        val right = r[2].coerceIn(left, width); val bottom = r[3].coerceIn(top, height)
        val out = LingoImage(right - left, bottom - top, depth)
        for (y in top until bottom) System.arraycopy(pixels, y * width + left, out.pixels, (y - top) * out.width, right - left)
        out.useAlpha = useAlpha
        return out
    }

    fun fill(r: IntArray, argb: Int) {
        for (y in maxOf(0, r[1]) until minOf(height, r[3])) for (x in maxOf(0, r[0]) until minOf(width, r[2])) {
            pixels[y * width + x] = argb
        }
    }

    /** Nearest-neighbour scaled copy with Director's #copy/#matte/#backgroundTransparent inks and blend. */
    fun copyPixels(source: LingoImage, dest: IntArray, src: IntArray, props: LPropList?) {
        val dw = dest[2] - dest[0]; val dh = dest[3] - dest[1]
        val sw = src[2] - src[0]; val sh = src[3] - src[1]
        if (dw <= 0 || dh <= 0 || sw <= 0 || sh <= 0) return
        fun prop(key: String) = props?.entries?.firstOrNull { lingoEquals(it.first, LSymbol(key)) }?.second
        val blend = prop("blendLevel")?.toInt()?.coerceIn(0, 255) ?: prop("blend")?.let { it.toInt() * 255 / 100 } ?: 255
        val ink = prop("ink")?.let { if (it is LSymbol) INK_NAMES[it.name.lowercase()] ?: 0 else it.toInt() } ?: 0
        val sourceAlpha = source.useAlpha && source.depth == 32
        val white = 0xFFFFFF
        for (y in 0 until dh) {
            val ty = dest[1] + y
            if (ty !in 0 until height) continue
            val sy = src[1] + y * sh / dh
            if (sy !in 0 until source.height) continue
            for (x in 0 until dw) {
                val tx = dest[0] + x
                if (tx !in 0 until width) continue
                val sx = src[0] + x * sw / dw
                if (sx !in 0 until source.width) continue
                val p = source.pixels[sy * source.width + sx]
                if ((ink == 36 || ink == 8) && !sourceAlpha && (p and 0xFFFFFF) == white) continue
                var alpha = if (sourceAlpha) (p ushr 24) else 255
                alpha = alpha * blend / 255
                if (alpha == 0) continue
                val i = ty * width + tx
                pixels[i] = if (alpha == 255) p or (0xFF shl 24) else blendPixel(pixels[i], p, alpha)
            }
        }
    }

    companion object {
        val INK_NAMES = mapOf("copy" to 0, "matte" to 8, "backgroundtransparent" to 36, "blend" to 32)

        fun blendPixel(under: Int, over: Int, alpha: Int): Int {
            // Per channel: (over * alpha + under * (255 - alpha)) / 255. Written out: this runs per pixel.
            val rest = 255 - alpha
            val r = (((over shr 16) and 0xFF) * alpha + ((under shr 16) and 0xFF) * rest) / 255
            val g = (((over shr 8) and 0xFF) * alpha + ((under shr 8) and 0xFF) * rest) / 255
            val b = ((over and 0xFF) * alpha + (under and 0xFF) * rest) / 255
            val outAlpha = maxOf(under ushr 24, alpha)
            return (outAlpha shl 24) or (r shl 16) or (g shl 8) or b
        }

        fun colorArgb(value: LingoValue): Int = LColor.of(value).argb

        /** rect value (or 4-element list) to [left, top, right, bottom]. */
        fun intRect(value: LingoValue): IntArray = when (value) {
            is LRect -> intArrayOf(value.left.toInt(), value.top.toInt(), value.right.toInt(), value.bottom.toInt())
            is LingoValue.LList -> if (value.items.size == 4) IntArray(4) { value.items[it].toInt() }
                else throw LingoError("expected a rect")
            else -> throw LingoError("expected a rect, got ${value::class.simpleName}")
        }
    }
}

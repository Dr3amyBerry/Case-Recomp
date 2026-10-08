package org.rigorcore.caserecomp.director

import org.rigorcore.caserecomp.flash.FlashDraw
import org.rigorcore.caserecomp.flash.FlashPlayer
import org.rigorcore.caserecomp.flash.SwfBitmap
import org.rigorcore.caserecomp.flash.SwfFill
import org.rigorcore.caserecomp.flash.SwfMatrix
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** Platform text drawing: renders a text member's text into an image of the given size. */
fun interface TextRasterizer {
    fun render(member: CastMember, width: Int, height: Int): LingoImage?

    object None : TextRasterizer {
        override fun render(member: CastMember, width: Int, height: Int): LingoImage? = null
    }
}

/**
 * Software compositor for the stage: draws a [DirectorRuntime] display list into an
 * ARGB frame. Bitmaps honour their alpha, sprite blend and the copy, matte and
 * background-transparent inks; shapes fill with their colour; Flash sprites draw
 * their shapes (solid or bitmap filled) through the movie's affine transforms.
 */
private const val BLACK = 0xFF000000.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()
private const val OPAQUE = 0xFF000000.toInt()

class StageRenderer(
    private val runtime: DirectorRuntime,
    private val text: TextRasterizer = TextRasterizer.None,
    private val decoder: ImageDecoder? = null,
    val background: Int = 0xFF000000.toInt(),
) {
    val width = runtime.movie.stageWidth
    val height = runtime.movie.stageHeight
    val frame = LingoImage(width, height)

    private val swfBitmaps = HashMap<Pair<Any, Int>, LingoImage?>()
    private val textCache = HashMap<CastMember, Triple<String, Pair<Int, Int>, LingoImage?>>()

    fun render(): LingoImage {
        frame.pixels.fill(background)
        for (item in runtime.displayList()) draw(item)
        return frame
    }

    private fun draw(item: DisplayItem) {
        val alpha = item.blend.coerceIn(0, 100) * 255 / 100
        if (alpha == 0) return
        val w = item.right - item.left; val h = item.bottom - item.top
        if (w <= 0 || h <= 0) return
        // Parked sprites (titles move them to 999,999) are skipped before touching their media.
        if (item.right <= 0 || item.bottom <= 0 || item.left >= width || item.top >= height) {
            if (item.rotation == 0.0) return
        }
        when (item.member.type) {
            "bitmap" -> item.member.pixels?.let { blit(it, item, alpha) }
            "shape" -> {
                val color = LColor.of(runtime.sprite(item.sprite).getProp("color"))
                if (item.member.data?.filled != false) fillRect(item.left, item.top, item.right, item.bottom, color.argb, alpha)
            }
            "text", "field" -> textImage(item.member, w, h)?.let { blit(colorize(item.member, it, item.fore, item.back), item, alpha) }
            "flash" -> item.flash?.let { drawFlash(it, item, alpha) }
        }
    }

    /** Last colorized text image per member: (source image, fore, back) -> result. */
    private val colorized = HashMap<CastMember, Pair<Triple<LingoImage, Int, Int>, LingoImage>>()

    /** Director's sprite colours remap the member: black becomes [fore], white [back], greys between. */
    private fun colorize(member: CastMember, src: LingoImage, fore: Int, back: Int): LingoImage {
        if (fore == BLACK && back == WHITE) return src
        val key = Triple(src, fore, back)
        colorized[member]?.let { (k, image) -> if (k.first === src && k.second == fore && k.third == back) return image }
        val out = LingoImage(src.width, src.height, src.depth, IntArray(src.pixels.size) { i ->
            val p = src.pixels[i]
            fun channel(shift: Int): Int {
                val f = (fore shr shift) and 0xFF; val b = (back shr shift) and 0xFF
                return f + (b - f) * ((p shr shift) and 0xFF) / 255
            }
            (p and 0xFF000000.toInt()) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
        }).also { it.useAlpha = src.useAlpha }
        colorized[member] = key to out
        return out
    }

    private fun textImage(member: CastMember, w: Int, h: Int): LingoImage? {
        val key = member.text to (w to h)
        textCache[member]?.let { (t, size, image) -> if (t == key.first && size == key.second) return image }
        return text.render(member, w, h).also { textCache[member] = Triple(key.first, key.second, it) }
    }

    /** Scaled (nearest) blit honouring alpha, inks, flips and rotation about the rect centre. */
    private fun blit(src: LingoImage, item: DisplayItem, alpha: Int) {
        val w = item.right - item.left; val h = item.bottom - item.top
        if (src.width == 0 || src.height == 0) return
        val useAlpha = src.useAlpha
        val keyWhite = !useAlpha && (item.ink == 36 || item.ink == 8)
        if (item.rotation != 0.0) {
            blitRotated(src, item, alpha, keyWhite); return
        }
        val y0 = maxOf(0, item.top); val y1 = minOf(height, item.bottom)
        val x0 = maxOf(0, item.left); val x1 = minOf(width, item.right)
        val n = x1 - x0
        if (n <= 0 || y1 <= y0) return
        // Source column of each destination column, computed once per blit.
        val columns = columnMap
        for (i in 0 until n) {
            var sx = (x0 + i - item.left) * src.width / w
            if (item.flipH) sx = src.width - 1 - sx
            columns[i] = sx
        }
        val sp = src.pixels; val fp = frame.pixels
        for (y in y0 until y1) {
            var sy = (y - item.top) * src.height / h
            if (item.flipV) sy = src.height - 1 - sy
            val row = sy * src.width
            val out = y * width + x0
            if (!useAlpha && !keyWhite && alpha == 255) {
                for (i in 0 until n) fp[out + i] = sp[row + columns[i]] or OPAQUE
                continue
            }
            for (i in 0 until n) {
                val p = sp[row + columns[i]]
                if (keyWhite && (p and 0xFFFFFF) == 0xFFFFFF) continue
                val sa = if (useAlpha) p ushr 24 else 255
                if (sa == 0) continue
                val a = if (alpha == 255) sa else sa * alpha / 255
                if (a == 0) continue
                fp[out + i] = if (a == 255) p or OPAQUE else LingoImage.blendPixel(fp[out + i], p, a)
            }
        }
    }

    private val columnMap = IntArray(width)

    private fun blitRotated(src: LingoImage, item: DisplayItem, alpha: Int, keyWhite: Boolean) {
        val w = (item.right - item.left).toDouble(); val h = (item.bottom - item.top).toDouble()
        val cx = item.left + w / 2; val cy = item.top + h / 2
        val rad = Math.toRadians(item.rotation)
        val c = cos(rad); val s = sin(rad)
        val r = kotlin.math.hypot(w, h) / 2
        for (y in maxOf(0, floor(cy - r).toInt()) until minOf(height, ceil(cy + r).toInt())) {
            for (x in maxOf(0, floor(cx - r).toInt()) until minOf(width, ceil(cx + r).toInt())) {
                // Inverse-rotate the destination pixel into the unrotated sprite rectangle.
                val dx = x + 0.5 - cx; val dy = y + 0.5 - cy
                val ux = dx * c + dy * s + w / 2; val uy = -dx * s + dy * c + h / 2
                if (ux < 0 || uy < 0 || ux >= w || uy >= h) continue
                var sx = (ux * src.width / w).toInt(); var sy = (uy * src.height / h).toInt()
                if (item.flipH) sx = src.width - 1 - sx
                if (item.flipV) sy = src.height - 1 - sy
                put(y * width + x, src.pixels[sy * src.width + sx], src.useAlpha, keyWhite, alpha)
            }
        }
    }

    private fun put(index: Int, p: Int, useAlpha: Boolean, keyWhite: Boolean, alpha: Int) {
        if (keyWhite && (p and 0xFFFFFF) == 0xFFFFFF) return
        val a = (if (useAlpha) p ushr 24 else 255) * alpha / 255
        if (a == 0) return
        frame.pixels[index] = if (a == 255) p or (0xFF shl 24) else LingoImage.blendPixel(frame.pixels[index], p, a)
    }

    private fun fillRect(left: Int, top: Int, right: Int, bottom: Int, argb: Int, alpha: Int) {
        val a = (argb ushr 24) * alpha / 255
        for (y in maxOf(0, top) until minOf(height, bottom)) for (x in maxOf(0, left) until minOf(width, right)) {
            val i = y * width + x
            frame.pixels[i] = if (a == 255) argb else LingoImage.blendPixel(frame.pixels[i], argb, a)
        }
    }

    private fun drawFlash(player: FlashPlayer, item: DisplayItem, alpha: Int) {
        val r = player.movie.bounds
        if (r.width <= 0 || r.height <= 0) return
        // Movie pixels to stage pixels through the sprite rectangle.
        val toStage = SwfMatrix((item.right - item.left) / r.width, 0.0, 0.0, (item.bottom - item.top) / r.height,
            item.left - r.xMin * (item.right - item.left) / r.width, item.top - r.yMin * (item.bottom - item.top) / r.height)
        val coverage = HashMap<List<FlashDraw>, BooleanArray>()
        for (d in player.drawList()) {
            // Clip layers: a pixel is drawn only where every layer covers it.
            val masks = d.masks.map { layer -> coverage.getOrPut(layer) { maskCoverage(layer, toStage) } }
            drawShape(player, d, toStage * d.matrix, (d.alpha * alpha).toInt(), masks)
        }
    }

    private fun maskCoverage(layer: List<FlashDraw>, toStage: SwfMatrix): BooleanArray {
        val covered = BooleanArray(width * height)
        for (d in layer) {
            val m = toStage * d.matrix
            for (style in 1..d.shape.fills.size) scanFill(d, m, style) { y, x0, x1, _ -> covered.fill(true, y * width + x0, y * width + x1) }
        }
        return covered
    }

    /** Scanline spans (stage y, x0 until x1, sample y) of one fill-style region of a shape, even-odd. */
    private inline fun scanFill(d: FlashDraw, m: SwfMatrix, style: Int, span: (Int, Int, Int, Double) -> Unit) {
        // Edges bounding this region, in stage pixels.
        val edges = d.shape.edges.filter { (it.fill0 == style) != (it.fill1 == style) }
            .map { doubleArrayOf(m.x(it.x0, it.y0), m.y(it.x0, it.y0), m.x(it.x1, it.y1), m.y(it.x1, it.y1)) }
        if (edges.isEmpty()) return
        val top = maxOf(0, floor(edges.minOf { minOf(it[1], it[3]) }).toInt())
        val bottom = minOf(height, ceil(edges.maxOf { maxOf(it[1], it[3]) }).toInt())
        val crossings = DoubleArray(edges.size)
        for (y in top until bottom) {
            val sy = y + 0.5
            var n = 0
            for (e in edges) if ((e[1] > sy) != (e[3] > sy)) crossings[n++] = e[0] + (sy - e[1]) * (e[2] - e[0]) / (e[3] - e[1])
            crossings.sort(0, n)
            var i = 0
            while (i + 1 < n) {
                val x0 = maxOf(0, ceil(crossings[i] - 0.5).toInt()); val x1 = minOf(width, ceil(crossings[i + 1] - 0.5).toInt())
                if (x1 > x0) span(y, x0, x1, sy)
                i += 2
            }
        }
    }

    /** Scanline fill of every fill-style region of a Flash shape (even-odd per region). */
    private fun drawShape(player: FlashPlayer, d: FlashDraw, m: SwfMatrix, alpha: Int, masks: List<BooleanArray>) {
        if (alpha <= 0) return
        val inv = m.inverse() ?: return
        for ((index, fill) in d.shape.fills.withIndex()) {
            fill ?: continue
            val bitmap = (fill as? SwfFill.Bitmap)?.let { swfBitmap(player, it.bitmapId) }
            val fillInverse = (fill as? SwfFill.Bitmap)?.matrix?.inverse()
            if (fill is SwfFill.Bitmap && (bitmap == null || fillInverse == null)) continue
            scanFill(d, m, index + 1) { y, x0, x1, sy ->
                for (x in x0 until x1) {
                    val i = y * width + x
                    if (masks.any { !it[i] }) continue
                    val p = when (fill) {
                        is SwfFill.Solid -> fill.argb
                        is SwfFill.Bitmap -> {
                            val image = bitmap!!
                            val fi = fillInverse!!
                            val lx = inv.x(x + 0.5, sy); val ly = inv.y(x + 0.5, sy)
                            val bx = floor(fi.x(lx, ly)).toInt(); val by = floor(fi.y(lx, ly)).toInt()
                            if (bx !in 0 until image.width || by !in 0 until image.height) {
                                if (fill.clipped) image.pixels[by.coerceIn(0, image.height - 1) * image.width + bx.coerceIn(0, image.width - 1)]
                                else image.pixels[Math.floorMod(by, image.height) * image.width + Math.floorMod(bx, image.width)]
                            } else image.pixels[by * image.width + bx]
                        }
                    }
                    put(i, p, true, false, alpha.coerceAtMost(255))
                }
            }
        }
    }

    private fun swfBitmap(player: FlashPlayer, id: Int): LingoImage? = swfBitmaps.getOrPut(player.movie to id) {
        val c = player.movie.characters[id] as? SwfBitmap ?: return@getOrPut null
        c.argb?.let { LingoImage(c.width, c.height, 32, it) } ?: c.jpeg?.let { jpeg ->
            decoder?.decode(jpeg)?.also { image ->
                val mask = c.alpha
                if (mask != null && mask.size >= image.pixels.size) for (i in image.pixels.indices) {
                    image.pixels[i] = (image.pixels[i] and 0xFFFFFF) or ((mask[i].toInt() and 0xFF) shl 24)
                }
                image.useAlpha = true
            }
        }
    }
}

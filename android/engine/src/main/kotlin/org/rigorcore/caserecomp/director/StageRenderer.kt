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

    /**
     * The same text, laid out for a [width] x [height] box, drawn [scale] times larger for a
     * higher-resolution stage. Null when unsupported; the 1x image is then enlarged.
     */
    fun render(member: CastMember, width: Int, height: Int, scale: Int): LingoImage? =
        if (scale == 1) render(member, width, height) else null

    object None : TextRasterizer {
        override fun render(member: CastMember, width: Int, height: Int): LingoImage? = null
    }
}

/**
 * Software compositor for the stage: draws a [DirectorRuntime] display list into an
 * ARGB frame. Bitmaps honour their alpha, sprite blend and the copy, matte and
 * background-transparent inks; shapes fill with their colour; Flash sprites draw
 * their shapes (solid or bitmap filled) through the movie's affine transforms.
 *
 * With [scale] > 1 the frame is that many times the stage size: bitmaps are enlarged
 * pixel-exactly while text and Flash vectors are drawn at the higher resolution.
 */
private const val BLACK = 0xFF000000.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()
private const val OPAQUE = 0xFF000000.toInt()

class StageRenderer(
    private val runtime: DirectorRuntime,
    private val text: TextRasterizer = TextRasterizer.None,
    private val decoder: ImageDecoder? = null,
    val background: Int = 0xFF000000.toInt(),
    val scale: Int = 1,
) {
    /** Stage size in Director pixels. */
    val width = runtime.movie.stageWidth
    val height = runtime.movie.stageHeight
    /** Frame size in output pixels. */
    private val fw = width * scale
    private val fh = height * scale
    val frame = LingoImage(fw, fh)

    init { require(scale in 1..4) { "stage scale must be 1..4" } }

    private val swfBitmaps = HashMap<Pair<Any, Int>, LingoImage?>()
    private val textCache = HashMap<CastMember, Triple<String, Pair<Int, Int>, LingoImage?>>()

    fun render(): LingoImage {
        masksInUse = 0
        val items = runtime.displayList()
        // An opaque full-stage backdrop overwrites every pixel: clearing first is wasted work.
        if (items.firstOrNull()?.let { coversStage(it) } != true) frame.pixels.fill(background)
        for (item in items) draw(item)
        return frame
    }

    private fun coversStage(item: DisplayItem): Boolean {
        if (item.member.type != "bitmap" || item.blend < 100 || item.rotation != 0.0 || item.ink != 0) return false
        if (item.left > 0 || item.top > 0 || item.right < width || item.bottom < height) return false
        val pixels = item.member.pixels ?: return false
        return !pixels.useAlpha && pixels.width > 0 && pixels.height > 0
    }

    private fun draw(stageItem: DisplayItem) {
        val alpha = stageItem.blend.coerceIn(0, 100) * 255 / 100
        if (alpha == 0) return
        val w = stageItem.right - stageItem.left; val h = stageItem.bottom - stageItem.top
        if (w <= 0 || h <= 0) return
        // Parked sprites (titles move them to 999,999) are skipped before touching their media.
        if (stageItem.right <= 0 || stageItem.bottom <= 0 || stageItem.left >= width || stageItem.top >= height) {
            if (stageItem.rotation == 0.0) return
        }
        val item = if (scale == 1) stageItem else stageItem.copy(left = stageItem.left * scale, top = stageItem.top * scale,
            right = stageItem.right * scale, bottom = stageItem.bottom * scale)
        when (item.member.type) {
            "bitmap" -> item.member.pixels?.let { blit(it, item, alpha) }
            "shape" -> {
                val color = LColor.of(runtime.sprite(item.sprite).getProp("color"))
                if (item.member.data?.filled != false) fillRect(item.left, item.top, item.right, item.bottom, color.argb, alpha)
            }
            "text", "field" -> textImage(item.member, w, h)?.let { image ->
                // A box grown to its content comes back taller: it extends the sprite downwards.
                val perStagePixel = (image.width / w).coerceAtLeast(1)
                val grown = if (image.height > h * perStagePixel) item.copy(bottom = item.top + image.height * scale / perStagePixel) else item
                blit(colorize(item.member, image, item.fore, item.back), grown, alpha)
            }
            // Flash is sampled per stage pixel and written as scale x scale blocks: full-stage
            // animations would otherwise cost scale² times more for little visible gain.
            "flash" -> stageItem.flash?.let { drawFlash(it, stageItem, alpha) }
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
        val image = text.render(member, w, h, scale) ?: if (scale != 1) text.render(member, w, h) else null
        return image.also { textCache[member] = Triple(key.first, key.second, it) }
    }

    /** Scaled (nearest) blit honouring alpha, inks, flips and rotation about the rect centre. */
    private fun blit(src: LingoImage, item: DisplayItem, alpha: Int) {
        val w = item.right - item.left; val h = item.bottom - item.top
        if (src.width == 0 || src.height == 0) return
        val useAlpha = src.useAlpha
        // Background transparent (36) drops every white pixel; matte (8) only the white
        // connected to the image edge, so white inside the outline stays.
        val keyWhite = !useAlpha && item.ink == 36
        val matte = if (!useAlpha && item.ink == 8) matteMask(src) else null
        if (item.rotation != 0.0) {
            blitRotated(src, item, alpha, keyWhite, matte); return
        }
        val y0 = maxOf(0, item.top); val y1 = minOf(fh, item.bottom)
        val x0 = maxOf(0, item.left); val x1 = minOf(fw, item.right)
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
        val opaque = !useAlpha && !keyWhite && matte == null && alpha == 255
        var previous = -1
        for (y in y0 until y1) {
            var sy = (y - item.top) * src.height / h
            if (item.flipV) sy = src.height - 1 - sy
            val row = sy * src.width
            val out = y * fw + x0
            if (opaque) {
                // Enlarged sprites repeat source rows: copy the row just written.
                if (sy == previous) System.arraycopy(fp, out - fw, fp, out, n)
                else for (i in 0 until n) fp[out + i] = sp[row + columns[i]] or OPAQUE
                previous = sy
                continue
            }
            for (i in 0 until n) {
                val p = sp[row + columns[i]]
                if (keyWhite && (p and 0xFFFFFF) == 0xFFFFFF) continue
                if (matte != null && matte[row + columns[i]]) continue
                val sa = if (useAlpha) p ushr 24 else 255
                if (sa == 0) continue
                val a = if (alpha == 255) sa else sa * alpha / 255
                if (a == 0) continue
                fp[out + i] = if (a == 255) p or OPAQUE else LingoImage.blendPixel(fp[out + i], p, a)
            }
        }
    }

    private val columnMap = IntArray(fw)

    /** Matte ink masks per image: true where white is reachable from the edge through white. */
    private val matteMasks = java.util.WeakHashMap<LingoImage, BooleanArray>()

    private fun matteMask(src: LingoImage): BooleanArray = matteMasks.getOrPut(src) {
        val w = src.width; val h = src.height; val px = src.pixels
        val clear = BooleanArray(w * h)
        val queue = IntArray(w * h)
        var head = 0; var tail = 0
        fun visit(i: Int) {
            if (!clear[i] && (px[i] and 0xFFFFFF) == 0xFFFFFF) { clear[i] = true; queue[tail++] = i }
        }
        for (x in 0 until w) { visit(x); visit((h - 1) * w + x) }
        for (y in 0 until h) { visit(y * w); visit(y * w + w - 1) }
        while (head < tail) {
            val i = queue[head++]
            val x = i % w
            if (x > 0) visit(i - 1)
            if (x < w - 1) visit(i + 1)
            if (i >= w) visit(i - w)
            if (i + w < w * h) visit(i + w)
        }
        clear
    }

    private fun blitRotated(src: LingoImage, item: DisplayItem, alpha: Int, keyWhite: Boolean, matte: BooleanArray?) {
        val w = (item.right - item.left).toDouble(); val h = (item.bottom - item.top).toDouble()
        val cx = item.left + w / 2; val cy = item.top + h / 2
        val rad = Math.toRadians(item.rotation)
        val c = cos(rad); val s = sin(rad)
        val r = kotlin.math.hypot(w, h) / 2
        for (y in maxOf(0, floor(cy - r).toInt()) until minOf(fh, ceil(cy + r).toInt())) {
            for (x in maxOf(0, floor(cx - r).toInt()) until minOf(fw, ceil(cx + r).toInt())) {
                // Inverse-rotate the destination pixel into the unrotated sprite rectangle.
                val dx = x + 0.5 - cx; val dy = y + 0.5 - cy
                val ux = dx * c + dy * s + w / 2; val uy = -dx * s + dy * c + h / 2
                if (ux < 0 || uy < 0 || ux >= w || uy >= h) continue
                var sx = (ux * src.width / w).toInt(); var sy = (uy * src.height / h).toInt()
                if (item.flipH) sx = src.width - 1 - sx
                if (item.flipV) sy = src.height - 1 - sy
                if (matte != null && matte[sy * src.width + sx]) continue
                put(y * fw + x, src.pixels[sy * src.width + sx], src.useAlpha, keyWhite, alpha)
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
        for (y in maxOf(0, top) until minOf(fh, bottom)) for (x in maxOf(0, left) until minOf(fw, right)) {
            val i = y * fw + x
            frame.pixels[i] = if (a == 255) argb else LingoImage.blendPixel(frame.pixels[i], argb, a)
        }
    }

    private fun drawFlash(player: FlashPlayer, item: DisplayItem, alpha: Int) {
        val r = player.movie.bounds
        if (r.width <= 0 || r.height <= 0) return
        // Movie pixels to stage pixels through the sprite rectangle.
        val toStage = SwfMatrix((item.right - item.left) / r.width, 0.0, 0.0, (item.bottom - item.top) / r.height,
            item.left - r.xMin * (item.right - item.left) / r.width, item.top - r.yMin * (item.bottom - item.top) / r.height)
        val coverage = HashMap<List<FlashDraw>, Mask>()
        for (d in player.drawList()) {
            // Clip layers: a pixel is drawn only where every layer covers it.
            val masks = d.masks.map { layer -> coverage.getOrPut(layer) { maskCoverage(layer, toStage) } }
            drawShape(player, d, toStage * d.matrix, (d.alpha * alpha).toInt(), masks)
        }
    }

    /** Stage-sized clip coverage; [top] until [bottom] are the rows that may hold covered pixels. */
    private class Mask(val bits: BooleanArray) {
        var top = 0
        var bottom = 0
    }

    /** Masks reused from frame to frame (a stage-sized array per clip layer is costly to allocate). */
    private val maskPool = ArrayList<Mask>()
    private var masksInUse = 0

    private fun maskCoverage(layer: List<FlashDraw>, toStage: SwfMatrix): Mask {
        val mask = maskPool.getOrNull(masksInUse) ?: Mask(BooleanArray(width * height)).also { maskPool += it }
        masksInUse++
        mask.bits.fill(false, mask.top * width, mask.bottom * width)
        mask.top = height; mask.bottom = 0
        for (d in layer) {
            val m = toStage * d.matrix
            for (style in 1..d.shape.fills.size) scanFill(d, m, style) { y, x0, x1, _ ->
                mask.bits.fill(true, y * width + x0, y * width + x1)
                mask.top = minOf(mask.top, y); mask.bottom = maxOf(mask.bottom, y + 1)
            }
        }
        if (mask.bottom < mask.top) mask.top = mask.bottom
        return mask
    }

    private fun masked(masks: List<Mask>, i: Int): Boolean {
        for (k in masks.indices) if (!masks[k].bits[i]) return true
        return false
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

    /** One stage pixel of Flash output: a scale x scale block of the frame. */
    private fun plot(x: Int, y: Int, p: Int, alpha: Int) {
        if (scale == 1) { put(y * fw + x, p, true, false, alpha); return }
        val a = (p ushr 24) * alpha / 255
        if (a == 0) return
        val fp = frame.pixels
        for (dy in 0 until scale) {
            val start = (y * scale + dy) * fw + x * scale
            for (i in start until start + scale) fp[i] = if (a == 255) p else LingoImage.blendPixel(fp[i], p, a)
        }
    }

    /** Scanline fill of every fill-style region of a Flash shape (even-odd per region), in stage pixels. */
    private fun drawShape(player: FlashPlayer, d: FlashDraw, m: SwfMatrix, alpha: Int, masks: List<Mask>) {
        if (alpha <= 0) return
        val inv = m.inverse() ?: return
        val a = alpha.coerceAtMost(255)
        for ((index, fill) in d.shape.fills.withIndex()) {
            fill ?: continue
            if (fill is SwfFill.Solid) {
                scanFill(d, m, index + 1) { y, x0, x1, _ ->
                    for (x in x0 until x1) if (!masked(masks, y * width + x)) plot(x, y, fill.argb, a)
                }
                continue
            }
            val bitmapFill = fill as? SwfFill.Bitmap ?: continue
            val image = swfBitmap(player, bitmapFill.bitmapId) ?: continue
            val fi = bitmapFill.matrix.inverse() ?: continue
            val iw = image.width; val ih = image.height; val src = image.pixels
            scanFill(d, m, index + 1) { y, x0, x1, sy ->
                // Bitmap coordinates are affine in x: evaluate at the span start, then step per pixel.
                val lx0 = inv.x(x0 + 0.5, sy); val ly0 = inv.y(x0 + 0.5, sy)
                val lx1 = inv.x(x0 + 1.5, sy); val ly1 = inv.y(x0 + 1.5, sy)
                var bxf = fi.x(lx0, ly0); var byf = fi.y(lx0, ly0)
                val dx = fi.x(lx1, ly1) - bxf; val dy = fi.y(lx1, ly1) - byf
                for (x in x0 until x1) {
                    if (!masked(masks, y * width + x)) {
                        val bx = floor(bxf).toInt(); val by = floor(byf).toInt()
                        val p = if (bx in 0 until iw && by in 0 until ih) src[by * iw + bx]
                        else if (bitmapFill.clipped) src[by.coerceIn(0, ih - 1) * iw + bx.coerceIn(0, iw - 1)]
                        else src[Math.floorMod(by, ih) * iw + Math.floorMod(bx, iw)]
                        plot(x, y, p, a)
                    }
                    bxf += dx; byf += dy
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

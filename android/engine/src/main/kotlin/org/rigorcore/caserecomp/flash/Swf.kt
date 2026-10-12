package org.rigorcore.caserecomp.flash

import java.io.ByteArrayOutputStream
import java.util.zip.Inflater

class FlashError(message: String) : RuntimeException(message)

/** 2D affine transform in pixels: x' = a*x + c*y + tx, y' = b*x + d*y + ty. */
data class SwfMatrix(val a: Double = 1.0, val b: Double = 0.0, val c: Double = 0.0, val d: Double = 1.0,
                     val tx: Double = 0.0, val ty: Double = 0.0) {
    operator fun times(o: SwfMatrix) = SwfMatrix(
        a * o.a + c * o.b, b * o.a + d * o.b, a * o.c + c * o.d, b * o.c + d * o.d,
        a * o.tx + c * o.ty + tx, b * o.tx + d * o.ty + ty,
    )
    fun x(px: Double, py: Double) = a * px + c * py + tx
    fun y(px: Double, py: Double) = b * px + d * py + ty
    fun inverse(): SwfMatrix? {
        val det = a * d - b * c
        if (det == 0.0) return null
        return SwfMatrix(d / det, -b / det, -c / det, a / det, (c * ty - d * tx) / det, (b * tx - a * ty) / det)
    }
    companion object { val IDENTITY = SwfMatrix() }
}

data class SwfRect(val xMin: Double, val yMin: Double, val xMax: Double, val yMax: Double) {
    val width get() = xMax - xMin
    val height get() = yMax - yMin
}

/** Colour transform; only multiply/add terms that matter for compositing. */
data class SwfColorTransform(val alphaMult: Double = 1.0, val alphaAdd: Double = 0.0) {
    fun alpha(base: Double) = (base * alphaMult + alphaAdd / 255.0).coerceIn(0.0, 1.0)
}

sealed interface SwfFill {
    data class Solid(val argb: Int) : SwfFill
    /** [matrix] maps bitmap pixels to shape pixels. */
    data class Bitmap(val bitmapId: Int, val matrix: SwfMatrix, val clipped: Boolean) : SwfFill
}

sealed interface SwfCharacter { val id: Int }

/** A straight edge (curves are flattened) with the fill styles on either side (1-based into fills, 0 = none). */
data class SwfEdge(val x0: Double, val y0: Double, val x1: Double, val y1: Double, val fill0: Int, val fill1: Int)

/**
 * A filled shape: every fill style region is bounded by the edges that have that style
 * on exactly one side, so it can be filled even-odd. Line styles are not drawn.
 */
class SwfShape(override val id: Int, val bounds: SwfRect, val fills: List<SwfFill?>, val edges: List<SwfEdge>) : SwfCharacter {
    /** First fill style (convenience for single-fill shapes). */
    val fill: SwfFill? get() = fills.firstOrNull { it != null }

    /** Fill style index (1-based) covering a shape-local point, last-defined region winning; 0 = none. */
    fun fillAt(x: Double, y: Double): Int {
        var found = 0
        for (style in 1..fills.size) {
            var inside = false
            for (e in edges) {
                if ((e.fill0 == style) == (e.fill1 == style)) continue
                if ((e.y0 > y) != (e.y1 > y) && x < e.x0 + (y - e.y0) * (e.x1 - e.x0) / (e.y1 - e.y0)) inside = !inside
            }
            if (inside) found = style
        }
        return found
    }
}

class SwfBitmap(
    override val id: Int,
    val width: Int,
    val height: Int,
    /** Decoded ARGB pixels for lossless bitmaps. */
    val argb: IntArray? = null,
    /** Complete JPEG stream for JPEG bitmaps (decoded by the platform). */
    val jpeg: ByteArray? = null,
    /** Per-pixel alpha (width*height) of DefineBitsJPEG3, if any. */
    val alpha: ByteArray? = null,
) : SwfCharacter

class SwfSprite(override val id: Int, val timeline: SwfTimeline) : SwfCharacter

/** One character of a button state: [states] bits 1 up, 2 over, 4 down, 8 hit area. */
data class SwfButtonRecord(val states: Int, val characterId: Int, val depth: Int, val matrix: SwfMatrix, val colorTransform: SwfColorTransform?)

/**
 * Actions run on a state transition: [conditions] bits 0x01 idle to over (roll over), 0x02 over to idle,
 * 0x04 press, 0x08 release, 0x10 drag out, 0x20 drag over, 0x40 release outside.
 */
class SwfButtonAction(val conditions: Int, val code: ByteArray)

/** DefineButton2: state records and conditional actions. */
class SwfButton(override val id: Int, val records: List<SwfButtonRecord>, val actions: List<SwfButtonAction>) : SwfCharacter

sealed interface SwfTag {
    data class Place(val depth: Int, val characterId: Int?, val move: Boolean, val matrix: SwfMatrix?,
                     val colorTransform: SwfColorTransform?, val name: String?, val clipDepth: Int = 0) : SwfTag
    data class Remove(val depth: Int) : SwfTag
    class Action(val code: ByteArray) : SwfTag
    data class StartSound(val soundId: Int) : SwfTag
}

class SwfFrame(val label: String?, val tags: List<SwfTag>)

class SwfTimeline(val frames: List<SwfFrame>) {
    fun labelFrame(label: String): Int? = frames.indexOfFirst { it.label.equals(label, ignoreCase = true) }.takeIf { it >= 0 }?.plus(1)
}

class SwfMovie(
    val version: Int,
    val bounds: SwfRect,
    val frameRate: Double,
    val background: Int,
    val characters: Map<Int, SwfCharacter>,
    val root: SwfTimeline,
    /** Export names (for attachSound and friends) to character ids. */
    val exports: Map<String, Int>,
    /** DefineSound MP3 data by character id (other codecs are not kept). */
    val sounds: Map<Int, ByteArray> = emptyMap(),
)

private class BitReader(private val data: ByteArray, start: Int) {
    private var bit = start * 8L
    fun ub(n: Int): Int {
        var v = 0
        repeat(n) {
            val byte = data[(bit ushr 3).toInt()].toInt() and 0xFF
            v = (v shl 1) or ((byte shr (7 - (bit and 7).toInt())) and 1)
            bit++
        }
        return v
    }
    fun sb(n: Int): Int { val v = ub(n); return if (n > 0 && v and (1 shl (n - 1)) != 0) v - (1 shl n) else v }
    fun fb(n: Int) = sb(n) / 65536.0
    val bytePos: Int get() = ((bit + 7) ushr 3).toInt()
    fun seek(bytePos: Int) { bit = bytePos * 8L }
}

private class ByteReader(val data: ByteArray, var pos: Int = 0, val end: Int = data.size) {
    fun u8() = (data[pos++].toInt() and 0xFF).also { if (pos > end) throw FlashError("SWF tag truncated") }
    fun u16() = u8() or (u8() shl 8)
    fun u32() = u16() or (u16() shl 16)
    fun bytes(n: Int) = data.copyOfRange(pos, pos + n).also { pos += n; if (pos > end) throw FlashError("SWF tag truncated") }
    fun rest() = bytes(end - pos)
    fun string(): String {
        val start = pos
        while (pos < end && data[pos] != 0.toByte()) pos++
        return String(data, start, pos - start, Charsets.ISO_8859_1).also { if (pos < end) pos++ }
    }
    fun rect(): SwfRect {
        val bits = BitReader(data, pos)
        val n = bits.ub(5)
        // SWF stores xMin, xMax, yMin, yMax.
        val xMin = bits.sb(n) / 20.0; val xMax = bits.sb(n) / 20.0
        val yMin = bits.sb(n) / 20.0; val yMax = bits.sb(n) / 20.0
        pos = bits.bytePos
        return SwfRect(xMin, yMin, xMax, yMax)
    }
    fun matrix(): SwfMatrix {
        val bits = BitReader(data, pos)
        var a = 1.0; var d = 1.0; var b = 0.0; var c = 0.0
        if (bits.ub(1) == 1) { val n = bits.ub(5); a = bits.fb(n); d = bits.fb(n) }
        if (bits.ub(1) == 1) { val n = bits.ub(5); b = bits.fb(n); c = bits.fb(n) }
        val n = bits.ub(5)
        val tx = bits.sb(n) / 20.0; val ty = bits.sb(n) / 20.0
        pos = bits.bytePos
        return SwfMatrix(a, b, c, d, tx, ty)
    }
    fun colorTransform(withAlpha: Boolean): SwfColorTransform {
        val bits = BitReader(data, pos)
        val hasAdd = bits.ub(1) == 1; val hasMult = bits.ub(1) == 1
        val n = bits.ub(4)
        val channels = if (withAlpha) 4 else 3
        var alphaMult = 1.0; var alphaAdd = 0.0
        if (hasMult) repeat(channels) { i -> val v = bits.sb(n); if (i == 3) alphaMult = v / 256.0 }
        if (hasAdd) repeat(channels) { i -> val v = bits.sb(n); if (i == 3) alphaAdd = v.toDouble() }
        pos = bits.bytePos
        return SwfColorTransform(alphaMult, alphaAdd)
    }
}

/** Parser for the subset of SWF (versions 1-8) used by Director-embedded Flash assets. */
object SwfParser {
    fun parse(input: ByteArray): SwfMovie {
        if (input.size < 8) throw FlashError("not a SWF file")
        val signature = String(input, 0, 3, Charsets.ISO_8859_1)
        val version = input[3].toInt() and 0xFF
        val data = when (signature) {
            "FWS" -> input
            "CWS" -> input.copyOfRange(0, 8) + inflate(input, 8)
            else -> throw FlashError("unsupported SWF signature $signature")
        }
        val r = ByteReader(data, 8)
        val bounds = r.rect()
        val frameRate = r.u16() / 256.0
        r.u16()
        val characters = HashMap<Int, SwfCharacter>()
        val exports = HashMap<String, Int>()
        var background = 0xFFFFFFFF.toInt()
        var jpegTables: ByteArray? = null
        val sounds = HashMap<Int, ByteArray>()
        val root = timeline(data, r.pos, data.size) { code, body ->
            when (code) {
                9 -> background = (0xFF shl 24) or ((body[0].toInt() and 0xFF) shl 16) or ((body[1].toInt() and 0xFF) shl 8) or (body[2].toInt() and 0xFF)
                8 -> jpegTables = body
                2, 22, 32, 83 -> shape(code, body).let { characters[it.id] = it }
                84 -> morphStart(body).let { characters[it.id] = it }
                // DefineSound: id, format byte, sample count; MP3 data starts with a 2-byte seek count.
                14 -> if (body.size > 9 && (body[2].toInt() and 0xFF) shr 4 == 2) {
                    sounds[(body[0].toInt() and 0xFF) or ((body[1].toInt() and 0xFF) shl 8)] = body.copyOfRange(9, body.size)
                }
                34 -> button(body).let { characters[it.id] = it }
                6, 21, 35 -> jpeg(code, body, jpegTables).let { characters[it.id] = it }
                20, 36 -> lossless(code, body).let { characters[it.id] = it }
                39 -> {
                    val br = ByteReader(body)
                    val id = br.u16(); br.u16()
                    characters[id] = SwfSprite(id, timeline(body, br.pos, body.size) { _, _ -> })
                }
                56, 57 -> {
                    val br = ByteReader(body)
                    repeat(br.u16()) { val id = br.u16(); exports[br.string()] = id }
                }
            }
        }
        return SwfMovie(version, bounds, frameRate, background, characters, root, exports, sounds)
    }

    private fun inflate(data: ByteArray, offset: Int): ByteArray {
        val inflater = Inflater()
        inflater.setInput(data, offset, data.size - offset)
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(65536)
        while (!inflater.finished()) {
            val n = inflater.inflate(buffer)
            if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) throw FlashError("truncated zlib data")
            out.write(buffer, 0, n)
            if (out.size() > 256 * 1024 * 1024) throw FlashError("SWF too large")
        }
        inflater.end()
        return out.toByteArray()
    }

    /** Reads control tags into frames; definition tags go to [definition]. */
    private fun timeline(data: ByteArray, start: Int, end: Int, definition: (Int, ByteArray) -> Unit): SwfTimeline {
        val frames = mutableListOf<SwfFrame>()
        var label: String? = null
        var tags = mutableListOf<SwfTag>()
        val r = ByteReader(data, start, end)
        while (r.pos + 2 <= end) {
            val header = r.u16()
            val code = header ushr 6
            var length = header and 0x3F
            if (length == 0x3F) length = r.u32()
            if (length < 0 || r.pos + length > end) throw FlashError("SWF tag $code overruns its container")
            val body = data.copyOfRange(r.pos, r.pos + length)
            r.pos += length
            when (code) {
                0 -> break
                1 -> { frames += SwfFrame(label, tags); label = null; tags = mutableListOf() }
                43 -> label = ByteReader(body).string()
                4 -> {
                    val br = ByteReader(body)
                    val id = br.u16(); val depth = br.u16(); val m = br.matrix()
                    val cx = if (br.pos < body.size) br.colorTransform(withAlpha = false) else null
                    tags += SwfTag.Place(depth, id, move = false, matrix = m, colorTransform = cx, name = null)
                }
                26, 70 -> tags += place(code, body)
                5 -> tags += SwfTag.Remove(ByteReader(body).let { it.u16(); it.u16() })
                28 -> tags += SwfTag.Remove(ByteReader(body).u16())
                12 -> tags += SwfTag.Action(body)
                15 -> tags += SwfTag.StartSound(ByteReader(body).u16())
                else -> definition(code, body)
            }
        }
        if (tags.isNotEmpty() || label != null) frames += SwfFrame(label, tags)
        return SwfTimeline(frames)
    }

    private fun place(code: Int, body: ByteArray): SwfTag.Place {
        val r = ByteReader(body)
        val flags = r.u8()
        if (code == 70) r.u8()
        val depth = r.u16()
        val id = if (flags and 0x02 != 0) r.u16() else null
        val m = if (flags and 0x04 != 0) r.matrix() else null
        val cx = if (flags and 0x08 != 0) r.colorTransform(withAlpha = true) else null
        if (flags and 0x10 != 0) r.u16()
        val name = if (flags and 0x20 != 0) r.string() else null
        val clipDepth = if (flags and 0x40 != 0) r.u16() else 0
        return SwfTag.Place(depth, id, move = flags and 0x01 != 0, matrix = m, colorTransform = cx, name = name, clipDepth = clipDepth)
    }

    private fun rgb(r: ByteReader, alpha: Boolean): Int {
        val red = r.u8(); val green = r.u8(); val blue = r.u8()
        val a = if (alpha) r.u8() else 255
        return (a shl 24) or (red shl 16) or (green shl 8) or blue
    }

    private fun shape(code: Int, body: ByteArray): SwfShape {
        val r = ByteReader(body)
        val id = r.u16()
        val bounds = r.rect()
        if (code == 83) { r.rect(); r.u8() } // DefineShape4: edge bounds and stroke flags
        val fills = mutableListOf<SwfFill?>()
        var fillBase = 0
        var fillBits = 0
        var lineBits = 0
        fun styles() {
            fillBase = fills.size
            fills += fillStyles(code, r)
            var lines = r.u8()
            if (lines == 0xFF && code != 2) lines = r.u16()
            repeat(lines) { if (code == 83) lineStyle2(r) else { r.u16(); rgb(r, alpha = code == 32) } }
            val bits = r.u8()
            fillBits = bits shr 4; lineBits = bits and 0x0F
        }
        styles()
        val edges = mutableListOf<SwfEdge>()
        val bits = BitReader(body, r.pos)
        var x = 0.0; var y = 0.0
        var fill0 = 0; var fill1 = 0
        while (true) {
            if (bits.ub(1) == 0) {
                val flags = bits.ub(5)
                if (flags == 0) break
                if (flags and 1 != 0) { val n = bits.ub(5); x = bits.sb(n) / 20.0; y = bits.sb(n) / 20.0 }
                if (flags and 2 != 0) fill0 = bits.ub(fillBits).let { if (it == 0) 0 else it + fillBase }
                if (flags and 4 != 0) fill1 = bits.ub(fillBits).let { if (it == 0) 0 else it + fillBase }
                if (flags and 8 != 0) bits.ub(lineBits)
                if (flags and 16 != 0) {
                    r.pos = bits.bytePos
                    styles()
                    bits.seek(r.pos)
                }
            } else if (bits.ub(1) == 1) {
                val n = bits.ub(4) + 2
                var dx = 0.0; var dy = 0.0
                if (bits.ub(1) == 1) { dx = bits.sb(n) / 20.0; dy = bits.sb(n) / 20.0 }
                else if (bits.ub(1) == 1) dy = bits.sb(n) / 20.0 else dx = bits.sb(n) / 20.0
                if (fill0 != 0 || fill1 != 0) edges += SwfEdge(x, y, x + dx, y + dy, fill0, fill1)
                x += dx; y += dy
            } else {
                val n = bits.ub(4) + 2
                val cx = x + bits.sb(n) / 20.0; val cy = y + bits.sb(n) / 20.0
                val ax = cx + bits.sb(n) / 20.0; val ay = cy + bits.sb(n) / 20.0
                if (fill0 != 0 || fill1 != 0) {
                    // Flatten the quadratic curve.
                    var px = x; var py = y
                    for (i in 1..CURVE_STEPS) {
                        val t = i.toDouble() / CURVE_STEPS; val u = 1 - t
                        val qx = u * u * x + 2 * u * t * cx + t * t * ax; val qy = u * u * y + 2 * u * t * cy + t * t * ay
                        edges += SwfEdge(px, py, qx, qy, fill0, fill1)
                        px = qx; py = qy
                    }
                }
                x = ax; y = ay
            }
        }
        return SwfShape(id, bounds, fills, edges)
    }

    private const val CURVE_STEPS = 8

    /** LINESTYLE2 (DefineShape4): width, cap/join flags, miter limit, then a fill style or a colour. */
    private fun lineStyle2(r: ByteReader) {
        r.u16()
        val f1 = r.u8(); r.u8()
        if ((f1 shr 4) and 3 == 2) r.u16()
        if (f1 and 0x08 != 0) fillStyles(83, r, single = true) else rgb(r, alpha = true)
    }

    /**
     * DefineMorphShape2 drawn as its start shape (ratio 0): start colours and start edges.
     * The end shape and the PlaceObject ratio are not interpolated.
     */
    private fun morphStart(body: ByteArray): SwfShape {
        val r = ByteReader(body)
        val id = r.u16()
        val bounds = r.rect(); r.rect(); r.rect(); r.rect(); r.u8(); r.u32()
        var count = r.u8(); if (count == 0xFF) count = r.u16()
        val fills = mutableListOf<SwfFill?>()
        repeat(count) {
            fills += when (val type = r.u8()) {
                0x00 -> SwfFill.Solid(rgb(r, alpha = true)).also { rgb(r, alpha = true) }
                0x10, 0x12, 0x13 -> {
                    r.matrix(); r.matrix()
                    val n = r.u8() and 0x0F
                    var first: Int? = null
                    repeat(n) { r.u8(); val c = rgb(r, alpha = true); r.u8(); rgb(r, alpha = true); if (first == null) first = c }
                    if (type == 0x13) { r.u16(); r.u16() }
                    first?.let { SwfFill.Solid(it) }
                }
                in 0x40..0x43 -> {
                    val bitmapId = r.u16(); val m = r.matrix(); r.matrix()
                    if (bitmapId == 0xFFFF) null else SwfFill.Bitmap(bitmapId, SwfMatrix(m.a / 20, m.b / 20, m.c / 20, m.d / 20, m.tx, m.ty), clipped = type == 0x41 || type == 0x43)
                }
                else -> throw FlashError("unsupported morph fill style $type")
            }
        }
        var lines = r.u8(); if (lines == 0xFF) lines = r.u16()
        repeat(lines) {
            r.u16(); r.u16()
            val f1 = r.u8(); r.u8()
            if ((f1 shr 4) and 3 == 2) r.u16()
            if (f1 and 0x08 != 0) throw FlashError("unsupported morph line fill") else { rgb(r, alpha = true); rgb(r, alpha = true) }
        }
        val bits = r.u8()
        return SwfShape(id, bounds, fills, edges(body, r.pos, bits shr 4, bits and 0x0F, fills.size))
    }

    /** Shape records without style changes (morph start edges), from byte [start]. */
    private fun edges(body: ByteArray, start: Int, fillBits: Int, lineBits: Int, fillCount: Int): List<SwfEdge> {
        val edges = mutableListOf<SwfEdge>()
        val bits = BitReader(body, start)
        var x = 0.0; var y = 0.0
        var fill0 = 0; var fill1 = 0
        while (true) {
            if (bits.ub(1) == 0) {
                val flags = bits.ub(5)
                if (flags == 0) break
                if (flags and 1 != 0) { val n = bits.ub(5); x = bits.sb(n) / 20.0; y = bits.sb(n) / 20.0 }
                if (flags and 2 != 0) fill0 = bits.ub(fillBits).let { if (it > fillCount) 0 else it }
                if (flags and 4 != 0) fill1 = bits.ub(fillBits).let { if (it > fillCount) 0 else it }
                if (flags and 8 != 0) bits.ub(lineBits)
                if (flags and 16 != 0) throw FlashError("unexpected new styles in morph shape")
            } else if (bits.ub(1) == 1) {
                val n = bits.ub(4) + 2
                var dx = 0.0; var dy = 0.0
                if (bits.ub(1) == 1) { dx = bits.sb(n) / 20.0; dy = bits.sb(n) / 20.0 }
                else if (bits.ub(1) == 1) dy = bits.sb(n) / 20.0 else dx = bits.sb(n) / 20.0
                if (fill0 != 0 || fill1 != 0) edges += SwfEdge(x, y, x + dx, y + dy, fill0, fill1)
                x += dx; y += dy
            } else {
                val n = bits.ub(4) + 2
                val cx = x + bits.sb(n) / 20.0; val cy = y + bits.sb(n) / 20.0
                val ax = cx + bits.sb(n) / 20.0; val ay = cy + bits.sb(n) / 20.0
                if (fill0 != 0 || fill1 != 0) {
                    var px = x; var py = y
                    for (i in 1..CURVE_STEPS) {
                        val t = i.toDouble() / CURVE_STEPS; val u = 1 - t
                        val qx = u * u * x + 2 * u * t * cx + t * t * ax; val qy = u * u * y + 2 * u * t * cy + t * t * ay
                        edges += SwfEdge(px, py, qx, qy, fill0, fill1)
                        px = qx; py = qy
                    }
                }
                x = ax; y = ay
            }
        }
        return edges
    }

    /** DefineButton2: records until a zero byte, then condition actions linked by their sizes. */
    private fun button(body: ByteArray): SwfButton {
        val r = ByteReader(body)
        val id = r.u16()
        r.u8()
        val actionOffset = r.u16()
        val records = mutableListOf<SwfButtonRecord>()
        while (r.pos < body.size) {
            val flags = r.u8()
            if (flags == 0) break
            val character = r.u16(); val depth = r.u16()
            val m = r.matrix()
            val cx = r.colorTransform(withAlpha = true)
            if (flags and 0x10 != 0) skipFilters(r)
            if (flags and 0x20 != 0) r.u8()
            records += SwfButtonRecord(flags and 0x0F, character, depth, m, cx)
        }
        val actions = mutableListOf<SwfButtonAction>()
        if (actionOffset != 0) {
            // ActionOffset counts from its own field (byte 3).
            var p = 3 + actionOffset
            while (p + 4 <= body.size) {
                val size = (body[p].toInt() and 0xFF) or ((body[p + 1].toInt() and 0xFF) shl 8)
                val conditions = (body[p + 2].toInt() and 0xFF) or ((body[p + 3].toInt() and 0xFF) shl 8)
                val end = if (size == 0) body.size else (p + size).coerceAtMost(body.size)
                actions += SwfButtonAction(conditions, body.copyOfRange(p + 4, end))
                if (size == 0) break
                p += size
            }
        }
        return SwfButton(id, records, actions)
    }

    /** Skips a FILTERLIST (Flash 8 filters are not rendered). */
    private fun skipFilters(r: ByteReader) {
        repeat(r.u8()) {
            when (r.u8()) {
                0 -> r.bytes(23)
                1 -> r.bytes(9)
                2 -> r.bytes(15)
                3 -> r.bytes(27)
                4, 7 -> { val n = r.u8(); r.bytes(n * 5 + 19) }
                5 -> { val w = r.u8(); val h = r.u8(); r.bytes(8 + w * h * 4 + 5) }
                6 -> r.bytes(80)
                else -> throw FlashError("unknown button filter")
            }
        }
    }

    private fun fillStyles(code: Int, r: ByteReader, single: Boolean = false): List<SwfFill?> {
        var count = if (single) 1 else r.u8()
        if (!single && count == 0xFF && code != 2) count = r.u16()
        val fills = mutableListOf<SwfFill?>()
        repeat(count) {
            fills += when (val type = r.u8()) {
                0x00 -> SwfFill.Solid(rgb(r, alpha = code == 32 || code == 83))
                0x10, 0x12, 0x13 -> {
                    r.matrix()
                    val n = r.u8() and 0x0F
                    var first: Int? = null
                    repeat(n) { r.u8(); val c = rgb(r, alpha = code == 32 || code == 83); if (first == null) first = c }
                    if (type == 0x13) r.u16()
                    first?.let { SwfFill.Solid(it) }
                }
                in 0x40..0x43 -> {
                    val bitmapId = r.u16()
                    val m = r.matrix()
                    // Bitmap fill matrices are in twips per bitmap pixel.
                    val px = SwfMatrix(m.a / 20, m.b / 20, m.c / 20, m.d / 20, m.tx, m.ty)
                    if (bitmapId == 0xFFFF) null else SwfFill.Bitmap(bitmapId, px, clipped = type == 0x41 || type == 0x43)
                }
                else -> throw FlashError("unsupported fill style $type")
            }
        }
        return fills
    }

    private fun jpeg(code: Int, body: ByteArray, tables: ByteArray?): SwfBitmap {
        val r = ByteReader(body)
        val id = r.u16()
        val alphaOffset = if (code == 35) r.u32() else -1
        var image = if (code == 35) r.bytes(alphaOffset) else r.rest()
        image = stripErroneousHeader(image)
        // An empty JPEGTables tag (allowed by the SWF format) means each image carries its own tables.
        if (code == 6 && tables != null && tables.size >= 4) {
            val t = stripErroneousHeader(tables)
            image = t.copyOfRange(0, t.size - 2) + image.copyOfRange(2, image.size)
        }
        image = joinTablesAndImage(image)
        val (w, h) = jpegSize(image)
        val alpha = if (code == 35 && r.pos < body.size) inflate(body, r.pos).takeIf { it.size >= w * h } else null
        return SwfBitmap(id, w, h, jpeg = image, alpha = alpha)
    }

    /** Older encoders prefix JPEG data with an empty EOI/SOI pair. */
    private fun stripErroneousHeader(data: ByteArray): ByteArray =
        if (data.size >= 4 && (data[0].toInt() and 0xFF) == 0xFF && (data[1].toInt() and 0xFF) == 0xD9 &&
            (data[2].toInt() and 0xFF) == 0xFF && (data[3].toInt() and 0xFF) == 0xD8) data.copyOfRange(4, data.size) else data

    /**
     * SWF JPEG data is often "tables EOI SOI image": an end-of-image marker before the frame.
     * Strict decoders stop there, so drop each EOI/SOI pair met before the start of scan.
     */
    internal fun joinTablesAndImage(data: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream(data.size)
        var p = 0
        if (data.size >= 2) { out.write(data, 0, 2); p = 2 }
        while (p + 3 < data.size) {
            if ((data[p].toInt() and 0xFF) != 0xFF) break
            val marker = data[p + 1].toInt() and 0xFF
            if (marker == 0xD9 && (data[p + 2].toInt() and 0xFF) == 0xFF && (data[p + 3].toInt() and 0xFF) == 0xD8) { p += 4; continue }
            if (marker == 0xDA || marker == 0xD8 || marker == 0x01 || marker in 0xD0..0xD7) break
            val end = p + 2 + (((data[p + 2].toInt() and 0xFF) shl 8) or (data[p + 3].toInt() and 0xFF))
            if (end > data.size) break
            out.write(data, p, end - p)
            p = end
        }
        out.write(data, p, data.size - p)
        return out.toByteArray()
    }

    private fun jpegSize(data: ByteArray): Pair<Int, Int> {
        var p = 2
        while (p + 9 < data.size) {
            if ((data[p].toInt() and 0xFF) != 0xFF) { p++; continue }
            val marker = data[p + 1].toInt() and 0xFF
            if (marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) {
                val h = ((data[p + 5].toInt() and 0xFF) shl 8) or (data[p + 6].toInt() and 0xFF)
                val w = ((data[p + 7].toInt() and 0xFF) shl 8) or (data[p + 8].toInt() and 0xFF)
                return w to h
            }
            // Standalone markers; SWF JPEG2/3 data often embeds an EOI/SOI pair after the tables.
            if (marker == 0xD8 || marker == 0xD9 || marker == 0x01 || marker in 0xD0..0xD7) { p += 2; continue }
            p += 2 + (((data[p + 2].toInt() and 0xFF) shl 8) or (data[p + 3].toInt() and 0xFF))
        }
        return 0 to 0
    }

    private fun lossless(code: Int, body: ByteArray): SwfBitmap {
        val r = ByteReader(body)
        val id = r.u16()
        val format = r.u8()
        val w = r.u16(); val h = r.u16()
        val alpha = code == 36
        val pixels = IntArray(w * h)
        when (format) {
            3 -> {
                val colors = r.u8() + 1
                val raw = inflate(body, r.pos)
                val entry = if (alpha) 4 else 3
                val palette = IntArray(colors) { i ->
                    val o = i * entry
                    val a = if (alpha) raw[o + 3].toInt() and 0xFF else 255
                    (a shl 24) or ((raw[o].toInt() and 0xFF) shl 16) or ((raw[o + 1].toInt() and 0xFF) shl 8) or (raw[o + 2].toInt() and 0xFF)
                }
                val pitch = (w + 3) and 3.inv()
                val base = colors * entry
                for (y in 0 until h) for (x in 0 until w) {
                    val index = raw[base + y * pitch + x].toInt() and 0xFF
                    pixels[y * w + x] = if (index < colors) palette[index] else 0
                }
            }
            5 -> {
                val raw = inflate(body, r.pos)
                for (i in 0 until w * h) {
                    val o = i * 4
                    val a = if (alpha) raw[o].toInt() and 0xFF else 255
                    var red = raw[o + 1].toInt() and 0xFF; var green = raw[o + 2].toInt() and 0xFF; var blue = raw[o + 3].toInt() and 0xFF
                    if (alpha && a in 1..254) { // stored premultiplied
                        red = minOf(255, red * 255 / a); green = minOf(255, green * 255 / a); blue = minOf(255, blue * 255 / a)
                    }
                    pixels[i] = (a shl 24) or (red shl 16) or (green shl 8) or blue
                }
            }
            4 -> {
                val raw = inflate(body, r.pos)
                val pitch = (w * 2 + 3) and 3.inv()
                for (y in 0 until h) for (x in 0 until w) {
                    val o = y * pitch + x * 2
                    val v = ((raw[o].toInt() and 0xFF) shl 8) or (raw[o + 1].toInt() and 0xFF)
                    val red = (v shr 10) and 0x1F; val green = (v shr 5) and 0x1F; val blue = v and 0x1F
                    pixels[y * w + x] = (0xFF shl 24) or ((red * 255 / 31) shl 16) or ((green * 255 / 31) shl 8) or (blue * 255 / 31)
                }
            }
            else -> throw FlashError("unsupported lossless bitmap format $format")
        }
        return SwfBitmap(id, w, h, argb = pixels)
    }
}

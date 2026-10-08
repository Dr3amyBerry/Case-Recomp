package org.rigorcore.caserecomp.flash

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater

/** Writes small synthetic SWF files for tests. */
internal class SwfBits {
    private val out = ByteArrayOutputStream()
    private var acc = 0
    private var n = 0
    fun ub(bits: Int, value: Int) = apply {
        for (i in bits - 1 downTo 0) {
            acc = (acc shl 1) or ((value shr i) and 1); n++
            if (n == 8) { out.write(acc); acc = 0; n = 0 }
        }
    }
    fun bytes(): ByteArray { if (n > 0) { out.write(acc shl (8 - n)); acc = 0; n = 0 }; return out.toByteArray() }
}

internal object SwfTestBuilder {
    fun rect(xMin: Int, xMax: Int, yMin: Int, yMax: Int): ByteArray =
        SwfBits().ub(5, 15).ub(15, xMin * 20).ub(15, xMax * 20).ub(15, yMin * 20).ub(15, yMax * 20).bytes()

    fun translate(x: Int, y: Int): ByteArray = SwfBits().ub(1, 0).ub(1, 0).ub(5, 15).ub(15, x * 20).ub(15, y * 20).bytes()

    fun u16(v: Int) = byteArrayOf((v and 0xFF).toByte(), (v shr 8).toByte())

    fun tag(code: Int, body: ByteArray): ByteArray =
        if (body.size < 0x3F) u16((code shl 6) or body.size) + body
        else u16((code shl 6) or 0x3F) + byteArrayOf((body.size and 0xFF).toByte(), (body.size shr 8).toByte(), (body.size shr 16).toByte(), 0) + body

    fun cstr(s: String) = s.toByteArray(Charsets.ISO_8859_1) + 0

    fun movie(width: Int, height: Int, frames: Int, vararg tags: ByteArray, compress: Boolean = false): ByteArray {
        val body = rect(0, width, 0, height) + u16(30 shl 8) + u16(frames) + tags.fold(ByteArray(0)) { a, b -> a + b } + tag(0, ByteArray(0))
        val length = 8 + body.size
        val header = byteArrayOf(if (compress) 'C'.code.toByte() else 'F'.code.toByte(), 'W'.code.toByte(), 'S'.code.toByte(), 6,
            (length and 0xFF).toByte(), (length shr 8).toByte(), (length shr 16).toByte(), 0)
        if (!compress) return header + body
        val deflater = Deflater(); deflater.setInput(body); deflater.finish()
        val buf = ByteArray(body.size + 64); val n = deflater.deflate(buf)
        return header + buf.copyOf(n)
    }

    /** DefineShape: a w x h rectangle path filled (fill style 0 side) with one solid RGB colour. */
    fun solidShape(id: Int, w: Int, h: Int, rgb: Int): ByteArray =
        tag(2, u16(id) + rect(0, w, 0, h) + byteArrayOf(1, 0, (rgb shr 16).toByte(), (rgb shr 8).toByte(), rgb.toByte(), 0, 0x10) +
            rectanglePath(w, h, fillBits = 1))

    /** Shape records: move to (0,0) selecting fill style 1, four straight edges, end. */
    fun rectanglePath(w: Int, h: Int, fillBits: Int): ByteArray {
        val b = SwfBits()
        b.ub(1, 0).ub(5, 0b00011).ub(5, 15).ub(15, 0).ub(15, 0).ub(fillBits, 1)
        fun line(dx: Int, dy: Int) {
            b.ub(1, 1).ub(1, 1).ub(4, 15 - 2).ub(1, 0)
            if (dx != 0) b.ub(1, 0).ub(15, dx * 20) else b.ub(1, 1).ub(15, dy * 20)
        }
        line(w, 0); line(0, h); line(-w, 0); line(0, -h)
        b.ub(1, 0).ub(5, 0)
        return b.bytes()
    }

    fun place(depth: Int, id: Int?, matrix: ByteArray?, name: String? = null, move: Boolean = false): ByteArray {
        var flags = 0
        if (move) flags = flags or 1
        if (id != null) flags = flags or 2
        if (matrix != null) flags = flags or 4
        if (name != null) flags = flags or 0x20
        return tag(26, byteArrayOf(flags.toByte()) + u16(depth) + (id?.let { u16(it) } ?: ByteArray(0)) +
            (matrix ?: ByteArray(0)) + (name?.let { cstr(it) } ?: ByteArray(0)))
    }

    fun showFrame() = tag(1, ByteArray(0))
    fun label(name: String) = tag(43, cstr(name))
    fun action(vararg code: ByteArray) = tag(12, code.fold(ByteArray(0)) { a, b -> a + b } + 0)
    fun sprite(id: Int, frames: Int, vararg tags: ByteArray) =
        tag(39, u16(id) + u16(frames) + tags.fold(ByteArray(0)) { a, b -> a + b } + tag(0, ByteArray(0)))

    // AVM1
    fun op(code: Int) = byteArrayOf(code.toByte())
    fun op(code: Int, data: ByteArray) = byteArrayOf(code.toByte()) + u16(data.size) + data
    fun push(vararg values: Any): ByteArray = op(0x96, values.fold(ByteArray(0)) { acc, v ->
        acc + when (v) {
            is String -> byteArrayOf(0) + cstr(v)
            is Int -> byteArrayOf(7, (v and 0xFF).toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())
            is Boolean -> byteArrayOf(5, if (v) 1 else 0)
            else -> error("push type")
        }
    })
    fun function(vararg body: ByteArray): ByteArray {
        val code = body.fold(ByteArray(0)) { a, b -> a + b }
        return op(0x9B, cstr("") + u16(0) + u16(code.size)) + code
    }
}

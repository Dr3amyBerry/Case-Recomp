package org.rigorcore.caserecomp.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import org.rigorcore.caserecomp.director.DirectorRuntime
import org.rigorcore.caserecomp.director.LingoImage
import org.rigorcore.caserecomp.lingo.LingoValue
import org.rigorcore.caserecomp.lingo.LingoValue.LInstance
import org.rigorcore.caserecomp.lingo.LingoValue.LPropList
import org.rigorcore.caserecomp.lingo.LingoValue.LString
import org.rigorcore.caserecomp.lingo.LingoValue.LSymbol
import kotlin.math.atan

/**
 * Ravenhearst only: the main menu's "Strategy Guide" board (members strategyGuideA, dark letters,
 * and strategyGuideB, light letters, which the original script alternates) reads "Apóyame"
 * and its original link script opens the project's support page instead of the guide.
 */
internal object RavenhearstSupportSign {
    const val TEXT = "Apóyame"

    fun install(runtime: DirectorRuntime, url: String) {
        val dark = runtime.member(LString("strategyGuideA"), null)
        val light = runtime.member(LString("strategyGuideB"), null)
        val a = dark.image ?: return
        val b = light.image ?: return
        if (a.width != b.width || a.height != b.height) return
        val letters = SignLetters.find(a.pixels, b.pixels, a.width, a.height) ?: return
        dark.setProp("image", draw(a, letters, letters.darkColor))
        light.setProp("image", draw(b, letters, letters.lightColor))
        // The board's script asks the upsell manager for #strategyLink and passes it to gotoNetPage.
        val upsell = runtime.vm.global("gUpsellManager") as? LInstance ?: return
        val links = runtime.vm.getObjectProp(upsell, "m_linkDataArray") as? LPropList ?: return
        val key = links.entries.indexOfFirst { (it.first as? LSymbol)?.name.equals("strategyLink", true) }
        if (key >= 0) links.entries[key] = links.entries[key].first to LString(url)
        else links.entries += LSymbol("strategyLink") to LString(url)
    }

    private fun draw(source: LingoImage, letters: SignLetters, color: Int): LingoImage {
        val pixels = SignLetters.erase(source.pixels, source.width, source.height, letters.mask)
        val bitmap = Bitmap.createBitmap(pixels, source.width, source.height, Bitmap.Config.ARGB_8888).copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            textSize = letters.height * 1.05f
        }
        // Same width as the original lettering at most, then the board's tilt around its centre.
        val width = paint.measureText(TEXT)
        if (width > letters.width * 0.92f) paint.textSize *= letters.width * 0.92f / width
        canvas.rotate(Math.toDegrees(atan(letters.slope)).toFloat(), letters.centerX, letters.centerY)
        val baseline = letters.centerY - (paint.ascent() + paint.descent()) / 2
        // A faint highlight under the letters, like the carved originals.
        paint.color = 0x55000000
        canvas.drawText(TEXT, letters.centerX + 1, baseline + 1, paint)
        paint.color = color
        canvas.drawText(TEXT, letters.centerX, baseline, paint)
        val out = IntArray(source.width * source.height)
        bitmap.getPixels(out, 0, source.width, 0, 0, source.width, source.height)
        bitmap.recycle()
        return LingoImage(source.width, source.height, 32, out).also { it.useAlpha = true }
    }
}

/** The board's painted letters: where the light and dark versions differ, and their slant. */
internal class SignLetters(
    val mask: BooleanArray,
    val centerX: Float, val centerY: Float,
    val width: Float, val height: Float,
    val slope: Double,
    val darkColor: Int, val lightColor: Int,
) {
    companion object {
        private fun luma(p: Int) = (((p shr 16) and 255) * 30 + ((p shr 8) and 255) * 59 + (p and 255) * 11) / 100

        /** Letter pixels: opaque, and much lighter in [light] than in [dark]. */
        fun find(dark: IntArray, light: IntArray, w: Int, h: Int): SignLetters? {
            val mask = BooleanArray(w * h) { i -> (light[i] ushr 24) > 200 && luma(light[i]) - luma(dark[i]) > 40 }
            val points = mask.indices.filter { mask[it] }
            if (points.size < 50) return null
            val xs = points.map { it % w }; val ys = points.map { it / w }
            // Slant: least squares through each 10-pixel column band's mean height.
            val bands = points.groupBy { (it % w) / 10 }.map { (band, ps) -> (band * 10 + 5).toDouble() to ps.map { it / w }.average() }
            val mx = bands.map { it.first }.average(); val my = bands.map { it.second }.average()
            val slope = bands.sumOf { (x, y) -> (x - mx) * (y - my) } / bands.sumOf { (x, _) -> (x - mx) * (x - mx) }.coerceAtLeast(1.0)
            fun average(pixels: IntArray): Int {
                var r = 0L; var g = 0L; var b = 0L
                for (i in points) { r += (pixels[i] shr 16) and 255; g += (pixels[i] shr 8) and 255; b += pixels[i] and 255 }
                val n = points.size
                return (0xFF shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
            }
            // Height across the slant: the letters' spread once the tilt is removed.
            val upright = points.map { (it / w) - slope * ((it % w) - mx) }
            return SignLetters(mask, mx.toFloat(), my.toFloat(), (xs.max() - xs.min()).toFloat(),
                (upright.max() - upright.min()).toFloat(), if (slope.isNaN()) 0.0 else slope, average(dark), average(light))
        }

        /** Removes the letters: masked pixels (grown by two, for their anti-aliased edges) take the average of their filled neighbours, outside in. */
        fun erase(pixels: IntArray, w: Int, h: Int, mask: BooleanArray): IntArray {
            val out = pixels.copyOf()
            val hole = BooleanArray(w * h)
            for (i in mask.indices) if (mask[i]) {
                val x = i % w; val y = i / w
                for (dy in -2..2) for (dx in -2..2) {
                    val nx = x + dx; val ny = y + dy
                    if (nx in 0 until w && ny in 0 until h && (pixels[ny * w + nx] ushr 24) > 200) hole[ny * w + nx] = true
                }
            }
            var remaining = hole.count { it }
            var guard = 0
            while (remaining > 0 && guard++ < 64) {
                val filled = ArrayList<Pair<Int, Int>>()
                for (i in hole.indices) if (hole[i]) {
                    val x = i % w; val y = i / w
                    var r = 0; var g = 0; var b = 0; var n = 0
                    for ((nx, ny) in listOf(x - 1 to y, x + 1 to y, x to y - 1, x to y + 1)) {
                        if (nx !in 0 until w || ny !in 0 until h) continue
                        val j = ny * w + nx
                        if (hole[j] || (out[j] ushr 24) < 200) continue
                        r += (out[j] shr 16) and 255; g += (out[j] shr 8) and 255; b += out[j] and 255; n++
                    }
                    if (n > 0) filled += i to ((out[i] and 0xFF000000.toInt()) or ((r / n) shl 16) or ((g / n) shl 8) or (b / n))
                }
                if (filled.isEmpty()) break
                for ((i, p) in filled) { out[i] = p; hole[i] = false }
                remaining -= filled.size
            }
            return out
        }
    }
}

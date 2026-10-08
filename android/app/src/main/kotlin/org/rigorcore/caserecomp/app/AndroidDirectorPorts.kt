package org.rigorcore.caserecomp.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.media.MediaPlayer
import org.rigorcore.caserecomp.director.CastMember
import org.rigorcore.caserecomp.director.DirectorContent
import org.rigorcore.caserecomp.director.DirectorStore
import org.rigorcore.caserecomp.director.ImageDecoder
import org.rigorcore.caserecomp.director.LColor
import org.rigorcore.caserecomp.director.LingoImage
import org.rigorcore.caserecomp.director.SoundOutput
import org.rigorcore.caserecomp.director.TextLayout
import org.rigorcore.caserecomp.director.TextLine
import org.rigorcore.caserecomp.director.TextMetrics
import org.rigorcore.caserecomp.director.TextRasterizer
import org.rigorcore.caserecomp.lingo.toInt
import java.io.File

/** Pixel-size capped Android decoding for private PNG/JPEG member media. */
internal object AndroidDirectorImageDecoder : ImageDecoder {
    override fun decode(bytes: ByteArray): LingoImage? {
        if (bytes.isEmpty() || bytes.size > MAX_IMAGE_BYTES) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth !in 1..MAX_SIDE || bounds.outHeight !in 1..MAX_SIDE ||
            bounds.outWidth.toLong() * bounds.outHeight > MAX_PIXELS) return null
        val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        return try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            LingoImage(bitmap.width, bitmap.height, 32, pixels).also { it.useAlpha = bitmap.hasAlpha() }
        } finally { bitmap.recycle() }
    }

    private const val MAX_IMAGE_BYTES = 64 * 1024 * 1024
    private const val MAX_SIDE = 4096
    private const val MAX_PIXELS = 16L * 1024 * 1024
}

/**
 * Text members drawn with platform fonts. The title's embedded font members are not
 * usable on Android, so each authored face maps to the closest system family.
 */
internal class AndroidDirectorText : TextRasterizer, TextMetrics {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun apply(member: CastMember) {
        paint.textSize = (member.fontSize * shrink * size(member.font)).coerceAtLeast(1f)
        // Fonts embedded in the title ("Name *") draw in their own weight and slant: Director
        // does not embolden them, so only the face name says bold or italic.
        val name = member.font.lowercase()
        val embedded = member.font.trimEnd().endsWith("*")
        val bold = if (embedded) "bold" in name else member.bold
        val italic = if (embedded) "italic" in name else member.italic
        paint.textSkewX = if ("readout" in name) -0.18f else 0f
        val style = when {
            bold && italic -> Typeface.BOLD_ITALIC
            bold -> Typeface.BOLD
            italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        paint.typeface = Typeface.create(family(member.font), style)
        paint.isUnderlineText = "underline" in member.fontStyle
        paint.textScaleX = condense * width(member.font)
    }

    /**
     * Fit adjustments while laying out text in a substitute face larger than the original:
     * a horizontal squeeze, then a smaller size. Both are 1 outside [render].
     */
    private var condense = 1f
    private var shrink = 1f

    override fun width(member: CastMember, text: String): Int {
        apply(member)
        return paint.measureText(text.replace('\t', ' ')).toInt()
    }

    override fun lineHeight(member: CastMember): Int =
        (member.fontSize * shrink * size(member.font) * leading(member.font)).toInt().coerceAtLeast(1)

    override fun render(member: CastMember, width: Int, height: Int): LingoImage? = render(member, width, height, 1)

    /** Lays out in stage pixels (same wrapping at every scale) and draws the glyphs [scale] times larger. */
    override fun render(member: CastMember, width: Int, height: Int, scale: Int): LingoImage? {
        if (width <= 0 || height <= 0 || scale !in 1..4) return null
        try {
            val lines = fit(member, width, height)
            val step = TextLayout.lineHeight(member, this)
            apply(member)
            val metrics = paint.fontMetrics
            val fixed = member.prop("fixedlinespace")?.toInt()?.takeIf { it > 0 } != null
            val rows = lines.dropLastWhile { it.text.isBlank() }.size.coerceAtLeast(1)
            // Lines that overflow the box close up (to 3/4 of their pitch), as the original's do;
            // beyond that the box grows to its content, so the image may be taller than the box.
            val pitch = if (!fixed && rows * step > height) maxOf(height.toFloat() / rows, step * MIN_PITCH) else step.toFloat()
            val content = if (fixed) rows * step else ((rows - 1) * pitch + metrics.descent - metrics.ascent).toInt() + 1
            val drawn = maxOf(height, minOf(content, height * 4))
            if (width.toLong() * drawn * scale * scale > 16L * 1024 * 1024) return null
            val bitmap = Bitmap.createBitmap(width * scale, drawn * scale, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(bitmap)
                canvas.scale(scale.toFloat(), scale.toFloat())
                paint.color = member.textColor
                paint.style = Paint.Style.FILL
                // A fixed line space is a slot per line whose glyphs sit on its bottom (less the descent).
                // The first baseline sits at about 0.8 em, as the originals' faces do; the platform
                // faces' ascents run taller and would set every line a few pixels low.
                var baseline = if (fixed) step - metrics.descent else minOf(-metrics.ascent, paint.textSize * FIRST_BASELINE)
                for ((index, line) in lines.withIndex()) {
                    if (baseline > drawn + step) break
                    for ((dx, run) in line.segments) canvas.drawText(run, (line.x + dx).toFloat(), baseline, paint)
                    // A leading empty line is short in the original (~0.6 of a line), unless lines are fixed.
                    baseline += if (index == 0 && !fixed && line.text.isBlank()) pitch * LEADING_BLANK else pitch
                }
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                return LingoImage(bitmap.width, bitmap.height, 32, pixels).also { it.useAlpha = true }
            } finally {
                bitmap.recycle()
            }
        } finally {
            condense = 1f; shrink = 1f
        }
    }

    /**
     * Lays the text out for a box sized for the original face. A substitute that needs more
     * lines than the box holds is first squeezed horizontally (up to 15%) when that gives fewer
     * lines. Extra lines are then absorbed by closing up the line pitch (see [render]) and, past
     * that, by the box growing; the glyphs shrink only when the text is far too long for its box.
     */
    private fun fit(member: CastMember, width: Int, height: Int): List<TextLine> {
        fun rows(lines: List<TextLine>) = lines.dropLastWhile { it.text.isBlank() }.size.coerceAtLeast(1)
        fun tooWide(lines: List<TextLine>) = lines.any { line -> line.x + line.segments.last().let { (dx, run) -> dx + width(member, run) } > width }
        /** Overflow once the pitch has closed up as far as it may. */
        fun overflow(lines: List<TextLine>) = rows(lines) * TextLayout.lineHeight(member, this) * MIN_PITCH - height
        var best: Pair<Float, List<TextLine>>? = null
        for (squeeze in CONDENSE) {
            condense = squeeze
            val lines = TextLayout.lines(member, width, this)
            if (tooWide(lines)) continue
            val step = TextLayout.lineHeight(member, this)
            // Fits at its natural pitch: keep the authored size and width.
            if (rows(lines) * step - height <= step / 4) return lines
            if (best == null || rows(lines) < rows(best.second)) best = squeeze to lines
        }
        condense = best?.first ?: CONDENSE.last()
        val lines = best?.second ?: TextLayout.lines(member, width, this)
        if (!tooWide(lines) && overflow(lines) <= TextLayout.lineHeight(member, this) * 3 / 2) return lines
        // Far too long for the box (or a word wider than it): shrink as a last resort.
        condense = CONDENSE.last()
        for (size in SHRINK) {
            shrink = size
            val smaller = TextLayout.lines(member, width, this)
            if (!tooWide(smaller) && overflow(smaller) <= TextLayout.lineHeight(member, this) / 4) return smaller
        }
        return TextLayout.lines(member, width, this)
    }

    private companion object {
        /** How far overflowing lines may close up: the original sets "Elementos necesarios…" at ~0.77. */
        const val MIN_PITCH = 0.75f
        /** First baseline below the box top, per em: Palatino 14 sits at ~10.5 px in the original. */
        const val FIRST_BASELINE = 0.78f
        /** Height of a text's leading empty line per line pitch (measured on the original's panels). */
        const val LEADING_BLANK = 0.6f
        val CONDENSE = listOf(1f, 0.95f, 0.9f, 0.85f)
        val SHRINK = listOf(0.9f, 0.8f, 0.7f)

        /**
         * Advance-width ratio of the authored face to its substitute, measured against the
         * original's rendering (Palatino 14 sets "Taller Mecánico" in 99 px; typewriter faces run narrow).
         */
        fun width(font: String): Float {
            val name = font.lowercase()
            return when {
                "typewriter" in name -> 0.9f
                "palatino" in name -> 0.95f
                "times" in name -> 0.86f
                // Tekton's letters run wider than the platform sans at the same glyph size.
                // The italic panel captions must stay inside a glass narrower than their boxes.
                "tekto" in name && "italic" in name -> 0.95f
                "tekto" in name -> 1.15f
                // A narrow seven-segment readout ("19:58" is ~60 px at 36 pt).
                "readout" in name -> 0.9f
                else -> 1f
            }
        }

        /** Glyph size of the authored face relative to its substitute at the same point size. */
        fun size(font: String): Float {
            val name = font.lowercase()
            return when {
                // The italic instance sets smaller than the upright one at the same point size.
                "tekto" in name && "italic" in name -> 0.68f
                "tekto" in name -> 0.8f
                "readout" in name -> 0.95f
                else -> 1f
            }
        }

        /** Line pitch per glyph size: the original lays Palatino 14 at ~15 px and Tekto 15 at ~13.5 px. */
        fun leading(font: String): Float {
            val name = font.lowercase()
            return when {
                "palatino" in name || "times" in name -> 1.08f
                "tekto" in name -> 1.12f
                else -> 1.25f
            }
        }

        fun family(font: String): String {
            val name = font.lowercase()
            return when {
                "times" in name || "palatino" in name || "typewriter" in name || "georgia" in name -> "serif"
                "readout" in name -> "sans-serif-condensed"
                "courier" in name || "writer" in name || "mono" in name -> "monospace"
                "slapstick" in name || "comic" in name || "script" in name -> "casual"
                else -> "sans-serif"
            }
        }
    }
}

/** Registry and FileIO logical values are kept in Android private SharedPreferences. */
internal class AndroidDirectorStore(context: Context, packageId: String) : DirectorStore {
    init { require(Regex("[a-f0-9]{64}").matches(packageId)) }
    private val prefs = context.getSharedPreferences("case-recomp-director-user-data", Context.MODE_PRIVATE)
    // Even when two bundles use the same BuddyAPI/registry key, their saves never cross.
    private val prefix = packageId + "/"
    private fun storedKey(key: String) = prefix + key
    override fun get(key: String): String? = if (key.length <= 512) prefs.getString(storedKey(key), null) else null
    override fun put(key: String, value: String) {
        require(key.length <= 512 && value.length <= 256 * 1024) { "Director save exceeds storage cap" }
        check(prefs.edit().putString(storedKey(key), value).commit()) { "cannot persist Director save" }
    }
    override fun remove(key: String) { if (key.length <= 512) prefs.edit().remove(storedKey(key)).commit() }
}

/**
 * Android audio backend for the currently selected private Director package.
 * MediaPlayer receives local app-cache files only; no original data enters public assets.
 */
private const val TAG = "CaseRecompDirector"

internal class AndroidDirectorSound(
    private val context: Context,
    private val content: DirectorContent,
) : SoundOutput, AutoCloseable {
    private data class Playback(val player: MediaPlayer, val file: File)
    private val channels = mutableMapOf<Int, Playback>()
    private val volumes = mutableMapOf<Int, Int>()
    private val pending = mutableSetOf<Int>()

    override fun play(channel: Int, member: CastMember, loops: Int) {
        stop(channel)
        val data = member.data ?: return
        val fileName = member.lib.file?.file ?: ""
        val (bytes, format) = content.sound(fileName, data) ?: run {
            if (android.util.Log.isLoggable(TAG, android.util.Log.DEBUG)) android.util.Log.d(TAG, "sound ${member.name}: no media")
            return
        }
        if (android.util.Log.isLoggable(TAG, android.util.Log.DEBUG)) {
            android.util.Log.d(TAG, "sound ch$channel ${member.name} $format ${bytes.size} bytes loops=$loops")
        }
        if (bytes.isEmpty() || bytes.size > 64 * 1024 * 1024) return
        val file = File(context.cacheDir, "director-audio-${System.nanoTime()}-$channel.$format")
        try {
            file.outputStream().use { it.write(bytes) }
            val player = MediaPlayer()
            player.setDataSource(file.absolutePath)
            player.isLooping = loops <= 0
            val volume = (volumes[channel] ?: 255) / 255f
            player.setVolume(volume, volume)
            channels[channel] = Playback(player, file)
            pending += channel
            var remaining = loops.coerceAtLeast(1)
            player.setOnPreparedListener {
                if (channels[channel]?.player === it) {
                    pending -= channel
                    it.start()
                }
            }
            player.setOnCompletionListener {
                if (channels[channel]?.player === it && !it.isLooping) {
                    remaining--
                    if (remaining > 0) { it.seekTo(0); it.start() } else stop(channel)
                }
            }
            player.setOnErrorListener { _, _, _ -> stop(channel); true }
            player.prepareAsync()
        } catch (_: Exception) {
            stop(channel)
            file.delete()
        }
    }

    override fun stop(channel: Int) {
        pending -= channel
        paused -= channel
        val playing = channels.remove(channel) ?: return
        runCatching { playing.player.reset() }
        runCatching { playing.player.release() }
        playing.file.delete()
    }

    override fun setVolume(channel: Int, volume: Int) {
        volumes[channel] = volume.coerceIn(0, 255)
        val level = volumes.getValue(channel) / 255f
        channels[channel]?.player?.setVolume(level, level)
    }

    override fun isPlaying(channel: Int): Boolean? {
        if (channel in pending) return true
        return channels[channel]?.player?.let { runCatching { it.isPlaying }.getOrDefault(false) } ?: false
    }

    private val paused = mutableSetOf<Int>()

    /** Activity pause: hold every playing channel where it is. */
    fun pauseAll() {
        for ((channel, playback) in channels) {
            if (runCatching { playback.player.isPlaying }.getOrDefault(false)) {
                runCatching { playback.player.pause() }
                paused += channel
            }
        }
    }

    /** Activity resume: continue the channels [pauseAll] held (e.g. looping music). */
    fun resumeAll() {
        for (channel in paused) channels[channel]?.let { runCatching { it.player.start() } }
        paused.clear()
    }

    override fun close() {
        paused.clear()
        for (channel in channels.keys.toList()) stop(channel)
    }
}

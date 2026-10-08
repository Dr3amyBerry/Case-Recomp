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
        val style = when {
            member.bold && member.italic -> Typeface.BOLD_ITALIC
            member.bold -> Typeface.BOLD
            member.italic -> Typeface.ITALIC
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

    override fun lineHeight(member: CastMember): Int = (member.fontSize * shrink * size(member.font) * 1.25f).toInt().coerceAtLeast(1)

    override fun render(member: CastMember, width: Int, height: Int): LingoImage? {
        if (width <= 0 || height <= 0 || width.toLong() * height > 16L * 1024 * 1024) return null
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            // The box was sized for the original font: rather than wrapping words or lines out of
            // the visible box, squeeze then shrink the substitute until the text fits.
            var lines = TextLayout.lines(member, width, this)
            for ((squeeze, size) in FIT_STEPS) {
                if (fits(lines, member, width, height)) break
                condense = squeeze; shrink = size
                lines = TextLayout.lines(member, width, this)
            }
            val step = TextLayout.lineHeight(member, this)
            apply(member)
            paint.color = member.textColor
            paint.style = Paint.Style.FILL
            var baseline = -paint.fontMetrics.ascent
            for (line in lines) {
                if (baseline > height + step) break
                for ((dx, run) in line.segments) canvas.drawText(run, (line.x + dx).toFloat(), baseline, paint)
                baseline += step
            }
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            return LingoImage(width, height, 32, pixels).also { it.useAlpha = true }
        } finally {
            condense = 1f; shrink = 1f
            bitmap.recycle()
        }
    }

    /** Lines fit when none is wider than the box and their glyphs end inside its height. */
    private fun fits(lines: List<TextLine>, member: CastMember, width: Int, height: Int): Boolean {
        if (lines.any { line -> line.x + line.segments.last().let { (dx, run) -> dx + width(member, run) } > width }) return false
        apply(member)
        val glyphs = paint.fontMetrics.descent - paint.fontMetrics.ascent
        // Trailing blank lines need not be visible.
        val rows = lines.dropLastWhile { it.text.isBlank() }.size.coerceAtLeast(1)
        return (rows - 1) * TextLayout.lineHeight(member, this) + glyphs <= height + 1
    }

    private companion object {
        val FIT_STEPS = listOf(0.95f to 1f, 0.9f to 1f, 0.85f to 1f, 0.85f to 0.9f, 0.85f to 0.8f, 0.85f to 0.7f)

        /** Advance-width ratio of the authored face to its substitute (typewriter faces run narrow). */
        fun width(font: String): Float = if ("typewriter" in font.lowercase()) 0.9f else 1f

        /** Glyph size of the authored face relative to its substitute at the same point size. */
        fun size(font: String): Float = if ("tekto" in font.lowercase()) 0.8f else 1f

        fun family(font: String): String {
            val name = font.lowercase()
            return when {
                "times" in name || "palatino" in name || "typewriter" in name || "georgia" in name -> "serif"
                "courier" in name || "writer" in name || "readout" in name || "mono" in name -> "monospace"
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

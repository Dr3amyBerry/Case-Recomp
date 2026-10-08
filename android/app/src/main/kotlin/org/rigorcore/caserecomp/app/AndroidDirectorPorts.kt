package org.rigorcore.caserecomp.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaPlayer
import org.rigorcore.caserecomp.director.CastMember
import org.rigorcore.caserecomp.director.DirectorContent
import org.rigorcore.caserecomp.director.DirectorStore
import org.rigorcore.caserecomp.director.ImageDecoder
import org.rigorcore.caserecomp.director.LColor
import org.rigorcore.caserecomp.director.LingoImage
import org.rigorcore.caserecomp.director.SoundOutput
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

internal class AndroidDirectorText : TextRasterizer, TextMetrics {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun width(member: CastMember, text: String): Int {
        paint.textSize = member.fontSize.toFloat().coerceAtLeast(1f)
        return paint.measureText(text).toInt()
    }

    override fun lineHeight(member: CastMember): Int = (member.fontSize * 1.25f).toInt().coerceAtLeast(1)

    override fun render(member: CastMember, width: Int, height: Int): LingoImage? {
        if (width <= 0 || height <= 0 || width.toLong() * height > 16L * 1024 * 1024) return null
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            paint.textSize = member.fontSize.toFloat().coerceAtLeast(1f)
            paint.color = LColor.of(member.getProp("foreColor")).argb
            paint.style = Paint.Style.FILL
            var baseline = -paint.fontMetrics.top
            val step = lineHeight(member)
            for (line in member.text.replace("\r\n", "\n").replace('\r', '\n').split('\n')) {
                if (baseline > height + step) break
                canvas.drawText(line, 0f, baseline, paint)
                baseline += step
            }
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            return LingoImage(width, height, 32, pixels)
        } finally { bitmap.recycle() }
    }
}

/** Registry and FileIO logical values are kept in Android private SharedPreferences. */
internal class AndroidDirectorStore(context: Context) : DirectorStore {
    private val prefs = context.getSharedPreferences("case-recomp-director-user-data", Context.MODE_PRIVATE)
    override fun get(key: String): String? = if (key.length <= 512) prefs.getString(key, null) else null
    override fun put(key: String, value: String) {
        require(key.length <= 512 && value.length <= 256 * 1024) { "Director save exceeds storage cap" }
        check(prefs.edit().putString(key, value).commit()) { "cannot persist Director save" }
    }
    override fun remove(key: String) { if (key.length <= 512) prefs.edit().remove(key).commit() }
}

/**
 * Android audio backend for the currently selected private Director package.
 * MediaPlayer receives local app-cache files only; no original data enters public assets.
 */
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
        val (bytes, format) = content.sound(fileName, data) ?: return
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

    override fun isPlaying(channel: Int): Boolean? =
        channel in pending || channels[channel]?.player?.let { runCatching { it.isPlaying }.getOrDefault(false) } ?: false

    fun pauseAll() {
        for (channel in channels.keys.toList()) stop(channel)
    }
    override fun close() = pauseAll()
}

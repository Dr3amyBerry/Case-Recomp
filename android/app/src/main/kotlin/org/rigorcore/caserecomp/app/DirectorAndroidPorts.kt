package org.rigorcore.caserecomp.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.media.MediaPlayer
import org.rigorcore.caserecomp.director.CastMember
import org.rigorcore.caserecomp.director.DirectorContent
import org.rigorcore.caserecomp.director.DirectorStore
import org.rigorcore.caserecomp.director.ImageDecoder
import org.rigorcore.caserecomp.director.LingoImage
import org.rigorcore.caserecomp.director.SoundOutput
import org.rigorcore.caserecomp.director.TextRasterizer
import java.io.File

/** PNG/JPEG to straight-alpha ARGB pixels. */
object AndroidImageDecoder : ImageDecoder {
    override fun decode(bytes: ByteArray): LingoImage? {
        val options = BitmapFactory.Options().apply { inPremultiplied = false; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return LingoImage(bitmap.width, bitmap.height, 32, pixels).also {
            it.useAlpha = bitmap.hasAlpha()
            bitmap.recycle()
        }
    }
}

/** Text members drawn with the platform sans-serif font at the member's size. */
object AndroidTextRasterizer : TextRasterizer {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF000000.toInt(); typeface = Typeface.SANS_SERIF }

    override fun render(member: CastMember, width: Int, height: Int): LingoImage? {
        if (width <= 0 || height <= 0 || member.text.isEmpty()) return null
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        paint.textSize = member.fontSize.toFloat()
        val metrics = paint.fontMetrics
        var y = -metrics.ascent
        for (line in member.text.split('\r')) {
            canvas.drawText(line, 0f, y, paint)
            y += metrics.descent - metrics.ascent
            if (y > height - metrics.ascent) break
        }
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        bitmap.recycle()
        return LingoImage(width, height, 32, pixels)
    }
}

/** Registry and file substitute kept in app-private shared preferences. */
class SharedPreferencesDirectorStore(context: Context, name: String) : DirectorStore {
    private val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    override fun get(key: String): String? = prefs.getString(key, null)
    override fun put(key: String, value: String) { prefs.edit().putString(key, value).apply() }
    override fun remove(key: String) { prefs.edit().remove(key).apply() }
}

/** One MediaPlayer per Director sound channel, fed from the private content package. */
class MediaPlayerSoundOutput(private val context: Context, private val content: DirectorContent) : SoundOutput {
    private val players = HashMap<Int, MediaPlayer>()
    private val volumes = HashMap<Int, Float>()
    private val cacheDir = File(context.cacheDir, "director-sounds").apply { mkdirs() }

    override fun play(channel: Int, member: CastMember, loops: Int) {
        stop(channel)
        val data = member.data ?: return
        val (bytes, ext) = content.sound(member.lib.file?.file ?: "", data) ?: return
        val file = File(cacheDir, "${member.lib.number}-${data.number}.$ext")
        if (!file.isFile || file.length() != bytes.size.toLong()) file.writeBytes(bytes)
        val player = runCatching {
            MediaPlayer().apply {
                setDataSource(file.absolutePath)
                isLooping = loops == 0
                val v = volumes[channel] ?: 1f
                setVolume(v, v)
                prepare()
                start()
            }
        }.getOrNull() ?: return
        players[channel] = player
    }

    override fun stop(channel: Int) {
        players.remove(channel)?.let { runCatching { it.stop() }; it.release() }
    }

    override fun setVolume(channel: Int, volume: Int) {
        val v = volume.coerceIn(0, 255) / 255f
        volumes[channel] = v
        players[channel]?.setVolume(v, v)
    }

    override fun isPlaying(channel: Int): Boolean? = players[channel]?.let { runCatching { it.isPlaying }.getOrDefault(false) }

    fun pauseAll() = players.values.forEach { runCatching { if (it.isPlaying) it.pause() } }
    fun resumeAll() = players.values.forEach { runCatching { it.start() } }
    fun release() { players.keys.toList().forEach(::stop) }
}

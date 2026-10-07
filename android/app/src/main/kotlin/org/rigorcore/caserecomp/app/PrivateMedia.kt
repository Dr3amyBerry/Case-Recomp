package org.rigorcore.caserecomp.app

import android.content.ComponentCallbacks2
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.LruCache
import org.rigorcore.caserecomp.AudioCue
import org.rigorcore.caserecomp.InterruptibleAudioPort

interface BitmapAssetLoader { fun load(assetId: String): Bitmap? }
interface MemoryAwareBitmapAssetLoader : BitmapAssetLoader { fun onTrimMemory(level: Int) }
object NoopBitmapAssetLoader : BitmapAssetLoader { override fun load(assetId: String): Bitmap? = null }

class AppPrivateBitmapAssetLoader(private val content: LoadedPrivateContent) : MemoryAwareBitmapAssetLoader {
    private val cache = object : LruCache<String, Bitmap>(32 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }
    override fun load(assetId: String): Bitmap? {
        cache.get(assetId)?.let { return it }
        val descriptor = content.manifest.asset(assetId) ?: return null
        if (descriptor.mediaType != "image/png") return null
        val file = content.fileForAsset(assetId) ?: return null
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        cache.put(assetId, bitmap)
        return bitmap
    }

    override fun onTrimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) cache.evictAll()
    }
}

class LifecycleMediaAudioPort(context: Context, private val content: LoadedPrivateContent) : InterruptibleAudioPort {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build())
        .setOnAudioFocusChangeListener { change ->
            when (change) {
                AudioManager.AUDIOFOCUS_GAIN -> onInterruptionEnd()
                AudioManager.AUDIOFOCUS_LOSS,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> onInterruptionStart()
            }
        }
        .build()
    private var resumed = false
    private var player: MediaPlayer? = null
    private var resumeAfterPause = false
    private var resumeAfterInterruption = false
    private var focusHeld = false

    override fun play(cue: AudioCue) {
        if (!resumed) return
        val assetId = content.audioAsset(cue.name) ?: return
        val descriptor = content.manifest.asset(assetId) ?: return
        if (descriptor.mediaType !in setOf("audio/wav", "audio/mpeg")) return
        val file = content.fileForAsset(assetId) ?: return
        if (!requestFocus()) return
        releasePlayer()
        player = runCatching {
            MediaPlayer().apply { setDataSource(file.absolutePath); prepare(); start() }
        }.getOrNull()
    }

    override fun onResume() {
        resumed = true
        if (resumeAfterPause && requestFocus()) runCatching { player?.start() }
        resumeAfterPause = false
    }

    override fun onPause() {
        resumed = false
        val current = player
        resumeAfterPause = runCatching { current?.isPlaying == true }.getOrDefault(false)
        if (resumeAfterPause) runCatching { current?.pause() }
        abandonFocus()
    }

    override fun onInterruptionStart() {
        val current = player
        resumeAfterInterruption = runCatching { current?.isPlaying == true }.getOrDefault(false)
        if (resumeAfterInterruption) runCatching { current?.pause() }
    }

    override fun onInterruptionEnd() {
        if (resumed && resumeAfterInterruption) runCatching { player?.start() }
        resumeAfterInterruption = false
    }

    override fun onDestroy() {
        resumed = false
        resumeAfterPause = false
        resumeAfterInterruption = false
        abandonFocus()
        releasePlayer()
    }

    private fun requestFocus(): Boolean {
        if (focusHeld) return true
        focusHeld = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return focusHeld
    }

    private fun abandonFocus() {
        if (focusHeld) audioManager.abandonAudioFocusRequest(focusRequest)
        focusHeld = false
    }

    private fun releasePlayer() { runCatching { player?.stop() }; runCatching { player?.release() }; player = null }
}

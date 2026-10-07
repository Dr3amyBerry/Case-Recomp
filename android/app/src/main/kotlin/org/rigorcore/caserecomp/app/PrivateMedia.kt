package org.rigorcore.caserecomp.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.util.LruCache
import org.rigorcore.caserecomp.AudioCue
import org.rigorcore.caserecomp.LifecycleAudioPort

interface BitmapAssetLoader { fun load(assetId: String): Bitmap? }
object NoopBitmapAssetLoader : BitmapAssetLoader { override fun load(assetId: String): Bitmap? = null }

class AppPrivateBitmapAssetLoader(private val content: LoadedPrivateContent) : BitmapAssetLoader {
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
}

class LifecycleMediaAudioPort(private val content: LoadedPrivateContent) : LifecycleAudioPort {
    private var resumed = false
    private var player: MediaPlayer? = null
    private var resumeAfterPause = false

    override fun play(cue: AudioCue) {
        if (!resumed) return
        val assetId = content.audioAsset(cue.name) ?: return
        val descriptor = content.manifest.asset(assetId) ?: return
        if (descriptor.mediaType !in setOf("audio/wav", "audio/mpeg")) return
        val file = content.fileForAsset(assetId) ?: return
        releasePlayer()
        player = runCatching {
            MediaPlayer().apply { setDataSource(file.absolutePath); prepare(); start() }
        }.getOrNull()
    }

    override fun onResume() {
        resumed = true
        if (resumeAfterPause) runCatching { player?.start() }
        resumeAfterPause = false
    }

    override fun onPause() {
        resumed = false
        val current = player
        resumeAfterPause = runCatching { current?.isPlaying == true }.getOrDefault(false)
        if (resumeAfterPause) runCatching { current?.pause() }
    }

    override fun onDestroy() { resumed = false; resumeAfterPause = false; releasePlayer() }

    private fun releasePlayer() { runCatching { player?.stop() }; runCatching { player?.release() }; player = null }
}

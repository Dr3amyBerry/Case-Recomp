package org.rigorcore.caserecomp.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import org.rigorcore.caserecomp.GameRuntime
import org.rigorcore.caserecomp.NoopAudioPort
import org.rigorcore.caserecomp.SlotSessionAdapter
import org.rigorcore.caserecomp.sha256Hex

class MainActivity : Activity() {
    private lateinit var runtime: GameRuntime
    private lateinit var gameView: GameShellView
    private lateinit var contentRepository: PrivateContentRepository
    private var loadedContent: LoadedPrivateContent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        contentRepository = PrivateContentRepository(this)
        loadedContent = contentRepository.loadActive()
        val scenario = loadedContent?.scenario ?: SyntheticContent.scenario()
        val packageId = loadedContent?.manifest?.packageId ?: sha256Hex("synthetic-shell-v1")
        val slots = SharedPreferencesSlotSessionStore(this)
        val audio = loadedContent?.let(::LifecycleMediaAudioPort) ?: NoopAudioPort
        runtime = GameRuntime(
            scenario,
            AndroidMonotonicClock(),
            SlotSessionAdapter(slots, "slot-1"),
            audio,
            AppPrivateRuntimeObserver(this, packageId),
        )
        runtime.onCreate()
        val bitmapLoader = loadedContent?.let(::AppPrivateBitmapAssetLoader) ?: NoopBitmapAssetLoader
        gameView = GameShellView(this, runtime, loadedContent, bitmapLoader)
        gameView.setOnLongClickListener { requestPrivateImport(); true }
        setContentView(gameView)
    }

    private fun requestPrivateImport() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
        }
        startActivityForResult(intent, REQUEST_PRIVATE_CONTENT)
    }

    @Deprecated("Android platform callback retained to avoid an AndroidX Activity dependency in the shell")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_PRIVATE_CONTENT || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        Thread {
            val result = runCatching {
                contentResolver.openInputStream(uri)?.use(contentRepository::importBundle)
                    ?: error("cannot open selected private content")
            }
            runOnUiThread {
                result.onSuccess {
                    Toast.makeText(this, "Private content imported into app-private storage", Toast.LENGTH_SHORT).show()
                    recreate()
                }.onFailure {
                    Toast.makeText(this, "Import rejected: ${it.message ?: "invalid bundle"}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    override fun onStart() { super.onStart(); runtime.onStart() }
    override fun onResume() { super.onResume(); runtime.onResume(); gameView.invalidate() }
    override fun onPause() { runtime.onPause(); super.onPause() }
    override fun onStop() { runtime.onStop(); super.onStop() }
    override fun onDestroy() { runtime.onDestroy(); super.onDestroy() }

    companion object { private const val REQUEST_PRIVATE_CONTENT = 4041 }
}

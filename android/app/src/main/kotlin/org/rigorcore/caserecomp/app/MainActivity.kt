package org.rigorcore.caserecomp.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import org.rigorcore.caserecomp.AllowAllFlowGate
import org.rigorcore.caserecomp.DenyAllFlowGate
import org.rigorcore.caserecomp.GameRuntime
import org.rigorcore.caserecomp.NoopAudioPort
import org.rigorcore.caserecomp.SlotSessionAdapter
import org.rigorcore.caserecomp.VerifiedFlowGate
import org.rigorcore.caserecomp.sha256Hex

class MainActivity : Activity() {
    private lateinit var runtime: GameRuntime
    private lateinit var gameView: GameShellView
    private lateinit var contentRepository: PrivateContentRepository
    private lateinit var flowRepository: PrivateVerifiedFlowRepository
    private var loadedContent: LoadedPrivateContent? = null
    private var bitmapLoader: BitmapAssetLoader = NoopBitmapAssetLoader

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        contentRepository = PrivateContentRepository(this)
        flowRepository = PrivateVerifiedFlowRepository(this)
        loadedContent = contentRepository.loadActive()
        val content = loadedContent
        val scenario = content?.scenario ?: SyntheticContent.scenario()
        val packageId = content?.manifest?.packageId ?: sha256Hex("synthetic-shell-v1")
        val slots = SharedPreferencesSlotSessionStore(this)
        val audio = content?.let { LifecycleMediaAudioPort(this, it) } ?: NoopAudioPort
        val proof = content?.let(flowRepository::loadFor)
        val flowGate = when {
            content == null -> AllowAllFlowGate
            proof != null -> VerifiedFlowGate(proof, scenario.id, content.manifest.scenarioSha256, content.manifest.packageId)
            else -> DenyAllFlowGate
        }
        runtime = GameRuntime(
            scenario,
            AndroidMonotonicClock(),
            SlotSessionAdapter(slots, "slot-1"),
            audio,
            AppPrivateRuntimeObserver(this, packageId),
            flowGate,
        )
        runtime.onCreate()
        bitmapLoader = content?.let(::AppPrivateBitmapAssetLoader) ?: NoopBitmapAssetLoader
        gameView = GameShellView(this, runtime, content, bitmapLoader)
        gameView.setOnLongClickListener {
            if (loadedContent == null) requestPrivateContentImport() else requestVerifiedFlowImport()
            true
        }
        setContentView(gameView)
    }

    private fun requestPrivateContentImport() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
        }
        startActivityForResult(intent, REQUEST_PRIVATE_CONTENT)
    }

    private fun requestVerifiedFlowImport() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }
        startActivityForResult(intent, REQUEST_VERIFIED_FLOW)
    }

    @Deprecated("Android platform callback retained to avoid an AndroidX Activity dependency in the shell")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        when (requestCode) {
            REQUEST_PRIVATE_CONTENT -> Thread {
                val result = runCatching {
                    contentResolver.openInputStream(uri)?.use(contentRepository::importBundle)
                        ?: error("cannot open selected private content")
                }
                runOnUiThread {
                    result.onSuccess {
                        Toast.makeText(this, "Private content imported into app-private storage", Toast.LENGTH_SHORT).show()
                        recreate()
                    }.onFailure {
                        Toast.makeText(this, "Import rejected: " + (it.message ?: "invalid bundle"), Toast.LENGTH_LONG).show()
                    }
                }
            }.start()
            REQUEST_VERIFIED_FLOW -> {
                val content = loadedContent ?: return
                Thread {
                    val result = runCatching {
                        contentResolver.openInputStream(uri)?.use { flowRepository.importProof(it, content) }
                            ?: error("cannot open selected verified flow")
                    }
                    runOnUiThread {
                        result.onSuccess {
                            Toast.makeText(this, "Verified flow imported; private navigation unlocked", Toast.LENGTH_SHORT).show()
                            recreate()
                        }.onFailure {
                            Toast.makeText(this, "Flow proof rejected: " + (it.message ?: "invalid proof"), Toast.LENGTH_LONG).show()
                        }
                    }
                }.start()
            }
        }
    }

    override fun onStart() { super.onStart(); runtime.onStart() }
    override fun onResume() { super.onResume(); runtime.onResume(); gameView.invalidate() }
    override fun onPause() { runtime.onPause(); super.onPause() }
    override fun onStop() { runtime.onStop(); super.onStop() }
    override fun onTrimMemory(level: Int) {
        (bitmapLoader as? MemoryAwareBitmapAssetLoader)?.onTrimMemory(level)
        super.onTrimMemory(level)
    }

    override fun onDestroy() { runtime.onDestroy(); super.onDestroy() }

    companion object {
        private const val REQUEST_PRIVATE_CONTENT = 4041
        private const val REQUEST_VERIFIED_FLOW = 4042
    }
}

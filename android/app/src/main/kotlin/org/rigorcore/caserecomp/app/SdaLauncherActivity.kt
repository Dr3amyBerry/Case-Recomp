package org.rigorcore.caserecomp.app

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.rigorcore.caserecomp.sda.SdaClock
import org.rigorcore.caserecomp.sda.SdaContent
import org.rigorcore.caserecomp.sda.SdaImageDecoder
import org.rigorcore.caserecomp.sda.SdaPixelSource
import org.rigorcore.caserecomp.sda.SdaScene
import java.io.File

/**
 * Launcher activity for SDA engine games (Mystery P.I.: The Vegas Heist).
 * Completely isolated from Director launcher and repositories.
 * Renders scenes with SdaGameView and handles touch interactions.
 */
class SdaLauncherActivity : Activity() {

    private lateinit var repository: PrivateSdaRepository
    private var content: SdaContent? = null
    private var scene: SdaScene? = null
    private var gameView: SdaGameView? = null
    private var clock: SdaClock? = null
    private var isFrameLoopRunning = false
    private var lastFrameNanos: Long = 0L

    private val frameCallback = object : android.view.Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isFrameLoopRunning) return
            if (lastFrameNanos != 0L) {
                val dt = ((frameTimeNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0.001f, 0.1f)
                gameView?.step(dt)
            }
            lastFrameNanos = frameTimeNanos
            android.view.Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = PrivateSdaRepository(this)

        val directPath = intent.getStringExtra(EXTRA_PACKAGE_PATH)
        val packageFile = if (!directPath.isNullOrBlank()) {
            File(directPath).takeIf { it.isFile }
        } else {
            repository.loadActive()?.path
        }

        if (packageFile == null) {
            showNoPackageScreen()
            return
        }

        launchSdaPackage(packageFile)
    }

    private fun showNoPackageScreen() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xFF10151E.toInt())
            setPadding(32, 32, 32, 32)
            addView(TextView(this@SdaLauncherActivity).apply {
                text = "Mystery P.I.: The Vegas Heist"
                setTextColor(0xFFE8B84A.toInt())
                textSize = 24f
                gravity = Gravity.CENTER
            })
            addView(TextView(this@SdaLauncherActivity).apply {
                text = "\nNo se ha importado ningún paquete SDA activo.\nImporta un paquete legítimo desde la pantalla principal para jugar."
                setTextColor(Color.WHITE)
                textSize = 16f
                gravity = Gravity.CENTER
            })
        }
        setContentView(layout)
    }

    private fun launchSdaPackage(file: File) {
        try {
            val bitmaps = mutableMapOf<String, Bitmap>()
            val decoder = SdaImageDecoder { bytes ->
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bmp != null) {
                    object : SdaPixelSource {
                        override val width: Int = bmp.width
                        override val height: Int = bmp.height
                        override fun getAlpha(px: Int, py: Int): Int {
                            if (px !in 0 until width || py !in 0 until height) return 0
                            return (bmp.getPixel(px, py) ushr 24) and 0xFF
                        }
                        override val nativeImage: Any get() = bmp
                    }
                } else null
            }

            val sdaContent = SdaContent.open(file, decoder)
            content = sdaContent

            // Load Vault scene by default or requested scene
            val sceneResource = intent.getStringExtra(EXTRA_SCENE) ?: "SCENE_VAULT.MSL"
            val loadedScene = sdaContent.loadScene(sceneResource, seed = System.currentTimeMillis() and 0xFFFFFFFFL)
            scene = loadedScene
            clock = SdaClock(limit = 1320f)

            // Cache bitmaps for objects and backdrop
            var bgBitmap: Bitmap? = null
            for (sprite in loadedScene.drawOrder) {
                val bmp = sprite.image.nativeImage as? Bitmap
                if (bmp != null) {
                    if (sprite.identity.isNotEmpty()) {
                        bitmaps[sprite.identity] = bmp
                    } else if (bgBitmap == null && bmp.width >= 600) {
                        bgBitmap = bmp
                    }
                }
            }

            // Restore checkpoint if saved
            val savedStateJson = repository.loadCheckpoint()
            if (!savedStateJson.isNullOrBlank()) {
                try {
                    val savedState = org.rigorcore.caserecomp.sda.SdaSceneState.fromJson(savedStateJson)
                    if (savedState.sceneName == loadedScene.name) {
                        loadedScene.restore(savedState)
                        clock?.elapsed = savedState.elapsed
                        Log.i(TAG, "Restored SDA checkpoint for ${loadedScene.name}")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to restore SDA checkpoint", e)
                }
            }

            val view = SdaGameView(this, loadedScene, clock, bgBitmap, bitmaps)
            Log.i(TAG, "Scene ${loadedScene.name} loaded. Targets: ${loadedScene.targets}")
            for (t in loadedScene.targets) {
                val s = loadedScene.objects[t]
                if (s != null) {
                    Log.i(TAG, "Target $t at (${s.x}, ${s.y}) size (${s.image.width}x${s.image.height})")
                }
            }

            view.onObjectFoundListener = { id, gain ->
                Log.i(TAG, "Object found: $id, +$gain pts, total: ${loadedScene.score.points}")
                Toast.makeText(this, "¡Objeto encontrado! +$gain pts", Toast.LENGTH_SHORT).show()
            }
            view.onMissListener = { penalty ->
                Log.i(TAG, "Miss clicked: penalty=$penalty, points: ${loadedScene.score.points}")
            }
            view.onSceneCompleteListener = {
                val sc = scene
                val deck = sc?.deck
                if (sc != null && deck != null && deck.cursor < sc.candidateSets.size) {
                    val nextBatch = sc.nextBatch(System.currentTimeMillis() and 0xFFFFFFFFL)
                    Log.i(TAG, "Next batch unlocked: ${nextBatch.size} sets")
                    Toast.makeText(this, "Siguiente lote de objetivos (${nextBatch.size})", Toast.LENGTH_SHORT).show()
                } else {
                    repository.clearCheckpoint()
                    Log.i(TAG, "Scene completed! Final score: ${sc?.score?.points}")
                    Toast.makeText(this, "¡Escena completada! Puntuación final: ${sc?.score?.points}", Toast.LENGTH_LONG).show()
                }
            }
            gameView = view
            setContentView(view)
            hideSystemBars()
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to launch SDA package", t)
            Toast.makeText(this, "Error al iniciar paquete SDA: ${t.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        if (!isFrameLoopRunning) {
            isFrameLoopRunning = true
            lastFrameNanos = 0L
            android.view.Choreographer.getInstance().postFrameCallback(frameCallback)
        }
    }

    override fun onPause() {
        isFrameLoopRunning = false
        android.view.Choreographer.getInstance().removeFrameCallback(frameCallback)
        scene?.let { sc ->
            try {
                repository.saveCheckpoint(sc.snapshot().toJson())
            } catch (e: Exception) {
                Log.w(TAG, "Failed to save SDA checkpoint", e)
            }
        }
        super.onPause()
    }

    override fun onDestroy() {
        isFrameLoopRunning = false
        android.view.Choreographer.getInstance().removeFrameCallback(frameCallback)
        content?.close()
        content = null
        super.onDestroy()
    }

    private fun hideSystemBars() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
    }

    companion object {
        const val EXTRA_PLAY = "extra-play-sda"
        const val EXTRA_PACKAGE_PATH = "package_path"
        const val EXTRA_SCENE = "extra-scene"
        private const val TAG = "CaseRecompSda"
    }
}

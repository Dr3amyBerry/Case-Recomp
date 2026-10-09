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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = PrivateSdaRepository(this)

        val install = repository.loadActive()
        if (install == null) {
            showNoPackageScreen()
            return
        }

        launchSdaPackage(install.path)
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
                    }
                } else null
            }

            val sdaContent = SdaContent.open(file, decoder)
            content = sdaContent

            // Load Vault scene by default or first available scene
            val loadedScene = sdaContent.loadScene("SCENE_VAULT.MSL", seed = System.currentTimeMillis() and 0xFFFFFFFFL)
            scene = loadedScene
            clock = SdaClock(limit = 1320f)

            // Cache bitmaps for rendering
            for ((id, sprite) in loadedScene.objects) {
                // If sprite has decoded image
            }

            val view = SdaGameView(this, loadedScene, clock, null, bitmaps)
            view.onObjectFoundListener = { id, gain ->
                Toast.makeText(this, "¡Objeto encontrado! +$gain pts", Toast.LENGTH_SHORT).show()
            }
            view.onSceneCompleteListener = {
                Toast.makeText(this, "¡Escena completada!", Toast.LENGTH_LONG).show()
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
    }

    override fun onDestroy() {
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
        private const val TAG = "CaseRecompSda"
    }
}

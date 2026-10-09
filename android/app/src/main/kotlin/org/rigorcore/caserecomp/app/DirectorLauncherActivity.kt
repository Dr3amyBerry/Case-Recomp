package org.rigorcore.caserecomp.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.rigorcore.caserecomp.director.DirectorContent
import org.rigorcore.caserecomp.director.DirectorEnvironment
import org.rigorcore.caserecomp.director.DirectorRuntime
import org.rigorcore.caserecomp.director.StageRenderer
import org.rigorcore.caserecomp.director.StandardXtras
import java.io.File

/**
 * Plays the user's imported private director-content ZIP on the Director/Lingo engine.
 * No original assets are packaged. Release builds open it from [HomeActivity]'s Play only;
 * debug builds also keep the import screen, adb extras and [DirectorDebugBridge].
 */
class DirectorLauncherActivity : Activity() {
    private lateinit var repository: PrivateDirectorRepository
    private var content: DirectorContent? = null
    private var runtime: DirectorRuntime? = null
    private var audio: AndroidDirectorSound? = null
    private var stage: DirectorStageView? = null
    private var activityResumed = false
    private var ready = false
    private var playbackClock: DirectorSessionClock? = null
    private var checkpoint: HuntsvilleSessionCheckpoint? = null
    private var workerToken = 0
    /** Debug-only adb automation bridge (see [DirectorDebugBridge]). */
    private var bridge: DirectorDebugBridge? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = PrivateDirectorRepository(this)
        if (!debuggable) {
            // Release: only the home screen's Play opens this, on the package the user imported.
            openActive()
            return
        }
        bridge = DirectorDebugBridge({ runtime }, { stage }).also { it.register(this) }
        showStartScreen("Director/Lingo private debug")
        if (savedInstanceState == null) pendingSave = intent.getStringExtra(EXTRA_IMPORT_SAVE)
        val external = intent.getStringExtra(EXTRA_IMPORT_EXTERNAL)
        if (savedInstanceState == null && external != null) importExternal(external) else openActive()
    }

    private val debuggable get() = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0

    /** Opened to play (home screen or release): a loading screen, and back home on failure. */
    private val playing get() = !debuggable || intent.getBooleanExtra(EXTRA_PLAY, false)

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.let {
                it.hide(android.view.WindowInsets.Type.systemBars())
                it.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
    }

    private fun showLoading() {
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            addView(android.widget.ProgressBar(this@DirectorLauncherActivity),
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        })
    }

    private fun showStartScreen(message: String) {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24, 24, 24, 24)
            setBackgroundColor(android.graphics.Color.BLACK)
        }
        column.addView(TextView(this).apply {
            text = message
            setTextColor(android.graphics.Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
        })
        column.addView(Button(this).apply {
            text = "Import private Director ZIP"
            setOnClickListener { selectPrivateZip() }
        })
        column.addView(Button(this).apply {
            text = "Open previously imported Director ZIP"
            setOnClickListener { openActive() }
        })
        setContentView(column)
    }

    private fun selectPrivateZip() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
        }
        startActivityForResult(intent, REQUEST_DIRECTOR_CONTENT)
    }

    /**
     * Debug/ADB route: import a ZIP the tester pushed to this app's own external files
     * directory (`Android/data/<pkg>/files/import/`), avoiding the document picker.
     */
    private fun importExternal(name: String) {
        val dir = getExternalFilesDir("import")?.canonicalFile
        val file = dir?.let { File(it, name).canonicalFile }
        if (dir == null || file == null || file.parentFile != dir || !file.isFile) {
            showStartScreen("Director import rejected: no such file in app import folder")
            return
        }
        val token = ++workerToken
        showStartScreen("Importing and hashing private Director content…")
        Thread {
            val result = runCatching { createSession(file.inputStream().use(repository::import).path) }
            runOnUiThread {
                if (isFinishing || isDestroyed || token != workerToken) {
                    result.getOrNull()?.close()
                } else result.fold(::displaySession) {
                    Log.e(TAG, "external import failed", it)
                    showStartScreen("Director import rejected: " + (it.message ?: "invalid package"))
                }
            }
        }.start()
    }

    private fun openActive() {
        val token = ++workerToken
        if (playing) showLoading() else showStartScreen("Validating private Director content…")
        Thread {
            val result = runCatching {
                val install = repository.loadActive() ?: error("No verified Director package is imported yet")
                createSession(install.path)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed || token != workerToken) {
                    result.getOrNull()?.close()
                } else result.fold(::displaySession) {
                    Log.e(TAG, "Director start failed", it)
                    if (playing) {
                        Toast.makeText(this, "No se pudo abrir el juego: " + (it.message ?: "paquete no válido"), Toast.LENGTH_LONG).show()
                        finish()
                    } else showStartScreen("Director not started: " + (it.message ?: "invalid private package"))
                }
            }
        }.start()
    }

    /** Optional legacy offsets, accepted only by the resolved profile's reviewed allowlist. */
    private fun presentationNudges(profile: DirectorPresentationProfile): Map<String, Pair<Int, Int>> = runCatching {
        if (profile.approvedLegacyNudges.isEmpty()) return emptyMap()
        val file = File(getExternalFilesDir("import") ?: return emptyMap(), "presentation.json")
        if (!file.isFile || file.length() > 64 * 1024) return emptyMap()
        profile.legacyNudges(file.readText())
    }.getOrElse {
        Log.w(TAG, "presentation.json ignored: ${it.message}")
        emptyMap()
    }

    /** Debug/ADB route: a save (JSON object of store keys) pushed to the import folder. */
    private var pendingSave: String? = null

    private fun importSave(store: AndroidDirectorStore, name: String) {
        val dir = getExternalFilesDir("import")?.canonicalFile ?: return
        val file = File(dir, name).canonicalFile
        require(file.parentFile == dir && file.isFile && file.length() <= 4L * 1024 * 1024) { "no such save in app import folder" }
        val json = org.json.JSONObject(file.readText())
        for (key in json.keys()) store.put(key, json.getString(key))
        Log.i(TAG, "imported ${json.length()} save entries from $name")
    }

    private fun createSession(file: File): Session {
        val loaded = DirectorContent.open(file, AndroidDirectorImageDecoder)
        try {
            // The repository checked this ZIP during user import and verified its entire file digest on load.
            val store = AndroidDirectorStore(this, file.nameWithoutExtension)
            pendingSave?.let { name -> importSave(store, name); store.remove(HuntsvilleSessionCheckpoint.KEY); pendingSave = null }
            val profile = DirectorPresentationProfiles.resolve(loaded)
            val text = AndroidDirectorText(profile.text)
            val output = AndroidDirectorSound(this, loaded)
            try {
                val movie = loaded.movie
                // Original-game FileIO/Buddy API probes must find bundled casts with a realistic size.
                val xtras = StandardXtras(store, castFileSize = loaded::castFileSize)
                require(movie.stageWidth in 1..2048 && movie.stageHeight in 1..2048 &&
                    movie.stageWidth.toLong() * movie.stageHeight <= 4L * 1024 * 1024 &&
                    movie.frameCount in 1..100_000 && movie.channelCount in 6..8_192) {
                    "Director movie dimensions or channel counts exceed safe bounds"
                }
                val clock = DirectorSessionClock(SystemClock::elapsedRealtime)
                val runtime = DirectorRuntime(
                    movie, loaded.lingo, media = loaded, sound = output,
                    clock = clock::now,
                    environment = DirectorEnvironment(moviePath = "C:\\CaseRecomp\\", platform = "Windows,32", runMode = "Projector"),
                    extensions = xtras, textMetrics = text, spriteHitOffsets = profile.interactiveNudges,
                    // Keep a scene's decoded cast in memory, within a third of this app's heap.
                    mediaCacheBytes = (Runtime.getRuntime().maxMemory() / 3).coerceIn(32L shl 20, 256L shl 20),
                )
                runtime.start()
                // Shown larger than the stage: compose at 2x so text and vectors are drawn at
                // (about) screen resolution; the view then scales the frame down smoothly.
                val display = resources.displayMetrics
                val fit = minOf(display.widthPixels.toFloat() / movie.stageWidth, display.heightPixels.toFloat() / movie.stageHeight)
                val scale = intent.getIntExtra(EXTRA_STAGE_SCALE, 0).takeIf { debuggable && it in 1..2 } ?: if (fit > 1.05f) 2 else 1
                val renderer = StageRenderer(runtime, text, AndroidDirectorImageDecoder, scale = scale, nudges = presentationNudges(profile), scopedNudges = profile.scopedNudges)
                val checkpoint = if (profile == DirectorPresentationProfiles.HUNTSVILLE_ES)
                    HuntsvilleSessionCheckpoint(runtime, store, store::atomically) { Log.w(TAG, "checkpoint failed", it) } else null
                return Session(loaded, runtime, renderer, output, profile, clock, checkpoint)
            } catch (e: Throwable) { output.close(); throw e }
        } catch (e: Throwable) { loaded.close(); throw e }
    }

    private fun displaySession(session: Session) {
        disposeSession()
        if (session.profile == DirectorPresentationProfiles.GENERIC) {
            Toast.makeText(this, "Edicion sin ajustes visuales verificados. Se usara la presentacion estandar.", Toast.LENGTH_LONG).show()
        }
        content = session.content
        runtime = session.runtime
        audio = session.audio
        playbackClock = session.clock
        checkpoint = session.checkpoint
        val newStage = DirectorStageView(this, session.runtime, session.renderer)
        stage = newStage
        newStage.onQuit = { exitWithSave() }
        val options = if (session.profile == DirectorPresentationProfiles.HUNTSVILLE_ES)
            HuntsvilleOptions(session.runtime) {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(HuntsvilleOptions.SUPPORT_URL)))
                } catch (_: android.content.ActivityNotFoundException) {
                    Toast.makeText(this, "No hay un navegador disponible", Toast.LENGTH_LONG).show()
                }
            } else null
        newStage.drawStageOverlay = options?.let { adapter -> { canvas -> adapter.draw(canvas) } }
        newStage.stageActionAt = options?.let { adapter -> { x, y -> adapter.actionAt(x, y) } }
        newStage.afterFrame = { options?.afterFrame(); checkpoint?.afterFrame() }
        newStage.onRuntimeError = { error ->
            newStage.pauseFrames()
            Log.e(TAG, "Director runtime paused", error)
            Toast.makeText(this, "Director runtime paused: " + (error.message ?: "unknown error"), Toast.LENGTH_LONG).show()
        }
        val layout = FrameLayout(this)
        layout.addView(newStage, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        // Keyboard for the player-name entry; Exit returns to the game selection screen.
        layout.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(Button(this@DirectorLauncherActivity).apply {
                text = "Teclado"
                alpha = 0.7f
                setOnClickListener { newStage.showKeyboard() }
            })
            addView(Button(this@DirectorLauncherActivity).apply {
                text = "Salir"
                alpha = 0.7f
                setOnClickListener { exitWithSave() }
            })
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END))
        setContentView(layout)
        newStage.requestFocus()
        ready = true
        if (!isFinishing && activityResumed) newStage.resumeFrames() else playbackClock?.pause()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_DIRECTOR_CONTENT || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val token = ++workerToken
        showStartScreen("Importing and hashing private Director content…")
        Thread {
            val result = runCatching {
                val installed = contentResolver.openInputStream(uri)?.use(repository::import)
                    ?: error("cannot open selected file")
                createSession(installed.path)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed || token != workerToken) {
                    result.getOrNull()?.close()
                } else result.fold(::displaySession) {
                    showStartScreen("Director import rejected: " + (it.message ?: "invalid package"))
                }
            }
        }.start()
    }

    private fun exitWithSave() {
        try { checkpoint?.save(); finish() }
        catch (e: Exception) {
            Log.e(TAG, "cannot save before exit", e)
            Toast.makeText(this, "No se pudo guardar. Intenta salir de nuevo.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        activityResumed = true
        if (ready) {
            playbackClock?.resume()
            stage?.resumeFrames()
            audio?.resumeAll()
        }
    }
    override fun onPause() {
        activityResumed = false
        stage?.pauseFrames()
        playbackClock?.pause()
        runCatching { checkpoint?.save() }.onFailure { Log.e(TAG, "cannot save on pause", it) }
        audio?.pauseAll()
        super.onPause()
    }
    override fun onDestroy() {
        workerToken++
        bridge?.let { runCatching { unregisterReceiver(it) } }; bridge = null
        disposeSession()
        super.onDestroy()
    }

    private fun disposeSession() {
        stage?.release(); stage = null
        runtime?.let { runCatching { it.stop() } }; runtime = null
        audio?.close(); audio = null
        content?.close(); content = null
        checkpoint = null; playbackClock = null
        ready = false
    }

    private class Session(
        val content: DirectorContent, val runtime: DirectorRuntime,
        val renderer: StageRenderer, val audio: AndroidDirectorSound,
        val profile: DirectorPresentationProfile,
        val clock: DirectorSessionClock, val checkpoint: HuntsvilleSessionCheckpoint?,
    ) : AutoCloseable {
        override fun close() { runCatching { runtime.stop() }; audio.close(); content.close() }
    }

    companion object {
        /** Open the imported package straight away (the home screen's Play). */
        const val EXTRA_PLAY = "play"
        private const val REQUEST_DIRECTOR_CONTENT = 6042
        private const val EXTRA_IMPORT_EXTERNAL = "import_external"
        private const val EXTRA_IMPORT_SAVE = "import_save"
        /** Debug QA: force the compositor scale (1 = stage resolution, 2 = double). */
        private const val EXTRA_STAGE_SCALE = "stage_scale"
        private const val TAG = "CaseRecompDirector"
    }
}

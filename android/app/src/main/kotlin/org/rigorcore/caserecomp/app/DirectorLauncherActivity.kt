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
 * Explicit debug-only gateway into the original Director/Lingo engine.
 * No original assets are packaged; user selects a private director-content ZIP.
 * The synthetic shell remains the primary launcher until actual native golden parity.
 */
class DirectorLauncherActivity : Activity() {
    private lateinit var repository: PrivateDirectorRepository
    private var content: DirectorContent? = null
    private var runtime: DirectorRuntime? = null
    private var audio: AndroidDirectorSound? = null
    private var stage: DirectorStageView? = null
    private var ready = false
    private var workerToken = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE == 0) {
            finish()
            return
        }
        repository = PrivateDirectorRepository(this)
        showStartScreen("Director/Lingo private debug")
        if (savedInstanceState == null) pendingSave = intent.getStringExtra(EXTRA_IMPORT_SAVE)
        val external = intent.getStringExtra(EXTRA_IMPORT_EXTERNAL)
        if (savedInstanceState == null && external != null) importExternal(external) else openActive()
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
        showStartScreen("Validating private Director content…")
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
                    showStartScreen("Director not started: " + (it.message ?: "invalid private package"))
                }
            }
        }.start()
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
            pendingSave?.let { name -> importSave(store, name); pendingSave = null }
            val text = AndroidDirectorText()
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
                val runtime = DirectorRuntime(
                    movie, loaded.lingo, media = loaded, sound = output,
                    clock = SystemClock::elapsedRealtime,
                    environment = DirectorEnvironment(moviePath = "C:\\CaseRecomp\\", platform = "Windows,32", runMode = "Projector"),
                    extensions = xtras, textMetrics = text,
                    // Keep a scene's decoded cast in memory, within a third of this app's heap.
                    mediaCacheBytes = (Runtime.getRuntime().maxMemory() / 3).coerceIn(32L shl 20, 256L shl 20),
                )
                runtime.start()
                val renderer = StageRenderer(runtime, text, AndroidDirectorImageDecoder)
                return Session(loaded, runtime, renderer, output)
            } catch (e: Throwable) { output.close(); throw e }
        } catch (e: Throwable) { loaded.close(); throw e }
    }

    private fun displaySession(session: Session) {
        disposeSession()
        content = session.content
        runtime = session.runtime
        audio = session.audio
        val newStage = DirectorStageView(this, session.runtime, session.renderer)
        stage = newStage
        newStage.onQuit = { finish() }
        newStage.onRuntimeError = { error ->
            newStage.pauseFrames()
            Log.e(TAG, "Director runtime paused", error)
            Toast.makeText(this, "Director runtime paused: " + (error.message ?: "unknown error"), Toast.LENGTH_LONG).show()
        }
        val layout = FrameLayout(this)
        layout.addView(newStage, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        layout.addView(Button(this).apply {
            text = "Keyboard"
            alpha = 0.7f
            setOnClickListener { newStage.showKeyboard() }
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END))
        setContentView(layout)
        newStage.requestFocus()
        ready = true
        if (!isFinishing) newStage.resumeFrames()
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

    override fun onResume() {
        super.onResume()
        if (ready) {
            stage?.resumeFrames()
            audio?.resumeAll()
        }
    }
    override fun onPause() {
        stage?.pauseFrames()
        audio?.pauseAll()
        super.onPause()
    }
    override fun onDestroy() {
        workerToken++
        disposeSession()
        super.onDestroy()
    }

    private fun disposeSession() {
        stage?.release(); stage = null
        runtime?.let { runCatching { it.stop() } }; runtime = null
        audio?.close(); audio = null
        content?.close(); content = null
        ready = false
    }

    private class Session(
        val content: DirectorContent, val runtime: DirectorRuntime,
        val renderer: StageRenderer, val audio: AndroidDirectorSound,
    ) : AutoCloseable {
        override fun close() { runCatching { runtime.stop() }; audio.close(); content.close() }
    }

    private companion object {
        const val REQUEST_DIRECTOR_CONTENT = 6042
        const val EXTRA_IMPORT_EXTERNAL = "import_external"
        const val EXTRA_IMPORT_SAVE = "import_save"
        const val TAG = "CaseRecompDirector"
    }
}

package org.rigorcore.caserecomp.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
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
        openActive()
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
                } else result.fold(::displaySession) { showStartScreen("Director not started: " + (it.message ?: "invalid private package")) }
            }
        }.start()
    }

    private fun createSession(file: File): Session {
        val loaded = DirectorContent.open(file, AndroidDirectorImageDecoder)
        try {
            // The repository checked this ZIP during user import and verified its entire file digest on load.
            val store = AndroidDirectorStore(this, file.nameWithoutExtension)
            val text = AndroidDirectorText()
            val output = AndroidDirectorSound(this, loaded)
            try {
                val movie = loaded.movie
                // Existing original-game FileIO/Buddy API probes must be able to discover bundled casts.
                val xtras = StandardXtras(store, castFileSize = { path ->
                    if (movie.externalCast(path) != null) 1 else -1
                })
                require(movie.stageWidth in 1..4096 && movie.stageHeight in 1..4096 &&
                    movie.stageWidth.toLong() * movie.stageHeight <= 16L * 1024 * 1024) {
                    "invalid Director stage geometry"
                }
                val runtime = DirectorRuntime(
                    movie, loaded.lingo, media = loaded, sound = output,
                    clock = SystemClock::elapsedRealtime,
                    environment = DirectorEnvironment(moviePath = "C:\\CaseRecomp\\", platform = "Windows,32", runMode = "Projector"),
                    extensions = xtras, textMetrics = text,
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
        newStage.onRuntimeError = { error ->
            newStage.pauseFrames()
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
        if (ready) stage?.resumeFrames()
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
    }
}

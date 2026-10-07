package org.rigorcore.caserecomp.app

import android.app.Activity
import android.os.Bundle
import org.rigorcore.caserecomp.GameRuntime
import org.rigorcore.caserecomp.NoopAudioPort

class MainActivity : Activity() {
    private lateinit var runtime: GameRuntime
    private lateinit var gameView: GameShellView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runtime = GameRuntime(
            SyntheticContent.scenario(),
            AndroidMonotonicClock(),
            SharedPreferencesSessionStore(this),
            NoopAudioPort,
        )
        runtime.onCreate()
        gameView = GameShellView(this, runtime)
        setContentView(gameView)
    }

    override fun onStart() {
        super.onStart()
        runtime.onStart()
    }

    override fun onResume() {
        super.onResume()
        runtime.onResume()
        gameView.invalidate()
    }

    override fun onPause() {
        runtime.onPause()
        super.onPause()
    }

    override fun onStop() {
        runtime.onStop()
        super.onStop()
    }

    override fun onDestroy() {
        runtime.onDestroy()
        super.onDestroy()
    }
}

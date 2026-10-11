package org.rigorcore.caserecomp.app

import org.rigorcore.caserecomp.director.DirectorRuntime
import org.rigorcore.caserecomp.director.StageRenderer
import org.rigorcore.caserecomp.lingo.LingoValue.LInstance
import org.rigorcore.caserecomp.lingo.isTruthy

/** Installed only for Prime. Detects the original active behaviour, not case numbers. */
internal class PrimeFlashlight(private val runtime: DirectorRuntime, renderer: StageRenderer, private val pointerPresent: () -> Boolean) {
    private val pulse = PrimeFocusPulse()
    private var shadow: Int? = null
    private var spanIdentity: LInstance? = null
    var visible = false
        private set
    var allowed = false
        private set
    private fun now() = android.os.SystemClock.uptimeMillis()
    init {
        renderer.drawItem = { item -> item.sprite != shadow || !allowed || !pulse.illuminated(now()) }
    }
    fun afterFrame() {
        val item = runtime.displayList().firstOrNull { item ->
            runtime.sprite(item.sprite).instances.any { it.script.name.equals("flashlight script", true) }
        }
        val behavior = item?.let { runtime.sprite(it.sprite).instances.first { it.script.name.equals("flashlight script", true) } }
        if (behavior !== spanIdentity) {
            pulse.cancel()
            if (behavior != null && !pointerPresent()) runtime.mouseMove(-runtime.movie.stageWidth, -runtime.movie.stageHeight)
        }
        spanIdentity = behavior
        shadow = item?.sprite
        visible = behavior != null
        val game = runtime.vm.global("gGameManager") as? LInstance
        val dialog = game?.let { runtime.vm.callMethod(it, "getBoolDialogUp", listOf(it)) }?.isTruthy() == true
        allowed = behavior?.properties?.entries?.firstOrNull { it.key.equals("m_boolLighted", true) }?.value?.isTruthy() == true && !dialog
        if (!allowed) pulse.cancel()
    }
    fun press() = pulse.start(now(), allowed)
    fun enabled() = allowed && !pulse.busy(now())
    fun pause() = pulse.cancel()
}

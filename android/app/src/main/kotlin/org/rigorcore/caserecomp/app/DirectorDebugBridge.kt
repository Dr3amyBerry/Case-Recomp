package org.rigorcore.caserecomp.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import org.rigorcore.caserecomp.director.DirectorRuntime
import org.rigorcore.caserecomp.lingo.LingoValue
import org.rigorcore.caserecomp.lingo.asText

/**
 * Debug-only automation bridge for on-device QA scripts: generic Director introspection
 * (frame label, sprites, hit points, Lingo globals and methods, Flash buttons) answered
 * through `adb shell am broadcast`. Senders need android.permission.DUMP, which only the
 * shell and the system hold; the launcher registers it only in debuggable builds.
 *
 * `adb shell am broadcast -a org.rigorcore.caserecomp.DIRECTOR_DEBUG --es cmd 'label'`
 * replies in the broadcast result data. Arguments are separated by `|`:
 * - `label` — frame number and label
 * - `sprites|from|to` — one line per on-stage sprite: number, member, type, rect, blend, scripted
 * - `hit|n` — a stage point where a click reaches sprite n, or `none`
 * - `text|n` — sprite n's member name, size and text (returns shown as `/`)
 * - `global|name`,`prop|target|name`, `call|target|method|args…` — target is `global:name`
 *   or `sprite:n`; arguments are integers, `#symbols` or strings; results as Lingo text
 * - `callsprites|from|to|method|args…` — the method on each sprite with a member, `n=result` lines
 * - `flashbutton|n` —a stage point on the first button of sprite n's Flash movie, or `none`
 */
internal class DirectorDebugBridge(private val runtime: () -> DirectorRuntime?) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val rt = runtime() ?: run { resultData = "error: no stage"; return }
        val parts = intent.getStringExtra("cmd")?.split('|') ?: run { resultData = "error: no cmd"; return }
        resultData = try { answer(rt, parts) } catch (e: Exception) { "error: ${e.javaClass.simpleName}: ${e.message}" }
    }

    private fun answer(rt: DirectorRuntime, parts: List<String>): String = when (parts[0]) {
        "label" -> "${rt.frame} ${rt.getMovieProp("frameLabel")?.asText()}"
        "sprites" -> (parts[1].toInt()..parts[2].toInt()).mapNotNull { n ->
            val s = rt.sprite(n)
            val m = s.member ?: return@mapNotNull null
            val b = s.bounds()
            if (b[0] >= rt.movie.stageWidth || b[1] >= rt.movie.stageHeight || b[2] <= 0 || b[3] <= 0) return@mapNotNull null
            "$n|${m.name}|${m.type}|${b[0]},${b[1]},${b[2]},${b[3]}|${s.blend}|${if (s.instances.isNotEmpty()) 1 else 0}"
        }.joinToString("\n")
        "hit" -> hitPoint(rt, parts[1].toInt())?.let { "${it.first} ${it.second}" } ?: "none"
        "text" -> rt.sprite(parts[1].toInt()).member?.let { m -> "${m.name}|${m.width}x${m.height}|${m.text.replace('\r', '/')}" } ?: "none"
        "global" -> rt.vm.global(parts[1]).asText()
        "prop" -> rt.vm.getObjectProp(target(rt, parts[1]), parts[2]).asText()
        "call" -> {
            val target = target(rt, parts[1])
            rt.vm.callMethod(target, parts[2], listOf(target) + parts.drop(3).map(::argument))?.asText() ?: "<void>"
        }
        "callsprites" -> (parts[1].toInt()..parts[2].toInt()).filter { rt.sprite(it).member != null }.joinToString("\n") { n ->
            val s = rt.sprite(n)
            "$n=" + (runCatching { rt.vm.callMethod(s, parts[3], listOf(s) + parts.drop(4).map(::argument))?.asText() }.getOrNull() ?: "<void>")
        }
        "flashbutton" -> flashButton(rt, parts[1].toInt())?.let { "${it.first} ${it.second}" } ?: "none"
        else -> "error: unknown command ${parts[0]}"
    }

    private fun target(rt: DirectorRuntime, spec: String): LingoValue = when {
        spec.startsWith("global:") -> rt.vm.global(spec.removePrefix("global:"))
        spec.startsWith("sprite:") -> rt.sprite(spec.removePrefix("sprite:").toInt())
        else -> throw IllegalArgumentException("target must be global:name or sprite:n")
    }

    private fun argument(text: String): LingoValue = when {
        text.matches(Regex("-?\\d+")) -> LingoValue.LInt(text.toInt())
        text.startsWith("#") -> LingoValue.LSymbol(text.drop(1))
        else -> LingoValue.LString(text)
    }

    /**
     * A point well inside the area where clicks reach sprite n (the reachable grid point
     * nearest the centroid of all of them), so a tap is not lost to rounding at an edge.
     */
    private fun hitPoint(rt: DirectorRuntime, n: Int): Pair<Int, Int>? {
        val sprite = rt.sprite(n)
        val b = sprite.bounds()
        val step = maxOf(2, maxOf(b[2] - b[0], b[3] - b[1]) / 40)
        val points = ArrayList<Pair<Int, Int>>()
        for (y in maxOf(0, b[1]) until minOf(rt.movie.stageHeight, b[3]) step step) {
            for (x in maxOf(0, b[0]) until minOf(rt.movie.stageWidth, b[2]) step step) {
                if (sprite.hit(x, y) && rt.activeSpriteAt(x, y) === sprite) points += x to y
            }
        }
        if (points.isEmpty()) return null
        val cx = points.sumOf { it.first } / points.size; val cy = points.sumOf { it.second } / points.size
        return points.minByOrNull { (x, y) -> (x - cx) * (x - cx) + (y - cy) * (y - cy) }
    }

    private fun flashButton(rt: DirectorRuntime, n: Int): Pair<Int, Int>? {
        val sprite = rt.sprite(n)
        val player = sprite.flash.current() ?: return null
        val b = sprite.bounds(); val r = player.movie.bounds
        if (b[2] <= b[0] || b[3] <= b[1]) return null
        for (y in b[1] until b[3] step 2) for (x in b[0] until b[2] step 2) {
            val mx = r.xMin + (x - b[0]) * r.width / (b[2] - b[0]); val my = r.yMin + (y - b[1]) * r.height / (b[3] - b[1])
            // A few pixels inside the button's edge.
            if (player.buttonAt(mx, my) != null) return (x + 6) to (y + 6)
        }
        return null
    }

    fun register(context: Context) {
        val filter = IntentFilter(ACTION)
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(this, filter, PERMISSION, null, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(this, filter, PERMISSION, null)
        }
    }

    companion object {
        const val ACTION = "org.rigorcore.caserecomp.DIRECTOR_DEBUG"
        /** Held by the adb shell and the system only. */
        const val PERMISSION = "android.permission.DUMP"
    }
}

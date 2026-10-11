package org.rigorcore.caserecomp.app

/** A touch-controlled visor has a persistent scene position, unlike a flashlight. */
internal class PrimeLensPosition {
    private var scene: Any? = null
    private var point = 330 to 300
    fun restore(identity: Any, saved: Pair<Int, Int>) {
        if (saved.first in 0 until 660 && saved.second in 0 until 600) {
            scene = identity
            point = saved
        }
    }
    fun update(identity: Any?, x: Int, y: Int): Pair<Int, Int>? {
        if (identity == null) { scene = null; return null }
        if (identity !== scene) {
            scene = identity
            point = 330 to 300
        } else if (x in 0 until 660 && y in 0 until 600) point = x to y
        return point
    }
}

package org.rigorcore.caserecomp.app

import org.rigorcore.caserecomp.director.DirectorRuntime
import org.rigorcore.caserecomp.director.DirectorStore
import org.rigorcore.caserecomp.lingo.LingoLiteralParser
import org.rigorcore.caserecomp.lingo.LingoValue.*
import org.rigorcore.caserecomp.lingo.literalText
import org.rigorcore.caserecomp.lingo.toInt

/** The original final box has no native board serialization. Keep its live puzzle data. */
internal object PrimeGemCheckpoint {
    const val KEY = "__case_recomp/prime-gem-board-v1"
    private val fields = listOf("m_gemSpriteArray", "m_circleArray", "m_state", "m_lastSetNum",
        "m_connectNum", "m_slideStart", "m_slideDelay", "m_slideSprite", "m_slideSpriteStartLoc",
        "m_slideSpriteEndLoc", "m_targetNum", "m_boolBoardSetup", "m_gemPullArray", "m_timeDelay",
        "m_timeLast", "m_boolAnimStarted")

    fun encode(data: LList): String = data.literalText().replace("<Void>", "VOID")

    fun save(rt: DirectorRuntime, store: DirectorStore) {
        val board = rt.vm.global("gGemBoard") as? LInstance ?: return
        val properties = LPropList(fields.mapNotNull { name ->
            board.properties.entries.firstOrNull { it.key.equals(name, true) }?.let { LSymbol(name) to it.value }
        }.toMutableList())
        val positions = LList((35..41).map { rt.sprite(it).getProp("loc") }.toMutableList())
        store.put(KEY, encode(LList(mutableListOf(LInt(rt.milliseconds().toInt()), properties, positions,
            LString(rt.sprite(30).member?.name.orEmpty()), rt.sprite(45).flash.frameValue))))
    }

    fun restore(rt: DirectorRuntime, store: DirectorStore) {
        val text = store.get(KEY)?.takeIf { it.length <= 16384 } ?: return
        val data = LingoLiteralParser.parseOrVoid(text) as? LList ?: return
        if (data.items.size !in 3..5) return
        val properties = data.items[1] as? LPropList ?: return
        val positions = data.items[2] as? LList ?: return
        if (positions.items.size != 7 || positions.items.any { it !is LPoint }) return
        val board = rt.vm.global("gGemBoard") as? LInstance ?: return
        val delta = rt.milliseconds().toInt() - data.items[0].toInt()
        for ((key, value) in properties.entries) {
            val name = (key as? LSymbol)?.name ?: continue
            if (name !in fields) continue
            rt.vm.setObjectProp(board, name, if (value != Void && name in listOf("m_slideStart", "m_timeLast")) LInt(value.toInt() + delta) else value)
        }
        positions.items.forEachIndexed { index, position -> rt.sprite(35 + index).setProp("loc", position) }
        val resetName = (data.items.getOrNull(3) as? LString)?.value
        if (resetName in listOf("resetA", "resetB")) rt.sprite(30).setProp("member", rt.member(LString(resetName!!), null))
        if (rt.vm.getObjectProp(board, "m_state") == LSymbol("endAnim")) {
            val time = rt.vm.global("gTimeManager")
            rt.vm.callMethod(time, "setFreezeTimer", listOf(time, LInt(1)))
            val completed = rt.vm.global("gPuzzCompleted")
            rt.vm.callMethod(completed, "lockInterface", listOf(completed))
            val game = rt.vm.global("gGameManager")
            rt.vm.callMethod(game, "setBoolDialogUp", listOf(game, LInt(1)))
            if (rt.vm.getObjectProp(board, "m_boolAnimStarted").toInt() != 0) {
                val flash = rt.sprite(45)
                (data.items.getOrNull(4) as? LInt)?.takeIf { it.value > 0 }?.let(flash.flash::goToFrame)
                rt.vm.callMethod(flash, "play", listOf(flash))
            }
        }
    }
}

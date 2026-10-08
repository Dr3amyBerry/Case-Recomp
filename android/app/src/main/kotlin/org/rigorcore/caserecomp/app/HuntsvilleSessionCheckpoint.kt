package org.rigorcore.caserecomp.app

import org.rigorcore.caserecomp.MiniJson
import org.rigorcore.caserecomp.director.DirectorRuntime
import org.rigorcore.caserecomp.director.DirectorStore
import org.rigorcore.caserecomp.lingo.LingoValue
import org.rigorcore.caserecomp.lingo.LingoValue.*
import org.rigorcore.caserecomp.lingo.asText
import org.rigorcore.caserecomp.lingo.toInt
import java.security.MessageDigest

/** Local metadata only. Native game handlers own inventory, hints, board and timer data. */
internal data class HuntsvilleBookmark(
    val userHash: String, val userIndex: Int, val round: Int, val label: String,
    val remaining: Int, val cumulative: Int, val reportCase: Int,
) {
    init {
        require(userHash.matches(Regex("[a-f0-9]{64}")))
        require(userIndex in 1..5 && round in 1..16)
        require(label == "map" || label == "metagame" || label == "mainmenu" || label.matches(Regex("loc([1-9]|1[0-9]|20)")))
        require(remaining in 0..86_400_000 && cumulative >= 0 && reportCase in 0..round)
    }
    fun encode() = """{"version":1,"userHash":"$userHash","userIndex":$userIndex,"round":$round,"label":"$label","remaining":$remaining,"cumulative":$cumulative,"reportCase":$reportCase}"""
    companion object {
        fun decode(json: String): HuntsvilleBookmark {
            require(json.length <= 1024)
            val m = MiniJson.parse(json) as? Map<*, *> ?: error("invalid bookmark")
            require(m.keys == setOf("version", "userHash", "userIndex", "round", "label", "remaining", "cumulative", "reportCase"))
            fun number(key: String): Int {
                val n = m[key] as? Long ?: error("invalid $key")
                require(n in Int.MIN_VALUE..Int.MAX_VALUE)
                return n.toInt()
            }
            require(number("version") == 1)
            return HuntsvilleBookmark(m["userHash"] as String, number("userIndex"), number("round"),
                m["label"] as String, number("remaining"), number("cumulative"), number("reportCase"))
        }
        fun hash(name: String) = MessageDigest.getInstance("SHA-256").digest(name.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}

/** Created only for a content-verified edition. No copied game code or arbitrary profile commands. */
internal class HuntsvilleSessionCheckpoint(
    private val runtime: DirectorRuntime,
    private val store: DirectorStore,
    private val atomic: (() -> Unit) -> Unit,
    private val onError: (Exception) -> Unit,
) {
    companion object { const val KEY = "__case_recomp/huntsville-bookmark-v1" }
    private var pending = store.get(KEY)?.let { runCatching { HuntsvilleBookmark.decode(it) }.getOrNull() }
    private var phase = 0
    private var observedLabel = ""
    private var stableFrames = 0
    private var loadedRemaining = 0
    private var loadedCumulative = 0
    private var lastSavedAt = runtime.milliseconds()
    private val label get() = runtime.getMovieProp("frameLabel")?.asText()?.lowercase().orEmpty()
    private fun global(name: String) = runtime.vm.global(name)
    private fun call(target: LingoValue, method: String, vararg args: LingoValue): LingoValue =
        runtime.vm.callMethod(target, method, listOf(target) + args) ?: error("checkpoint method unavailable: $method")
    private fun call(name: String, method: String, vararg args: LingoValue) = call(global(name), method, *args)

    fun afterFrame() {
        try {
            val current = label
            stableFrames = if (current == observedLabel) stableFrames + 1 else 0
            observedLabel = current
            val bookmark = pending
            if (bookmark != null) {
                restore(bookmark)
                return
            }
            if (runtime.milliseconds() - lastSavedAt >= 5000) {
                save()
                lastSavedAt = runtime.milliseconds()
            }
        } catch (e: Exception) {
            pending = null
            lastSavedAt = runtime.milliseconds()
            onError(e)
        }
    }

    fun save() {
        if (pending != null) return // Never replace the checkpoint with an intermediate boot frame.
        if (label !in listOf("map", "metagame", "mainmenu") && !label.matches(Regex("loc([1-9]|1[0-9]|20)"))) return
        runtime.vm.resetBudget()
        val user = global("gUserData") as? LInstance ?: return
        val name = call(user, "getUserName").asText()
        val round = call(user, "getPlayerRoundNum").toInt()
        if (name.isEmpty() || round !in 1..16) return
        val report = call("gGameManager", "getCaseReport")
        val reportCase = if (call(report, "getDisplayed").toInt() != 0)
            runtime.vm.getObjectProp(report, "m_caseNumDisplaying").toInt() else 0
        val bookmark = HuntsvilleBookmark(HuntsvilleBookmark.hash(name), call(user, "getUserIndex").toInt(), round,
            label, call("gTimeManager", "getTimeRemaining").toInt(), call("gTimeManager", "getCumulativeTime").toInt(), reportCase)
        atomic {
            val locationData = if (label.startsWith("loc")) runtime.vm.getObjectProp(global("gLocationManager"), "m_userDataArray") else Void
            call(user, "setPlayerData", locationData)
            // Persist the live board without exitGame(), which would reset an in-progress board.
            val board = runtime.vm.getObjectProp(global("gMetaGameBoard"), "m_gameBoardArray")
            call(user, "setPlayerPuzzleData", board)
            call("gPrefManager", "updatePrefs")
            store.put(KEY, bookmark.encode())
        }
    }

    private fun restore(bookmark: HuntsvilleBookmark) {
        // The first exitFrame initializes a new Score span. Let it finish before changing state.
        if (stableFrames < 2) return
        if (phase == 0) {
            if (label !in listOf("splash2", "mainmenu")) return
            runtime.vm.resetBudget()
            val user = global("gUserData")
            if (call(user, "getUserIndex").toInt() != bookmark.userIndex ||
                call(user, "getPlayerRoundNum").toInt() != bookmark.round ||
                HuntsvilleBookmark.hash(call(user, "getUserName").asText()) != bookmark.userHash) {
                pending = null
                store.remove(KEY)
                return
            }
            loadedRemaining = minOf(bookmark.remaining, call("gTimeManager", "getTimeRemaining").toInt())
            loadedCumulative = maxOf(bookmark.cumulative, call("gTimeManager", "getCumulativeTime").toInt())
            call("gGameManager", "setGameState", LSymbol(if (bookmark.label == "mainmenu") "fadeToMainMenu" else "fadeToMap"))
            phase = 1
            return
        }
        if (phase == 1) {
            if (bookmark.label == "mainmenu" && label == "mainmenu") { complete(bookmark); return }
            if (label != "map") return
            val report = call("gGameManager", "getCaseReport")
            call(report, "Hide")
            val dialog = call("gGameManager", "getGoodJobDialog")
            call(dialog, "Hide", LInt(1))
            if (bookmark.label == "map") { complete(bookmark); return }
            if (bookmark.label.startsWith("loc")) {
                call("gGameManager", "setLocationIndex", LInt(bookmark.label.removePrefix("loc").toInt()))
                call("gGameManager", "setGameState", LSymbol("fadeToLocation"))
            } else call("gGameManager", "setGameState", LSymbol("fadeToMetaGame"))
            phase = 2
            return
        }
        if (phase == 2 && label == bookmark.label) complete(bookmark)
    }

    private fun complete(bookmark: HuntsvilleBookmark) {
        call("gTimeManager", "setTimeRemaining", LInt(loadedRemaining))
        call("gTimeManager", "setCumulativeTime", LInt(loadedCumulative))
        runtime.vm.setObjectProp(global("gTimeManager"), "m_timeUpdateLast", LInt(runtime.milliseconds().toInt()))
        call("gTimeManager", "updateTimeDisplay")
        if (bookmark.reportCase > 0) call(call("gGameManager", "getCaseReport"), "display", LInt(bookmark.reportCase))
        pending = null
        lastSavedAt = runtime.milliseconds()
    }
}

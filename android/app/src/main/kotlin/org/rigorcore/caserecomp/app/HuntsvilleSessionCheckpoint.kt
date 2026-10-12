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
    val primeSuspects: Boolean = false,
) {
    init {
        require(userHash.matches(Regex("[a-f0-9]{64}")))
        require(userIndex in 1..5 && round in 1..if (primeSuspects) 23 else 16)
        require(if (primeSuspects) label in listOf("map", "mainmenu", "casebook", "metagame1", "metagame2", "metagame3", "metagame4") ||
            label.matches(Regex("loc([1-9]|[12][0-9])")) else
            label == "map" || label == "metagame" || label == "mainmenu" || label.matches(Regex("loc([1-9]|1[0-9]|20)")))
        require(remaining in 0..86_400_000 && cumulative >= 0 && reportCase in 0..round)
    }
    fun encode() = """{"version":${if (primeSuspects) 2 else 1},"userHash":"$userHash","userIndex":$userIndex,"round":$round,"label":"$label","remaining":$remaining,"cumulative":$cumulative,"reportCase":$reportCase}"""
    companion object {
        fun decode(json: String, primeSuspects: Boolean = false): HuntsvilleBookmark {
            require(json.length <= 1024)
            val m = MiniJson.parse(json) as? Map<*, *> ?: error("invalid bookmark")
            require(m.keys == setOf("version", "userHash", "userIndex", "round", "label", "remaining", "cumulative", "reportCase"))
            fun number(key: String): Int {
                val n = m[key] as? Long ?: error("invalid $key")
                require(n in Int.MIN_VALUE..Int.MAX_VALUE)
                return n.toInt()
            }
            require(number("version") == if (primeSuspects) 2 else 1)
            return HuntsvilleBookmark(m["userHash"] as String, number("userIndex"), number("round"),
                m["label"] as String, number("remaining"), number("cumulative"), number("reportCase"), primeSuspects)
        }
        fun hash(name: String) = MessageDigest.getInstance("SHA-256").digest(name.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}

/** Mid-scene save and restore of a title's session, driven by the Launcher each frame. */
internal interface SessionCheckpoint {
    fun afterFrame()
    fun save()
    /** A pointer position to restore once (Prime's X-ray lens). */
    fun takeRestoredPointer(): Pair<Int, Int>? = null
}

/** Created only for a content-verified edition. No copied game code or arbitrary profile commands. */
internal class HuntsvilleSessionCheckpoint(
    private val runtime: DirectorRuntime,
    private val store: DirectorStore,
    private val atomic: (() -> Unit) -> Unit,
    private val primeSuspects: Boolean = false,
    private val onError: (Exception) -> Unit,
) : SessionCheckpoint {
    companion object {
        const val KEY = "__case_recomp/huntsville-bookmark-v1"
        const val PRIME_KEY = "__case_recomp/prime-bookmark-v2"
        const val PRIME_POINTER_KEY = "__case_recomp/prime-pointer-v1"
    }
    private val bookmarkKey = if (primeSuspects) PRIME_KEY else KEY
    private var pending = store.get(bookmarkKey)?.let { runCatching { HuntsvilleBookmark.decode(it, primeSuspects) }.getOrNull() }
    private var phase = 0
    private var observedLabel = ""
    private var stableFrames = 0
    private var loadedRemaining = 0
    private var loadedCumulative = 0
    private var lastSavedAt = runtime.milliseconds()
    private var restoredPointer: Pair<Int, Int>? = null
    override fun takeRestoredPointer(): Pair<Int, Int>? = restoredPointer.also { restoredPointer = null }
    private val label get() = runtime.getMovieProp("frameLabel")?.asText()?.lowercase().orEmpty()
    private fun global(name: String) = runtime.vm.global(name)
    private fun call(target: LingoValue, method: String, vararg args: LingoValue): LingoValue =
        runtime.vm.callMethod(target, method, listOf(target) + args) ?: error("checkpoint method unavailable: $method")
    private fun call(name: String, method: String, vararg args: LingoValue) = call(global(name), method, *args)

    override fun afterFrame() {
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

    override fun save() {
        if (pending != null) return // Never replace the checkpoint with an intermediate boot frame.
        if (primeSuspects) { savePrimeProgress(); return }
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

    /** Original handlers serialize Prime's inventory, clocks, hints and puzzle data. */
    private fun savePrimeProgress() {
        if (label !in listOf("map", "mainmenu", "casebook", "metagame1", "metagame2", "metagame3", "metagame4") &&
            !label.matches(Regex("loc([1-9]|[12][0-9])"))) return
        runtime.vm.resetBudget()
        val user = global("gUserData") as? LInstance ?: return
        if (call(user, "getUserName").asText().isEmpty()) return
        val round = call(user, "getPlayerRoundNum").toInt()
        if (round !in 1..23) return
        val puzzleLabel = if (round == 22) "metagame4" else "metagame${call(user, "getPlayerMetaGameNum").toInt()}"
        // Native transitions can advance the round before leaving the old Score span.
        val resumeLabel = if (label.startsWith("metagame") && label != puzzleLabel) "map" else label
        val bookmark = HuntsvilleBookmark(HuntsvilleBookmark.hash(call(user, "getUserName").asText()),
            call(user, "getUserIndex").toInt(), round, resumeLabel,
            call("gTimeManager", "getTimeRemaining").toInt(), call("gTimeManager", "getCumulativeTime").toInt(), 0, true)
        atomic {
            if (label.startsWith("loc")) {
                call(user, "setPlayerLocData", runtime.vm.getObjectProp(global("gLocationManager"), "m_userDataArray"))
            } else call(user, "setPlayerData", Void)
            // The final gem box uses the separate checkpoint below instead of native board serialization.
            if (round < 22) {
                val boardName = when (call(user, "getPlayerMetaGameNum").toInt()) {
                    1 -> "gMetaGameBoard"; 2 -> "gMemoryGameBoard"; 3 -> "gWordGameBoard"
                    else -> error("unsupported Prime puzzle type")
                }
                var board = runtime.vm.getObjectProp(global(boardName), "m_gameBoardArray")
                if (boardName == "gWordGameBoard" && board is LList) {
                    board = LList(board.items.toMutableList().also { it.add(runtime.vm.getObjectProp(global(boardName), "m_storedWordData")) })
                }
                call(user, "setPlayerPuzzleData", board)
            } else call(user, "setPlayerPuzzleData", Void)
            call("gPrefManager", "updatePrefs")
            if (label == "metagame4") PrimeGemCheckpoint.save(runtime, store)
            else store.remove(PrimeGemCheckpoint.KEY)
            val visor = runtime.sprite(154)
            if (label.startsWith("loc") && visor.instances.any { it.script.name.equals("xray device sprite script", true) }) {
                store.put(PRIME_POINTER_KEY, "${visor.locH - 21},${visor.locV - 104}")
            } else store.remove(PRIME_POINTER_KEY)
            store.put(bookmarkKey, bookmark.encode())
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
                store.remove(bookmarkKey)
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
            if (!primeSuspects) call(call("gGameManager", "getCaseReport"), "Hide")
            val dialog = call("gGameManager", "getGoodJobDialog")
            call(dialog, "Hide", LInt(1))
            if (bookmark.label == "map") { complete(bookmark); return }
            if (bookmark.label.startsWith("loc")) {
                call("gGameManager", "setLocationIndex", LInt(bookmark.label.removePrefix("loc").toInt()))
                call("gGameManager", "setGameState", LSymbol("fadeToLocation"))
            } else call("gGameManager", "setGameState", LSymbol(if (bookmark.label == "casebook") "fadeToCaseBook" else "fadeToMetaGame"))
            phase = 2
            return
        }
        if (phase == 2 && label == bookmark.label) {
            if (primeSuspects && label == "metagame4") PrimeGemCheckpoint.restore(runtime, store)
            complete(bookmark)
        } else if (phase == 2 && label.startsWith("metagame") && bookmark.label.startsWith("metagame")) {
            // A changed puzzle type must not lock checkpoint saving forever.
            complete(bookmark)
        }
    }

    private fun complete(bookmark: HuntsvilleBookmark) {
        if (primeSuspects) {
            val coordinates = store.get(PRIME_POINTER_KEY)?.split(',')?.mapNotNull(String::toIntOrNull)
            if (coordinates?.size == 2) restoredPointer = coordinates[0] to coordinates[1]
        }
        call("gTimeManager", "setTimeRemaining", LInt(loadedRemaining))
        call("gTimeManager", "setCumulativeTime", LInt(loadedCumulative))
        runtime.vm.setObjectProp(global("gTimeManager"), "m_timeUpdateLast", LInt(runtime.milliseconds().toInt()))
        call("gTimeManager", "updateTimeDisplay")
        if (bookmark.reportCase > 0) call(call("gGameManager", "getCaseReport"), "display", LInt(bookmark.reportCase))
        pending = null
        lastSavedAt = runtime.milliseconds()
    }
}

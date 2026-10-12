package org.rigorcore.caserecomp.app

import org.rigorcore.caserecomp.MiniJson
import org.rigorcore.caserecomp.director.DirectorRuntime
import org.rigorcore.caserecomp.director.DirectorStore
import org.rigorcore.caserecomp.lingo.LingoValue
import org.rigorcore.caserecomp.lingo.LingoValue.LInstance
import org.rigorcore.caserecomp.lingo.LingoValue.LInt
import org.rigorcore.caserecomp.lingo.LingoValue.LSymbol
import org.rigorcore.caserecomp.lingo.asText
import org.rigorcore.caserecomp.lingo.toInt

/** The scene a Ravenhearst session was in; the game's own registry data holds everything else. */
internal data class RavenhearstBookmark(
    val userHash: String, val userIndex: Int, val level: Int, val label: String,
    val remaining: Int, val cumulative: Int,
) {
    init {
        require(userHash.matches(Regex("[a-f0-9]{64}")))
        require(userIndex in 1..5 && level in 1..20)
        require(label in LABELS || label.matches(Regex("loc([1-9]|[12][0-9]|3[0-2])")))
        require(remaining in 0..86_400_000 && cumulative >= 0)
    }
    fun encode() = """{"version":1,"userHash":"$userHash","userIndex":$userIndex,"level":$level,"label":"$label","remaining":$remaining,"cumulative":$cumulative}"""
    companion object {
        val LABELS = listOf("map", "door", "metagame", "mainmenu")
        fun decode(json: String): RavenhearstBookmark {
            require(json.length <= 1024)
            val m = MiniJson.parse(json) as? Map<*, *> ?: error("invalid bookmark")
            require(m.keys == setOf("version", "userHash", "userIndex", "level", "label", "remaining", "cumulative"))
            fun number(key: String): Int = (m[key] as? Long)?.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt() ?: error("invalid $key")
            require(number("version") == 1)
            return RavenhearstBookmark(m["userHash"] as String, number("userIndex"), number("level"), m["label"] as String,
                number("remaining"), number("cumulative"))
        }
    }
}

/**
 * Ravenhearst saves its player data to the registry only at its own points (leaving a room,
 * finishing a level). This saves the live room inventory and clocks through the game's own
 * handlers every few seconds, on pause and on exit, and returns the player to the same
 * room, locked door or diary puzzle. Neither the lock puzzles nor the jigsaw keep their
 * pieces in the original, so those restart on return, as they do when leaving them there.
 */
internal class RavenhearstCheckpoint(
    private val runtime: DirectorRuntime,
    private val store: DirectorStore,
    private val atomic: (() -> Unit) -> Unit,
    private val onError: (Exception) -> Unit,
) : SessionCheckpoint {
    companion object { const val KEY = "__case_recomp/ravenhearst-bookmark-v1" }

    private var pending = store.get(KEY)?.let { runCatching { RavenhearstBookmark.decode(it) }.getOrNull() }
    private var phase = 0
    private var observedLabel = ""
    private var stableFrames = 0
    private var phaseFrames = 0
    private var lastSavedAt = runtime.milliseconds()
    private val label get() = runtime.getMovieProp("frameLabel")?.asText()?.lowercase().orEmpty()
    private fun global(name: String) = runtime.vm.global(name)
    private fun call(target: LingoValue, method: String, vararg args: LingoValue): LingoValue =
        runtime.vm.callMethod(target, method, listOf(target) + args) ?: error("checkpoint method unavailable: $method")
    private fun call(name: String, method: String, vararg args: LingoValue) = call(global(name), method, *args)
    private fun state() = (call("gGameManager", "getGameState") as? LSymbol)?.name?.lowercase().orEmpty()

    override fun afterFrame() {
        try {
            val current = label
            stableFrames = if (current == observedLabel) stableFrames + 1 else 0
            observedLabel = current
            pending?.let { restore(it); return }
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
        val scene = label
        if (scene !in RavenhearstBookmark.LABELS && !scene.matches(Regex("loc([1-9]|[12][0-9]|3[0-2])"))) return
        // Only settled scenes: not while fading in or out, and not mid item grab.
        if (state() !in listOf("map", "location", "lockeddoor", "metagame", "mainmenu")) return
        runtime.vm.resetBudget()
        val user = global("gUserData") as? LInstance ?: return
        val name = call(user, "getPlayerData", LSymbol("userName")).asText()
        val level = call(user, "getPlayerData", LSymbol("currentLevel")).toInt()
        if (name.isEmpty() || level !in 1..20) return
        val locations = global("gLocationManager")
        if (scene.startsWith("loc") && call(locations, "getBoolGrabInProgress").toInt() != 0) return
        val bookmark = RavenhearstBookmark(HuntsvilleBookmark.hash(name), call(user, "getUserIndex").toInt(), level, scene,
            call("gTimeManager", "getTimeRemaining").toInt(), call("gTimeManager", "getCumulativeTime").toInt())
        atomic {
            call(user, "updateGameTime")
            // setPlayerItemData / setPlayerData write the player's record to the registry themselves.
            if (scene.startsWith("loc")) call(user, "setPlayerItemData", runtime.vm.getObjectProp(locations, "m_userDataArray"))
            else call(user, "setPlayerData", LInt(0), call(user, "getPlayerData", LInt(0)))
            call("gPrefManager", "updatePrefs")
            store.put(KEY, bookmark.encode())
        }
    }

    private fun restore(bookmark: RavenhearstBookmark) {
        // The first exitFrame initializes a new Score span. Let it finish before changing state.
        if (stableFrames < 2) return
        if (++phaseFrames > 3000) { pending = null; return } // never block saving forever
        when (phase) {
            0 -> {
                if (label != "mainmenu") return
                if (state() != "mainmenu" || call("gGameManager", "getBoolDialogUp").toInt() != 0) return
                runtime.vm.resetBudget()
                val user = global("gUserData")
                if (call(user, "getUserIndex").toInt() != bookmark.userIndex ||
                    call(user, "getPlayerData", LSymbol("currentLevel")).toInt() != bookmark.level ||
                    HuntsvilleBookmark.hash(call(user, "getPlayerData", LSymbol("userName")).asText()) != bookmark.userHash) {
                    pending = null
                    store.remove(KEY)
                    return
                }
                if (bookmark.label == "mainmenu") { complete(bookmark); return }
                // The menu's own Play: it chooses map, diary or the diary puzzle from the saved record.
                call("gGameManager", "gameStart")
                phase = 1; phaseFrames = 0
            }
            1 -> {
                if (call("gGameManager", "getBoolDialogUp").toInt() != 0) return
                when {
                    label == bookmark.label -> complete(bookmark)
                    label == "map" && state() == "map" -> {
                        when {
                            bookmark.label == "map" -> { complete(bookmark); return }
                            bookmark.label.startsWith("loc") -> {
                                call("gGameManager", "setLocationIndex", LInt(bookmark.label.removePrefix("loc").toInt()))
                                call("gGameManager", "setGameState", LSymbol("fadeToLocation"))
                            }
                            bookmark.label == "door" -> call("gGameManager", "setGameState", LSymbol("fadeToLockedDoor"))
                            else -> call("gGameManager", "setGameState", LSymbol("fadeToMetaGame"))
                        }
                        phase = 2; phaseFrames = 0
                    }
                    // The new level's diary page shows first, as after any level change: then stop.
                    label == "diary" -> complete(bookmark)
                }
            }
            2 -> if (label == bookmark.label) complete(bookmark)
        }
    }

    private fun complete(bookmark: RavenhearstBookmark) {
        // The clocks the checkpoint saw; time while the app was closed is not counted.
        call("gTimeManager", "setTimeRemaining", LInt(bookmark.remaining))
        call("gTimeManager", "setCumulativeTime", LInt(maxOf(bookmark.cumulative, call("gTimeManager", "getCumulativeTime").toInt())))
        runtime.vm.setObjectProp(global("gTimeManager"), "m_timeUpdateLast", LInt(runtime.milliseconds().toInt()))
        call("gTimeManager", "updateTimeDisplay")
        pending = null
        lastSavedAt = runtime.milliseconds()
    }
}

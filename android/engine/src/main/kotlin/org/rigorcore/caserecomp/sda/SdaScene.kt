package org.rigorcore.caserecomp.sda

/**
 * Pixel source abstraction for SDA hit testing and alpha channel checks.
 */
interface SdaPixelSource {
    val width: Int
    val height: Int
    fun getAlpha(px: Int, py: Int): Int
    /** Required only by raster composition; alpha-only sources must fail explicitly. */
    fun getArgb(px: Int, py: Int): Int = throw UnsupportedOperationException("RGB pixels unavailable")
    val nativeImage: Any? get() = null
}

/**
 * Memory buffer backed pixel source for unit testing and offline rendering.
 */
class SdaBufferPixelSource(
    override val width: Int,
    override val height: Int,
    private val alphas: ByteArray,
) : SdaPixelSource {
    override fun getAlpha(px: Int, py: Int): Int {
        if (px !in 0 until width || py !in 0 until height) return 0
        return alphas[py * width + px].toInt() and 0xFF
    }
}

/**
 * An eyespy object in an SDA scene.
 */
class SdaSprite(
    val identity: String,
    val x: Int,
    val y: Int,
    val image: SdaPixelSource,
    var found: Boolean = false,
    var motion: SdaFoundMotion? = null,
    var hidden: Boolean = false,
) {
    /**
     * 0x004251f0 / 0x00473e0e: half-open rectangle bounding box, then nonzero alpha pixel check.
     */
    fun hit(clickX: Int, clickY: Int): Boolean {
        val px = clickX - x
        val py = clickY - y
        return !found && !hidden &&
                px in 0 until image.width &&
                py in 0 until image.height &&
                image.getAlpha(px, py) != 0
    }
}

sealed class SdaClickResult {
    object Outside : SdaClickResult()
    data class Found(val id: String, val gain: Int) : SdaClickResult()
    data class Miss(val penalty: Boolean) : SdaClickResult()
}

data class SdaSpriteState(
    val found: Boolean,
    val hidden: Boolean,
    val motionDelay: Float?,
    val motionVelocity: Float?,
    val motionScale: Float?,
    val motionPhase: Int?,
    val motionPulses: Int?,
    val motionRemoved: Boolean?,
    val motionX: Int?,
    val motionY: Int?,
)

data class SdaRowState(
    val set: List<String>,
    val alpha: Float,
    val phase: Int,
    val removed: Boolean,
)

data class SdaSceneState(
    val sceneName: String,
    val activeSets: List<List<String>>,
    val candidateSets: List<List<String>>,
    val deckCursor: Int?,
    val deckOrder: List<List<String>>?,
    val scorePoints: Int,
    val scoreFastChain: Boolean,
    val scoreFastBonus: Int,
    val scoreMisses: List<Long>,
    val elapsed: Float,
    val sinceFound: Float,
    val foundOrder: List<String>,
    val objects: Map<String, SdaSpriteState>,
    val rows: List<SdaRowState>,
    val history: List<HistoryMark>,
    val historyVariant: Int?,
    val historyPruned: Boolean,
) {
    fun toJson(): String {
        val map = mutableMapOf<String, Any?>()
        map["sceneName"] = sceneName
        map["activeSets"] = activeSets
        map["candidateSets"] = candidateSets
        map["deckCursor"] = deckCursor
        map["deckOrder"] = deckOrder
        map["scorePoints"] = scorePoints
        map["scoreFastChain"] = scoreFastChain
        map["scoreFastBonus"] = scoreFastBonus
        map["scoreMisses"] = scoreMisses
        map["elapsed"] = elapsed
        map["sinceFound"] = sinceFound
        map["foundOrder"] = foundOrder
        val objsMap = mutableMapOf<String, Any?>()
        for ((k, v) in objects) {
            objsMap[k] = mapOf(
                "found" to v.found,
                "hidden" to v.hidden,
                "motionDelay" to v.motionDelay,
                "motionVelocity" to v.motionVelocity,
                "motionScale" to v.motionScale,
                "motionPhase" to v.motionPhase,
                "motionPulses" to v.motionPulses,
                "motionRemoved" to v.motionRemoved,
                "motionX" to v.motionX,
                "motionY" to v.motionY,
            )
        }
        map["objects"] = objsMap
        map["rows"] = rows.map { r ->
            mapOf("set" to r.set, "alpha" to r.alpha, "phase" to r.phase, "removed" to r.removed)
        }
        map["history"] = history.map { h ->
            mapOf("text" to h.text, "x" to h.x, "y" to h.y)
        }
        map["historyVariant"] = historyVariant
        map["historyPruned"] = historyPruned
        return org.rigorcore.caserecomp.MiniJson.canonical(map)
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(text: String): SdaSceneState {
            val raw = org.rigorcore.caserecomp.MiniJson.parse(text) as Map<String, Any?>
            val sceneName = raw["sceneName"] as String
            val activeSets = (raw["activeSets"] as List<List<String>>)
            val candidateSets = (raw["candidateSets"] as List<List<String>>)
            val deckCursor = (raw["deckCursor"] as? Number)?.toInt()
            val deckOrder = raw["deckOrder"] as? List<List<String>>
            val scorePoints = (raw["scorePoints"] as Number).toInt()
            val scoreFastChain = raw["scoreFastChain"] as Boolean
            val scoreFastBonus = (raw["scoreFastBonus"] as Number).toInt()
            val scoreMisses = (raw["scoreMisses"] as List<Number>).map { it.toLong() }
            val elapsed = (raw["elapsed"] as Number).toFloat()
            val sinceFound = (raw["sinceFound"] as Number).toFloat()
            val foundOrder = (raw["foundOrder"] as List<String>)
            val rawObjs = raw["objects"] as Map<String, Map<String, Any?>>
            val objects = rawObjs.mapValues { (_, v) ->
                SdaSpriteState(
                    found = v["found"] as Boolean,
                    hidden = v["hidden"] as Boolean,
                    motionDelay = (v["motionDelay"] as? Number)?.toFloat(),
                    motionVelocity = (v["motionVelocity"] as? Number)?.toFloat(),
                    motionScale = (v["motionScale"] as? Number)?.toFloat(),
                    motionPhase = (v["motionPhase"] as? Number)?.toInt(),
                    motionPulses = (v["motionPulses"] as? Number)?.toInt(),
                    motionRemoved = v["motionRemoved"] as? Boolean,
                    motionX = (v["motionX"] as? Number)?.toInt(),
                    motionY = (v["motionY"] as? Number)?.toInt(),
                )
            }
            val rawRows = raw["rows"] as List<Map<String, Any?>>
            val rows = rawRows.map { r ->
                SdaRowState(
                    set = r["set"] as List<String>,
                    alpha = (r["alpha"] as Number).toFloat(),
                    phase = (r["phase"] as Number).toInt(),
                    removed = r["removed"] as Boolean,
                )
            }
            val rawHistory = raw["history"] as List<Map<String, Any?>>
            val history = rawHistory.map { h ->
                HistoryMark(
                    text = h["text"] as String,
                    x = (h["x"] as Number).toInt(),
                    y = (h["y"] as Number).toInt(),
                )
            }
            val historyVariant = (raw["historyVariant"] as? Number)?.toInt()
            val historyPruned = raw["historyPruned"] as Boolean

            return SdaSceneState(
                sceneName = sceneName,
                activeSets = activeSets,
                candidateSets = candidateSets,
                deckCursor = deckCursor,
                deckOrder = deckOrder,
                scorePoints = scorePoints,
                scoreFastChain = scoreFastChain,
                scoreFastBonus = scoreFastBonus,
                scoreMisses = scoreMisses,
                elapsed = elapsed,
                sinceFound = sinceFound,
                foundOrder = foundOrder,
                objects = objects,
                rows = rows,
                history = history,
                historyVariant = historyVariant,
                historyPruned = historyPruned,
            )
        }
    }
}

/**
 * Complete SDA Scene runtime:
 * - Active target batching, deck rotation, and retirement.
 * - Accurate click hit testing (bounding box + alpha channel).
 * - Animated found-motion lifecycle updates.
 * - Active target rows fade-out tracking.
 * - History marks recording, replaying, and pruning.
 * - Complete state snapshot and restoration.
 */
data class SdaSceneCollectible(val definition:SdaXuiCollectible,val sprite:SdaSprite)

class SdaScene(
    val objects: Map<String, SdaSprite>,
    val targetSets: List<List<String>>,
    val captions: Map<List<String>, List<String>> = emptyMap(),
    seed: Long? = null,
    val name: String = "SCENE_DEFAULT",
    history: List<HistoryMark> = emptyList(),
    historyVariant: Int? = null,
    prunePreviousHistory: Boolean = false,
    val drawOrder: List<SdaSprite> = objects.values.toList(),
    val collectibles:List<SdaSceneCollectible> = emptyList(),
) {
    val sceneIdentity: String = name.substringBeforeLast('.').removePrefix("SCENE_").lowercase()
    val score = SdaScore()
    var elapsed: Float = 0f
    var sinceFound: Float = 0f
    val foundOrder = mutableListOf<String>()
    var candidateSets: List<List<String>> = targetSets
    var historyPruned: Boolean = false
    var historyVariant: Int? = historyVariant
    val history: MutableList<HistoryMark> = history.toMutableList()

    init {
        if (prunePreviousHistory) {
            val (pruned, wasPruned) = pruneSceneHistory(candidateSets, captions, this.history, sceneIdentity)
            this.history.clear()
            this.history.addAll(pruned)
            this.historyPruned = wasPruned
        }
        if (this.history.isNotEmpty() && historyVariant != null) {
            val rects = objects.mapValues { (_, sprite) ->
                SdaRect(sprite.x, sprite.y, sprite.image.width, sprite.image.height)
            }
            val replay = replayHistory(candidateSets, captions, rects, this.history, sceneIdentity, historyVariant)
            candidateSets = replay.candidates
            for (id in replay.hidden) {
                objects[id]?.hidden = true
            }
            for (id in replay.retired) {
                val sprite = objects[id]
                if (sprite != null) {
                    sprite.found = true
                    val m = SdaFoundMotion(sprite.x, sprite.y, sprite.image.width, sprite.image.height)
                    m.removed = true
                    m.y = -sprite.image.height
                    sprite.motion = m
                }
            }
        }
    }

    val deck: SdaTargetDeck<List<String>>? = if (seed != null) SdaTargetDeck(candidateSets) else null
    var activeSets: List<List<String>> = if (deck != null && seed != null) deck.nextBatch(seed) else candidateSets
    var targets: List<String> = activeSets.flatten()
    val rows: MutableMap<List<String>, SdaTargetRow> = mutableMapOf()

    init {
        for (ids in activeSets) {
            rows[ids] = SdaTargetRow()
        }
    }

    val batchRetired: Boolean
        get() = rows.isNotEmpty() && rows.values.all { it.removed }

    fun nextBatch(seed: Long): List<List<String>> {
        require(deck != null) { "explicit diagnostic sets do not have a native selection deck" }
        require(batchRetired) { "finish all active objects and their retirement animations first" }
        val selected = deck.nextBatch(seed)
        activeSets = selected
        targets = selected.flatten()
        rows.clear()
        for (ids in selected) {
            rows[ids] = SdaTargetRow()
        }
        return selected
    }

    fun click(x: Int, y: Int): SdaClickResult {
        if (x !in 0 until 800 || y !in 0 until 600) {
            return SdaClickResult.Outside
        }
        for (identity in targets) {
            val sprite = objects[identity] ?: continue
            if (sprite.hit(x, y)) {
                if (historyVariant != null) {
                    val ids = activeSets.firstOrNull { identity in it }
                    if (ids != null) {
                        val count = ids.count { objects[it]?.found == true }
                        val captionList = captions[ids]
                        val caption = if (captionList != null && count < captionList.size) captionList[count] else identity
                        history.add(HistoryMark.create(caption, sceneIdentity, historyVariant!!, x, y))
                    }
                }
                sprite.found = true
                sprite.motion = SdaFoundMotion(sprite.x, sprite.y, sprite.image.width, sprite.image.height)
                foundOrder.add(identity)
                val isFast = sinceFound < 3.0f
                val gain = score.found(isFast)
                sinceFound = 0f
                return SdaClickResult.Found(identity, gain)
            }
        }
        val penalty = score.miss((elapsed * 1000f).toLong())
        return SdaClickResult.Miss(penalty)
    }

    fun advance(seconds: Float) {
        require(seconds.isFinite() && seconds >= 0f) { "invalid duration" }
        elapsed += seconds
        sinceFound += seconds
        for (identity in foundOrder) {
            objects[identity]?.motion?.update(seconds)
        }
        for ((ids, row) in rows) {
            val retired = ids.all { objects[it]?.motion?.removed == true }
            row.update(retired)
        }
    }

    fun remainingCaptions(): List<String> {
        val result = mutableListOf<String>()
        for (ids in activeSets) {
            val foundCount = ids.count { objects[it]?.found == true }
            if (foundCount < ids.size) {
                val captionList = captions[ids]
                if (captionList != null && foundCount < captionList.size) {
                    result.add(captionList[foundCount])
                }
            }
        }
        return result
    }

    fun savedCaptions(): List<String> {
        val result = mutableListOf<String>()
        for (ids in activeSets) {
            val removedCount = ids.count { objects[it]?.motion?.removed == true }
            if (removedCount < ids.size) {
                val captionList = captions[ids]
                if (captionList != null && removedCount < captionList.size) {
                    result.add(captionList[removedCount])
                }
            }
        }
        return result
    }

    fun snapshot(): SdaSceneState {
        val objStates = objects.mapValues { (_, s) ->
            SdaSpriteState(
                found = s.found,
                hidden = s.hidden,
                motionDelay = s.motion?.delay,
                motionVelocity = s.motion?.velocity,
                motionScale = s.motion?.scale,
                motionPhase = s.motion?.phase,
                motionPulses = s.motion?.pulses,
                motionRemoved = s.motion?.removed,
                motionX = s.motion?.x,
                motionY = s.motion?.y,
            )
        }
        val rowStates = rows.map { (ids, r) ->
            SdaRowState(set = ids, alpha = r.alpha, phase = r.phase, removed = r.removed)
        }
        return SdaSceneState(
            sceneName = name,
            activeSets = activeSets,
            candidateSets = candidateSets,
            deckCursor = deck?.cursor,
            deckOrder = deck?.order,
            scorePoints = score.points,
            scoreFastChain = score.fastChain,
            scoreFastBonus = score.fastBonus,
            scoreMisses = score.misses.toList(),
            elapsed = elapsed,
            sinceFound = sinceFound,
            foundOrder = foundOrder.toList(),
            objects = objStates,
            rows = rowStates,
            history = history.toList(),
            historyVariant = historyVariant,
            historyPruned = historyPruned,
        )
    }

    fun restore(state: SdaSceneState) {
        require(state.sceneName == name) { "state scene name mismatch" }
        candidateSets = state.candidateSets
        activeSets = state.activeSets
        targets = activeSets.flatten()

        score.points = state.scorePoints
        score.fastChain = state.scoreFastChain
        score.fastBonus = state.scoreFastBonus
        score.misses.clear()
        score.misses.addAll(state.scoreMisses)

        elapsed = state.elapsed
        sinceFound = state.sinceFound

        history.clear()
        history.addAll(state.history)
        historyVariant = state.historyVariant
        historyPruned = state.historyPruned

        foundOrder.clear()
        foundOrder.addAll(state.foundOrder)

        for ((id, sState) in state.objects) {
            val sprite = objects[id] ?: continue
            sprite.found = sState.found
            sprite.hidden = sState.hidden
            if (sState.motionRemoved != null) {
                val motion = SdaFoundMotion(sprite.x, sprite.y, sprite.image.width, sprite.image.height)
                motion.delay = sState.motionDelay ?: motion.delay
                motion.velocity = sState.motionVelocity ?: motion.velocity
                motion.scale = sState.motionScale ?: motion.scale
                motion.phase = sState.motionPhase ?: motion.phase
                motion.pulses = sState.motionPulses ?: motion.pulses
                motion.removed = sState.motionRemoved
                motion.x = sState.motionX ?: motion.x
                motion.y = sState.motionY ?: motion.y
                sprite.motion = motion
            } else {
                sprite.motion = null
            }
        }

        rows.clear()
        for (rState in state.rows) {
            rows[rState.set] = SdaTargetRow(rState.alpha, rState.phase, rState.removed)
        }
    }
}

package org.rigorcore.caserecomp.sda

import org.rigorcore.caserecomp.MiniJson

enum class SdaCampaignPhase {
    MAP,
    SCENE,
    SCENE_COMPLETE,
    OBJECTS_COMPLETE,
    BONUS,
    LEVEL_COMPLETE,
    FINALE_1,
    FINALE_2,
    FINALE_3,
    CAMPAIGN_COMPLETE,
    TIMEOUT,
}

data class SdaLevelSummary(
    val clue: Int,
    val levelIndex: Int,
    val title: String,
    val elapsed: Float,
    val remainingTime: Float,
    val speedBonus: Int,
    val points: Int,
    val rank: String,
    val isLastLevel: Boolean,
)

data class SdaCampaignState(
    val levelIndex: Int,
    val seed: Long,
    val points: Int,
    val phase: String,
    val currentScene: String?,
    val completedObjects: Int,
    val clockLimit: Float,
    val clockElapsed: Float,
    val totalElapsed: Float,
    val scenes: Map<String, SdaSceneState>,
    val countedSets: Map<String, List<List<String>>>,
    val bonusGameState: Map<String, Any?>?,
) {
    fun toJson(): String {
        val map = mapOf(
            "levelIndex" to levelIndex.toLong(),
            "seed" to seed,
            "points" to points.toLong(),
            "phase" to phase,
            "currentScene" to currentScene,
            "completedObjects" to completedObjects.toLong(),
            "clockLimit" to clockLimit,
            "clockElapsed" to clockElapsed,
            "totalElapsed" to totalElapsed,
            "scenes" to scenes.mapValues { MiniJson.parse(it.value.toJson()) },
            "countedSets" to countedSets,
            "bonusGameState" to bonusGameState,
        )
        return MiniJson.canonical(map)
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(text: String): SdaCampaignState {
            val raw = MiniJson.parse(text) as Map<String, Any?>
            val scenesRaw = raw["scenes"] as? Map<String, Any?> ?: emptyMap()
            val parsedScenes = scenesRaw.mapValues { (_, v) ->
                SdaSceneState.fromJson(MiniJson.canonical(v))
            }
            val countedRaw = raw["countedSets"] as? Map<String, List<List<String>>> ?: emptyMap()

            return SdaCampaignState(
                levelIndex = (raw["levelIndex"] as Number).toInt(),
                seed = (raw["seed"] as Number).toLong(),
                points = (raw["points"] as Number).toInt(),
                phase = raw["phase"] as String,
                currentScene = raw["currentScene"] as? String,
                completedObjects = (raw["completedObjects"] as Number).toInt(),
                clockLimit = (raw["clockLimit"] as Number).toFloat(),
                clockElapsed = (raw["clockElapsed"] as Number).toFloat(),
                totalElapsed = (raw["totalElapsed"] as Number).toFloat(),
                scenes = parsedScenes,
                countedSets = countedRaw,
                bonusGameState = raw["bonusGameState"] as? Map<String, Any?>,
            )
        }
    }
}

/**
 * Generic SDA Campaign session runner.
 * Manages levels progression, investigative map, scenes, scoring, bonuses, and campaign finale.
 */
class SdaCampaign(
    val levels: List<SdaLevel>,
    var seed: Long = 0L,
    var levelIndex: Int = 0,
) {
    init {
        require(levels.isNotEmpty()) { "campaign must have at least one level" }
        require(levelIndex in levels.indices) { "invalid level index" }
    }

    val currentLevel: SdaLevel
        get() = levels[levelIndex]

    var clock = SdaClock(limit = currentLevel.time)
    var points: Int = 0
    var completedObjects: Int = 0
    var phase: SdaCampaignPhase = SdaCampaignPhase.MAP
    var currentSceneName: String? = null
    val counted: MutableMap<String, MutableSet<List<String>>> = mutableMapOf()
    val scenes: MutableMap<String, SdaScene> = mutableMapOf()
    var bonusGame: SdaBonusGame? = null
    var totalElapsed: Float = 0f

    companion object {
        val RANKS = listOf(
            "Sabueso novato",
            "Aspirante a investigador",
            "Rastreador",
            "Investigador amateur",
            "Sabueso",
            "Inspector",
            "Rastreador veterano",
            "Indagador",
            "Husmeador",
            "Detective",
            "Rastreador astuto",
            "SÃºper indagador",
            "Detective explosivo",
            "As investigador",
            "P.I. Maestro",
        )
    }

    val rank: String
        get() {
            val idx = ((levelIndex.toFloat() / levels.size.toFloat()) * RANKS.size).toInt().coerceIn(0, RANKS.size - 1)
            return RANKS[idx]
        }

    val remainingObjects: Int
        get() = maxOf(0, currentLevel.objects - completedObjects)

    val currentScene: SdaScene?
        get() = currentSceneName?.let { scenes[it] }

    fun enterScene(name: String, content: SdaContent): SdaScene {
        require(phase == SdaCampaignPhase.MAP || phase == SdaCampaignPhase.SCENE) { "scene is unavailable in $phase" }
        val normalized = name.lowercase().removePrefix("scene_").substringBeforeLast('.')
        require(normalized in currentLevel.scenes) { "scene '$name' not in current level scenes" }

        val scene = scenes.getOrPut(normalized) {
            val resName = "SCENE_${normalized.uppercase()}.MSL"
            content.loadScene(resName, seed = seed)
        }
        counted.getOrPut(normalized) { mutableSetOf() }

        currentSceneName = normalized
        phase = SdaCampaignPhase.SCENE

        // Carry over points and reset scene streaks
        scene.score.points = points
        scene.score.fastChain = false
        scene.score.fastBonus = 2500
        scene.score.misses.clear()
        scene.sinceFound = 0f

        if (scene.batchRetired) {
            phase = SdaCampaignPhase.SCENE_COMPLETE
        }
        return scene
    }

    fun toInvestigationMap() {
        require(phase == SdaCampaignPhase.SCENE || phase == SdaCampaignPhase.SCENE_COMPLETE) { "not in a scene" }
        currentScene?.let { sc ->
            points = sc.score.points
        }
        phase = SdaCampaignPhase.MAP
        currentSceneName = null
    }

    fun confirmSceneComplete() {
        require(phase == SdaCampaignPhase.SCENE_COMPLETE) { "cannot confirm when not scene complete" }
        toInvestigationMap()
    }

    fun advance(seconds: Float) {
        if (phase == SdaCampaignPhase.SCENE) {
            val sc = currentScene ?: return
            val clockEvents = clock.advance(seconds)
            sc.advance(seconds)

            // Check active sets retired
            val sceneCounted = counted.getOrPut(currentSceneName!!) { mutableSetOf() }
            for (ids in sc.activeSets) {
                if (ids !in sceneCounted && sc.rows[ids]?.removed == true) {
                    sceneCounted.add(ids)
                    completedObjects += 1 // One completed objective row, including compound sets.
                }
            }
            points = sc.score.points

            if (remainingObjects == 0) {
                phase = SdaCampaignPhase.OBJECTS_COMPLETE
            } else if (SdaClockEvent.Timeout in clockEvents) {
                phase = SdaCampaignPhase.TIMEOUT
            } else if (sc.batchRetired) {
                phase = SdaCampaignPhase.SCENE_COMPLETE
            }
        } else if (phase in listOf(SdaCampaignPhase.BONUS, SdaCampaignPhase.FINALE_1, SdaCampaignPhase.FINALE_2, SdaCampaignPhase.FINALE_3)) {
            val clockEvents = clock.advance(seconds)
            if (SdaClockEvent.Timeout in clockEvents) {
                phase = SdaCampaignPhase.TIMEOUT
            }
        }
    }

    fun clickScene(x: Int, y: Int): SdaClickResult {
        if (phase != SdaCampaignPhase.SCENE) return SdaClickResult.Outside
        val sc = currentScene ?: return SdaClickResult.Outside
        val res = sc.click(x, y)
        points = sc.score.points
        return res
    }

    fun startBonus(content: SdaContent) {
        require(phase == SdaCampaignPhase.OBJECTS_COMPLETE) { "cannot start bonus before completing objects" }
        // Resolve first: an invalid resource must leave the completion checkpoint intact.
        val loaded = if (currentLevel.bonus.isNotEmpty())
            SdaBonusLoader.load(content, currentLevel.bonus, seed, currentLevel.bonusImage) else null
        currentSceneName = null
        bonusGame = loaded
        phase = if (loaded != null) SdaCampaignPhase.BONUS else SdaCampaignPhase.LEVEL_COMPLETE
    }

    fun clickBonus(x: Int, y: Int, clockwise: Boolean = false): Boolean {
        if (phase !in listOf(SdaCampaignPhase.BONUS, SdaCampaignPhase.FINALE_1,
                SdaCampaignPhase.FINALE_2, SdaCampaignPhase.FINALE_3)) return false
        val bg = bonusGame ?: return false
        fun placementScore() = when (bg) {
            is SdaTileRotGame -> bg.linePoints
            is SdaTileSwapGame -> bg.placementPoints
            else -> 0
        }
        val beforeLines = placementScore()
        val moved = if (bg is SdaTileRotGame) bg.rotatePixel(x, y, clockwise) else bg.clickPixel(x, y)
        points += placementScore() - beforeLines
        if (bg.isSolved) {
            points += bg.points
            when (phase) {
                SdaCampaignPhase.BONUS -> phase = SdaCampaignPhase.LEVEL_COMPLETE
                SdaCampaignPhase.FINALE_1 -> {
                    phase = SdaCampaignPhase.FINALE_2
                    bonusGame = SdaMasterRiddleGame(stage = 2, seed = seed)
                }
                SdaCampaignPhase.FINALE_2 -> {
                    phase = SdaCampaignPhase.FINALE_3
                    bonusGame = SdaMasterRiddleGame(stage = 3, seed = seed)
                }
                SdaCampaignPhase.FINALE_3 -> {
                    phase = SdaCampaignPhase.CAMPAIGN_COMPLETE
                    bonusGame = null
                }
                else -> {}
            }
        }
        return moved
    }

    fun solveBonus() {
        val bg = bonusGame ?: return
        bg.solve()
        when (phase) {
            SdaCampaignPhase.BONUS -> phase = SdaCampaignPhase.LEVEL_COMPLETE
            SdaCampaignPhase.FINALE_1 -> {
                phase = SdaCampaignPhase.FINALE_2
                bonusGame = SdaMasterRiddleGame(stage = 2, seed = seed)
            }
            SdaCampaignPhase.FINALE_2 -> {
                phase = SdaCampaignPhase.FINALE_3
                bonusGame = SdaMasterRiddleGame(stage = 3, seed = seed)
            }
            SdaCampaignPhase.FINALE_3 -> {
                phase = SdaCampaignPhase.CAMPAIGN_COMPLETE
                bonusGame = null
            }
            else -> {}
        }
    }

    /** 00418600 uses the remaining minute/second components, excluding whole hours. */
    private fun speedBonus(): Int {
        val seconds = maxOf(0f, clock.limit - clock.elapsed).toInt()
        return (seconds % 3600) * 100
    }

    fun levelSummary(): SdaLevelSummary {
        val remTime = maxOf(0f, clock.limit - clock.elapsed)
        val speedBonus = speedBonus()
        return SdaLevelSummary(
            clue = currentLevel.clue,
            levelIndex = levelIndex,
            title = currentLevel.title,
            elapsed = clock.elapsed,
            remainingTime = remTime,
            speedBonus = speedBonus,
            points = points,
            rank = rank,
            isLastLevel = levelIndex >= levels.size - 1,
        )
    }

    fun confirmLevelComplete() {
        require(phase == SdaCampaignPhase.LEVEL_COMPLETE) { "cannot confirm level when not level complete" }
        val speedBonus = speedBonus()
        points += speedBonus
        totalElapsed += clock.elapsed

        if (levelIndex >= levels.size - 1) {
            // Reached final level: transition to Master Riddle Finale
            phase = SdaCampaignPhase.FINALE_1
            currentSceneName = null
            bonusGame = SdaMasterRiddleGame(stage = 1, seed = seed)
        } else {
            levelIndex++
            clock = SdaClock(limit = currentLevel.time)
            completedObjects = 0
            counted.clear()
            scenes.clear()
            currentSceneName = null
            bonusGame = null
            phase = SdaCampaignPhase.MAP
        }
    }

    fun snapshot(): SdaCampaignState {
        val sceneSnapshots = scenes.mapValues { it.value.snapshot() }
        val countedMap = counted.mapValues { entry -> entry.value.map { it } }
        return SdaCampaignState(
            levelIndex = levelIndex,
            seed = seed,
            points = points,
            phase = phase.name,
            currentScene = currentSceneName,
            completedObjects = completedObjects,
            clockLimit = clock.limit,
            clockElapsed = clock.elapsed,
            totalElapsed = totalElapsed,
            scenes = sceneSnapshots,
            countedSets = countedMap,
            bonusGameState = bonusGame?.state(),
        )
    }

    fun restore(state: SdaCampaignState, content: SdaContent) {
        // Build and validate everything first. Failed restoration must not corrupt a live session.
        require(state.levelIndex in levels.indices && state.seed in 0..0xFFFFFFFFL) { "invalid campaign identity" }
        val level = levels[state.levelIndex]
        require(state.points >= 0 && state.completedObjects >= 0 &&
            state.totalElapsed.isFinite() && state.totalElapsed >= 0f) { "invalid campaign counters" }
        require(state.clockLimit == level.time) { "saved clock differs from level" }
        val restoredClock = SdaClock(state.clockLimit, state.clockElapsed)
        val restoredPhase = try { SdaCampaignPhase.valueOf(state.phase) }
            catch (e: Exception) { throw IllegalArgumentException("invalid campaign phase", e) }
        require(state.scenes.keys == state.countedSets.keys && state.scenes.keys.all { it in level.scenes }) { "invalid campaign scenes" }
        val restoredScenes = state.scenes.mapValues { (name, sceneState) ->
            val scene = content.loadScene("SCENE_${name.uppercase()}.MSL", seed = state.seed)
            require(sceneState.activeSets.distinct().size == sceneState.activeSets.size &&
                sceneState.activeSets.all { it in scene.targetSets } &&
                sceneState.candidateSets.all { it in scene.targetSets } &&
                sceneState.objects.keys == scene.objects.keys) { "invalid scene references" }
            scene.restore(sceneState)
            scene
        }
        for ((name, groups) in state.countedSets) {
            val scene = restoredScenes.getValue(name)
            require(groups.distinct().size == groups.size && groups.all { ids ->
                ids in scene.targetSets && ids.all { scene.objects[it]?.motion?.removed == true } &&
                    (scene.rows[ids]?.removed != false)
            }) { "invalid completed objectives" }
        }
        require(state.completedObjects == state.countedSets.values.sumOf { it.size }) { "completed count differs from retired rows" }
        val inScene = restoredPhase in listOf(SdaCampaignPhase.SCENE, SdaCampaignPhase.SCENE_COMPLETE,
            SdaCampaignPhase.OBJECTS_COMPLETE) || (restoredPhase == SdaCampaignPhase.TIMEOUT && state.bonusGameState == null)
        require(if (inScene) state.currentScene in restoredScenes else state.currentScene == null) { "invalid active scene" }
        val completedPhase = restoredPhase in listOf(SdaCampaignPhase.OBJECTS_COMPLETE, SdaCampaignPhase.BONUS,
            SdaCampaignPhase.LEVEL_COMPLETE, SdaCampaignPhase.FINALE_1, SdaCampaignPhase.FINALE_2,
            SdaCampaignPhase.FINALE_3, SdaCampaignPhase.CAMPAIGN_COMPLETE)
        require(if (completedPhase) state.completedObjects >= level.objects
            else restoredPhase == SdaCampaignPhase.TIMEOUT || state.completedObjects < level.objects) { "invalid completion boundary" }
        if (restoredPhase == SdaCampaignPhase.SCENE_COMPLETE)
            require(restoredScenes.getValue(state.currentScene!!).batchRetired) { "unfinished completed scene" }
        if (restoredPhase == SdaCampaignPhase.TIMEOUT) require(restoredClock.isExpired) { "invalid timeout" }
        val finale = restoredPhase in listOf(SdaCampaignPhase.FINALE_1, SdaCampaignPhase.FINALE_2,
            SdaCampaignPhase.FINALE_3, SdaCampaignPhase.CAMPAIGN_COMPLETE)
        if (finale) require(state.levelIndex == levels.lastIndex) { "finale before last level" }
        val restoredBonus = state.bonusGameState?.let {
            if (finale || (restoredPhase == SdaCampaignPhase.TIMEOUT && it["kind"] == "master_riddle")) {
                require(state.levelIndex == levels.lastIndex) { "finale before last level" }
                SdaBonusLoader.restoreLegacyFinale(it, state.seed)
            } else {
                require(restoredPhase in listOf(SdaCampaignPhase.BONUS, SdaCampaignPhase.LEVEL_COMPLETE,
                    SdaCampaignPhase.TIMEOUT)) { "unexpected bonus checkpoint" }
                SdaBonusLoader.restore(content, it, level.bonus, state.seed, level.bonusImage)
            }
        }
        if (restoredPhase == SdaCampaignPhase.BONUS) require(restoredBonus != null && !restoredBonus.isSolved) { "invalid active bonus" }
        if (restoredPhase == SdaCampaignPhase.LEVEL_COMPLETE && level.bonus.isNotEmpty())
            require(restoredBonus?.isSolved == true) { "unfinished level bonus" }
        if (finale && restoredPhase != SdaCampaignPhase.CAMPAIGN_COMPLETE)
            require(restoredBonus is SdaMasterRiddleGame) { "missing legacy finale checkpoint" }
        if (restoredPhase == SdaCampaignPhase.CAMPAIGN_COMPLETE)
            require(restoredBonus == null) { "unexpected finished finale bonus" }

        levelIndex = state.levelIndex
        seed = state.seed
        points = state.points
        phase = restoredPhase
        currentSceneName = state.currentScene
        completedObjects = state.completedObjects
        clock = restoredClock
        totalElapsed = state.totalElapsed
        scenes.clear(); scenes.putAll(restoredScenes)
        counted.clear()
        state.countedSets.forEach { (name, groups) -> counted[name] = groups.toMutableSet() }
        bonusGame = restoredBonus
    }
}

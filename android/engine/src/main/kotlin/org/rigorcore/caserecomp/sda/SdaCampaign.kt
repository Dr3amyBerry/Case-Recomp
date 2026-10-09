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
            "Súper indagador",
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
            clock.advance(seconds)
            sc.advance(seconds)

            // Check active sets retired
            val sceneCounted = counted.getOrPut(currentSceneName!!) { mutableSetOf() }
            for (ids in sc.activeSets) {
                if (ids !in sceneCounted && sc.rows[ids]?.removed == true) {
                    sceneCounted.add(ids)
                    completedObjects += ids.size
                }
            }
            points = sc.score.points

            if (remainingObjects == 0) {
                phase = SdaCampaignPhase.OBJECTS_COMPLETE
            } else if (clock.isExpired) {
                phase = SdaCampaignPhase.TIMEOUT
            } else if (sc.batchRetired) {
                phase = SdaCampaignPhase.SCENE_COMPLETE
            }
        } else if (phase in listOf(SdaCampaignPhase.BONUS, SdaCampaignPhase.FINALE_1, SdaCampaignPhase.FINALE_2, SdaCampaignPhase.FINALE_3)) {
            clock.advance(seconds)
            if (clock.isExpired) {
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
        currentSceneName = null
        if (currentLevel.bonus.isNotEmpty()) {
            bonusGame = SdaBonusLoader.load(content, currentLevel.bonus, seed, currentLevel.bonusImage)
            phase = SdaCampaignPhase.BONUS
        } else {
            phase = SdaCampaignPhase.LEVEL_COMPLETE
        }
    }

    fun clickBonus(x: Int, y: Int): Boolean {
        val bg = bonusGame ?: return false
        val moved = bg.clickPixel(x, y)
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

    fun levelSummary(): SdaLevelSummary {
        val remTime = maxOf(0f, clock.limit - clock.elapsed)
        val speedBonus = (remTime.toInt()) * 10
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
        val remTime = maxOf(0f, clock.limit - clock.elapsed)
        val speedBonus = (remTime.toInt()) * 10
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
        levelIndex = state.levelIndex
        seed = state.seed
        points = state.points
        phase = SdaCampaignPhase.valueOf(state.phase)
        currentSceneName = state.currentScene
        completedObjects = state.completedObjects
        clock = SdaClock(limit = state.clockLimit)
        clock.elapsed = state.clockElapsed
        totalElapsed = state.totalElapsed

        scenes.clear()
        for ((name, scState) in state.scenes) {
            val resName = "SCENE_${name.uppercase()}.MSL"
            val sc = content.loadScene(resName, seed = seed)
            sc.restore(scState)
            scenes[name] = sc
        }

        counted.clear()
        for ((name, sets) in state.countedSets) {
            counted[name] = sets.toMutableSet()
        }

        if (state.bonusGameState != null) {
            val bgKind = state.bonusGameState["kind"] as? String
            val bgRes = state.bonusGameState["resourceName"] as? String ?: currentLevel.bonus
            bonusGame = SdaBonusLoader.load(content, bgRes, seed, currentLevel.bonusImage)
        }
    }
}

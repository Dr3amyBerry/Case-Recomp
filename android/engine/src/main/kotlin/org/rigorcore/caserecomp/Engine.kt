package org.rigorcore.caserecomp

/** Generic, Android-independent engine prototype, NOT original game rules. */
data class SceneDefinition(
    val id: String,
    val targetIds: Set<String>,
    val frameStart: Int = 1,
    val frameEnd: Int = 1,
) {
    init {
        require(id.isNotBlank() && targetIds.isNotEmpty())
        require(targetIds.none { it.isBlank() })
        require(frameStart >= 1 && frameEnd >= frameStart)
    }
}

data class Scenario(val id: String, val scenes: List<SceneDefinition>) {
    init {
        require(id.isNotBlank() && scenes.isNotEmpty())
        require(scenes.map { it.id }.toSet().size == scenes.size)
    }
    fun scene(id: String): SceneDefinition? = scenes.find { it.id == id }
}

enum class Screen { MENU, MAP, SCENE, COMPLETE }

data class Session(
    val screen: Screen = Screen.MENU,
    val selectedSceneId: String? = null,
    val foundByScene: Map<String, Set<String>> = emptyMap(),
    val frame: Int = 1,
) {
    /** Saved state is immutable and independent of any device / renderer. */
    fun found(sceneId: String): Set<String> = foundByScene[sceneId].orEmpty()
}

sealed interface Input {
    data object Start : Input
    data class EnterScene(val sceneId: String) : Input
    data class FindObject(val objectId: String) : Input
    data class AdvanceFrame(val frames: Int = 1) : Input
    data object BackToMap : Input
    data object Reset : Input
}

sealed interface EngineEvent {
    data object Started : EngineEvent
    data class EnteredScene(val sceneId: String, val frame: Int) : EngineEvent
    data class TargetFound(val sceneId: String, val objectId: String) : EngineEvent
    data class FrameReached(val sceneId: String, val frame: Int) : EngineEvent
    data class SceneCompleted(val sceneId: String) : EngineEvent
    data class LeftScene(val sceneId: String) : EngineEvent
    data object ScenarioCompleted : EngineEvent
}

data class StepResult(val session: Session, val events: List<EngineEvent>)

class Engine(private val scenario: Scenario) {
    private fun reduce(session: Session, input: Input): Session {
        return when (input) {
            Input.Start -> if (session.screen == Screen.MENU) session.copy(screen = Screen.MAP) else session
            is Input.EnterScene -> {
                val scene = scenario.scene(input.sceneId)
                if (session.screen == Screen.MAP && scene != null) {
                    session.copy(screen = Screen.SCENE, selectedSceneId = input.sceneId, frame = scene.frameStart)
                } else session
            }
            is Input.FindObject -> {
                val id = session.selectedSceneId ?: return session
                val scene = scenario.scene(id) ?: return session
                if (session.screen != Screen.SCENE || input.objectId !in scene.targetIds) return session
                if (input.objectId in session.found(id)) return session
                val found = session.found(id) + input.objectId
                val updated = session.copy(foundByScene = session.foundByScene + (id to found))
                if (found != scene.targetIds) return updated
                val finished = scenario.scenes.all { item ->
                    val completed = if (item.id == id) found else updated.found(item.id)
                    completed == item.targetIds
                }
                updated.copy(screen = if (finished) Screen.COMPLETE else Screen.MAP, selectedSceneId = null)
            }
            is Input.AdvanceFrame -> {
                val id = session.selectedSceneId ?: return session
                val scene = scenario.scene(id) ?: return session
                if (session.screen != Screen.SCENE || input.frames <= 0) return session
                session.copy(frame = minOf(scene.frameEnd, session.frame + input.frames))
            }
            Input.BackToMap -> if (session.screen == Screen.SCENE)
                session.copy(screen = Screen.MAP, selectedSceneId = null) else session
            Input.Reset -> Session()
        }
    }

    fun update(session: Session, input: Input): Session = reduce(session, input)

    /** State transition plus content-neutral events; deterministic for the same input trace. */
    fun step(session: Session, input: Input): StepResult {
        val next = reduce(session, input)
        if (next == session) return StepResult(next, emptyList())
        val events = mutableListOf<EngineEvent>()
        when (input) {
            Input.Start -> events += EngineEvent.Started
            is Input.EnterScene -> next.selectedSceneId?.let { events += EngineEvent.EnteredScene(it, next.frame) }
            is Input.FindObject -> {
                val sceneId = session.selectedSceneId
                if (sceneId != null && input.objectId !in session.found(sceneId) && input.objectId in next.found(sceneId)) {
                    events += EngineEvent.TargetFound(sceneId, input.objectId)
                    if (next.screen != Screen.SCENE) events += EngineEvent.SceneCompleted(sceneId)
                    if (next.screen == Screen.COMPLETE) events += EngineEvent.ScenarioCompleted
                }
            }
            is Input.AdvanceFrame -> next.selectedSceneId?.let { events += EngineEvent.FrameReached(it, next.frame) }
            Input.BackToMap -> session.selectedSceneId?.let { events += EngineEvent.LeftScene(it) }
            Input.Reset -> Unit
        }
        return StepResult(next, events.toList())
    }
}

/** Aspect-fit input transform: never register touches in the letterboxed margins. */
data class GamePoint(val x: Float, val y: Float)

class LetterboxViewport(private val designWidth: Float, private val designHeight: Float) {
    init { require(designWidth > 0f && designHeight > 0f) }
    fun gamePoint(screenX: Float, screenY: Float, viewWidth: Float, viewHeight: Float): GamePoint? {
        if (viewWidth <= 0f || viewHeight <= 0f || !screenX.isFinite() || !screenY.isFinite()) return null
        val scale = minOf(viewWidth / designWidth, viewHeight / designHeight)
        val originX = (viewWidth - designWidth * scale) / 2f
        val originY = (viewHeight - designHeight * scale) / 2f
        val localX = (screenX - originX) / scale
        val localY = (screenY - originY) / scale
        return if (localX in 0f..designWidth && localY in 0f..designHeight) GamePoint(localX, localY) else null
    }
}

/** Synthetic-layout hit regions used by Phase 4 tests; no original coordinates. */
data class GameRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    init {
        require(left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite())
        require(right > left && bottom > top)
    }
    fun contains(point: GamePoint): Boolean =
        point.x >= left && point.x < right && point.y >= top && point.y < bottom
}

data class TargetRegion(val objectId: String, val bounds: GameRect, val zIndex: Int = 0) {
    init { require(objectId.isNotBlank()) }
}

data class SceneLayout(val sceneId: String, val targets: List<TargetRegion>) {
    init {
        require(sceneId.isNotBlank() && targets.isNotEmpty())
        require(targets.map { it.objectId }.toSet().size == targets.size)
    }

    /** Highest z-index wins; lexical id makes equal-z overlap deterministic. */
    fun hit(point: GamePoint): String? = targets
        .filter { it.bounds.contains(point) }
        .sortedWith(compareByDescending<TargetRegion> { it.zIndex }.thenBy { it.objectId })
        .firstOrNull()?.objectId
}

data class ReplayResult(
    val finalSession: Session,
    val states: List<Session>,
    val events: List<EngineEvent> = emptyList(),
)

fun Engine.replay(initial: Session = Session(), inputs: Iterable<Input>): ReplayResult {
    var current = initial
    val states = mutableListOf(current)
    val events = mutableListOf<EngineEvent>()
    for (input in inputs) {
        val result = step(current, input)
        current = result.session
        states += current
        events += result.events
    }
    return ReplayResult(current, states.toList(), events.toList())
}

fun Engine.inputForTap(session: Session, layout: SceneLayout, point: GamePoint): Input.FindObject? {
    if (session.screen != Screen.SCENE || session.selectedSceneId != layout.sceneId) return null
    val objectId = layout.hit(point) ?: return null
    return Input.FindObject(objectId)
}

/** Kotlin representation of the public case-recomp-scenario v1 contract. */
data class ScenarioTargetV1(val id: String, val bounds: GameRect, val z: Int)
data class ScenarioSceneV1(
    val id: String,
    val frameStart: Int,
    val frameEnd: Int,
    val targets: List<ScenarioTargetV1>,
)
data class ScenarioEventV1(
    val id: String,
    val kind: String,
    val scene: String? = null,
    val target: String? = null,
    val frame: Int? = null,
    val toScene: String? = null,
)
data class ScenarioV1(
    val format: String = "case-recomp-scenario",
    val version: Int = 1,
    val id: String,
    val designWidth: Int,
    val designHeight: Int,
    val scenes: List<ScenarioSceneV1>,
    val events: List<ScenarioEventV1> = emptyList(),
) {
    init {
        require(format == "case-recomp-scenario" && version == 1)
        require(id.isNotBlank() && designWidth > 0 && designHeight > 0 && scenes.isNotEmpty())
        require(scenes.map { it.id }.toSet().size == scenes.size)
        require(events.map { it.id }.toSet().size == events.size)
        val sceneIds = scenes.map { it.id }.toSet()
        scenes.forEach { scene ->
            require(scene.id.isNotBlank() && scene.frameStart >= 1 && scene.frameEnd >= scene.frameStart)
            require(scene.targets.isNotEmpty() && scene.targets.map { it.id }.toSet().size == scene.targets.size)
        }
        val allowedKinds = setOf("enter", "leave", "target-found", "frame-reached", "scenario-complete")
        events.forEach { event ->
            require(event.id.isNotBlank() && event.kind in allowedKinds)
            require(event.scene == null || event.scene in sceneIds)
            require(event.toScene == null || event.toScene in sceneIds)
            if (event.target != null) {
                val scene = scenes.find { it.id == event.scene }
                require(scene != null && scene.targets.any { it.id == event.target })
            }
            if (event.frame != null) {
                val scene = scenes.find { it.id == event.scene }
                require(scene != null && event.frame in scene.frameStart..scene.frameEnd)
            }
        }
    }

    fun engineScenario(): Scenario = Scenario(id, scenes.map { scene ->
        SceneDefinition(scene.id, scene.targets.map { it.id }.toSet(), scene.frameStart, scene.frameEnd)
    })

    fun layout(sceneId: String): SceneLayout? = scenes.find { it.id == sceneId }?.let { scene ->
        SceneLayout(scene.id, scene.targets.map { TargetRegion(it.id, it.bounds, it.z) })
    }
}

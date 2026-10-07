package org.rigorcore.caserecomp

/** Generic, Android-independent engine prototype, NOT original game rules. */
data class SceneDefinition(val id: String, val targetIds: Set<String>) {
    init {
        require(id.isNotBlank() && targetIds.isNotEmpty())
        require(targetIds.none { it.isBlank() })
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
) {
    /** Saved state is immutable and independent of any device / renderer. */
    fun found(sceneId: String): Set<String> = foundByScene[sceneId].orEmpty()
}

sealed interface Input {
    data object Start : Input
    data class EnterScene(val sceneId: String) : Input
    data class FindObject(val objectId: String) : Input
    data object BackToMap : Input
    data object Reset : Input
}

class Engine(private val scenario: Scenario) {
    fun update(session: Session, input: Input): Session {
        return when (input) {
        Input.Start -> if (session.screen == Screen.MENU) session.copy(screen = Screen.MAP) else session
        is Input.EnterScene -> {
            if (session.screen == Screen.MAP && scenario.scene(input.sceneId) != null) {
                session.copy(screen = Screen.SCENE, selectedSceneId = input.sceneId)
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
        Input.BackToMap -> if (session.screen == Screen.SCENE)
            session.copy(screen = Screen.MAP, selectedSceneId = null) else session
        Input.Reset -> Session()
        }
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

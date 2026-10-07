package org.rigorcore.caserecomp

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

interface GameClock {
    fun nowMillis(): Long
}

class DeterministicClock(startMillis: Long = 0L) : GameClock {
    private var value: Long = startMillis
    init { require(startMillis >= 0L) }
    override fun nowMillis(): Long = value
    fun advanceBy(millis: Long) {
        require(millis >= 0L)
        value = Math.addExact(value, millis)
    }
    fun set(millis: Long) {
        require(millis >= value)
        value = millis
    }
}

class SystemGameClock : GameClock {
    override fun nowMillis(): Long = System.nanoTime() / 1_000_000L
}

enum class LifecycleState { NEW, CREATED, STARTED, RESUMED, PAUSED, STOPPED, DESTROYED }

enum class AudioCue { START, TARGET_FOUND, SCENE_COMPLETE, SCENARIO_COMPLETE }

interface AudioPort {
    fun play(cue: AudioCue)
}

object NoopAudioPort : AudioPort {
    override fun play(cue: AudioCue) = Unit
}

class RecordingAudioPort : AudioPort {
    private val mutableCues = mutableListOf<AudioCue>()
    val cues: List<AudioCue> get() = mutableCues.toList()
    override fun play(cue: AudioCue) { mutableCues += cue }
}

data class SessionSnapshotV1(
    val scenarioId: String,
    val screen: Screen,
    val selectedSceneId: String?,
    val frame: Int,
    val foundByScene: Map<String, Set<String>>,
    val savedAtMillis: Long,
) {
    init {
        require(scenarioId.isNotBlank())
        require(frame >= 1)
        require(savedAtMillis >= 0L)
        require(foundByScene.keys.none { it.isBlank() })
        require(foundByScene.values.flatten().none { it.isBlank() })
    }

    fun toSession(): Session = Session(screen, selectedSceneId, foundByScene, frame)

    companion object {
        fun fromSession(scenarioId: String, session: Session, savedAtMillis: Long): SessionSnapshotV1 =
            SessionSnapshotV1(scenarioId, session.screen, session.selectedSceneId, session.frame, session.foundByScene, savedAtMillis)
    }
}

interface SessionStore {
    fun load(): String?
    fun save(encoded: String)
    fun clear()
}

class InMemorySessionStore(initial: String? = null) : SessionStore {
    var value: String? = initial
        private set
    override fun load(): String? = value
    override fun save(encoded: String) { value = encoded }
    override fun clear() { value = null }
}

object SessionSnapshotCodecV1 {
    private const val HEADER = "case-recomp-session-v1"

    private fun esc(value: String): String = URLEncoder.encode(value, "UTF-8")
    private fun unesc(value: String): String = URLDecoder.decode(value, "UTF-8")
    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }

    fun encode(snapshot: SessionSnapshotV1): String {
        val rows = mutableListOf(
            HEADER,
            "scenario=${esc(snapshot.scenarioId)}",
            "screen=${snapshot.screen.name}",
            "selected=${snapshot.selectedSceneId?.let(::esc) ?: "-"}",
            "frame=${snapshot.frame}",
            "savedAt=${snapshot.savedAtMillis}",
        )
        snapshot.foundByScene.toSortedMap().forEach { (scene, found) ->
            val ids = found.toSortedSet().joinToString(",") { esc(it) }
            rows += "found.${esc(scene)}=$ids"
        }
        val body = rows.joinToString("\n")
        return "$body\nsha256=${sha256(body)}\n"
    }

    fun decode(encoded: String): SessionSnapshotV1? {
        val normalized = encoded.removeSuffix("\n")
        val lines = normalized.lines()
        if (lines.size < 7 || lines.firstOrNull() != HEADER) return null
        val checksumRow = lines.last()
        if (!checksumRow.startsWith("sha256=")) return null
        val body = lines.dropLast(1).joinToString("\n")
        if (sha256(body) != checksumRow.removePrefix("sha256=")) return null
        val values = mutableMapOf<String, String>()
        for (line in lines.drop(1).dropLast(1)) {
            val split = line.indexOf('=')
            if (split <= 0) return null
            val key = line.substring(0, split)
            if (key in values) return null
            values[key] = line.substring(split + 1)
        }
        return runCatching {
            val scenario = unesc(values.getValue("scenario"))
            val screen = Screen.valueOf(values.getValue("screen"))
            val selectedRaw = values.getValue("selected")
            val selected = if (selectedRaw == "-") null else unesc(selectedRaw)
            val frame = values.getValue("frame").toInt()
            val savedAt = values.getValue("savedAt").toLong()
            val found = values.filterKeys { it.startsWith("found.") }.map { (key, value) ->
                val scene = unesc(key.removePrefix("found."))
                val ids = if (value.isEmpty()) emptySet() else value.split(',').map(::unesc).toSet()
                scene to ids
            }.toMap()
            SessionSnapshotV1(scenario, screen, selected, frame, found, savedAt)
        }.getOrNull()
    }
}

data class RenderTarget(val id: String, val bounds: GameRect, val z: Int, val found: Boolean)
data class RenderFrame(
    val screen: Screen,
    val sceneId: String?,
    val frame: Int,
    val targets: List<RenderTarget>,
    val foundCount: Int,
    val totalTargets: Int,
)

object RenderModelBuilder {
    fun build(scenario: ScenarioV1, session: Session): RenderFrame {
        val scene = session.selectedSceneId?.let { selected -> scenario.scenes.find { it.id == selected } }
        val found = scene?.let { session.found(it.id) }.orEmpty()
        val targets = scene?.targets.orEmpty().map { RenderTarget(it.id, it.bounds, it.z, it.id in found) }
        return RenderFrame(session.screen, scene?.id, session.frame, targets, found.size, scene?.targets?.size ?: 0)
    }
}

class GameRuntime(
    private val scenario: ScenarioV1,
    private val clock: GameClock,
    private val store: SessionStore,
    private val audio: AudioPort = NoopAudioPort,
) {
    private val engine = Engine(scenario.engineScenario())
    var lifecycle: LifecycleState = LifecycleState.NEW
        private set
    var session: Session = Session()
        private set

    fun onCreate() {
        check(lifecycle == LifecycleState.NEW)
        session = restoreOrFresh()
        lifecycle = LifecycleState.CREATED
    }

    fun onStart() {
        check(lifecycle == LifecycleState.CREATED || lifecycle == LifecycleState.STOPPED)
        lifecycle = LifecycleState.STARTED
    }

    fun onResume() {
        check(lifecycle == LifecycleState.STARTED || lifecycle == LifecycleState.PAUSED)
        lifecycle = LifecycleState.RESUMED
    }

    fun onPause() {
        check(lifecycle == LifecycleState.RESUMED)
        persist()
        lifecycle = LifecycleState.PAUSED
    }

    fun onStop() {
        check(lifecycle == LifecycleState.PAUSED || lifecycle == LifecycleState.STARTED)
        persist()
        lifecycle = LifecycleState.STOPPED
    }

    fun onDestroy() {
        check(lifecycle != LifecycleState.DESTROYED)
        if (lifecycle != LifecycleState.NEW) persist()
        lifecycle = LifecycleState.DESTROYED
    }

    fun dispatch(input: Input): StepResult {
        check(lifecycle == LifecycleState.RESUMED)
        val result = engine.step(session, input)
        session = result.session
        emitAudio(result.events)
        return result
    }

    fun renderFrame(): RenderFrame = RenderModelBuilder.build(scenario, session)

    fun inputForTap(screenX: Float, screenY: Float, viewWidth: Float, viewHeight: Float): Input? {
        if (session.screen != Screen.SCENE) return null
        val sceneId = session.selectedSceneId ?: return null
        val layout = scenario.layout(sceneId) ?: return null
        val point = LetterboxViewport(scenario.designWidth.toFloat(), scenario.designHeight.toFloat())
            .gamePoint(screenX, screenY, viewWidth, viewHeight) ?: return null
        return engine.inputForTap(session, layout, point)
    }

    fun clearProgress() {
        store.clear()
        session = Session()
    }

    private fun restoreOrFresh(): Session {
        val snapshot = store.load()?.let(SessionSnapshotCodecV1::decode) ?: return Session()
        if (snapshot.scenarioId != scenario.id) return Session()
        val candidate = snapshot.toSession()
        if (!sessionIsValid(candidate)) return Session()
        return candidate
    }

    private fun sessionIsValid(candidate: Session): Boolean {
        val selected = candidate.selectedSceneId
        if (selected != null && scenario.scenes.none { it.id == selected }) return false
        if (candidate.screen == Screen.SCENE && selected == null) return false
        if (candidate.screen != Screen.SCENE && selected != null) return false
        for ((sceneId, found) in candidate.foundByScene) {
            val scene = scenario.scenes.find { it.id == sceneId } ?: return false
            if (!scene.targets.map { it.id }.toSet().containsAll(found)) return false
        }
        if (selected != null) {
            val scene = scenario.scenes.first { it.id == selected }
            if (candidate.frame !in scene.frameStart..scene.frameEnd) return false
        }
        return true
    }

    private fun persist() {
        val snapshot = SessionSnapshotV1.fromSession(scenario.id, session, clock.nowMillis())
        store.save(SessionSnapshotCodecV1.encode(snapshot))
    }

    private fun emitAudio(events: List<EngineEvent>) {
        events.forEach { event ->
            when (event) {
                EngineEvent.Started -> audio.play(AudioCue.START)
                is EngineEvent.TargetFound -> audio.play(AudioCue.TARGET_FOUND)
                is EngineEvent.SceneCompleted -> audio.play(AudioCue.SCENE_COMPLETE)
                EngineEvent.ScenarioCompleted -> audio.play(AudioCue.SCENARIO_COMPLETE)
                else -> Unit
            }
        }
    }
}

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

interface LifecycleAudioPort : AudioPort {
    fun onResume()
    fun onPause()
    fun onDestroy()
}

interface InterruptibleAudioPort : LifecycleAudioPort {
    fun onInterruptionStart()
    fun onInterruptionEnd()
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

interface SlotSessionStore {
    fun load(slot: String): String?
    fun save(slot: String, encoded: String)
    fun clear(slot: String)
    fun slots(): Set<String>
}

class SlotSessionAdapter(private val slots: SlotSessionStore, val slot: String) : SessionStore {
    init { require(slot.matches(Regex("[A-Za-z0-9._-]{1,64}"))) }
    override fun load(): String? = slots.load(slot)
    override fun save(encoded: String) = slots.save(slot, encoded)
    override fun clear() = slots.clear(slot)
}

class InMemorySlotSessionStore : SlotSessionStore {
    private val values = linkedMapOf<String, String>()
    override fun load(slot: String): String? = values[slot]
    override fun save(slot: String, encoded: String) { require(slot.matches(Regex("[A-Za-z0-9._-]{1,64}"))); values[slot] = encoded }
    override fun clear(slot: String) { values.remove(slot) }
    override fun slots(): Set<String> = values.keys.toSet()
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
    private const val LEGACY_HEADER = "case-recomp-session-v0"

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
        return "$body\nsha256=${sha256(body)}"
    }

    fun decode(encoded: String): SessionSnapshotV1? {
        // SharedPreferences XML may preserve serializer indentation after a legacy
        // trailing newline. Encoded fields are URL-escaped and the checksum is the
        // final row, so trailing ASCII whitespace is never semantic.
        val normalized = encoded.trimEnd(' ', '\t', '\r', '\n')
        val lines = normalized.lines()
        val header = lines.firstOrNull() ?: return null
        val legacy = header == LEGACY_HEADER
        if ((!legacy && header != HEADER) || lines.size < if (legacy) 6 else 7) return null
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
            val savedAt = if (legacy) 0L else values.getValue("savedAt").toLong()
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
    val designWidth: Int,
    val designHeight: Int,
    /** An acknowledge-mode scene is complete and shows its closing dialog. */
    val awaitingAcknowledge: Boolean = false,
)

object RenderModelBuilder {
    fun build(scenario: ScenarioV1, session: Session): RenderFrame {
        val scene = session.selectedSceneId?.let { selected -> scenario.scenes.find { it.id == selected } }
        val found = scene?.let { session.found(it.id) }.orEmpty()
        val targets = scene?.targets.orEmpty().map { RenderTarget(it.id, it.bounds, it.z, it.id in found) }
        val awaiting = scene != null && session.screen == Screen.SCENE &&
            scene.completion == SceneCompletion.ACKNOWLEDGE_THEN_MAP && found == scene.targets.map { it.id }.toSet()
        return RenderFrame(session.screen, scene?.id, session.frame, targets, found.size, scene?.targets?.size ?: 0,
            scenario.designWidth, scenario.designHeight, awaiting)
    }
}

data class RuntimeObservationStep(
    val sequence: Int,
    val atMillis: Long,
    val frame: Int,
    val screen: String,
    val scene: String?,
    val inputKind: String,
    val eventKinds: List<String>,
    val observableStateSha256: String,
    val renderSha256: String,
)

interface RuntimeObserver {
    fun record(atMillis: Long, input: Input, result: StepResult, render: RenderFrame)
}

object NoopRuntimeObserver : RuntimeObserver {
    override fun record(atMillis: Long, input: Input, result: StepResult, render: RenderFrame) = Unit
}

class RuntimeTraceRecorder(private val sourcePackageSha256: String) : RuntimeObserver {
    private val rows = mutableListOf<RuntimeObservationStep>()
    init { require(sourcePackageSha256.matches(Regex("[0-9a-f]{64}"))) }
    val steps: List<RuntimeObservationStep> get() = rows.toList()

    override fun record(atMillis: Long, input: Input, result: StepResult, render: RenderFrame) {
        val session = result.session
        val state = mapOf(
            "screen" to session.screen.name, "scene" to session.selectedSceneId, "frame" to session.frame,
            "found" to session.foundByScene.toSortedMap().mapValues { it.value.toSortedSet().toList() },
        )
        val renderValue = mapOf(
            "screen" to render.screen.name, "scene" to render.sceneId, "frame" to render.frame,
            "targets" to render.targets.sortedWith(compareBy<RenderTarget> { it.z }.thenBy { it.id }).map {
                listOf(it.id, it.bounds.left, it.bounds.top, it.bounds.right, it.bounds.bottom, it.z, it.found)
            },
        )
        rows += RuntimeObservationStep(rows.size, atMillis, session.frame, session.screen.name, session.selectedSceneId,
            inputKind(input), result.events.map(::eventKind), sha256Hex(MiniJson.canonical(state)), sha256Hex(MiniJson.canonical(renderValue)))
    }

    fun document(): Map<String, Any?> = mapOf(
        "format" to "case-recomp-runtime-observation", "version" to 1, "kind" to "runtime-draft",
        "source_package_sha256" to sourcePackageSha256,
        "steps" to rows.map { row -> mapOf(
            "sequence" to row.sequence, "at_millis" to row.atMillis, "frame" to row.frame, "screen" to row.screen,
            "scene" to row.scene, "input_kind" to row.inputKind, "event_kinds" to row.eventKinds,
            "observable_state_sha256" to row.observableStateSha256, "render_sha256" to row.renderSha256,
        ) },
        "promotion_allowed" to false,
        "reason" to "runtime draft lacks independent Director sprite/handler fingerprints",
    )

    fun encode(): String = MiniJson.canonical(document()) + "\n"

    private fun inputKind(input: Input): String = when (input) {
        Input.Start -> "start"
        is Input.EnterScene -> "enter-scene"
        is Input.FindObject -> "tap"
        is Input.AdvanceFrame -> "advance-frame"
        Input.BackToMap -> "back"
        Input.Acknowledge -> "acknowledge"
        Input.Reset -> "reset"
    }
    private fun eventKind(event: EngineEvent): String = when (event) {
        EngineEvent.Started -> "start"
        is EngineEvent.EnteredScene -> "enter"
        is EngineEvent.TargetFound -> "target-found"
        is EngineEvent.FrameReached -> "frame-reached"
        is EngineEvent.SceneCompleted -> "scene-complete"
        is EngineEvent.LeftScene -> "leave"
        EngineEvent.ScenarioCompleted -> "scenario-complete"
    }
}

class GameRuntime(
    private val scenario: ScenarioV1,
    private val clock: GameClock,
    private val store: SessionStore,
    private val audio: AudioPort = NoopAudioPort,
    private val observer: RuntimeObserver = NoopRuntimeObserver,
    private val flowGate: FlowGate = AllowAllFlowGate,
) {
    private val engine = Engine(scenario.engineScenario())
    var lifecycle: LifecycleState = LifecycleState.NEW
        private set
    var session: Session = Session()
        private set

    fun onCreate() {
        check(lifecycle == LifecycleState.NEW)
        flowGate.onRuntimeCreated(clock.nowMillis())
        val restored = restoreOrFresh()
        session = if (flowGate.allowRestored(restored)) restored else Session()
        lifecycle = LifecycleState.CREATED
    }

    fun onStart() {
        check(lifecycle == LifecycleState.CREATED || lifecycle == LifecycleState.STOPPED)
        lifecycle = LifecycleState.STARTED
    }

    fun onResume() {
        check(lifecycle == LifecycleState.STARTED || lifecycle == LifecycleState.PAUSED)
        lifecycle = LifecycleState.RESUMED
        (audio as? LifecycleAudioPort)?.onResume()
    }

    fun onPause() {
        check(lifecycle == LifecycleState.RESUMED)
        (audio as? LifecycleAudioPort)?.onPause()
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
        (audio as? LifecycleAudioPort)?.onDestroy()
        lifecycle = LifecycleState.DESTROYED
    }

    fun dispatch(input: Input): StepResult {
        check(lifecycle == LifecycleState.RESUMED)
        val now = clock.nowMillis()
        val before = session
        val proposed = engine.step(before, input)
        val result = if (flowGate.allow(before, input, proposed.session, now)) proposed else StepResult(before, emptyList())
        session = result.session
        if (session != before) persist()
        emitAudio(result.events)
        observer.record(now, input, result, renderFrame())
        return result
    }

    fun renderFrame(): RenderFrame = RenderModelBuilder.build(scenario, session)
    fun firstSceneId(): String = scenario.scenes.first().id
    fun scenarioId(): String = scenario.id

    fun inputForTap(screenX: Float, screenY: Float, viewWidth: Float, viewHeight: Float): Input? {
        if (session.screen != Screen.SCENE) return null
        val sceneId = session.selectedSceneId ?: return null
        val layout = scenario.layout(sceneId) ?: return null
        val point = LetterboxViewport(scenario.designWidth.toFloat(), scenario.designHeight.toFloat())
            .gamePoint(screenX, screenY, viewWidth, viewHeight) ?: return null
        if (engine.awaitingAcknowledge(session)) {
            val region = scenario.scenes.first { it.id == sceneId }.acknowledgeRegion ?: return null
            return if (region.contains(point)) Input.Acknowledge else null
        }
        return engine.inputForTap(session, layout, point)
    }

    fun clearProgress() {
        store.clear()
        session = Session()
    }

    fun onAudioInterruptionStart() {
        (audio as? InterruptibleAudioPort)?.onInterruptionStart()
    }

    fun onAudioInterruptionEnd() {
        (audio as? InterruptibleAudioPort)?.onInterruptionEnd()
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

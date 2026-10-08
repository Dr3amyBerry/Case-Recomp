package org.rigorcore.caserecomp

private const val VERIFIED_SCENE_FORMAT = "case-recomp-verified-scene"
private const val VERIFIED_SCENE_VERSION = 1
private val SCENE_SHA256_HEX = Regex("[0-9a-f]{64}")

data class VerifiedSceneTargetV1(val id: String, val mask: HitMask) {
    init { require(id.isNotBlank()) }
}

/**
 * Kotlin form of the Python `case-recomp-verified-scene` v1 proof (`.crscene`).
 * It carries generic hidden-object rules and hit masks for one scene, bound to a
 * private-content package, scenario, scene and Director source. It is an integrity
 * checksum over locally produced evidence, not a signature.
 */
data class VerifiedSceneProofV1(
    val packageId: String,
    val scenarioId: String,
    val scenarioSha256: String,
    val sourceSha256: String,
    val sceneId: String,
    val observationSha256: String,
    val trialSetSha256: String,
    val bindingSha256: String,
    val targets: List<VerifiedSceneTargetV1>,
    val acknowledgeRegion: GameRect,
    val counterStart: Int,
) {
    init {
        listOf(packageId, scenarioSha256, sourceSha256, observationSha256, trialSetSha256, bindingSha256)
            .forEach { require(SCENE_SHA256_HEX.matches(it)) }
        require(scenarioId.isNotBlank() && sceneId.isNotBlank())
        require(targets.isNotEmpty() && targets.map { it.id } == targets.map { it.id }.toSortedSet().toList())
        require(counterStart >= 0)
    }

    val targetIds: Set<String> get() = targets.map { it.id }.toSet()

    /** Replace the proven scene's targets with the verified masks and acknowledge-mode completion. */
    fun applyTo(scenario: ScenarioV1): ScenarioV1 {
        require(scenario.id == scenarioId) { "verified scene scenario mismatch" }
        require(scenario.scenes.any { it.id == sceneId }) { "verified scene is not present in scenario" }
        require(scenario.events.none { it.scene == sceneId && it.target != null }) {
            "scenario target events cannot be rebound to verified scene targets"
        }
        val scenes = scenario.scenes.map { scene ->
            if (scene.id != sceneId) scene else scene.copy(
                targets = targets.map { ScenarioTargetV1(it.id, it.mask.bounds, 0, it.mask) },
                completion = SceneCompletion.ACKNOWLEDGE_THEN_MAP,
                acknowledgeRegion = acknowledgeRegion,
            )
        }
        return scenario.copy(scenes = scenes)
    }

    /** Case-wide clue counter after the given number of verified finds in this scene. */
    fun counterAfter(found: Int): Int = (counterStart - found).coerceAtLeast(0)
}

object VerifiedSceneProofParser {
    private val rootKeys = setOf(
        "format", "version", "package_id", "scenario_id", "scenario_sha256", "source_sha256", "scene_id",
        "evidence_kind", "evidence_chain", "targets", "rules", "binding_sha256",
    )
    private val maskKeys = setOf("left", "top", "width", "height", "bits")

    private fun strictInt(value: Any?, name: String): Int {
        require(value is Long && value in 0L..Int.MAX_VALUE.toLong()) { "$name must be a non-negative integer JSON number" }
        return value.toInt()
    }

    private fun mask(value: Any?): HitMask {
        val row = value.jsonObject("mask")
        require(row.keys == maskKeys) { "mask fields" }
        return HitMask(strictInt(row["left"], "mask.left"), strictInt(row["top"], "mask.top"),
            strictInt(row["width"], "mask.width"), strictInt(row["height"], "mask.height"), row["bits"].jsonString("mask.bits"))
    }

    fun parse(text: String): VerifiedSceneProofV1 {
        val root = MiniJson.parse(text).jsonObject("verified-scene")
        require(root.keys == rootKeys) { "verified-scene fields" }
        require(root["format"] == VERIFIED_SCENE_FORMAT && strictInt(root["version"], "version") == VERIFIED_SCENE_VERSION)
        val binding = root["binding_sha256"].jsonString("binding_sha256")
        require(SCENE_SHA256_HEX.matches(binding)) { "binding_sha256" }
        require(sha256Hex(MiniJson.canonical(root.filterKeys { it != "binding_sha256" })) == binding) {
            "verified-scene binding hash mismatch"
        }
        require(root["evidence_kind"] == "independent-original-runtime") { "verified-scene evidence kind" }

        val chain = root["evidence_chain"].jsonObject("evidence_chain")
        require(chain.keys == setOf("observation_sha256", "trial_set_sha256")) { "evidence_chain fields" }

        val targets = root["targets"].jsonList("targets").map { item ->
            val row = item.jsonObject("target")
            require(row.keys == setOf("id", "mask")) { "target fields" }
            VerifiedSceneTargetV1(row["id"].jsonString("target.id"), mask(row["mask"]))
        }

        val rules = root["rules"].jsonObject("rules")
        require(rules.keys == setOf("miss_effect", "find_effect", "completion", "counter_start")) { "rules fields" }
        require(rules["miss_effect"] == "none") { "unsupported miss effect" }
        val find = rules["find_effect"].jsonObject("find_effect")
        require(find.keys == setOf("hide_target", "counter_delta") && find["hide_target"] == true && find["counter_delta"] == -1L) {
            "unsupported find effect"
        }
        val completion = rules["completion"].jsonObject("completion")
        require(completion.keys == setOf("kind", "acknowledge_region", "locks_scene") &&
            completion["kind"] == "acknowledge-then-map" && completion["locks_scene"] == true) { "unsupported completion" }
        val region = completion["acknowledge_region"].jsonList("acknowledge_region").map { strictInt(it, "acknowledge_region") }
        require(region.size == 4) { "acknowledge_region size" }

        return VerifiedSceneProofV1(
            packageId = root["package_id"].jsonString("package_id"),
            scenarioId = root["scenario_id"].jsonString("scenario_id"),
            scenarioSha256 = root["scenario_sha256"].jsonString("scenario_sha256"),
            sourceSha256 = root["source_sha256"].jsonString("source_sha256"),
            sceneId = root["scene_id"].jsonString("scene_id"),
            observationSha256 = chain["observation_sha256"].jsonString("observation_sha256"),
            trialSetSha256 = chain["trial_set_sha256"].jsonString("trial_set_sha256"),
            bindingSha256 = binding,
            targets = targets,
            acknowledgeRegion = GameRect(region[0].toFloat(), region[1].toFloat(), region[2].toFloat(), region[3].toFloat()),
            counterStart = strictInt(rules["counter_start"], "counter_start"),
        )
    }

    fun canonicalize(text: String): String {
        parse(text)
        return MiniJson.canonical(MiniJson.parse(text).jsonObject("verified-scene")) + "\n"
    }
}

/**
 * Wraps the navigation gate: hidden-object finds and the completion acknowledge are
 * allowed only inside scenes with a verified-scene proof, and only for its targets.
 * Navigation itself stays governed by [delegate] (normally [VerifiedFlowGate]).
 */
class VerifiedSceneGate(
    private val delegate: FlowGate,
    proofs: List<VerifiedSceneProofV1>,
) : FlowGate {
    private val byScene = proofs.associateBy { it.sceneId }

    init { require(byScene.size == proofs.size) { "duplicate verified scene proof" } }

    override fun onRuntimeCreated(nowMillis: Long) = delegate.onRuntimeCreated(nowMillis)

    override fun allowRestored(session: Session): Boolean {
        if (!delegate.allowRestored(session)) return false
        return session.foundByScene.all { (scene, found) -> byScene[scene]?.targetIds?.containsAll(found) == true }
    }

    override fun allow(before: Session, input: Input, after: Session, nowMillis: Long): Boolean {
        if (before == after) return true
        val proof = before.selectedSceneId?.let(byScene::get)
        return when (input) {
            is Input.FindObject -> before.screen == Screen.SCENE && proof != null && input.objectId in proof.targetIds
            Input.Acknowledge -> before.screen == Screen.SCENE && proof != null
            else -> delegate.allow(before, input, after, nowMillis)
        }
    }
}

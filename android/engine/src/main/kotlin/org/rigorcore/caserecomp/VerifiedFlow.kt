package org.rigorcore.caserecomp

private const val VERIFIED_FLOW_FORMAT = "case-recomp-verified-flow"
private const val VERIFIED_FLOW_VERSION = 2
private val SHA256_HEX = Regex("[0-9a-f]{64}")

data class VerifiedFlowRuleV2(
    val id: String,
    val fromScreen: Screen,
    val inputKind: String,
    val toScreen: Screen,
    val sceneId: String? = null,
    val notBeforeMs: Long = 0L,
) {
    init {
        require(notBeforeMs >= 0L)
        when (id) {
            "menu-map" -> require(
                fromScreen == Screen.MENU && inputKind == "start" &&
                    toScreen == Screen.MAP && sceneId == null
            )
            "map-scene" -> require(
                fromScreen == Screen.MAP && inputKind == "enter-scene" &&
                    toScreen == Screen.SCENE && !sceneId.isNullOrBlank()
            )
            else -> error("unsupported verified-flow rule id")
        }
    }
}

data class VerifiedFlowEvidenceChainV2(
    val specSha256: String,
    val observationSha256: String,
    val captureConsensusSha256: String,
) {
    init {
        require(SHA256_HEX.matches(specSha256))
        require(SHA256_HEX.matches(observationSha256))
        require(SHA256_HEX.matches(captureConsensusSha256))
    }
}

data class VerifiedFlowProofV2(
    val packageId: String,
    val scenarioId: String,
    val scenarioSha256: String,
    val sourceSha256: String,
    val evidenceKind: String,
    val evidenceChain: VerifiedFlowEvidenceChainV2,
    val bindingSha256: String,
    val bootVerified: Boolean,
    val rules: List<VerifiedFlowRuleV2>,
) {
    init {
        require(SHA256_HEX.matches(packageId))
        require(scenarioId.isNotBlank())
        require(SHA256_HEX.matches(scenarioSha256) && SHA256_HEX.matches(sourceSha256))
        require(evidenceKind == "independent-original-runtime")
        require(SHA256_HEX.matches(bindingSha256))
        require(bootVerified)
        require(rules.map { it.id } == listOf("menu-map", "map-scene"))
    }
}

object VerifiedFlowProofParser {
    private val rootKeys = setOf(
        "format", "version", "package_id", "scenario_id", "scenario_sha256", "source_sha256",
        "evidence_kind", "evidence_chain", "boot_verified", "rules", "binding_sha256",
    )
    private val chainKeys = setOf("spec_sha256", "observation_sha256", "capture_consensus_sha256")
    private val ruleKeys = setOf("id", "from_screen", "input_kind", "to_screen", "scene_id", "not_before_ms")

    fun parse(text: String): VerifiedFlowProofV2 {
        val root = MiniJson.parse(text).jsonObject("verified-flow")
        require(root.keys == rootKeys) { "verified-flow fields" }
        require(root["format"] == VERIFIED_FLOW_FORMAT && root["version"].jsonInt("version") == VERIFIED_FLOW_VERSION)
        val binding = root["binding_sha256"].jsonString("binding_sha256")
        require(SHA256_HEX.matches(binding)) { "binding_sha256" }
        val bindingBase = root.filterKeys { it != "binding_sha256" }
        require(sha256Hex(MiniJson.canonical(bindingBase)) == binding) { "verified-flow binding hash mismatch" }

        val chain = root["evidence_chain"].jsonObject("evidence_chain")
        require(chain.keys == chainKeys) { "evidence_chain fields" }
        val evidence = VerifiedFlowEvidenceChainV2(
            specSha256 = chain["spec_sha256"].jsonString("spec_sha256"),
            observationSha256 = chain["observation_sha256"].jsonString("observation_sha256"),
            captureConsensusSha256 = chain["capture_consensus_sha256"].jsonString("capture_consensus_sha256"),
        )

        val rules = root["rules"].jsonList("rules").map { item ->
            val row = item.jsonObject("rule")
            require(row.keys == ruleKeys) { "verified-flow rule fields" }
            val sceneValue = row["scene_id"]
            require(sceneValue == null || sceneValue is String) { "scene_id must be string or null" }
            VerifiedFlowRuleV2(
                id = row["id"].jsonString("rule.id"),
                fromScreen = Screen.valueOf(row["from_screen"].jsonString("from_screen")),
                inputKind = row["input_kind"].jsonString("input_kind"),
                toScreen = Screen.valueOf(row["to_screen"].jsonString("to_screen")),
                sceneId = sceneValue as? String,
                notBeforeMs = row["not_before_ms"].jsonInt("not_before_ms").toLong(),
            )
        }

        return VerifiedFlowProofV2(
            packageId = root["package_id"].jsonString("package_id"),
            scenarioId = root["scenario_id"].jsonString("scenario_id"),
            scenarioSha256 = root["scenario_sha256"].jsonString("scenario_sha256"),
            sourceSha256 = root["source_sha256"].jsonString("source_sha256"),
            evidenceKind = root["evidence_kind"].jsonString("evidence_kind"),
            evidenceChain = evidence,
            bindingSha256 = binding,
            bootVerified = root["boot_verified"] as? Boolean ?: error("boot_verified boolean"),
            rules = rules,
        )
    }

    fun canonicalize(text: String): String {
        parse(text)
        val root = MiniJson.parse(text).jsonObject("verified-flow")
        return MiniJson.canonical(root) + "\n"
    }
}

interface FlowGate {
    fun onRuntimeCreated(nowMillis: Long) = Unit
    fun allowRestored(session: Session): Boolean = true
    fun allow(before: Session, input: Input, after: Session, nowMillis: Long): Boolean
}

object AllowAllFlowGate : FlowGate {
    override fun allow(before: Session, input: Input, after: Session, nowMillis: Long): Boolean = true
}

object DenyAllFlowGate : FlowGate {
    override fun allowRestored(session: Session): Boolean =
        session.screen == Screen.MENU && session.selectedSceneId == null
    override fun allow(before: Session, input: Input, after: Session, nowMillis: Long): Boolean =
        input == Input.Reset
}

class VerifiedFlowGate(
    private val proof: VerifiedFlowProofV2,
    scenarioId: String,
    scenarioSha256: String,
    packageId: String,
) : FlowGate {
    private var stageEnteredAtMillis = 0L

    init {
        require(proof.packageId == packageId)
        require(proof.scenarioId == scenarioId)
        require(proof.scenarioSha256 == scenarioSha256)
    }

    override fun onRuntimeCreated(nowMillis: Long) {
        require(nowMillis >= 0L)
        stageEnteredAtMillis = nowMillis
    }

    override fun allowRestored(session: Session): Boolean = when (session.screen) {
        Screen.MENU -> session.selectedSceneId == null
        Screen.MAP -> proof.rules.any { it.toScreen == Screen.MAP } && session.selectedSceneId == null
        Screen.SCENE -> proof.rules.any { it.toScreen == Screen.SCENE && it.sceneId == session.selectedSceneId }
        Screen.COMPLETE -> false
    }

    override fun allow(before: Session, input: Input, after: Session, nowMillis: Long): Boolean {
        if (before == after) return true
        if (input == Input.Reset) {
            stageEnteredAtMillis = nowMillis
            return true
        }
        val kind = when (input) {
            Input.Start -> "start"
            is Input.EnterScene -> "enter-scene"
            else -> return false
        }
        val scene = (input as? Input.EnterScene)?.sceneId
        val elapsed = (nowMillis - stageEnteredAtMillis).coerceAtLeast(0L)
        val matched = proof.rules.any { rule ->
            rule.fromScreen == before.screen && rule.inputKind == kind && rule.toScreen == after.screen &&
                rule.sceneId == scene && elapsed >= rule.notBeforeMs
        }
        if (matched) stageEnteredAtMillis = nowMillis
        return matched
    }
}

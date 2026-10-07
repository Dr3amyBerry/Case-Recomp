package org.rigorcore.caserecomp

private const val VERIFIED_FLOW_FORMAT = "case-recomp-verified-flow"
private val SHA256_HEX = Regex("[0-9a-f]{64}")

data class VerifiedFlowRuleV1(
    val id: String,
    val fromScreen: Screen,
    val inputKind: String,
    val toScreen: Screen,
    val sceneId: String? = null,
    val notBeforeMs: Long = 0L,
) {
    init {
        require(id.isNotBlank() && inputKind in setOf("start", "enter-scene"))
        require(notBeforeMs >= 0L)
        if (inputKind == "enter-scene") require(!sceneId.isNullOrBlank()) else require(sceneId == null)
    }
}

data class VerifiedFlowProofV1(
    val scenarioId: String,
    val scenarioSha256: String,
    val sourceSha256: String,
    val evidenceKind: String,
    val bootVerified: Boolean,
    val rules: List<VerifiedFlowRuleV1>,
) {
    init {
        require(scenarioId.isNotBlank())
        require(SHA256_HEX.matches(scenarioSha256) && SHA256_HEX.matches(sourceSha256))
        require(evidenceKind == "independent-original-runtime")
        require(bootVerified && rules.size == 2 && rules.map { it.id }.toSet().size == rules.size)
        require(rules[0].fromScreen == Screen.MENU && rules[0].inputKind == "start" && rules[0].toScreen == Screen.MAP)
        require(rules[1].fromScreen == Screen.MAP && rules[1].inputKind == "enter-scene" && rules[1].toScreen == Screen.SCENE)
    }
}

object VerifiedFlowProofParser {
    fun parse(text: String): VerifiedFlowProofV1 {
        val root = MiniJson.parse(text).jsonObject("verified-flow")
        require(root["format"] == VERIFIED_FLOW_FORMAT && root["version"].jsonInt("version") == 1)
        val rules = root["rules"].jsonList("rules").map { item ->
            val row = item.jsonObject("rule")
            VerifiedFlowRuleV1(
                id = row["id"].jsonString("rule.id"),
                fromScreen = Screen.valueOf(row["from_screen"].jsonString("from_screen")),
                inputKind = row["input_kind"].jsonString("input_kind"),
                toScreen = Screen.valueOf(row["to_screen"].jsonString("to_screen")),
                sceneId = row["scene_id"] as? String,
                notBeforeMs = (row["not_before_ms"] as? Number)?.toLong() ?: error("not_before_ms number"),
            )
        }
        return VerifiedFlowProofV1(
            scenarioId = root["scenario_id"].jsonString("scenario_id"),
            scenarioSha256 = root["scenario_sha256"].jsonString("scenario_sha256"),
            sourceSha256 = root["source_sha256"].jsonString("source_sha256"),
            evidenceKind = root["evidence_kind"].jsonString("evidence_kind"),
            bootVerified = root["boot_verified"] as? Boolean ?: error("boot_verified boolean"),
            rules = rules,
        )
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
    private val proof: VerifiedFlowProofV1,
    scenarioId: String,
    scenarioSha256: String,
) : FlowGate {
    private var stageEnteredAtMillis = 0L

    init {
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

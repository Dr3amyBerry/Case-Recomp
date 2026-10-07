package org.rigorcore.caserecomp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VerifiedFlowUnitTest {
    private val packageId = "9".repeat(64)
    private val scenarioHash = "a".repeat(64)

    private fun chain() = VerifiedFlowEvidenceChainV2(
        specSha256 = "c".repeat(64),
        observationSha256 = "d".repeat(64),
        captureConsensusSha256 = "e".repeat(64),
    )

    private fun proof() = VerifiedFlowProofV2(
        packageId = packageId,
        scenarioId = "flow",
        scenarioSha256 = scenarioHash,
        sourceSha256 = "b".repeat(64),
        evidenceKind = "independent-original-runtime",
        evidenceChain = chain(),
        bindingSha256 = "f".repeat(64),
        bootVerified = true,
        rules = listOf(
            VerifiedFlowRuleV2("menu-map", Screen.MENU, "start", Screen.MAP, notBeforeMs = 100),
            VerifiedFlowRuleV2("map-scene", Screen.MAP, "enter-scene", Screen.SCENE, "room", 50),
        ),
    )

    private fun scenario() = ScenarioV1(
        id = "flow", designWidth = 320, designHeight = 240,
        scenes = listOf(ScenarioSceneV1("room", 1, 5,
            listOf(ScenarioTargetV1("target", GameRect(10f, 10f, 30f, 30f), 1)))),
    )

    private fun proofDocument(packageValue: String = packageId): MutableMap<String, Any?> = linkedMapOf(
        "format" to "case-recomp-verified-flow",
        "version" to 2L,
        "package_id" to packageValue,
        "scenario_id" to "flow",
        "scenario_sha256" to scenarioHash,
        "source_sha256" to "b".repeat(64),
        "evidence_kind" to "independent-original-runtime",
        "evidence_chain" to linkedMapOf(
            "spec_sha256" to "c".repeat(64),
            "observation_sha256" to "d".repeat(64),
            "capture_consensus_sha256" to "e".repeat(64),
        ),
        "boot_verified" to true,
        "rules" to listOf(
            linkedMapOf<String, Any?>(
                "id" to "menu-map", "from_screen" to "MENU", "input_kind" to "start",
                "to_screen" to "MAP", "scene_id" to null, "not_before_ms" to 0L,
            ),
            linkedMapOf<String, Any?>(
                "id" to "map-scene", "from_screen" to "MAP", "input_kind" to "enter-scene",
                "to_screen" to "SCENE", "scene_id" to "room", "not_before_ms" to 0L,
            ),
        ),
    )

    private fun proofText(packageValue: String = packageId): String {
        val base = proofDocument(packageValue)
        val binding = sha256Hex(MiniJson.canonical(base))
        return MiniJson.canonical(base + ("binding_sha256" to binding))
    }

    @Test fun runtime_gate_is_stage_local_persistent_and_blocks_unverified_scene_rules() {
        val clock = DeterministicClock()
        val store = InMemorySessionStore()
        val audio = RecordingAudioPort()
        val runtime = GameRuntime(scenario(), clock, store, audio,
            flowGate = VerifiedFlowGate(proof(), "flow", scenarioHash, packageId))
        runtime.onCreate(); runtime.onStart(); runtime.onResume()
        runtime.dispatch(Input.Start)
        assertEquals(Screen.MENU, runtime.session.screen)
        clock.advanceBy(100); runtime.dispatch(Input.Start)
        assertEquals(Screen.MAP, runtime.session.screen)
        assertEquals(listOf(AudioCue.START), audio.cues)
        clock.advanceBy(49); runtime.dispatch(Input.EnterScene("room"))
        assertEquals(Screen.MAP, runtime.session.screen)
        clock.advanceBy(1); runtime.dispatch(Input.EnterScene("room"))
        assertEquals(Screen.SCENE, runtime.session.screen)
        val before = runtime.session
        runtime.dispatch(Input.FindObject("target"))
        assertEquals(before, runtime.session)
        val render = runtime.renderFrame()
        val golden = render.screen.toString() + "|" + render.sceneId + "|" + render.frame + "|" +
            render.foundCount + "/" + render.totalTargets + "|" +
            render.targets.joinToString(",") { it.id + ":" + it.z + ":" + it.found }
        assertEquals("SCENE|room|1|0/1|target:1:false", golden)
        runtime.onPause(); runtime.onStop(); runtime.onDestroy()
        assertTrue(store.value != null)
        val restored = GameRuntime(scenario(), DeterministicClock(1000), store, RecordingAudioPort(),
            flowGate = VerifiedFlowGate(proof(), "flow", scenarioHash, packageId))
        restored.onCreate()
        assertEquals(Screen.SCENE, restored.session.screen)
    }

    @Test fun parser_requires_v2_exact_schema_complete_chain_and_binding_digest() {
        val text = proofText()
        val parsed = VerifiedFlowProofParser.parse(text)
        assertTrue(parsed.bootVerified)
        assertEquals(packageId, parsed.packageId)
        assertEquals("c".repeat(64), parsed.evidenceChain.specSha256)
        assertTrue(parsed.bindingSha256.matches(Regex("[0-9a-f]{64}")))
        assertEquals(text + "\n", VerifiedFlowProofParser.canonicalize("\n  " + text + "  \n"))

        fun mutated(block: (MutableMap<String, Any?>) -> Unit): String {
            val root = MiniJson.parse(text).jsonObject("root").toMutableMap()
            block(root)
            return MiniJson.canonical(root)
        }

        assertTrue(runCatching { VerifiedFlowProofParser.parse(mutated { it["version"] = 1L }) }.isFailure)
        assertTrue(runCatching { VerifiedFlowProofParser.parse(mutated { it["evidence_kind"] = "synthetic-test" }) }.isFailure)
        assertTrue(runCatching {
            VerifiedFlowProofParser.parse(mutated {
                val chain = it["evidence_chain"].jsonObject("chain").toMutableMap()
                chain.remove("spec_sha256")
                it["evidence_chain"] = chain
            })
        }.isFailure)
        assertTrue(runCatching { VerifiedFlowProofParser.parse(mutated { it.remove("package_id") }) }.isFailure)
        assertTrue(runCatching { VerifiedFlowProofParser.parse(mutated { it["unexpected"] = 1L }) }.isFailure)
        assertTrue(runCatching {
            VerifiedFlowProofParser.parse(mutated {
                val rules = it["rules"].jsonList("rules").map { row -> row.jsonObject("rule").toMutableMap() }.toMutableList()
                rules[0]["not_before_ms"] = 1L
                it["rules"] = rules
            })
        }.isFailure)
        assertTrue(runCatching {
            VerifiedFlowProofParser.parse(mutated {
                val rules = it["rules"].jsonList("rules").map { row -> row.jsonObject("rule").toMutableMap() }.toMutableList()
                rules[0]["id"] = "other"
                it["rules"] = rules
            })
        }.isFailure)
    }

    @Test fun python_generated_binding_vector_is_accepted_by_kotlin() {
        val url = requireNotNull(javaClass.getResource("/verified-flow-v2-python-vector.json"))
        val parsed = VerifiedFlowProofParser.parse(url.readText())
        assertEquals("1f0645b60bb17ed028c3e97aa43de49f030ab9b1fa532a147de94377ca7bdd83", parsed.bindingSha256)
        assertEquals(packageId, parsed.packageId)
    }

    @Test fun package_and_scenario_binding_and_default_deny_are_fail_closed() {
        assertTrue(runCatching { VerifiedFlowGate(proof(), "flow", scenarioHash, "8".repeat(64)) }.isFailure)
        assertTrue(runCatching { VerifiedFlowGate(proof(), "flow", "c".repeat(64), packageId) }.isFailure)
        val menu = Session()
        assertFalse(DenyAllFlowGate.allow(menu, Input.Start, menu.copy(screen = Screen.MAP), 0))
        assertTrue(DenyAllFlowGate.allow(menu, Input.Reset, menu, 0))
    }
}

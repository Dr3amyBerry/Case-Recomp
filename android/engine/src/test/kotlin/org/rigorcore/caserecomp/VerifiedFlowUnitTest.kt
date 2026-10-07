package org.rigorcore.caserecomp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VerifiedFlowUnitTest {
    private val scenarioHash = "a".repeat(64)

    private fun proof() = VerifiedFlowProofV1(
        scenarioId = "flow",
        scenarioSha256 = scenarioHash,
        sourceSha256 = "b".repeat(64),
        evidenceKind = "independent-original-runtime",
        bootVerified = true,
        rules = listOf(
            VerifiedFlowRuleV1("menu-map", Screen.MENU, "start", Screen.MAP, notBeforeMs = 100),
            VerifiedFlowRuleV1("map-scene", Screen.MAP, "enter-scene", Screen.SCENE, "room", 50),
        ),
    )

    private fun scenario() = ScenarioV1(
        id = "flow", designWidth = 320, designHeight = 240,
        scenes = listOf(ScenarioSceneV1("room", 1, 5,
            listOf(ScenarioTargetV1("target", GameRect(10f, 10f, 30f, 30f), 1)))),
    )

    @Test fun runtime_gate_is_stage_local_persistent_and_blocks_unverified_scene_rules() {
        val clock = DeterministicClock()
        val store = InMemorySessionStore()
        val audio = RecordingAudioPort()
        val runtime = GameRuntime(scenario(), clock, store, audio,
            flowGate = VerifiedFlowGate(proof(), "flow", scenarioHash))
        runtime.onCreate(); runtime.onStart(); runtime.onResume()

        runtime.dispatch(Input.Start)
        assertEquals(Screen.MENU, runtime.session.screen)
        clock.advanceBy(100)
        runtime.dispatch(Input.Start)
        assertEquals(Screen.MAP, runtime.session.screen)
        assertEquals(listOf(AudioCue.START), audio.cues)

        clock.advanceBy(49)
        runtime.dispatch(Input.EnterScene("room"))
        assertEquals(Screen.MAP, runtime.session.screen)
        clock.advanceBy(1)
        runtime.dispatch(Input.EnterScene("room"))
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
            flowGate = VerifiedFlowGate(proof(), "flow", scenarioHash))
        restored.onCreate()
        assertEquals(Screen.SCENE, restored.session.screen)
    }

    @Test fun parser_and_default_deny_are_fail_closed() {
        val text = "{"boot_verified":true,"evidence_kind":"independent-original-runtime","format":"case-recomp-verified-flow","rules":[{"from_screen":"MENU","id":"menu-map","input_kind":"start","not_before_ms":0,"scene_id":null,"to_screen":"MAP"},{"from_screen":"MAP","id":"map-scene","input_kind":"enter-scene","not_before_ms":0,"scene_id":"room","to_screen":"SCENE"}],"scenario_id":"flow","scenario_sha256":"" +
            scenarioHash + "","source_sha256":"" + "b".repeat(64) + "","version":1}"
        assertTrue(VerifiedFlowProofParser.parse(text).bootVerified)
        assertTrue(runCatching { VerifiedFlowProofParser.parse(text.replace("independent-original-runtime", "synthetic-test")) }.isFailure)
        assertTrue(runCatching { VerifiedFlowGate(proof(), "flow", "c".repeat(64)) }.isFailure)
        val menu = Session()
        assertFalse(DenyAllFlowGate.allow(menu, Input.Start, menu.copy(screen = Screen.MAP), 0))
        assertTrue(DenyAllFlowGate.allow(menu, Input.Reset, menu, 0))
    }
}

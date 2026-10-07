package org.rigorcore.caserecomp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class VerifiedFlowFidelityUnitTest {
    private val scenarioHash = "a".repeat(64)
    private val packageId = "9".repeat(64)

    private fun scenario() = ScenarioV1(
        id = "fidelity-flow", designWidth = 100, designHeight = 50,
        scenes = listOf(ScenarioSceneV1("room", 1, 3,
            listOf(ScenarioTargetV1("target", GameRect(10f, 10f, 30f, 30f), 2)))),
    )

    private fun proof() = VerifiedFlowProofV2(
        packageId = packageId,
        scenarioId = "fidelity-flow", scenarioSha256 = scenarioHash,
        sourceSha256 = "b".repeat(64), evidenceKind = "independent-original-runtime",
        evidenceChain = VerifiedFlowEvidenceChainV2("c".repeat(64), "d".repeat(64), "e".repeat(64)),
        bindingSha256 = "f".repeat(64),
        bootVerified = true,
        rules = listOf(
            VerifiedFlowRuleV2("menu-map", Screen.MENU, "start", Screen.MAP, notBeforeMs = 25),
            VerifiedFlowRuleV2("map-scene", Screen.MAP, "enter-scene", Screen.SCENE, "room", 40),
        ),
    )

    @Test fun visual_input_and_timing_fidelity_are_deterministic() {
        val clock = DeterministicClock()
        val runtime = GameRuntime(
            scenario(), clock, InMemorySessionStore(), RecordingAudioPort(),
            flowGate = VerifiedFlowGate(proof(), scenario(), scenarioHash, packageId),
        )
        runtime.onCreate(); runtime.onStart(); runtime.onResume()

        runtime.dispatch(Input.Start)
        assertEquals(Screen.MENU, runtime.session.screen)
        clock.advanceBy(25); runtime.dispatch(Input.Start)
        assertEquals(Screen.MAP, runtime.session.screen)

        runtime.dispatch(Input.EnterScene("room"))
        assertEquals(Screen.MAP, runtime.session.screen)
        clock.advanceBy(40); runtime.dispatch(Input.EnterScene("room"))
        assertEquals(Screen.SCENE, runtime.session.screen)

        val render = runtime.renderFrame()
        val golden = listOf(
            render.screen.name, render.sceneId, render.frame,
            render.designWidth, render.designHeight,
            render.targets.single().id, render.targets.single().z,
            render.targets.single().bounds.left, render.targets.single().bounds.top,
            render.targets.single().bounds.right, render.targets.single().bounds.bottom,
        ).joinToString("|")
        assertEquals("SCENE|room|1|100|50|target|2|10.0|10.0|30.0|30.0", golden)

        val hit = runtime.inputForTap(40f, 40f, 200f, 100f)
        assertEquals(Input.FindObject("target"), hit)
        assertNull(runtime.inputForTap(5f, 5f, 200f, 100f))
        assertNotNull(runtime.renderFrame())
    }
}

package org.rigorcore.caserecomp

import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceBudgetUnitTest {
    @Test fun fixed_engine_and_viewport_workload_stays_within_guardrail() {
        val scenario = ScenarioV1(
            id = "perf", designWidth = 640, designHeight = 360,
            scenes = listOf(ScenarioSceneV1(
                "room", 1, 120,
                (0 until 100).map { index ->
                    val x = (index % 10) * 50f
                    val y = (index / 10) * 30f
                    ScenarioTargetV1("t" + index, GameRect(x, y, x + 20f, y + 20f), index)
                },
            )),
        )
        val engine = Engine(scenario.engineScenario())
        val viewport = LetterboxViewport(640f, 360f)

        repeat(5_000) { viewport.gamePoint(320f, 180f, 1280f, 720f) }
        val start = System.nanoTime()
        var session = Session()
        repeat(50_000) { index ->
            viewport.gamePoint((index % 1280).toFloat(), (index % 720).toFloat(), 1280f, 720f)
            if (index % 1000 == 0) {
                session = engine.update(session, if (session.screen == Screen.MENU) Input.Start else Input.Reset)
            }
            RenderModelBuilder.build(scenario, session)
        }
        val elapsedNs = System.nanoTime() - start
        val nsPerIteration = elapsedNs / 50_000.0
        println("CASE_RECOMP_BENCH engine_viewport_50000 elapsed_ns=" + elapsedNs + " ns_per_iteration=" + nsPerIteration)
        assertTrue("fixed QA workload exceeded catastrophic 15s guardrail", elapsedNs < 15_000_000_000L)
    }

    @Test fun session_codec_fixed_workload_is_reproducible() {
        val snapshot = SessionSnapshotV1(
            "perf", Screen.SCENE, "room", 77,
            mapOf("room" to (0 until 30).map { "target-" + it }.toSet()),
            123456L,
        )
        repeat(200) { SessionSnapshotCodecV1.decode(SessionSnapshotCodecV1.encode(snapshot)) }
        val start = System.nanoTime()
        repeat(5_000) {
            val encoded = SessionSnapshotCodecV1.encode(snapshot)
            check(SessionSnapshotCodecV1.decode(encoded) == snapshot)
        }
        val elapsedNs = System.nanoTime() - start
        println("CASE_RECOMP_BENCH session_codec_5000 elapsed_ns=" + elapsedNs + " ns_per_roundtrip=" + (elapsedNs / 5_000.0))
        assertTrue("session codec workload exceeded catastrophic 15s guardrail", elapsedNs < 15_000_000_000L)
    }
}

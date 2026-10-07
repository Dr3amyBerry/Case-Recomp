package org.rigorcore.caserecomp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeUnitTest {
    private fun scenario() = ScenarioV1(
        id = "unit",
        designWidth = 320,
        designHeight = 240,
        scenes = listOf(
            ScenarioSceneV1("room", 4, 12, listOf(
                ScenarioTargetV1("a", GameRect(10f, 10f, 60f, 60f), 1),
                ScenarioTargetV1("b", GameRect(40f, 40f, 90f, 90f), 2),
            )),
        ),
    )

    @Test fun persistence_round_trip_and_checksum() {
        val snapshot = SessionSnapshotV1("unit", Screen.SCENE, "room", 7, mapOf("room" to setOf("a")), 123)
        val encoded = SessionSnapshotCodecV1.encode(snapshot)
        assertEquals(snapshot, SessionSnapshotCodecV1.decode(encoded))
        assertNull(SessionSnapshotCodecV1.decode(encoded.replace("frame=7", "frame=8")))
    }

    @Test fun lifecycle_render_input_and_audio_are_deterministic() {
        val clock = DeterministicClock(100)
        val store = InMemorySessionStore()
        val audio = RecordingAudioPort()
        val runtime = GameRuntime(scenario(), clock, store, audio)
        runtime.onCreate(); runtime.onStart(); runtime.onResume()
        runtime.dispatch(Input.Start)
        runtime.dispatch(Input.EnterScene("room"))
        assertEquals(4, runtime.session.frame)
        val hit = runtime.inputForTap(100f, 100f, 640f, 480f)
        assertEquals(Input.FindObject("b"), hit)
        runtime.dispatch(hit!!)
        val render = runtime.renderFrame()
        assertEquals(1, render.foundCount)
        assertTrue(render.targets.first { it.id == "b" }.found)
        assertFalse(render.targets.first { it.id == "a" }.found)
        clock.advanceBy(50)
        runtime.onPause(); runtime.onStop()
        assertNotNull(store.load())
        assertEquals(listOf(AudioCue.START, AudioCue.TARGET_FOUND), audio.cues)
    }

    @Test fun invalid_or_incompatible_persistence_fails_closed() {
        val clock = DeterministicClock()
        val bad = SessionSnapshotV1("other", Screen.MAP, null, 1, emptyMap(), 0)
        val runtime = GameRuntime(scenario(), clock, InMemorySessionStore(SessionSnapshotCodecV1.encode(bad)))
        runtime.onCreate()
        assertEquals(Session(), runtime.session)
    }
}

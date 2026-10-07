package org.rigorcore.caserecomp.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.rigorcore.caserecomp.DeterministicClock
import org.rigorcore.caserecomp.GameRuntime
import org.rigorcore.caserecomp.InMemorySessionStore
import org.rigorcore.caserecomp.Input
import org.rigorcore.caserecomp.RecordingAudioPort
import org.rigorcore.caserecomp.Screen

class SyntheticShellUnitTest {
    @Test fun synthetic_shell_completes_without_game_assets() {
        val audio = RecordingAudioPort()
        val runtime = GameRuntime(SyntheticContent.scenario(), DeterministicClock(), InMemorySessionStore(), audio)
        runtime.onCreate(); runtime.onStart(); runtime.onResume()
        runtime.dispatch(Input.Start)
        runtime.dispatch(Input.EnterScene("synthetic-room"))
        runtime.dispatch(Input.FindObject("circle"))
        runtime.dispatch(Input.FindObject("square"))
        assertEquals(Screen.COMPLETE, runtime.session.screen)
        assertTrue(audio.cues.isNotEmpty())
    }
}

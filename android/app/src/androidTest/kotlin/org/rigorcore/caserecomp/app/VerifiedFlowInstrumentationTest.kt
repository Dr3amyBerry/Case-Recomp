package org.rigorcore.caserecomp.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.rigorcore.caserecomp.ContentAssetV1
import org.rigorcore.caserecomp.ContentBindingsV1
import org.rigorcore.caserecomp.DeterministicClock
import org.rigorcore.caserecomp.GameRect
import org.rigorcore.caserecomp.GameRuntime
import org.rigorcore.caserecomp.InMemorySessionStore
import org.rigorcore.caserecomp.PrivateContentManifestV1
import org.rigorcore.caserecomp.RecordingAudioPort
import org.rigorcore.caserecomp.ScenarioSceneV1
import org.rigorcore.caserecomp.ScenarioTargetV1
import org.rigorcore.caserecomp.ScenarioV1
import org.rigorcore.caserecomp.Screen
import org.rigorcore.caserecomp.VerifiedFlowGate
import java.io.ByteArrayInputStream
import java.io.File

@RunWith(AndroidJUnit4::class)
class VerifiedFlowInstrumentationTest {
    private lateinit var context: Context

    @Before fun clean() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "private-flow").deleteRecursively()
    }

    @Test fun imported_proof_gates_timing_navigation_and_scene_render() {
        val scenarioHash = "a".repeat(64)
        val assetHash = "c".repeat(64)
        val scenario = ScenarioV1(
            id = "instrumented-flow", designWidth = 100, designHeight = 50,
            scenes = listOf(ScenarioSceneV1("room", 1, 2,
                listOf(ScenarioTargetV1("target", GameRect(10f, 10f, 30f, 30f), 1)))),
        )
        val manifest = PrivateContentManifestV1(
            packageId = "d".repeat(64), scenarioPath = "scenario.json", scenarioSha256 = scenarioHash,
            scenarioId = scenario.id,
            assets = listOf(ContentAssetV1("sha256-" + assetHash, "assets/" + assetHash + ".png", assetHash, 0, "image/png")),
            bindings = ContentBindingsV1(), conversionManifestSha256 = "e".repeat(64),
        )
        val loaded = LoadedPrivateContent(manifest, scenario, context.filesDir)
        val proofText = """
            {"boot_verified":true,"evidence_kind":"independent-original-runtime","format":"case-recomp-verified-flow",
             "rules":[
               {"from_screen":"MENU","id":"menu-map","input_kind":"start","not_before_ms":100,"scene_id":null,"to_screen":"MAP"},
               {"from_screen":"MAP","id":"map-scene","input_kind":"enter-scene","not_before_ms":50,"scene_id":"room","to_screen":"SCENE"}],
             "scenario_id":"instrumented-flow","scenario_sha256":"SCENARIO_HASH","source_sha256":"SOURCE_HASH","version":1}
        """.trimIndent().replace("SCENARIO_HASH", scenarioHash).replace("SOURCE_HASH", "f".repeat(64))
        val repository = PrivateVerifiedFlowRepository(context)
        val imported = repository.importProof(ByteArrayInputStream(proofText.toByteArray()), loaded)
        assertNotNull(repository.loadFor(loaded))

        val clock = DeterministicClock()
        val runtime = GameRuntime(scenario, clock, InMemorySessionStore(), RecordingAudioPort(),
            flowGate = VerifiedFlowGate(imported, scenario.id, scenarioHash))
        runtime.onCreate(); runtime.onStart(); runtime.onResume()

        lateinit var view: GameShellView
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view = GameShellView(context, runtime, loaded, NoopBitmapAssetLoader)
            view.measure(android.view.View.MeasureSpec.makeMeasureSpec(200, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(100, android.view.View.MeasureSpec.EXACTLY))
            view.layout(0, 0, 200, 100)
            fun tap() {
                val event = MotionEvent.obtain(0, 1, MotionEvent.ACTION_UP, 20f, 20f, 0)
                view.onTouchEvent(event); event.recycle()
            }
            tap(); assertEquals(Screen.MENU, runtime.session.screen)
            clock.advanceBy(100); tap(); assertEquals(Screen.MAP, runtime.session.screen)
            tap(); assertEquals(Screen.MAP, runtime.session.screen)
            clock.advanceBy(50); tap(); assertEquals(Screen.SCENE, runtime.session.screen)
            view.draw(Canvas(Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)))
        }
        assertEquals(Screen.SCENE, runtime.session.screen)
        assertTrue(File(context.filesDir, "private-flow/" + manifest.packageId + ".crflow").isFile)
        assertTrue(repository.clearFor(loaded))
        assertEquals(null, repository.loadFor(loaded))
    }
}

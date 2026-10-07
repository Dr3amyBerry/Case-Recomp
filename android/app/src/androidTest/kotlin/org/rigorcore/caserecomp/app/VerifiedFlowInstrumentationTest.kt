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
import org.junit.Assert.assertNull
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
    private val scenarioHash = "a".repeat(64)
    private val packageId = "d".repeat(64)

    @Before fun clean() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "private-flow").deleteRecursively()
    }

    private fun loaded(packageOverride: String = packageId): LoadedPrivateContent {
        val assetHash = "c".repeat(64)
        val scenario = ScenarioV1(
            id = "instrumented-flow", designWidth = 100, designHeight = 50,
            scenes = listOf(ScenarioSceneV1("room", 1, 2,
                listOf(ScenarioTargetV1("target", GameRect(10f, 10f, 30f, 30f), 1)))),
        )
        val manifest = PrivateContentManifestV1(
            packageId = packageOverride, scenarioPath = "scenario.json", scenarioSha256 = scenarioHash,
            scenarioId = scenario.id,
            assets = listOf(ContentAssetV1("sha256-" + assetHash, "assets/" + assetHash + ".png", assetHash, 0, "image/png")),
            bindings = ContentBindingsV1(), conversionManifestSha256 = "e".repeat(64),
        )
        return LoadedPrivateContent(manifest, scenario, context.filesDir)
    }

    private fun proofText(packageOverride: String = packageId): String = """
        {"boot_verified":true,"evidence_kind":"independent-original-runtime",
         "evidence_chain":{"capture_consensus_sha256":"8888888888888888888888888888888888888888888888888888888888888888","observation_sha256":"7777777777777777777777777777777777777777777777777777777777777777","spec_sha256":"6666666666666666666666666666666666666666666666666666666666666666"},
         "format":"case-recomp-verified-flow","package_id":"$packageOverride",
         "rules":[
           {"from_screen":"MENU","id":"menu-map","input_kind":"start","not_before_ms":100,"scene_id":null,"to_screen":"MAP"},
           {"from_screen":"MAP","id":"map-scene","input_kind":"enter-scene","not_before_ms":50,"scene_id":"room","to_screen":"SCENE"}],
         "scenario_id":"instrumented-flow","scenario_sha256":"$scenarioHash","source_sha256":"ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff","version":2}
    """.trimIndent()

    @Test fun imported_v2_proof_gates_timing_navigation_and_scene_render() {
        val loaded = loaded()
        val repository = PrivateVerifiedFlowRepository(context)
        val imported = repository.importProof(ByteArrayInputStream(proofText().toByteArray()), loaded)
        assertNotNull(repository.loadFor(loaded))
        val clock = DeterministicClock()
        val runtime = GameRuntime(loaded.scenario, clock, InMemorySessionStore(), RecordingAudioPort(),
            flowGate = VerifiedFlowGate(imported, loaded.scenario.id, scenarioHash, packageId))
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
        assertTrue(File(context.filesDir, "private-flow/" + packageId + ".crflow").isFile)
        assertTrue(repository.clearFor(loaded))
        assertNull(repository.loadFor(loaded))
    }

    @Test fun legacy_tampered_and_cross_package_imports_fail_without_replacing_valid_proof() {
        val loaded = loaded()
        val repository = PrivateVerifiedFlowRepository(context)
        repository.importProof(ByteArrayInputStream(proofText().toByteArray()), loaded)
        assertNotNull(repository.loadFor(loaded))
        val legacy = proofText().dropLast(2) + "1}"
        assertTrue(runCatching { repository.importProof(ByteArrayInputStream(legacy.toByteArray()), loaded) }.isFailure)
        assertNotNull(repository.loadFor(loaded))
        val wrongPackage = proofText("9".repeat(64))
        assertTrue(runCatching { repository.importProof(ByteArrayInputStream(wrongPackage.toByteArray()), loaded) }.isFailure)
        assertNotNull(repository.loadFor(loaded))
        val tamperedChain = proofText().replace("6".repeat(64), "not-a-hash")
        assertTrue(runCatching { repository.importProof(ByteArrayInputStream(tamperedChain.toByteArray()), loaded) }.isFailure)
        assertNotNull(repository.loadFor(loaded))
        val stored = File(context.filesDir, "private-flow/" + packageId + ".crflow")
        stored.writeText(legacy, Charsets.UTF_8)
        assertNull(repository.loadFor(loaded))
    }

    @Test fun same_scenario_in_different_package_cannot_reuse_proof() {
        val repository = PrivateVerifiedFlowRepository(context)
        val other = loaded("9".repeat(64))
        assertTrue(runCatching {
            repository.importProof(ByteArrayInputStream(proofText().toByteArray()), other)
        }.isFailure)
        assertNull(repository.loadFor(other))
    }
}

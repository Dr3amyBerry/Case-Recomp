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
import org.rigorcore.caserecomp.MiniJson
import org.rigorcore.caserecomp.PrivateContentManifestV1
import org.rigorcore.caserecomp.RecordingAudioPort
import org.rigorcore.caserecomp.ScenarioSceneV1
import org.rigorcore.caserecomp.ScenarioTargetV1
import org.rigorcore.caserecomp.ScenarioV1
import org.rigorcore.caserecomp.Screen
import org.rigorcore.caserecomp.VerifiedFlowGate
import org.rigorcore.caserecomp.sha256Hex
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

    private fun proofText(packageOverride: String = packageId): String {
        val base = linkedMapOf<String, Any?>(
            "format" to "case-recomp-verified-flow",
            "version" to 2L,
            "package_id" to packageOverride,
            "scenario_id" to "instrumented-flow",
            "scenario_sha256" to scenarioHash,
            "source_sha256" to "f".repeat(64),
            "evidence_kind" to "independent-original-runtime",
            "evidence_chain" to linkedMapOf(
                "spec_sha256" to "6".repeat(64),
                "observation_sha256" to "7".repeat(64),
                "capture_consensus_sha256" to "8".repeat(64),
            ),
            "boot_verified" to true,
            "rules" to listOf(
                linkedMapOf<String, Any?>(
                    "from_screen" to "MENU", "id" to "menu-map", "input_kind" to "start",
                    "not_before_ms" to 100L, "scene_id" to null, "to_screen" to "MAP",
                ),
                linkedMapOf<String, Any?>(
                    "from_screen" to "MAP", "id" to "map-scene", "input_kind" to "enter-scene",
                    "not_before_ms" to 50L, "scene_id" to "room", "to_screen" to "SCENE",
                ),
            ),
        )
        val binding = sha256Hex(MiniJson.canonical(base))
        return MiniJson.canonical(base + ("binding_sha256" to binding))
    }

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
        val valid = proofText()
        repository.importProof(ByteArrayInputStream(valid.toByteArray()), loaded)
        assertNotNull(repository.loadFor(loaded))

        val legacy = valid.dropLast(2) + "1}"
        assertTrue(runCatching { repository.importProof(ByteArrayInputStream(legacy.toByteArray()), loaded) }.isFailure)
        assertNotNull(repository.loadFor(loaded))

        val wrongPackage = proofText("9".repeat(64))
        assertTrue(runCatching { repository.importProof(ByteArrayInputStream(wrongPackage.toByteArray()), loaded) }.isFailure)
        assertNotNull(repository.loadFor(loaded))

        val tamperedChain = valid.replace("6".repeat(64), "0".repeat(64))
        assertTrue(runCatching { repository.importProof(ByteArrayInputStream(tamperedChain.toByteArray()), loaded) }.isFailure)
        assertNotNull(repository.loadFor(loaded))

        val tamperedTiming = valid.replace(":100,", ":101,")
        assertTrue(runCatching { repository.importProof(ByteArrayInputStream(tamperedTiming.toByteArray()), loaded) }.isFailure)
        assertNotNull(repository.loadFor(loaded))

        val stored = File(context.filesDir, "private-flow/" + packageId + ".crflow")
        stored.writeText(legacy, Charsets.UTF_8)
        assertNull(repository.loadFor(loaded))
    }

    @Test fun import_persists_canonical_v2_and_recovers_only_valid_interrupted_backup() {
        val loaded = loaded()
        val repository = PrivateVerifiedFlowRepository(context)
        val valid = proofText()
        val nonCanonical = "\n  " + valid + "  \n"
        repository.importProof(ByteArrayInputStream(nonCanonical.toByteArray()), loaded)

        val directory = File(context.filesDir, "private-flow")
        val target = File(directory, packageId + ".crflow")
        val expectedCanonical = MiniJson.canonical(MiniJson.parse(valid).jsonObject("proof")) + "\n"
        assertEquals(expectedCanonical, target.readText(Charsets.UTF_8))

        // Simulate process death after target -> .bak but before .tmp -> target.
        val backup = File(directory, target.name + ".bak")
        assertTrue(target.renameTo(backup))
        File(directory, target.name + ".tmp").writeText("untrusted-staging", Charsets.UTF_8)
        assertNotNull(repository.loadFor(loaded))
        assertTrue(target.isFile)
        assertTrue(!backup.exists())
        assertTrue(!File(directory, target.name + ".tmp").exists())

        // clearFor must erase every recoverable transactional copy.
        assertTrue(target.renameTo(backup))
        File(directory, target.name + ".tmp").writeText("stale", Charsets.UTF_8)
        assertTrue(repository.clearFor(loaded))
        assertTrue(!target.exists() && !backup.exists() && !File(directory, target.name + ".tmp").exists())
        assertNull(repository.loadFor(loaded))

        // Re-import, then a corrupt target with a valid interrupted backup recovers safely.
        repository.importProof(ByteArrayInputStream(valid.toByteArray()), loaded)
        assertTrue(target.renameTo(backup))
        target.writeText("corrupt", Charsets.UTF_8)
        assertNotNull(repository.loadFor(loaded))
        assertTrue(target.isFile && !backup.exists())

        // Invalid backup must never be promoted.
        assertTrue(target.delete())
        backup.writeText("{\"format\":\"case-recomp-verified-flow\",\"version\":1}", Charsets.UTF_8)
        assertNull(repository.loadFor(loaded))
        assertTrue(!target.exists())
        assertTrue(!backup.exists())
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

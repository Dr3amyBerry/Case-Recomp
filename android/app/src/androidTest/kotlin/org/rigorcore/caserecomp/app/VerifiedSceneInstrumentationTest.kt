package org.rigorcore.caserecomp.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.rigorcore.caserecomp.AllowAllFlowGate
import org.rigorcore.caserecomp.ContentAssetV1
import org.rigorcore.caserecomp.ContentBindingsV1
import org.rigorcore.caserecomp.DeterministicClock
import org.rigorcore.caserecomp.GameRect
import org.rigorcore.caserecomp.GameRuntime
import org.rigorcore.caserecomp.InMemorySessionStore
import org.rigorcore.caserecomp.Input
import org.rigorcore.caserecomp.MiniJson
import org.rigorcore.caserecomp.PrivateContentManifestV1
import org.rigorcore.caserecomp.ScenarioSceneV1
import org.rigorcore.caserecomp.ScenarioTargetV1
import org.rigorcore.caserecomp.ScenarioV1
import org.rigorcore.caserecomp.Screen
import org.rigorcore.caserecomp.VerifiedSceneGate
import org.rigorcore.caserecomp.VerifiedSceneProofParser
import org.rigorcore.caserecomp.sha256Hex
import java.io.ByteArrayInputStream
import java.io.File

@RunWith(AndroidJUnit4::class)
class VerifiedSceneInstrumentationTest {
    private lateinit var context: Context
    private lateinit var proofText: String
    private val packageId = "d".repeat(64)

    @Before fun clean() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "private-scene").deleteRecursively()
        proofText = InstrumentationRegistry.getInstrumentation().context.assets
            .open("synthetic-verified-scene.crscene").use { it.readBytes().toString(Charsets.UTF_8) }
    }

    private fun loaded(
        packageOverride: String = packageId,
        traceSource: String? = "7".repeat(64),
        width: Int = 64,
    ): LoadedPrivateContent {
        val proof = VerifiedSceneProofParser.parse(proofText)
        val scenario = ScenarioV1(id = "synthetic-scene-proof", designWidth = width, designHeight = 48,
            scenes = listOf(ScenarioSceneV1("room", 1, 2, listOf(ScenarioTargetV1("placeholder", GameRect(1f, 1f, 4f, 4f), 1)))))
        val manifest = PrivateContentManifestV1(
            packageId = packageOverride, scenarioPath = "scenario.json", scenarioSha256 = proof.scenarioSha256,
            scenarioId = scenario.id,
            assets = listOf(ContentAssetV1("sha256-" + "c".repeat(64), "assets/" + "c".repeat(64) + ".png", "c".repeat(64), 0, "image/png")),
            bindings = ContentBindingsV1(), conversionManifestSha256 = "e".repeat(64),
        )
        return LoadedPrivateContent(manifest, scenario, context.filesDir, traceSource)
    }

    private fun mutated(change: (MutableMap<String, Any?>) -> Unit): ByteArrayInputStream {
        @Suppress("UNCHECKED_CAST")
        val root = (MiniJson.parse(proofText) as Map<String, Any?>).toMutableMap()
        change(root)
        root.remove("binding_sha256")
        root["binding_sha256"] = sha256Hex(MiniJson.canonical(root))
        return ByteArrayInputStream(MiniJson.canonical(root).toByteArray())
    }

    @Test fun imported_scene_proof_plays_scene_to_acknowledged_lock() {
        val content = loaded()
        val repository = PrivateVerifiedSceneRepository(context)
        repository.importProof(ByteArrayInputStream(proofText.toByteArray()), content)
        val proofs = repository.loadFor(content)
        assertEquals(listOf("room"), proofs.map { it.sceneId })

        val scenario = proofs.fold(content.scenario) { current, proof -> proof.applyTo(current) }
        val runtime = GameRuntime(scenario, DeterministicClock(), InMemorySessionStore(),
            flowGate = VerifiedSceneGate(AllowAllFlowGate, proofs))
        runtime.onCreate(); runtime.onStart(); runtime.onResume()
        runtime.dispatch(Input.Start); runtime.dispatch(Input.EnterScene("room"))
        listOf(4.5f to 4.5f, 41.5f to 31.5f).forEach { (x, y) ->
            runtime.dispatch(requireNotNull(runtime.inputForTap(x, y, 64f, 48f)))
        }
        assertTrue(runtime.renderFrame().awaitingAcknowledge)
        runtime.dispatch(requireNotNull(runtime.inputForTap(30.5f, 30.5f, 64f, 48f)))
        assertEquals(Screen.MAP, runtime.session.screen)
        assertEquals(Screen.MAP, runtime.dispatch(Input.EnterScene("room")).session.screen)
        assertTrue(repository.clearFor(content))
        assertTrue(repository.loadFor(content).isEmpty())
    }

    @Test fun unbound_scene_proofs_are_rejected() {
        val repository = PrivateVerifiedSceneRepository(context)
        val raw = { ByteArrayInputStream(proofText.toByteArray()) }
        listOf(
            { repository.importProof(raw(), loaded(packageOverride = "f".repeat(64))) },
            { repository.importProof(raw(), loaded(traceSource = null)) },
            { repository.importProof(raw(), loaded(traceSource = "1".repeat(64))) },
            { repository.importProof(raw(), loaded(width = 40)) },
            { repository.importProof(mutated { it["scene_id"] = "missing" }, loaded()) },
            { repository.importProof(ByteArrayInputStream(ByteArray(0)), loaded()) },
        ).forEach { attempt -> assertTrue(runCatching(attempt).isFailure) }
        assertTrue(repository.loadFor(loaded()).isEmpty())
    }
}

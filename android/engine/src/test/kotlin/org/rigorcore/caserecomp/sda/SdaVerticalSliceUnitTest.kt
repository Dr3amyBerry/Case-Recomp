package org.rigorcore.caserecomp.sda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SdaVerticalSliceUnitTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun synthetic_vault_scene_vertical_slice_playable_and_resumable() {
        val packageFile = File(tempFolder.root, "synthetic-vault.zip")
        SyntheticSdaPackage.buildVaultPackage(packageFile)
        assertTrue(packageFile.isFile && packageFile.length() > 0)

        // Mock decoder: wraps 32x32 sample texture with non-zero alpha
        val alphas = ByteArray(32 * 32) { 255.toByte() }
        val samplePixelSource = SdaBufferPixelSource(32, 32, alphas)
        val decoder = SdaImageDecoder { samplePixelSource }

        val content = SdaContent.open(packageFile, decoder)
        content.verifyAll()
        assertNotNull(content.coverPng())

        val scene = content.loadScene("SCENE_VAULT.MSL", seed = null)
        assertEquals("SCENE_VAULT.MSL", scene.name)
        assertEquals(2, scene.targets.size)
        assertEquals(listOf("Caja fuerte", "Ficha de casino"), scene.remainingCaptions())
        assertEquals(0, scene.score.points)

        // Step 1: Hit 'safe' at (105, 105)
        val click1 = scene.click(105, 105)
        assertTrue(click1 is SdaClickResult.Found)
        assertEquals("safe", (click1 as SdaClickResult.Found).id)
        assertEquals(7500, click1.gain)
        assertEquals(listOf("Ficha de casino"), scene.remainingCaptions())

        // Advance frames until 'safe' is retired
        while (scene.objects["safe"]?.motion?.removed != true) {
            scene.advance(0.04f)
        }
        assertTrue(scene.objects["safe"]!!.motion!!.removed)

        // Step 2: Hit 'chip' at (205, 155)
        val click2 = scene.click(205, 155)
        assertTrue(click2 is SdaClickResult.Found)
        assertEquals("chip", (click2 as SdaClickResult.Found).id)
        assertEquals(8500, click2.gain)
        assertTrue(scene.remainingCaptions().isEmpty())
        assertEquals(16000, scene.score.points)

        // Advance frames until 'chip' motion and target row fade-out are retired
        while (!scene.batchRetired) {
            scene.advance(0.04f)
        }
        assertTrue(scene.objects["chip"]!!.motion!!.removed)
        assertTrue(scene.batchRetired)

        // Step 3: Snapshot and restore verification
        val snapshot = scene.snapshot()
        assertEquals(16000, snapshot.scorePoints)
        assertTrue(snapshot.rows.all { it.removed })

        val restoredScene = content.loadScene("SCENE_VAULT.MSL", seed = null)
        restoredScene.restore(snapshot)
        assertEquals(16000, restoredScene.score.points)
        assertTrue(restoredScene.batchRetired)
        assertTrue(restoredScene.remainingCaptions().isEmpty())

        content.close()
    }

    @Test
    fun authentic_vault_scene_verifiable_if_available() {
        val candidates = listOf(
            File("../../local-output/vegas_vault.zip"),
            File("../local-output/vegas_vault.zip"),
            File("local-output/vegas_vault.zip"),
        )
        val packageFile = candidates.firstOrNull { it.isFile } ?: return

        val decoder = SdaImageDecoder { bytes ->
            // Minimal PNG parser/dimension extractor for headless unit tests
            val w = if (bytes.size >= 24) ((bytes[16].toInt() and 0xFF) shl 24) or
                    ((bytes[17].toInt() and 0xFF) shl 16) or
                    ((bytes[18].toInt() and 0xFF) shl 8) or
                    (bytes[19].toInt() and 0xFF) else 32
            val h = if (bytes.size >= 24) ((bytes[20].toInt() and 0xFF) shl 24) or
                    ((bytes[21].toInt() and 0xFF) shl 16) or
                    ((bytes[22].toInt() and 0xFF) shl 8) or
                    (bytes[23].toInt() and 0xFF) else 32
            object : SdaPixelSource {
                override val width: Int = maxOf(1, w)
                override val height: Int = maxOf(1, h)
                override fun getAlpha(px: Int, py: Int): Int = 255
            }
        }

        val content = SdaContent.open(packageFile, decoder)
        content.verifyAll()
        assertNotNull(content.coverPng())

        val scene = content.loadScene("SCENE_VAULT.MSL", seed = 12345L)
        assertEquals("SCENE_VAULT.MSL", scene.name)
        assertEquals(87, scene.objects.size)
        assertTrue(scene.drawOrder.size >= 88)
        assertEquals(10, scene.activeSets.size)
        assertTrue(scene.remainingCaptions().isNotEmpty())

        // Hit first active target
        val firstTarget = scene.targets.first()
        val sprite = scene.objects[firstTarget]!!
        val click = scene.click(sprite.x + 1, sprite.y + 1)
        assertTrue(click is SdaClickResult.Found)
        assertEquals(firstTarget, (click as SdaClickResult.Found).id)

        // Checkpoint JSON round-trip
        val state = scene.snapshot()
        val json = state.toJson()
        val restoredState = SdaSceneState.fromJson(json)
        assertEquals(state.scorePoints, restoredState.scorePoints)
        assertEquals(state.sceneName, restoredState.sceneName)
        assertEquals(state.activeSets, restoredState.activeSets)

        content.close()
    }
}

package org.rigorcore.caserecomp.sda

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import org.rigorcore.caserecomp.MiniJson
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Own synthetic fixture for the unlimited search: refilled lists, per-scene quotas, no collectibles. */
class SdaUnlimitedUnitTest {
    private val unlimitedXui = """<xui><mpi:levels><mpi:level clue="1" setsinscene="12,1" scenes="one,two"/></mpi:levels></xui>"""

    private fun withContent(block: (SdaContent) -> Unit) {
        val file = File.createTempFile("sda-unlimited-unit-", ".zip")
        try {
            val many = buildString {
                append("""<xui><texture id="t" uri="pixel.png"/><mpi:key id="key" x="790" y="590" tex="t"/>""")
                for (i in 0 until 12) append("""<eyespyimage id="o$i" x="${200 + i * 10}" y="20" tex="t"/>""")
                for (i in 0 until 12) append("""<eyespyset objects="o$i" itemnamelist="Item $i"/>""")
                append("</xui>")
            }
            val single = """<xui><texture id="t" uri="pixel.png"/><eyespyimage id="s" x="300" y="300" tex="t"/>
                <eyespyset objects="s" itemnamelist="Single"/></xui>"""
            val files = mapOf(
                "SCENE_ONE.MSL" to many.toByteArray(),
                "SCENE_TWO.MSL" to single.toByteArray(),
                "pixel.png" to SyntheticSdaPackage.createSamplePng(3, 3),
            )
            val manifest = MiniJson.canonical(mapOf(
                "format" to "case-recomp-sda-content", "version" to 1,
                "game_id" to "own-unlimited-fixture", "files" to files.mapValues {
                    listOf(it.value.size, MessageDigest.getInstance("SHA-256").digest(it.value)
                        .joinToString("") { b -> "%02x".format(b) })
                },
            )).toByteArray()
            ZipOutputStream(file.outputStream()).use { zip ->
                for ((name, bytes) in files + ("manifest.json" to manifest)) {
                    zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
                }
            }
            SdaContent.open(file) { SdaBufferPixelSource(3, 3, ByteArray(9) { -1 }) }.use(block)
        } finally { file.delete() }
    }

    private fun campaign() = SdaCampaign(listOf(SdaLevels.parseUnlimited(unlimitedXui.toByteArray())), seed = 7, unlimited = true)

    private fun findFirstListed(camp: SdaCampaign) {
        val scene = camp.currentScene!!
        val id = scene.activeSets.first { set -> scene.rows[set]?.removed == false && set.any { scene.objects.getValue(it).found.not() } }.first()
        val sprite = scene.objects.getValue(id)
        assertTrue(camp.clickScene(sprite.x + 1, sprite.y + 1) is SdaClickResult.Found)
        repeat(100) { camp.advance(.04f) }
    }

    @Test fun parses_unlimited_levels_with_quotas() {
        val level = SdaLevels.parseUnlimited(unlimitedXui.toByteArray())
        assertEquals(listOf("one", "two"), level.scenes)
        assertEquals(listOf(12, 1), level.sceneQuotas)
        assertEquals(13, level.objects)
        assertEquals(SdaLevels.UNLIMITED_TIME, level.time, 0f)
    }

    @Test fun list_refills_one_by_one_until_the_scene_is_empty() = withContent { content ->
        val camp = campaign()
        val scene = camp.enterScene("one", content)
        assertEquals(10, scene.activeSets.size)
        assertEquals(12, camp.sceneRemaining("one"))
        assertTrue(scene.collectibles.none { camp.collectibleAvailable(it) })
        val second = scene.activeSets[1]
        findFirstListed(camp)
        // The found row leaves, the rest shift up and the next queued set is appended.
        assertEquals(second, scene.activeSets[0])
        assertEquals(10, scene.activeSets.size)
        assertEquals(11, camp.sceneRemaining("one"))
        assertEquals(SdaCampaignPhase.SCENE, camp.phase)
        repeat(11) { findFirstListed(camp) }
        assertEquals(0, camp.sceneRemaining("one"))
        assertEquals(SdaCampaignPhase.SCENE_COMPLETE, camp.phase)
        assertEquals(1, camp.remainingObjects)
        camp.toInvestigationMap()
        camp.enterScene("two", content)
        findFirstListed(camp)
        assertEquals(SdaCampaignPhase.SCENE_COMPLETE, camp.phase)
        assertTrue(camp.unlimitedFinished)
        camp.toInvestigationMap()
        assertEquals(SdaCampaignPhase.MAP, camp.phase)
        assertTrue(camp.points >= 13 * 5000) // Fast finds add the native speed bonus.
    }

    @Test fun unlimited_clock_counts_up_without_timeout() = withContent { content ->
        val camp = campaign()
        camp.enterScene("one", content)
        repeat(1000) { camp.advance(1f) }
        assertEquals(SdaCampaignPhase.SCENE, camp.phase)
        assertEquals("00:16:40", camp.clock.text(unlimited = true))
    }

    @Test fun refilled_scene_survives_checkpoint_restore() = withContent { content ->
        val camp = campaign()
        camp.enterScene("one", content)
        repeat(3) { findFirstListed(camp) }
        val saved = camp.snapshot().toJson()
        val restored = campaign()
        restored.restore(SdaCampaignState.fromJson(saved), content)
        assertEquals(saved, restored.snapshot().toJson())
        assertEquals(9, restored.sceneRemaining("one"))
        repeat(9) { findFirstListed(restored) }
        assertEquals(SdaCampaignPhase.SCENE_COMPLETE, restored.phase)
    }
}

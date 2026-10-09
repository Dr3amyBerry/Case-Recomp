package org.rigorcore.caserecomp.sda

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import org.rigorcore.caserecomp.MiniJson
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Own synthetic fixtures. These are unit journeys, not proof of Vegas completion. */
class SdaCampaignUnitTest {
    private fun levels() = listOf(
        SdaLevel(1, 1320f, 2, listOf("one", "two"), "Fixture 1", "test.trg"),
        SdaLevel(2, 1800f, 2, listOf("one", "two"), "Fixture 2", "test.trg"),
    )

    private fun withContent(block: (SdaContent) -> Unit) {
        val file = File.createTempFile("sda-campaign-unit-", ".zip")
        try {
            val compound = """<xui><texture id="t" uri="pixel.png"/>
                <eyespyimage id="a" x="200" y="20" tex="t"/>
                <eyespyimage id="b" x="210" y="20" tex="t"/>
                <eyespyset objects="a,b" itemnamelist="First,Second"/></xui>"""
            val files = mapOf(
                "SCENE_ONE.MSL" to compound.toByteArray(),
                "SCENE_TWO.MSL" to compound.toByteArray(),
                "pixel.png" to SyntheticSdaPackage.createSamplePng(3, 3),
                "test.trg" to "<xui><mpi:tilerotgameobjects rows=\"2\" columns=\"2\"/></xui>".toByteArray(),
            )
            val manifest = MiniJson.canonical(mapOf(
                "format" to "case-recomp-sda-content", "version" to 1,
                "game_id" to "own-unit-fixture", "files" to files.mapValues {
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

    private fun finishScene(camp: SdaCampaign, content: SdaContent, name: String) {
        val scene = camp.enterScene(name, content)
        for (id in scene.targets) {
            val sprite = scene.objects.getValue(id)
            assertTrue(camp.clickScene(sprite.x + 1, sprite.y + 1) is SdaClickResult.Found)
        }
        repeat(100) { camp.advance(.04f) }
    }

    @Test fun compound_objective_counts_once_after_all_components_retire() = withContent { content ->
        val camp = SdaCampaign(levels(), seed = 8)
        val scene = camp.enterScene("one", content)
        val first = scene.objects.getValue(scene.targets.first())
        assertTrue(camp.clickScene(first.x + 1, first.y + 1) is SdaClickResult.Found)
        repeat(100) { camp.advance(.04f) }
        assertEquals(0, camp.completedObjects)
        val second = scene.objects.getValue(scene.targets.last())
        assertTrue(camp.clickScene(second.x + 1, second.y + 1) is SdaClickResult.Found)
        repeat(100) { camp.advance(.04f) }
        assertEquals(1, camp.completedObjects)
        assertEquals(SdaCampaignPhase.SCENE_COMPLETE, camp.phase)
        repeat(100) { camp.advance(.04f) }
        assertEquals(1, camp.completedObjects)
        camp.confirmSceneComplete()
        val saved = SdaCampaignState.fromJson(camp.snapshot().toJson())
        val resumed = SdaCampaign(levels())
        resumed.restore(saved, content)
        assertEquals(camp.snapshot().toJson(), resumed.snapshot().toJson())
        finishScene(resumed, content, "two")
        assertEquals(2, resumed.completedObjects)
        assertEquals(SdaCampaignPhase.OBJECTS_COMPLETE, resumed.phase)
    }

    @Test fun synthetic_level_to_bonus_to_next_level_uses_player_inputs() = withContent { content ->
        val camp = SdaCampaign(levels(), seed = 8)
        finishScene(camp, content, "one")
        assertEquals(SdaCampaignPhase.SCENE_COMPLETE, camp.phase)
        camp.confirmSceneComplete()
        finishScene(camp, content, "two")
        assertEquals(SdaCampaignPhase.OBJECTS_COMPLETE, camp.phase)
        camp.startBonus(content)
        val bonus = camp.bonusGame as SdaTileRotGame
        for (index in bonus.tileRotations.indices) {
            val x = 172 + (index % bonus.cols) * 612 / bonus.cols + 1
            val y = 95 + (index / bonus.cols) * 408 / bonus.rows + 1
            repeat((4 - bonus.tileRotations[index]) % 4) { assertTrue(camp.clickBonus(x, y)) }
        }
        assertTrue(bonus.isSolved)
        assertEquals(SdaCampaignPhase.LEVEL_COMPLETE, camp.phase)
        val points = camp.points
        assertFalse(camp.clickBonus(200, 100))
        assertEquals(points, camp.points)
        camp.confirmLevelComplete()
        assertEquals(1, camp.levelIndex)
        assertEquals(SdaCampaignPhase.MAP, camp.phase)
        assertEquals(0, camp.completedObjects)
        assertEquals(1800f, camp.clock.limit)
    }

    @Test fun cannot_enter_scene_or_return_to_map_through_bonus_boundary() = withContent { content ->
        val camp = SdaCampaign(levels(), seed = 8)
        finishScene(camp, content, "one"); camp.confirmSceneComplete()
        finishScene(camp, content, "two"); camp.startBonus(content)
        val before = camp.snapshot().toJson()
        assertThrows(IllegalArgumentException::class.java) { camp.enterScene("one", content) }
        assertThrows(IllegalArgumentException::class.java) { camp.toInvestigationMap() }
        assertEquals(before, camp.snapshot().toJson())
    }

    @Test fun unknown_and_missing_bonus_resources_are_rejected() = withContent { content ->
        assertThrows(IllegalArgumentException::class.java) { SdaBonusLoader.load(content, "unknown.bin") }
        assertThrows(IllegalArgumentException::class.java) { SdaBonusLoader.load(content, "missing.trg") }
    }
}

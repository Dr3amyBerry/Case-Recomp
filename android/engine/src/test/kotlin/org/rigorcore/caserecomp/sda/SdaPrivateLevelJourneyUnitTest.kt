package org.rigorcore.caserecomp.sda

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO

/** Optional private-resource JVM journey. Never replaces missing media with synthetic data. */
class SdaPrivateLevelJourneyUnitTest {
    @Test fun private_first_level_reaches_bonus_through_real_alpha_hits_and_row_retirement() {
        val packageFile = listOf(File("../../local-output/vegas_full.zip"),
            File("../local-output/vegas_full.zip"), File("local-output/vegas_full.zip"))
            .firstOrNull { it.isFile }
        assumeTrue("local private Vegas package is required", packageFile != null)
        SdaContent.open(packageFile!!, SdaImageDecoder { bytes ->
            ImageIO.read(ByteArrayInputStream(bytes))?.let { image ->
                object : SdaPixelSource {
                    override val width = image.width
                    override val height = image.height
                    override fun getAlpha(px: Int, py: Int): Int = image.getRGB(px, py).ushr(24) and 255
                }
            }
        }).use { content ->
            val levels = SdaLevels.parse(content.read("LEVELS_1.XUI")!!)
            assertEquals(25, levels.size)
            val camp = SdaCampaign(levels, seed = 8)
            assertEquals(18, camp.currentLevel.objects)
            for (name in camp.currentLevel.scenes) {
                val scene = camp.enterScene(name, content)
                for (group in scene.activeSets.take(camp.remainingObjects)) {
                    for (id in group) {
                        val sprite = scene.objects.getValue(id)
                        if (sprite.found) continue
                        var point: Pair<Int, Int>? = null
                        search@ for (y in 0 until sprite.image.height) {
                            for (x in 0 until sprite.image.width) {
                                val px = sprite.x + x; val py = sprite.y + y
                                if (px !in 174 until 800 || py !in 0 until 600) continue
                                if (scene.targets.firstOrNull { scene.objects.getValue(it).hit(px, py) } == id) {
                                    point = px to py; break@search
                                }
                            }
                        }
                        assertNotNull("no exposed alpha input for $id", point)
                        val result = camp.clickScene(point!!.first, point.second)
                        assertTrue(result is SdaClickResult.Found)
                        assertEquals(id, (result as SdaClickResult.Found).id)
                    }
                    repeat(100) { camp.advance(.04f) }
                }
                if (camp.phase == SdaCampaignPhase.SCENE_COMPLETE) camp.confirmSceneComplete()
                if (camp.phase == SdaCampaignPhase.OBJECTS_COMPLETE) break
            }
            assertEquals(18, camp.completedObjects)
            assertEquals(SdaCampaignPhase.OBJECTS_COMPLETE, camp.phase)
            assertTrue(camp.points > 0)
            assertTrue(camp.clock.elapsed > 0f)
            val before = camp.snapshot().toJson()
            val resumed = SdaCampaign(levels)
            resumed.restore(SdaCampaignState.fromJson(before), content)
            assertEquals(before, resumed.snapshot().toJson())
            resumed.startBonus(content)
            assertEquals(SdaCampaignPhase.BONUS, resumed.phase)
            assertEquals("tilerotgame01.trg", resumed.bonusGame!!.resourceName)
            // The rotation mechanics remain partial. This deliberately does not certify them or the ending.
        }
    }
}

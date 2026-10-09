package org.rigorcore.caserecomp.sda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SdaCampaignUnitTest {

    private fun loadAuthenticLevels(): List<SdaLevel>? {
        val candidates = listOf(
            File("../../local-output/vegas_full.zip"),
            File("../local-output/vegas_full.zip"),
            File("local-output/vegas_full.zip"),
        )
        val packageFile = candidates.firstOrNull { it.isFile } ?: return null
        val content = SdaContent.open(packageFile) { null }
        val raw = content.read("LEVELS_1.XUI") ?: return null
        content.close()
        return SdaLevels.parse(raw)
    }

    private fun createSyntheticLevels(): List<SdaLevel> {
        val xml = """
            <levels>
              <level clue="1" time="1320" objects="2" scenes="vault,slots" levelname="Nivel 1" bonus="tilerot01.trg"/>
              <level clue="2" time="1800" objects="3" scenes="vault,casino" levelname="Nivel 2" bonus="tileswap01.tgl"/>
              <level clue="25" time="3120" objects="2" scenes="vault" levelname="Nivel 25" bonus="tilerot01.trg"/>
            </levels>
        """.trimIndent().toByteArray(Charsets.UTF_8)
        return SdaLevels.parse(xml)
    }

    @Test
    fun campaign_levels_parsing_and_ranks() {
        val levels = loadAuthenticLevels() ?: createSyntheticLevels()
        assertTrue(levels.isNotEmpty())
        assertEquals(1, levels.first().clue)

        val camp = SdaCampaign(levels, seed = 42L)
        assertEquals(0, camp.levelIndex)
        assertEquals("Sabueso novato", camp.rank)
        assertEquals(SdaCampaignPhase.MAP, camp.phase)
    }

    @Test
    fun campaign_complete_playthrough_from_level_1_to_finale() {
        val levels = loadAuthenticLevels() ?: createSyntheticLevels()
        val camp = SdaCampaign(levels, seed = 12345L)

        // Verify initial state
        assertEquals(SdaCampaignPhase.MAP, camp.phase)
        assertEquals(0, camp.points)
        assertEquals(0, camp.completedObjects)

        // Mock SdaContent for level scenes
        val packageFile = listOf(
            File("../../local-output/vegas_vault.zip"),
            File("../local-output/vegas_vault.zip"),
            File("local-output/vegas_vault.zip"),
        ).firstOrNull { it.isFile }

        if (packageFile != null) {
            val decoder = SdaImageDecoder {
                object : SdaPixelSource {
                    override val width: Int = 32
                    override val height: Int = 32
                    override fun getAlpha(px: Int, py: Int): Int = 255
                }
            }
            val content = SdaContent.open(packageFile, decoder)

            // 1. Enter first scene
            val firstSceneName = camp.currentLevel.scenes.first()
            val scene = camp.enterScene("SCENE_VAULT.MSL", content)
            assertEquals(SdaCampaignPhase.SCENE, camp.phase)
            assertNotNull(scene)

            // Hit first target
            val targetId = scene.targets.first()
            val sprite = scene.objects[targetId]!!
            val clickRes = camp.clickScene(sprite.x + 1, sprite.y + 1)
            assertTrue(clickRes is SdaClickResult.Found)
            assertTrue(camp.points > 0)

            // Advance frames until target motion removed
            repeat(60) { camp.advance(0.04f) }

            content.close()
        }

        // 2. Simulate completing level 0
        camp.completedObjects = camp.currentLevel.objects
        camp.phase = SdaCampaignPhase.OBJECTS_COMPLETE
        assertEquals(0, camp.remainingObjects)

        // Start bonus
        camp.bonusGame = SdaTileRotGame("tilerot01.trg", rows = 4, cols = 6, seed = 12345L)
        camp.phase = SdaCampaignPhase.BONUS

        // Solve bonus minigame
        assertFalse(camp.bonusGame!!.isSolved)
        camp.solveBonus()
        assertEquals(SdaCampaignPhase.LEVEL_COMPLETE, camp.phase)

        // Confirm level complete and advance
        camp.confirmLevelComplete()
        assertEquals(1, camp.levelIndex)
        assertEquals(SdaCampaignPhase.MAP, camp.phase)

        // 3. Fast-forward to final level
        camp.levelIndex = camp.levels.size - 1
        assertEquals(camp.levels.size - 1, camp.levelIndex)
        camp.completedObjects = camp.currentLevel.objects
        camp.phase = SdaCampaignPhase.OBJECTS_COMPLETE

        camp.bonusGame = SdaTileRotGame("tilerot01.trg", rows = 4, cols = 6, seed = 12345L)
        camp.phase = SdaCampaignPhase.BONUS
        camp.solveBonus()
        assertEquals(SdaCampaignPhase.LEVEL_COMPLETE, camp.phase)

        // 4. Trigger Master Riddle Finale
        camp.confirmLevelComplete()
        assertEquals(SdaCampaignPhase.FINALE_1, camp.phase)
        assertNotNull(camp.bonusGame)

        // Finale Stage 1 -> Stage 2
        camp.solveBonus()
        assertEquals(SdaCampaignPhase.FINALE_2, camp.phase)

        // Finale Stage 2 -> Stage 3
        camp.solveBonus()
        assertEquals(SdaCampaignPhase.FINALE_3, camp.phase)

        // Finale Stage 3 -> Campaign Complete!
        camp.solveBonus()
        assertEquals(SdaCampaignPhase.CAMPAIGN_COMPLETE, camp.phase)
        assertEquals("P.I. Maestro", camp.rank)
    }

    @Test
    fun campaign_snapshot_and_restore_serialization() {
        val levels = createSyntheticLevels()
        val camp = SdaCampaign(levels, seed = 999L)
        camp.points = 45000
        camp.levelIndex = 2
        camp.completedObjects = 1
        camp.phase = SdaCampaignPhase.MAP

        val snapshot = camp.snapshot()
        val json = snapshot.toJson()
        assertTrue(json.contains("\"points\":45000"))
        assertTrue(json.contains("\"levelIndex\":2"))

        val restored = SdaCampaignState.fromJson(json)
        assertEquals(snapshot.points, restored.points)
        assertEquals(snapshot.levelIndex, restored.levelIndex)
        assertEquals(snapshot.phase, restored.phase)
        assertEquals(snapshot.completedObjects, restored.completedObjects)
    }
}

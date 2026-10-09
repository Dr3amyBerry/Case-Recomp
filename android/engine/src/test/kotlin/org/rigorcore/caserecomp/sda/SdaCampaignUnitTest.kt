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
                "test.tgl" to "<xui><mpi:tilegameobjects rows=\"2\" columns=\"2\"/></xui>".toByteArray(),
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

    @Test fun speed_bonus_uses_native_minute_second_component_and_multiplier() {
        for ((time, expected) in listOf(59.9f to 5900, 60f to 6000,
                1320f to 132000, 3600f to 0, 3665f to 6500, 7200f to 0)) {
            val camp = SdaCampaign(levels().map { it.copy(time = time) })
            assertEquals("limit=$time", expected, camp.levelSummary().speedBonus)
            assertEquals(0, camp.points)
        }
    }

    @Test fun level_result_resume_credits_displayed_time_bonus_only_once() = withContent { content ->
        val camp = SdaCampaign(levels(), seed = 8)
        reachBonus(camp, content)
        val bonus = camp.bonusGame as SdaTileRotGame
        for (index in bonus.tileRotations.indices) {
            val x = 172 + (index % bonus.cols) * 612 / bonus.cols + 1
            val y = 95 + (index / bonus.cols) * 408 / bonus.rows + 1
            repeat(bonus.tileRotations[index]) { assertTrue(camp.clickBonus(x, y)) }
        }
        assertEquals(SdaCampaignPhase.LEVEL_COMPLETE, camp.phase)
        val before = camp.points
        val elapsed = camp.clock.elapsed
        val expectedBonus = (maxOf(0f, camp.clock.limit - elapsed).toInt() % 3600) * 100
        assertEquals(expectedBonus, camp.levelSummary().speedBonus)
        val saved = camp.snapshot().toJson()
        camp.restore(SdaCampaignState.fromJson(saved), content)
        assertEquals(saved, camp.snapshot().toJson())
        camp.confirmLevelComplete()
        assertEquals(before + expectedBonus, camp.points)
        assertEquals(elapsed, camp.totalElapsed)
        assertEquals(0f, camp.clock.elapsed)
        val next = camp.snapshot().toJson()
        assertThrows(IllegalArgumentException::class.java) { camp.confirmLevelComplete() }
        assertEquals(next, camp.snapshot().toJson())
    }

    @Test fun campaign_timeout_waits_for_native_preupdate_event() = withContent { content ->
        val camp = SdaCampaign(levels().map { it.copy(time = 1f) }, seed = 8)
        camp.enterScene("one", content)
        camp.advance(1.01f)
        assertEquals(SdaCampaignPhase.SCENE, camp.phase)
        camp.advance(.2f)
        assertEquals(SdaCampaignPhase.SCENE, camp.phase)
        camp.advance(1f)
        assertEquals(SdaCampaignPhase.TIMEOUT, camp.phase)
        val saved = camp.snapshot().toJson()
        camp.restore(SdaCampaignState.fromJson(saved), content)
        assertEquals(saved, camp.snapshot().toJson())
        camp.advance(5f)
        assertEquals(saved, camp.snapshot().toJson())
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
            repeat(bonus.tileRotations[index]) { assertTrue(camp.clickBonus(x, y)) }
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
    private fun reachBonus(camp: SdaCampaign, content: SdaContent) {
        finishScene(camp, content, "one"); camp.confirmSceneComplete()
        finishScene(camp, content, "two"); camp.startBonus(content)
    }

    @Test fun partial_rotation_bonus_checkpoint_restores_exact_board() = withContent { content ->
        val camp = SdaCampaign(levels(), seed = 8)
        reachBonus(camp, content)
        assertTrue(camp.clickBonus(200, 100))
        camp.advance(.04f)
        val saved = SdaCampaignState.fromJson(camp.snapshot().toJson())
        val resumed = SdaCampaign(levels())
        resumed.restore(saved, content)
        assertEquals(camp.snapshot().toJson(), resumed.snapshot().toJson())
        assertEquals(camp.clickBonus(200, 100), resumed.clickBonus(200, 100))
        assertEquals(camp.snapshot().toJson(), resumed.snapshot().toJson())
    }

    @Test fun retired_rotation_row_resumes_without_duplicate_points() = withContent { content ->
        val camp = SdaCampaign(levels(), seed = 8)
        reachBonus(camp, content)
        val bonus = camp.bonusGame as SdaTileRotGame
        val beforePoints = camp.points
        for (index in 0 until bonus.cols) {
            val x = 172 + index * 612 / bonus.cols + 1
            repeat(bonus.tileRotations[index]) { assertTrue(camp.clickBonus(x, 100)) }
        }
        assertEquals(beforePoints + 250, camp.points)
        val saved = camp.snapshot().toJson()
        val resumed = SdaCampaign(levels())
        resumed.restore(SdaCampaignState.fromJson(saved), content)
        assertEquals(saved, resumed.snapshot().toJson())
        assertFalse(resumed.clickBonus(180, 100))
        assertEquals(saved, resumed.snapshot().toJson())
        val bad = resumed.snapshot().copy(bonusGameState = resumed.bonusGame!!.state() +
            ("lockedTiles" to List(4) { false }))
        assertThrows(IllegalArgumentException::class.java) { resumed.restore(bad, content) }
        assertEquals(saved, resumed.snapshot().toJson())
    }

    @Test fun partial_swap_bonus_checkpoint_restores_selection_and_permutation() = withContent { content ->
        val swapLevels = levels().map { it.copy(bonus = "test.tgl") }
        val camp = SdaCampaign(swapLevels, seed = 8)
        reachBonus(camp, content)
        assertTrue(camp.clickBonus(200, 100))
        assertTrue(camp.clickBonus(600, 100))
        assertTrue(camp.clickBonus(200, 100))
        val saved = SdaCampaignState.fromJson(camp.snapshot().toJson())
        val resumed = SdaCampaign(swapLevels)
        resumed.restore(saved, content)
        assertEquals(camp.snapshot().toJson(), resumed.snapshot().toJson())
        assertEquals(camp.clickBonus(600, 400), resumed.clickBonus(600, 400))
        assertEquals(camp.snapshot().toJson(), resumed.snapshot().toJson())
    }

    @Test fun swap_bonus_retires_tiles_resumes_and_transitions_once() = withContent { content ->
        val swapLevels = levels().map { it.copy(bonus = "test.tgl") }
        val camp = SdaCampaign(swapLevels, seed = 8)
        reachBonus(camp, content)
        val before = camp.points
        fun tap(index: Int): Boolean {
            val game = camp.bonusGame as SdaTileSwapGame
            return camp.clickBonus(172 + index % game.cols * 612 / game.cols + 1,
                96 + index / game.cols * 408 / game.rows + 1)
        }
        var resumed = false
        for (index in 0 until 4) {
            val game = camp.bonusGame as SdaTileSwapGame
            if (game.lockedTiles[index]) continue
            assertTrue(tap(index))
            assertTrue(tap(game.tilePositions.indexOf(index)))
            if (camp.phase == SdaCampaignPhase.BONUS && !resumed) {
                val saved = camp.snapshot().toJson()
                camp.restore(SdaCampaignState.fromJson(saved), content)
                assertEquals(saved, camp.snapshot().toJson())
                assertFalse(tap(index))
                assertEquals(saved, camp.snapshot().toJson())
                resumed = true
            }
        }
        assertTrue(resumed)
        assertEquals(SdaCampaignPhase.LEVEL_COMPLETE, camp.phase)
        assertEquals(before + 1000 + SDA_BONUS_REWARD, camp.points)
        assertFalse(tap(0))
        assertEquals(before + 1000 + SDA_BONUS_REWARD, camp.points)
        camp.confirmLevelComplete()
        assertEquals(1, camp.levelIndex)
        assertEquals(SdaCampaignPhase.MAP, camp.phase)
    }

    @Test fun missing_finale_binding_preserves_last_level_result_before_crediting() = withContent { content ->
        val camp=SdaCampaign(levels().take(1),seed=8)
        finishScene(camp,content,"one");camp.confirmSceneComplete();finishScene(camp,content,"two")
        camp.startBonus(content)
        val game=camp.bonusGame as SdaTileRotGame
        for(i in game.tileRotations.indices) repeat(game.tileRotations[i]) {
            assertTrue(camp.clickBonus(172+i%game.cols*612/game.cols+1,95+i/game.cols*408/game.rows+1))
        }
        val before=camp.snapshot().toJson()
        assertThrows(IllegalArgumentException::class.java) { camp.confirmLevelComplete(content) }
        assertEquals(before,camp.snapshot().toJson())
    }

    @Test fun invalid_bonus_checkpoint_is_rejected_without_mutating_session() = withContent { content ->
        val camp = SdaCampaign(levels(), seed = 8)
        reachBonus(camp, content)
        val valid = camp.snapshot()
        val resumed = SdaCampaign(levels())
        val before = resumed.snapshot().toJson()
        val bad = valid.copy(bonusGameState = valid.bonusGameState!! + ("rotations" to listOf(99)))
        assertThrows(IllegalArgumentException::class.java) { resumed.restore(bad, content) }
        assertEquals(before, resumed.snapshot().toJson())
        assertThrows(IllegalArgumentException::class.java) {
            resumed.restore(valid.copy(completedObjects = 100), content)
        }
        assertEquals(before, resumed.snapshot().toJson())
    }

}

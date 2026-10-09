package org.rigorcore.caserecomp.sda

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.rigorcore.caserecomp.MiniJson
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO

/** Optional private-resource JVM journey. Never replaces missing media with synthetic data. */
class SdaPrivateLevelJourneyUnitTest {
    @Test fun private_wordsearch_resources_generate_and_accept_only_recorded_paths() {
        val packageFile = listOf(File("../../local-output/vegas_full.zip"),
            File("../local-output/vegas_full.zip"), File("local-output/vegas_full.zip"))
            .firstOrNull { it.isFile }
        assumeTrue("local private Vegas package is required", packageFile != null)
        SdaContent.open(packageFile!!, SdaImageDecoder { null }).use { content ->
            val table = SdaStrings.parse(content.read("WORDSEARCH.TXT")!!)
            val resources = SdaLevels.parse(content.read("LEVELS_1.XUI")!!)
                .map { it.bonus }.filter { it.endsWith(".WSG", ignoreCase = true) }.distinct()
            assertEquals(7, resources.size)
            for (resource in resources) {
                val nodes = SdaXml.parse(content.read(resource)!!).getElementsByTagName("*")
                val attrs = mutableMapOf<String, String>()
                for (i in 0 until nodes.length) {
                    val values = nodes.item(i).attributes
                    for (j in 0 until values.length) {
                        val item = values.item(j)
                        attrs[item.nodeName.lowercase()] = item.nodeValue
                    }
                }
                val pool = SdaStrings.resolve(attrs.getValue("text"), table).split(',')
                val board = SdaWordSearchBoard(attrs.getValue("rows").toInt(),
                    attrs.getValue("columns").toInt(), pool, 8)
                assertEquals(minOf(10, pool.size), board.words.size)
                for ((word, path) in board.placements) {
                    assertEquals(word, path.map { board.grid[it / board.cols][it % board.cols] }.joinToString(""))
                    assertTrue(board.begin(path.first()))
                    assertTrue(board.end(path.last()))
                    assertTrue(board.begin(path.last()))
                    assertFalse(board.end(path.first()))
                }
                assertTrue(board.isSolved)
                assertEquals(board.words.size * 250, board.basePoints)
            }
            println("PRIVATE KERNEL: seven wordsearch resources generated and solved through recorded endpoints; not Android/campaign/native-locale parity")
        }
    }

    @Test fun private_level_four_swap_bonus_solves_through_inputs_and_resumes() {
        val packageFile = listOf(File("../../local-output/vegas_full.zip"),
            File("../local-output/vegas_full.zip"), File("local-output/vegas_full.zip"))
            .firstOrNull { it.isFile }
        assumeTrue("local private Vegas package is required", packageFile != null)
        SdaContent.open(packageFile!!, SdaImageDecoder { null }).use { content ->
            val level = SdaLevels.parse(content.read("LEVELS_1.XUI")!!)[3]
            var game = SdaBonusLoader.load(content, level.bonus, 8, level.bonusImage) as SdaTileSwapGame
            assertEquals(6, game.rows); assertEquals(6, game.cols)
            fun tap(index: Int) = game.clickPixel(172 + index % game.cols * 612 / game.cols + 1,
                96 + index / game.cols * 408 / game.rows + 1)
            // Native shuffle may leave the last identity correct but not yet retired.
            // Move such initial tiles out by legitimate swaps before retiring the rest.
            for (index in game.tilePositions.indices) {
                if (game.tilePositions[index] != index || game.lockedTiles[index]) continue
                val other = game.tilePositions.indices.first { it != index && !game.lockedTiles[it] }
                assertTrue(tap(index)); assertTrue(tap(other))
            }
            var resumed = false
            for (index in game.tilePositions.indices) {
                if (game.lockedTiles[index]) continue
                assertTrue(tap(index))
                assertTrue(tap(game.tilePositions.indexOf(index)))
                if (!resumed && !game.isSolved) {
                    val saved = MiniJson.canonical(game.state())
                    @Suppress("UNCHECKED_CAST")
                    val state = MiniJson.parse(saved) as Map<String, Any?>
                    game = SdaBonusLoader.restore(content, state, level.bonus, 8, level.bonusImage) as SdaTileSwapGame
                    assertEquals(saved, MiniJson.canonical(game.state()))
                    assertFalse(tap(index))
                    assertEquals(saved, MiniJson.canonical(game.state()))
                    resumed = true
                }
            }
            assertTrue(resumed)
            assertTrue(game.isSolved)
            assertEquals(9000, game.placementPoints)
            println("PRIVATE BONUS: level 4 resource, 36 tiles retired through coordinate inputs, exact partial resume; base placement points=9000")
            // Bonus-only probe: this does not claim levels 2/3 or native timing/fast scoring.
        }
    }

    @Test fun private_first_level_solves_rotation_resumes_and_advances_to_level_two() {
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
            val bonus = resumed.bonusGame as SdaTileRotGame
            for (index in bonus.tileRotations.indices) {
                val x = 172 + (index % bonus.cols) * 612 / bonus.cols + 1
                val y = 95 + (index / bonus.cols) * 408 / bonus.rows + 1
                repeat((resumed.bonusGame as SdaTileRotGame).tileRotations[index]) { assertTrue(resumed.clickBonus(x, y)) }
                if (index == bonus.cols - 1) {
                    val partial = resumed.snapshot().toJson()
                    resumed.restore(SdaCampaignState.fromJson(partial), content)
                    assertEquals(partial, resumed.snapshot().toJson())
                }
            }
            assertEquals(SdaCampaignPhase.LEVEL_COMPLETE, resumed.phase)
            val complete = resumed.snapshot().toJson()
            resumed.restore(SdaCampaignState.fromJson(complete), content)
            assertEquals(complete, resumed.snapshot().toJson())
            val earned = resumed.points
            val elapsed = resumed.clock.elapsed
            resumed.confirmLevelComplete()
            assertEquals(1, resumed.levelIndex)
            assertEquals(2, resumed.currentLevel.clue)
            assertEquals(SdaCampaignPhase.MAP, resumed.phase)
            assertTrue(resumed.points >= earned)
            assertEquals(elapsed, resumed.totalElapsed)
            assertEquals(0f, resumed.clock.elapsed)
            val next = resumed.snapshot().toJson()
            resumed.restore(SdaCampaignState.fromJson(next), content)
            assertEquals(next, resumed.snapshot().toJson())
            assertEquals(25, levels.last().clue)
            assertEquals(90, levels.last().objects)
            assertEquals(3120f, levels.last().time)
            assertEquals(9, levels.last().scenes.size)
            println("PRIVATE JOURNEY: level 1 alpha objectives -> rotation inputs -> partial resume -> result resume -> level 2; points=${resumed.points}, totalElapsed=${resumed.totalElapsed}")
            // No forced counters/phases or generic solve. Not proof of Android, native RNG/scoring or finale.
        }
    }
}

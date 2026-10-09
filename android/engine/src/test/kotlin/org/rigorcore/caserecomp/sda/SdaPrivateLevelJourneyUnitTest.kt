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
    private fun finishObjectives(camp: SdaCampaign, content: SdaContent) {
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
    }


    @Test fun private_jigsaw_piece_kernel_rotates_places_and_resumes_all_masks() {
        val packageFile = listOf(File("../../local-output/vegas_full.zip"),
            File("../local-output/vegas_full.zip"), File("local-output/vegas_full.zip"))
            .firstOrNull { it.isFile }
        assumeTrue("local private Vegas package is required", packageFile != null)
        SdaContent.open(packageFile!!, SdaImageDecoder { bytes ->
            ImageIO.read(ByteArrayInputStream(bytes))?.let { image ->
                SdaBufferPixelSource(image.width, image.height, ByteArray(image.width * image.height) { -1 })
            }
        }).use { content ->
            val nodes = SdaXml.parse(content.read("JIGSAW01.JSW")!!).getElementsByTagName("*")
            val elements = (0 until nodes.length).map { nodes.item(it) as org.w3c.dom.Element }
            val textures = elements.filter { it.localName == "texture" || it.tagName == "texture" }
                .associate { it.getAttribute("id") to it.getAttribute("uri") }
            val infos = elements.filter { (it.localName ?: it.tagName.substringAfter(':')) == "jswimageinfo" }.associateBy { it.getAttribute("id") }
            val definitions = elements.filter { (it.localName ?: it.tagName.substringAfter(':')) == "jswgamepiece" }.map { piece ->
                val info = infos.getValue(piece.getAttribute("imageinfo"))
                val mask = content.decodeImage(textures.getValue(info.getAttribute("alpha")))
                SdaJigsawPiece(piece.getAttribute("id"), piece.getAttribute("x").toInt(),
                    piece.getAttribute("y").toInt(), mask.width, mask.height)
            }
            assertEquals(24, definitions.size)
            var board = SdaJigsawBoard(definitions, 8)
            var resumed = false
            for (id in board.trayOrder) {
                val piece = board.pieces.getValue(id)
                assertTrue(board.select(id))
                while (board.quarterTurns.getValue(id) != 0) assertTrue(board.rotateSelected())
                board.moveHeld(piece.x, piece.y)
                if (!resumed && board.placed.isNotEmpty()) {
                    val saved = MiniJson.canonical(board.state())
                    @Suppress("UNCHECKED_CAST")
                    val state = MiniJson.parse(saved) as Map<String, Any?>
                    board = SdaJigsawBoard(definitions, 999, state)
                    assertEquals(saved, MiniJson.canonical(board.state()))
                    resumed = true
                }
                assertTrue(board.drop(piece.x, piece.y))
                assertFalse(board.select(id))
            }
            assertTrue(resumed)
            assertTrue(board.isSolved)
            assertEquals(6000, board.placementPoints)
            println("PRIVATE JIGSAW KERNEL: 24 real mask dimensions, recorded targets, rotations and held placements, exact partial resume; no tray/pixel/Android/campaign claim")
        }
    }

    @Test fun private_wordsearch_resources_generate_and_accept_only_recorded_paths() {
        val packageFile = listOf(File("../../local-output/vegas_full.zip"),
            File("../local-output/vegas_full.zip"), File("local-output/vegas_full.zip"))
            .firstOrNull { it.isFile }
        assumeTrue("local private Vegas package is required", packageFile != null)
        SdaContent.open(packageFile!!, SdaImageDecoder { bytes ->
            ImageIO.read(ByteArrayInputStream(bytes))?.let { image ->
                SdaBufferPixelSource(image.width, image.height, ByteArray(image.width * image.height) { -1 })
            }
        }).use { content ->
            val table = SdaStrings.parse(content.read("WORDSEARCH.TXT")!!)
            val resources = SdaLevels.parse(content.read("LEVELS_1.XUI")!!)
                .filter { it.bonus.endsWith(".WSG", ignoreCase = true) }.distinctBy { it.bonus }
            assertEquals(7, resources.size)
            for (level in resources) {
                val resource = level.bonus
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
                var game = SdaBonusLoader.load(content, resource, 8, level.bonusImage) as SdaWordSearchGame
                assertEquals(board.grid, game.board.grid)
                for ((word, path) in game.board.placements) {
                    fun x(cell: Int) = game.originX + cell % game.cols * game.cellWidth + 1
                    fun y(cell: Int) = game.originY + cell / game.cols * game.cellHeight + 1
                    assertTrue(game.beginPixel(x(path.first()), y(path.first())))
                    game.movePixel(x(path.last()), y(path.last()))
                    val saved = MiniJson.canonical(game.state())
                    @Suppress("UNCHECKED_CAST")
                    val checkpoint = MiniJson.parse(saved) as Map<String, Any?>
                    game = SdaBonusLoader.restore(content, checkpoint, resource, 8, level.bonusImage) as SdaWordSearchGame
                    assertEquals(saved, MiniJson.canonical(game.state()))
                    assertTrue(game.endPixel(x(path.last()), y(path.last())))
                    assertTrue(word in game.foundWords)
                }
                assertTrue(game.isSolved)
                assertEquals(game.words.size * 250, game.placementPoints)
            }
            println("PRIVATE KERNEL: seven wordsearch resources generated, loaded, solved through pixel endpoints and restored mid-selection; not Android/native-locale parity")
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

    @Test fun private_first_two_levels_solve_bonuses_resume_and_advance_to_level_three() {
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
            finishObjectives(camp, content)
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
            finishObjectives(resumed, content)
            assertEquals(resumed.currentLevel.objects, resumed.completedObjects)
            assertEquals(SdaCampaignPhase.OBJECTS_COMPLETE, resumed.phase)
            resumed.startBonus(content)
            var wordGame = resumed.bonusGame as SdaWordSearchGame
            assertEquals(172, wordGame.originX); assertEquals(96, wordGame.originY)
            assertEquals(51, wordGame.cellWidth); assertEquals(51, wordGame.cellHeight)
            assertFalse(resumed.beginBonusSelection(0, 0))
            var partialSaved = false
            val wordsBefore = resumed.points
            for (word in wordGame.words) {
                val path = wordGame.board.placements.getValue(word)
                fun x(cell: Int) = wordGame.originX + cell % wordGame.cols * wordGame.cellWidth + 1
                fun y(cell: Int) = wordGame.originY + cell / wordGame.cols * wordGame.cellHeight + 1
                assertTrue(resumed.beginBonusSelection(x(path.last()), y(path.last())))
                assertTrue(resumed.moveBonusSelection(x(path.first()), y(path.first())))
                if (!partialSaved && wordGame.foundWords.isNotEmpty()) {
                    val partial = resumed.snapshot().toJson()
                    resumed.restore(SdaCampaignState.fromJson(partial), content)
                    assertEquals(partial, resumed.snapshot().toJson())
                    val badBoard = resumed.bonusGame!!.state().toMutableMap()
                    @Suppress("UNCHECKED_CAST")
                    val board = badBoard["wordBoard"] as Map<String, Any?>
                    badBoard["wordBoard"] = board + ("grid" to listOf("BAD"))
                    assertThrows(IllegalArgumentException::class.java) {
                        resumed.restore(resumed.snapshot().copy(bonusGameState = badBoard), content)
                    }
                    assertEquals(partial, resumed.snapshot().toJson())
                    wordGame = resumed.bonusGame as SdaWordSearchGame
                    partialSaved = true
                }
                assertTrue(resumed.endBonusSelection(x(path.first()), y(path.first())))
                assertFalse(resumed.endBonusSelection(x(path.first()), y(path.first())))
                resumed.advance(.25f)
            }
            assertTrue(partialSaved)
            assertTrue(wordGame.isSolved)
            assertEquals(wordGame.words.size * 250, wordGame.placementPoints)
            assertEquals(wordsBefore + wordGame.placementPoints + wordGame.points, resumed.points)
            assertEquals(SdaCampaignPhase.LEVEL_COMPLETE, resumed.phase)
            val wordResult = resumed.snapshot().toJson()
            resumed.restore(SdaCampaignState.fromJson(wordResult), content)
            assertEquals(wordResult, resumed.snapshot().toJson())
            resumed.confirmLevelComplete()
            assertEquals(2, resumed.levelIndex)
            assertEquals(3, resumed.currentLevel.clue)
            assertEquals(SdaCampaignPhase.MAP, resumed.phase)
            val third = resumed.snapshot().toJson()
            resumed.restore(SdaCampaignState.fromJson(third), content)
            assertEquals(third, resumed.snapshot().toJson())
            assertEquals(25, levels.last().clue)
            assertEquals(90, levels.last().objects)
            assertEquals(3120f, levels.last().time)
            assertEquals(9, levels.last().scenes.size)
            println("PRIVATE JOURNEY: levels 1 and 2 alpha objectives -> rotation and wordsearch inputs -> partial/result resume -> level 3; points=${resumed.points}, totalElapsed=${resumed.totalElapsed}")
            // No forced counters/phases or generic solve. Not proof of Android, native RNG/scoring or finale.
        }
    }
}

package org.rigorcore.caserecomp.sda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.rigorcore.caserecomp.MiniJson
import org.junit.Test

class SdaBonusUnitTest {
    @Test fun rotation_initial_angles_follow_native_scaled_draw_and_zero_rejection() {
        val game = SdaTileRotGame("test.trg", seed = 8)
        assertEquals(listOf(3, 3, 3, 1, 1, 1, 1, 3, 1, 2, 3, 1,
            2, 1, 3, 1, 2, 2, 2, 3, 3, 1, 3, 3), game.tileRotations.toList())
    }

    @Test fun rotation_maximum_crt_sample_stays_below_full_turn_in_x87_precision() {
        // This seed makes the first CRT sample 32767. Premature float rounding gives 4.
        val game = SdaTileRotGame("test.trg", seed = 4028364353L)
        assertEquals(3, game.tileRotations[0])
        assertTrue(game.tileRotations.all { it in 1..3 })
    }

    @Test fun swap_native_forward_shuffle_seed_fixture() {
        val game = SdaTileSwapGame("test.tgl", rows = 2, cols = 3, seed = 8)
        assertEquals(listOf(4, 2, 5, 1, 3, 0), game.tilePositions.toList())
    }

    @Test fun swap_correct_tiles_retire_individually_and_award_base_score() {
        val game = SdaTileSwapGame("test.tgl", rows = 2, cols = 2,
            tiles = intArrayOf(1, 0, 3, 2))
        assertTrue(game.clickPixel(180, 100))
        assertTrue(game.clickPixel(500, 100))
        assertEquals(listOf(0, 1, 3, 2), game.tilePositions.toList())
        assertFalse(game.clickPixel(180, 100))
        assertFalse(game.clickPixel(500, 100))
        assertEquals(500, game.state()["placementPoints"])
        assertFalse(game.isSolved)
        assertTrue(game.clickPixel(180, 310))
        assertFalse(game.clickPixel(180, 100)) // Retired tile must not change pending selection.
        assertEquals(2, game.selectedIndex)
        assertTrue(game.clickPixel(500, 310))
        assertTrue(game.isSolved)
        assertEquals(1000, game.state()["placementPoints"])
    }


    @Test fun rotation_completed_row_becomes_immutable_and_awards_base_line_score() {
        val game = SdaTileRotGame("test.trg", rows = 2, cols = 2,
            rotations = intArrayOf(3, 0, 3, 3))
        assertFalse(game.clickPixel(171, 95))
        repeat(3) { assertTrue(game.clickPixel(180, 100)) }
        assertEquals(0, game.tileRotations[0])
        assertFalse(game.clickPixel(180, 100))
        assertFalse(game.clickPixel(500, 100))
        assertEquals(250, game.state()["linePoints"])
        assertFalse(game.isSolved)
        repeat(3) { assertTrue(game.clickPixel(180, 310)) }
        assertFalse(game.clickPixel(180, 310))
        repeat(3) { assertTrue(game.clickPixel(500, 310)) }
        assertTrue(game.isSolved)
        assertEquals(1000, game.state()["linePoints"])
        assertFalse(game.clickPixel(500, 310))
    }

    @Test fun rotation_secondary_input_is_inverse_and_does_not_award_unsolved_lines() {
        val game = SdaTileRotGame("test.trg", rows = 2, cols = 2,
            rotations = intArrayOf(1, 1, 1, 1))
        assertTrue(game.rotatePixel(180, 100, clockwise = true))
        assertEquals(2, game.tileRotations[0])
        assertTrue(game.clickPixel(180, 100))
        assertEquals(1, game.tileRotations[0])
        assertEquals(0, game.linePoints)
        assertFalse(game.rotatePixel(784, 100, clockwise = true))
    }

    @Test
    fun tile_rot_game_rotations_and_solution() {
        val game = SdaTileRotGame("test.trg", rows = 4, cols = 6, seed = 42L)
        assertFalse(game.isSolved)
        assertEquals(24, game.tileRotations.size)
        assertEquals(SDA_BONUS_REWARD, game.points)

        // Click pixel inside tile (0, 0)
        // boardX = 172, boardY = 95, tileW = 102, tileH = 102
        val initialRot = game.tileRotations[0]
        val clicked = game.clickPixel(180, 105)
        assertTrue(clicked)
        assertEquals((initialRot + 3) % 4, game.tileRotations[0])

        // Solve method
        game.solve()
        assertTrue(game.isSolved)
        assertEquals(0, game.points)
        assertTrue(game.tileRotations.all { it == 0 })
    }

    @Test
    fun tile_swap_game_selection_and_swapping() {
        val game = SdaTileSwapGame("test.tgl", rows = 6, cols = 6, seed = 42L)
        assertFalse(game.isSolved)
        assertEquals(36, game.tilePositions.size)

        // Click tile 0, then tile 1
        val click1 = game.clickPixel(180, 105)
        assertTrue(click1)
        assertEquals(0, game.selectedIndex)

        val tile0 = game.tilePositions[0]
        val tile1 = game.tilePositions[1]
        val click2 = game.clickPixel(285, 105) // col 1
        assertTrue(click2)
        assertEquals(null, game.selectedIndex)
        assertEquals(tile1, game.tilePositions[0])
        assertEquals(tile0, game.tilePositions[1])

        // Solve method
        game.solve()
        assertTrue(game.isSolved)
        assertEquals(0, game.points)
    }

    @Test
    fun word_search_requires_drag_endpoints_and_resumes_exact_board() {
        val game = SdaWordSearchGame("test.wsg", words = listOf("Clue", "Vault", "Casino"), seed = 8)
        assertFalse(game.clickPixel(-1, -1))
        assertTrue(game.foundWords.isEmpty())
        val path = game.board.placements.values.first()
        fun x(cell: Int) = cell % game.cols
        fun y(cell: Int) = cell / game.cols
        assertTrue(game.beginPixel(x(path.first()), y(path.first())))
        assertFalse(game.endPixel(x(path.first()), y(path.first())))
        assertTrue(game.foundWords.isEmpty())
        assertTrue(game.beginPixel(x(path.first()), y(path.first())))
        game.movePixel(x(path.last()), y(path.last()))
        val saved = game.state()
        @Suppress("UNCHECKED_CAST")
        val parsed = MiniJson.parse(MiniJson.canonical(saved)) as Map<String, Any?>
        val restored = SdaWordSearchGame("test.wsg", words = listOf("Clue", "Vault", "Casino"),
            seed = 8, checkpoint = parsed)
        assertEquals(MiniJson.canonical(saved), MiniJson.canonical(restored.state()))
        assertTrue(restored.endPixel(x(path.last()), y(path.last())))
        assertEquals(250, restored.placementPoints)
        assertFalse(restored.endPixel(x(path.last()), y(path.last())))
        assertEquals(250, restored.placementPoints)
        assertThrows(IllegalArgumentException::class.java) {
            SdaWordSearchGame("test.wsg", words = listOf("Clue", "Vault", "Casino"), seed = 8,
                checkpoint = saved + ("wordBoard" to ((saved["wordBoard"] as Map<*, *>) + ("grid" to listOf("BAD")))))
        }
    }

    @Test
    fun jigsaw_game_placing_pieces() {
        val pieces = (0..3).map { SdaJigsawPiece("own$it", 200 + 30 * it, 100, 21, 21) }
        val images = pieces.associate { it.id to SdaArgbPixelSource(21, 21, IntArray(441) { -1 }) }
        val game = SdaJigsawGame("test.jsw", pieces, images, SdaJigsawTrayDefinition(10,88,130,269,4,31), seed = 8)
        repeat(4) { assertFalse(game.clickPixel(0, 0)) }
        assertFalse(game.isSolved)
        for (id in game.interaction.board.trayOrder) {
            val rect = game.trayRectangles().first { it.id == id }
            assertTrue(game.clickPixel(rect.x + 1, rect.y + 1))
            while (game.interaction.board.quarterTurns.getValue(id) != 0) assertTrue(game.rotateHeld())
            val piece = game.interaction.board.pieces.getValue(id)
            assertTrue(game.clickPixel(piece.x + piece.width / 2, piece.y + piece.height / 2))
        }
        assertTrue(game.isSolved)
        assertEquals(1000, game.placementPoints)
    }

    @Test fun jigsaw_skip_checkpoint_preserves_zero_reward_without_manufacturing_placements() {
        val pieces = listOf(SdaJigsawPiece("own",200,100,21,21))
        val images = mapOf("own" to SdaArgbPixelSource(21,21,IntArray(441) { -1 }))
        val geometry = SdaJigsawTrayDefinition(10,88,130,269,4,31)
        val game = SdaJigsawGame("own.jsw",pieces,images,geometry,8)
        game.solve()
        assertTrue(game.isSolved)
        assertTrue(game.interaction.board.placed.isEmpty())
        @Suppress("UNCHECKED_CAST")
        val saved = org.rigorcore.caserecomp.MiniJson.parse(org.rigorcore.caserecomp.MiniJson.canonical(game.state())) as Map<String,Any?>
        val restored = SdaJigsawGame("own.jsw",pieces,images,geometry,999,checkpoint=saved)
        assertEquals(0,restored.points)
        assertEquals(org.rigorcore.caserecomp.MiniJson.canonical(saved),org.rigorcore.caserecomp.MiniJson.canonical(restored.state() + ("seed" to 8L)))
        assertThrows(IllegalArgumentException::class.java) {
            SdaJigsawGame("own.jsw",pieces,images,geometry,8,checkpoint=mapOf("placedPieces" to listOf(0)))
        }
    }

    @Test
    fun legacy_simulated_finale_is_read_only_and_cannot_complete_by_click_or_skip() {
        val riddle = SdaMasterRiddleGame(stage = 1)
        val before = riddle.state()
        repeat(50) { assertFalse(riddle.clickPixel(0, 0)) }
        assertThrows(UnsupportedOperationException::class.java) { riddle.solve() }
        assertEquals(before, riddle.state())
        assertFalse(riddle.isSolved)
    }
}

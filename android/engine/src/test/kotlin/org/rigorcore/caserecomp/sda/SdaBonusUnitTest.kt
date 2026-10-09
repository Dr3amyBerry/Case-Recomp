package org.rigorcore.caserecomp.sda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SdaBonusUnitTest {
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
    fun word_search_game_finding_words() {
        val words = listOf("CLUE", "VAULT", "CASINO")
        val game = SdaWordSearchGame("test.wsg", words = words)
        assertFalse(game.isSolved)

        // Simulate clicking to find words
        assertTrue(game.clickPixel(0, 0))
        assertTrue(game.foundWords.contains("CLUE"))
        assertFalse(game.isSolved)

        game.clickPixel(0, 0)
        game.clickPixel(0, 0)
        assertTrue(game.isSolved)
    }

    @Test
    fun jigsaw_game_placing_pieces() {
        val game = SdaJigsawGame("test.jsw", totalPieces = 4)
        assertFalse(game.isSolved)

        repeat(4) { game.clickPixel(0, 0) }
        assertTrue(game.isSolved)
    }

    @Test
    fun master_riddle_game_stages_progression() {
        val riddle = SdaMasterRiddleGame(stage = 1)
        assertFalse(riddle.isSolved)
        assertEquals(1, riddle.stage)

        // Stage 1 requirements
        repeat(SdaMasterRiddleGame.STAGE_1_ITEMS.size) {
            riddle.clickPixel(0, 0)
        }
        assertEquals(2, riddle.stage)

        // Stage 2 requirements
        repeat(SdaMasterRiddleGame.STAGE_2_ITEMS.size) {
            riddle.clickPixel(0, 0)
        }
        assertEquals(3, riddle.stage)

        // Stage 3 requirements
        repeat(SdaMasterRiddleGame.STAGE_3_ITEMS.size) {
            riddle.clickPixel(0, 0)
        }
        assertEquals(4, riddle.stage)
        assertTrue(riddle.isSolved)
    }
}

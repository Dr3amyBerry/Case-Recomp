package org.rigorcore.caserecomp.sda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SdaBonusUnitTest {

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
        assertEquals((initialRot + 1) % 4, game.tileRotations[0])

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

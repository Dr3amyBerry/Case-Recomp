package org.rigorcore.caserecomp.sda

import org.junit.Assert.*
import org.junit.Test

class SdaWordSearchBoardUnitTest {
    @Test fun input_requires_real_endpoints_and_rejects_repeated_or_arbitrary_selection() {
        val game = SdaWordSearchBoard(8, 12, listOf("Clue", "Vault", "Casino"), 8)
        assertFalse(game.begin(-1))
        assertTrue(game.begin(0))
        assertFalse(game.end(0))
        assertTrue(game.foundWords.isEmpty())
        for ((word, cells) in game.placements) {
            assertTrue(game.begin(cells.last()))
            assertTrue(game.end(cells.first()))
            assertTrue(word in game.foundWords)
            assertTrue(game.begin(cells.first()))
            assertFalse(game.end(cells.last()))
        }
        assertTrue(game.isSolved)
        assertEquals(750, game.basePoints)
    }

    @Test fun generated_board_is_deterministic_and_contains_its_recorded_paths() {
        val pool = listOf("Clue", "Vault", "Casino", "Token", "Hotel", "Money",
            "Lock", "Wheel", "Card", "Slot", "Coin", "Ring")
        val game = SdaWordSearchBoard(8, 12, pool, 8)
        val same = SdaWordSearchBoard(8, 12, pool, 8)
        assertEquals(10, game.words.size)
        assertEquals(game.grid, same.grid)
        assertEquals(game.displayGrid, same.displayGrid)
        assertEquals(game.placements, same.placements)
        assertEquals(game.finalRngState, same.finalRngState)
        for ((word, path) in game.placements) {
            assertEquals(word, path.map { game.grid[it / 12][it % 12] }.joinToString(""))
        }
        assertEquals(8, game.grid.size)
        assertTrue(game.grid.all { it.length == 12 })
    }
}

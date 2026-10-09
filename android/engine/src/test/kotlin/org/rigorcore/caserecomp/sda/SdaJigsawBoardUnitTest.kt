package org.rigorcore.caserecomp.sda

import org.junit.Assert.*
import org.junit.Test
import org.rigorcore.caserecomp.MiniJson

class SdaJigsawBoardUnitTest {
    private val pieces = listOf(SdaJigsawPiece("own", 200, 100, 101, 83))
    @Test fun placement_requires_selected_upright_piece_and_native_inset_bounds() {
        val board = SdaJigsawBoard(pieces, 8)
        assertFalse(board.drop(200, 100))
        assertFalse(board.select("missing"))
        assertTrue(board.select("own"))
        while (board.quarterTurns.getValue("own") != 0) assertTrue(board.rotateSelected())
        // Center is left+floor(maskWidth/2), top+floor(maskHeight/2).
        assertFalse(board.drop(200 + 101 - 5 - 50, 100 + 5 - 41)) // upper edge excluded
        assertTrue(board.placed.isEmpty())
        assertNull(board.selected)
        assertTrue(board.select("own"))
        assertTrue(board.drop(200 + 5 - 50, 100 + 5 - 41)) // lower edge included
        assertEquals(250, board.placementPoints)
        assertTrue(board.isSolved)
        assertFalse(board.select("own"))
        assertFalse(board.drop(200, 100))
        assertEquals(250, board.placementPoints)
    }
    @Test fun full_range_shuffle_and_quarter_draws_match_independent_scalar_fixture() {
        val definitions = (0..5).map { SdaJigsawPiece("own$it", it * 100, 0, 101, 83) }
        val board = SdaJigsawBoard(definitions, 8)
        // Independent Python reconstruction of 004365b0 with the stored float constant.
        assertEquals(listOf("own2", "own3", "own0", "own1", "own4", "own5"), board.trayOrder)
        assertEquals(mapOf("own2" to 0, "own3" to 1, "own0" to 1, "own1" to 1, "own4" to 0, "own5" to 3), board.quarterTurns)
        assertEquals(4012203412L, board.finalRngState)
    }
    @Test fun native_acceptance_is_not_a_twenty_pixel_target_distance() {
        for ((dx, dy, expected) in listOf(Triple(21, 0, true), Triple(-45, -36, true),
                Triple(-46, 0, false), Triple(0, -37, false), Triple(46, 0, false), Triple(0, 37, false))) {
            val board = SdaJigsawBoard(pieces, 8)
            board.select("own")
            while (board.quarterTurns.getValue("own") != 0) board.rotateSelected()
            assertEquals("offset=$dx,$dy", expected, board.drop(200 + dx, 100 + dy))
        }
    }

    @Test fun rotation_selection_and_checkpoint_preserve_real_state() {
        val definitions = pieces + SdaJigsawPiece("second", 310, 100, 90, 80)
        val board = SdaJigsawBoard(definitions, 8)
        assertTrue(board.select("own"))
        while (board.quarterTurns.getValue("own") == 0) board.rotateSelected()
        assertFalse(board.drop(200, 100))
        assertTrue(board.select("second"))
        board.moveHeld(50, 80)
        @Suppress("UNCHECKED_CAST")
        val saved = MiniJson.parse(MiniJson.canonical(board.state())) as Map<String, Any?>
        val restored = SdaJigsawBoard(definitions, 999, saved)
        assertEquals(MiniJson.canonical(board.state()), MiniJson.canonical(restored.state()))
        assertTrue(restored.rotateSelected())
        restored.cancel()
        assertNull(restored.selected)
        assertThrows(IllegalArgumentException::class.java) {
            SdaJigsawBoard(definitions, 8, saved + ("placed" to listOf("missing")))
        }
    }
}

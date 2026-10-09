package org.rigorcore.caserecomp.sda

import org.junit.Assert.*
import org.junit.Test
import org.rigorcore.caserecomp.MiniJson

class SdaJigsawInteractionUnitTest {
    @Test fun alpha_pick_failed_drop_return_and_held_checkpoint_are_consistent() {
        val definitions = (0..5).map { SdaJigsawPiece("own$it", 200 + it * 100, 100, 101, 83) }
        val geometry = SdaJigsawTrayDefinition(10, 88, 130, 269, 4, 31)
        val game = SdaJigsawInteraction(definitions, 8, geometry)
        val sizes = definitions.associate { it.id to SdaJigsawTraySize(50, 40, 54, 49) }
        val images = definitions.associate { it.id to SdaBufferPixelSource(50, 40, ByteArray(2000) { if (it == 0) 0 else -1 }) }
        val first = game.tray.rectangles(sizes).first()
        assertFalse(game.pickPixel(first.x, first.y, sizes, images))
        assertTrue(game.pickPixel(first.x + 1, first.y, sizes, images))
        assertFalse(game.tray.order.contains(first.id))
        assertFalse(game.scroll(1))
        val before = MiniJson.canonical(game.state())
        @Suppress("UNCHECKED_CAST")
        val saved = MiniJson.parse(before) as Map<String, Any?>
        val resumed = SdaJigsawInteraction(definitions, 999, geometry, saved)
        assertEquals(before, MiniJson.canonical(resumed.state()))
        assertFalse(resumed.dropHeld(Int.MAX_VALUE, Int.MAX_VALUE))
        assertEquals(game.board.trayOrder, resumed.tray.order)
        assertTrue(resumed.board.placed.isEmpty())
        assertTrue(resumed.pickPixel(first.x + 1, first.y, sizes, images))
        while (resumed.board.quarterTurns.getValue(first.id) != 0) assertTrue(resumed.rotateHeld())
        val piece = resumed.board.pieces.getValue(first.id)
        assertTrue(resumed.dropHeld(piece.x, piece.y))
        assertEquals(250, resumed.board.placementPoints)
        assertFalse(resumed.tray.order.contains(first.id))
        assertThrows(IllegalArgumentException::class.java) {
            SdaJigsawInteraction(definitions, 8, geometry, saved + ("returnIndex" to null))
        }
        assertEquals(before, MiniJson.canonical(game.state()))
    }
}

package org.rigorcore.caserecomp.sda

import org.junit.Assert.*
import org.junit.Test
import org.rigorcore.caserecomp.MiniJson

class SdaJigsawTrayUnitTest {
    private val ids = (0..5).map { "own$it" }
    private val sizes = ids.associateWith { SdaJigsawTraySize(50, 40, 54, 49) }
    private val images = ids.associateWith { SdaBufferPixelSource(50, 40, ByteArray(2000) { if (it == 0) 0 else -1 }) }
    @Test fun native_slot_geometry_and_hit_test_use_visible_piece_rectangles() {
        val tray = SdaJigsawTray(ids, 10, 88, 130, 269, 4, 31)
        assertEquals(listOf(132, 199, 266, 333), tray.rectangles(sizes).map { it.y })
        assertTrue(tray.rectangles(sizes).all { it.x == 50 && it.width == 54 && it.height == 49 })
        assertNull(tray.hitTest(49, 132, sizes, ids, images))
        assertNull(tray.hitTest(50, 132, sizes, ids, images)) // transparent corner
        assertEquals("own0", tray.hitTest(51, 132, sizes, ids, images))
        assertNull(tray.hitTest(104, 132, sizes, ids, images))
        assertTrue(tray.scroll(1)); assertTrue(tray.scroll(1)); assertFalse(tray.scroll(1))
        assertEquals(listOf("own2", "own3", "own4", "own5"), tray.rectangles(sizes).map { it.id })
    }
    @Test fun removal_return_and_checkpoint_keep_dynamic_order_and_last_page() {
        val tray = SdaJigsawTray(ids, 10, 88, 130, 269, 4, 31)
        tray.scroll(1); tray.scroll(1)
        assertEquals(5, tray.take("own5"))
        assertEquals(1, tray.firstVisible)
        assertFalse(tray.order.contains("own5"))
        assertTrue(tray.returnPiece("own5", 5))
        assertEquals(ids, tray.order)
        assertEquals(2, tray.firstVisible)
        @Suppress("UNCHECKED_CAST")
        val saved = MiniJson.parse(MiniJson.canonical(tray.state())) as Map<String, Any?>
        val restored = SdaJigsawTray(ids, 10, 88, 130, 269, 4, 31, saved)
        assertEquals(MiniJson.canonical(tray.state()), MiniJson.canonical(restored.state()))
        assertEquals(tray.rectangles(sizes), restored.rectangles(sizes))
        assertThrows(IllegalArgumentException::class.java) {
            SdaJigsawTray(ids, 10, 88, 130, 269, 4, 31, saved + ("firstVisible" to 99L))
        }
        assertFalse(restored.returnPiece("own0", 0))
    }
}

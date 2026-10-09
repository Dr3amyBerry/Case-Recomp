package org.rigorcore.caserecomp.sda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SdaSceneUnitTest {

    @Test
    fun alpha_hit_ignores_transparent_pixels_and_edges() {
        // 3x2 image: only (1, 0) has alpha = 1, others 0
        val alphas = ByteArray(3 * 2)
        alphas[0 * 3 + 1] = 1.toByte() // (x=1, y=0)
        val image = SdaBufferPixelSource(3, 2, alphas)
        val sprite = SdaSprite("target", 10, 20, image)

        assertTrue(sprite.hit(11, 20)) // 11 - 10 = 1, 20 - 20 = 0 -> alpha = 1

        val outsideCoordinates = listOf(
            10 to 20, // (0, 0) -> alpha = 0
            13 to 20, // px = 3 -> out of bounds
            11 to 22, // py = 2 -> out of bounds
            9 to 20,  // px = -1 -> out of bounds
        )
        for ((x, y) in outsideCoordinates) {
            assertFalse(sprite.hit(x, y))
        }

        sprite.found = true
        assertFalse(sprite.hit(11, 20))
    }

    @Test
    fun scene_click_handling_and_target_sequence() {
        val alphasA = ByteArray(3 * 3) { 255.toByte() }
        val alphasB = ByteArray(3 * 3) { 255.toByte() }
        val spriteA = SdaSprite("a", 20, 20, SdaBufferPixelSource(3, 3, alphasA))
        val spriteB = SdaSprite("b", 30, 20, SdaBufferPixelSource(3, 3, alphasB))
        val objects = mapOf("a" to spriteA, "b" to spriteB)
        val captions = mapOf(listOf("a", "b") to listOf("Two samples", "One sample"))

        val scene = SdaScene(objects, listOf(listOf("a", "b")), captions)
        assertEquals(listOf("Two samples"), scene.remainingCaptions())

        val result1 = scene.click(20, 20)
        assertTrue(result1 is SdaClickResult.Found)
        assertEquals("a", (result1 as SdaClickResult.Found).id)
        assertEquals(7500, result1.gain)
        assertEquals(listOf("One sample"), scene.remainingCaptions())

        // Clicking same found object again is a miss
        val result2 = scene.click(20, 20)
        assertTrue(result2 is SdaClickResult.Miss)

        // Clicking outside
        val resultOutside = scene.click(-5, 100)
        assertTrue(resultOutside is SdaClickResult.Outside)

        // Click second object
        val result3 = scene.click(30, 20)
        assertTrue(result3 is SdaClickResult.Found)
        assertEquals(8500, (result3 as SdaClickResult.Found).gain)
        assertTrue(scene.remainingCaptions().isEmpty())
        assertEquals(16000, scene.score.points)
    }

    @Test
    fun xui_prefix_and_bom() {
        val xuiWithBom = "\uFEFF<xui><mpi:eyespyobjects/></xui>".toByteArray(Charsets.UTF_8)
        val doc = SdaXui.parse(xuiWithBom)
        assertTrue(doc.textures.isEmpty())

        val xuiDoctype = "<!DOCTYPE xui><xui/>".toByteArray(Charsets.UTF_8)
        assertThrows(IllegalArgumentException::class.java) {
            SdaXui.parse(xuiDoctype)
        }
    }
}

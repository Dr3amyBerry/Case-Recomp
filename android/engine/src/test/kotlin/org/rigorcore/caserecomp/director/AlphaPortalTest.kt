package org.rigorcore.caserecomp.director

import org.junit.Assert.assertEquals
import org.junit.Test

class AlphaPortalTest {
    @Test fun resetting_alpha_restores_original_colour_below_a_transparent_portal() {
        val image = LingoImage(1, 1, 32, intArrayOf(0x001f1d1b))
        image.call("setAlpha", listOf(org.rigorcore.caserecomp.lingo.LingoValue.LInt(255)))
        assertEquals(0xff1f1d1b.toInt(), image.pixels[0])
    }
    @Test fun rgba_white_portal_copies_white_palette_index_zero_into_alpha_plane() {
        val mask = (LingoImage(3, 1, 32, IntArray(3) { -1 }).call("extractAlpha", emptyList()) as LingoImage).duplicate()
        val portal = LingoImage(3, 1, 32, intArrayOf(0xffffff, -1, 0xffffff))
        mask.copyPixels(portal, intArrayOf(0, 0, 3, 1), intArrayOf(0, 0, 3, 1), null)
        assertEquals(255, mask.pixels[0] and 255)
        assertEquals(0, mask.pixels[1] and 255)
        assertEquals(255, mask.pixels[2] and 255)
    }
    @Test fun ordinary_eight_bit_image_keeps_rgb_white() {
        val image = LingoImage(1, 1, 8)
        image.copyPixels(LingoImage(1, 1, 24, intArrayOf(-1)), intArrayOf(0, 0, 1, 1), intArrayOf(0, 0, 1, 1), null)
        assertEquals(-1, image.pixels[0])
    }
}

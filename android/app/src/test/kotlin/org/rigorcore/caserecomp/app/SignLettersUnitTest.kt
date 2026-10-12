package org.rigorcore.caserecomp.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignLettersUnitTest {
    @Test fun letters_are_found_where_the_light_board_differs_and_erased_with_the_wood() {
        val w = 40; val h = 12
        val wood = 0xFF503018.toInt()
        val dark = IntArray(w * h) { wood }
        val light = IntArray(w * h) { wood }
        // A slanted stroke of "letters": dark brown on one board, cream on the other.
        for (x in 5 until 35) for (dy in 0..2) {
            val y = 7 - x / 10 + dy
            dark[y * w + x] = 0xFF201510.toInt()
            light[y * w + x] = 0xFFA49969.toInt()
        }
        val letters = SignLetters.find(dark, light, w, h)
        assertNotNull(letters); letters!!
        assertTrue("rises to the right", letters.slope < 0)
        assertEquals(0xFF201510.toInt(), letters.darkColor)
        val erased = SignLetters.erase(light, w, h, letters.mask)
        assertTrue(erased.all { it == wood })
    }
}

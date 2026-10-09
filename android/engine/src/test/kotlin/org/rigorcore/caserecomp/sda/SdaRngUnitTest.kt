package org.rigorcore.caserecomp.sda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SdaRngUnitTest {

    @Test
    fun native_random_reference_sequence_and_u32_wrap() {
        val random = SdaRng(1L)
        val expected = listOf(41, 18467, 6334, 26500, 19169)
        val actual = (1..5).map { random.next() }
        assertEquals(expected, actual)

        val wrapRandom = SdaRng(0xFFFFFFFFL)
        assertEquals(35, wrapRandom.next())

        assertThrows(IllegalArgumentException::class.java) {
            SdaRng(-1L)
        }
    }

    @Test
    fun forward_shuffle_reference_and_suffix() {
        val original = (0..4).toList()
        assertEquals(listOf(1, 4, 3, 2, 0), sdaShuffle(original, 1L))
        assertEquals((0..4).toList(), original)

        val largeList = (0..19).toList()
        val shuffled = sdaShuffle(largeList, 1L, start = 4)
        assertEquals(listOf(0, 1, 2, 3), shuffled.subList(0, 4))
        assertEquals((4..19).toList(), shuffled.subList(4, 20).sorted())
    }

    @Test
    fun first_batch_and_unshuffled_second_batch() {
        val deck = SdaTargetDeck((0..29).toList())
        val first = deck.nextBatch(1L)
        val order = deck.order
        assertEquals(order.subList(0, 10), first)
        assertEquals(order.subList(10, 20), deck.nextBatch(999L))
        assertEquals(20, deck.cursor)
    }

    @Test
    fun native_equal_count_boundary_is_not_silently_wrapped() {
        val deck = SdaTargetDeck((0..9).toList())
        assertEquals(10, deck.nextBatch(1L).size)
        assertEquals(10, deck.cursor)
        val exception = assertThrows(IllegalArgumentException::class.java) {
            deck.nextBatch(2L)
        }
        assertEquals("native batch boundary reaches null child; transition needs validation", exception.message)
        assertEquals(10, deck.cursor)
    }

    @Test
    fun restore_matches_variants_and_pins_prefix() {
        val deck = SdaTargetDeck((0..14).toList())
        val variants = (0..14).associateWith { listOf(it.toString(), "part-$it") }
        val restored = deck.restoreBatch(variants, listOf("part-8", "not present", "2"), 1L)
        assertEquals(listOf(8, 2), restored)
        assertEquals(listOf(8, 2), deck.order.subList(0, 2))
        val remaining = deck.order.subList(2, 15).sorted()
        assertEquals((0..14).filter { it != 8 && it != 2 }, remaining)
        assertEquals(10, deck.cursor)
    }

    @Test
    fun restore_last_match_and_short_pool_cursor() {
        val deck = SdaTargetDeck(listOf("first", "last", "other"))
        val variants = mapOf(
            "first" to listOf("same"),
            "last" to listOf("same"),
            "other" to listOf("different")
        )
        assertEquals(listOf("last"), deck.restoreBatch(variants, listOf("same"), 1L))
        assertEquals("last", deck.order[0])
        assertEquals(0, deck.cursor)

        assertThrows(IllegalArgumentException::class.java) {
            deck.restoreBatch(variants, List(11) { "same" }, 1L)
        }
    }
}

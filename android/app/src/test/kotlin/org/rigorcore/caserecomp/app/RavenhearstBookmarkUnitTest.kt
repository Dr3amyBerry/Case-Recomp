package org.rigorcore.caserecomp.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RavenhearstBookmarkUnitTest {
    @Test fun bookmark_round_trips_and_rejects_unknown_scenes() {
        val mark = RavenhearstBookmark("a".repeat(64), 1, 3, "loc19", 1_800_000, 42_000)
        assertEquals(mark, RavenhearstBookmark.decode(mark.encode()))
        for (label in listOf("map", "door", "metagame", "mainmenu", "loc1", "loc32")) RavenhearstBookmark("a".repeat(64), 1, 1, label, 0, 0)
        assertThrows(IllegalArgumentException::class.java) { RavenhearstBookmark("a".repeat(64), 1, 1, "loc33", 0, 0) }
        assertThrows(IllegalArgumentException::class.java) { RavenhearstBookmark("a".repeat(64), 1, 21, "map", 0, 0) }
        assertThrows(IllegalArgumentException::class.java) { RavenhearstBookmark.decode(mark.encode().replace("\"version\":1", "\"version\":2")) }
    }
}

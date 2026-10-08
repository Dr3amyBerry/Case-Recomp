package org.rigorcore.caserecomp.app

import org.junit.Assert.*
import org.junit.Test

class DirectorSessionPersistenceUnitTest {
    @Test fun session_clock_excludes_background_time_without_resetting_playback_time() {
        var wall = 1000L
        val clock = DirectorSessionClock { wall }
        val start = clock.now()
        wall += 9000
        assertEquals(9000, clock.now() - start)
        clock.pause(); clock.pause()
        wall += 86_400_000
        assertEquals(9000, clock.now() - start)
        clock.resume(); clock.resume()
        wall += 3000
        assertEquals(12000, clock.now() - start)
        clock.pause(); wall += 10_000; clock.resume(); wall += 1000
        assertEquals(13000, clock.now() - start)
    }

    @Test fun checkpoint_metadata_round_trips_without_losing_clock_or_location() {
        val bookmark = HuntsvilleBookmark(HuntsvilleBookmark.hash("Synthetic User"), 2, 3, "loc7", 543000, 87000, 2)
        assertEquals(bookmark, HuntsvilleBookmark.decode(bookmark.encode()))
        assertNotEquals(bookmark.userHash, HuntsvilleBookmark.hash("Another User"))
    }

    @Test fun corrupted_and_unsupported_bookmarks_cannot_select_arbitrary_frames_or_commands() {
        val bookmark = HuntsvilleBookmark("a".repeat(64), 1, 1, "map", 120000, 0, 0).encode()
        for (bad in listOf(bookmark.replace("\"map\"", "\"quit()\""), bookmark.replace("\"map\"", "\"loc999\""),
            bookmark.replace("\"version\":1", "\"version\":2"), bookmark.replace("\"remaining\":120000", "\"remaining\":-1"),
            bookmark.replace("\"remaining\":120000", "\"remaining\":4294967296"), bookmark.dropLast(1) + ",\"script\":1}", "[1]")) {
            assertThrows(RuntimeException::class.java) { HuntsvilleBookmark.decode(bad) }
        }
    }
}

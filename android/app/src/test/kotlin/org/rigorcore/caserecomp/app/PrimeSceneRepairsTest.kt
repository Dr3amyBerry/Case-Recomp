package org.rigorcore.caserecomp.app

import org.junit.Assert.*
import org.junit.Test
import org.rigorcore.caserecomp.director.*

class PrimeSceneRepairsTest {
    private fun movie(existing: Boolean = false): DirectorMovie {
        val runs = if (existing) listOf(SpriteRun(153, 153, 67, 36, 13, 44, 100, 100, 38, 39, 100, false, false)) else emptyList()
        return DirectorMovie(800, 600, 30, listOf(153 to "loc12"),
            listOf(CastLibData(13, "12", "empty.cst")),
            CastFile("", mapOf(13 to MemberData(13, "item", "script"))),
            mapOf("12" to CastFile("12.cct", mapOf(44 to MemberData(44, "plastic bug", "bitmap", width = 38, height = 39)))),
            160, 200, runs, emptyList())
    }

    @Test fun restores_real_item_and_behavior_only_on_its_frame() {
        val repaired = PrimeSceneRepairs.apply(movie(), DirectorPresentationProfiles.PRIME_ID)
        val run = repaired.runAt(67, 153)!!
        assertEquals(13, run.castLib)
        assertEquals(44, run.member)
        assertEquals(305, run.x)
        assertEquals(0, run.blend)
        assertEquals(13, repaired.spanAt(67, 153)!!.behaviors.single().member)
        assertNull(repaired.runAt(67, 152))
        assertNull(repaired.runAt(67, 154))
        assertSame(repaired, PrimeSceneRepairs.apply(repaired, DirectorPresentationProfiles.PRIME_ID))
    }

    @Test fun leaves_other_titles_and_existing_items_untouched() {
        val original = movie()
        assertSame(original, PrimeSceneRepairs.apply(original, DirectorPresentationProfiles.HUNTSVILLE_ID))
        assertSame(original, PrimeSceneRepairs.apply(original, DirectorPresentationProfiles.GENERIC_ID))
        val fixed = movie(true)
        assertSame(fixed, PrimeSceneRepairs.apply(fixed, DirectorPresentationProfiles.PRIME_ID))
    }
}

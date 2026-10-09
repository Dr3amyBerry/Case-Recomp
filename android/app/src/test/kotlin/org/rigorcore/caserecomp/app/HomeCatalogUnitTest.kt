package org.rigorcore.caserecomp.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeCatalogUnitTest {

    @Test
    fun catalog_contains_all_planned_titles_with_correct_engines() {
        val games = HomeCatalog.GAMES
        assertTrue(games.size >= 4)

        val huntsville = games.find { it.id == "huntsville" }
        assertNotNull(huntsville)
        assertEquals(EngineFamily.DIRECTOR, huntsville!!.engine)
        assertEquals(CompatibilityStatus.VERIFIED, huntsville.status)

        val vegas = games.find { it.id == "vegas_heist" }
        assertNotNull(vegas)
        assertEquals(EngineFamily.SDA, vegas!!.engine)
        assertEquals(CompatibilityStatus.IN_DEVELOPMENT, vegas.status)
        assertEquals("Mystery P.I.", vegas.subtitle)

        val prime = games.find { it.id == "prime_suspects" }
        assertNotNull(prime)
        assertEquals(EngineFamily.DIRECTOR, prime!!.engine)
        assertEquals(CompatibilityStatus.IN_DEVELOPMENT, prime.status)
    }

    @Test
    fun sda_titles_are_isolated_from_director_ready_status() {
        val sdaGames = HomeCatalog.GAMES.filter { it.engine == EngineFamily.SDA }
        assertFalse(sdaGames.isEmpty())
        for (game in sdaGames) {
            // SDA games must NOT be marked VERIFIED for Director launcher
            assertFalse(game.status == CompatibilityStatus.VERIFIED)
        }
    }
}

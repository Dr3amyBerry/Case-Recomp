package org.rigorcore.caserecomp.sda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SdaProfileUnitTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun profile_serialization_and_storage() {
        val storage = SdaProfileStorage(tempFolder.root)
        assertTrue(storage.listProfiles().isEmpty())

        val p1 = SdaProfile(id = "pi_john", name = "Detective John", totalScore = 150000L, currentLevelIndex = 5)
        assertTrue(storage.saveProfile(p1))

        val loaded = storage.getProfile("pi_john")
        assertNotNull(loaded)
        assertEquals("Detective John", loaded!!.name)
        assertEquals(150000L, loaded.totalScore)
        assertEquals(5, loaded.currentLevelIndex)

        val list = storage.listProfiles()
        assertEquals(1, list.size)
        assertEquals("pi_john", list.first().id)

        // JSON roundtrip
        val json = p1.toJson()
        val restored = SdaProfile.fromJson(json)
        assertEquals(p1, restored)

        // Delete profile
        assertTrue(storage.deleteProfile("pi_john"))
        assertTrue(storage.listProfiles().isEmpty())
    }
}

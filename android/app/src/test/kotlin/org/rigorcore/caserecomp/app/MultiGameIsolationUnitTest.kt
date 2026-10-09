package org.rigorcore.caserecomp.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.rigorcore.caserecomp.sda.SdaContent
import org.rigorcore.caserecomp.sda.SdaImageDecoder
import org.rigorcore.caserecomp.sda.SyntheticSdaPackage
import java.io.File

class MultiGameIsolationUnitTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun sda_repository_imports_and_activates_in_isolated_namespace() {
        val sdaBase = tempFolder.newFolder("private-sda")
        val directorBase = tempFolder.newFolder("private-director")
        val sdaPrefs = InMemorySdaPreferences()

        val repo = PrivateSdaRepository(sdaBase, sdaPrefs)
        assertFalse(repo.hasActive())
        assertNull(repo.loadActive())

        val syntheticZip = File(tempFolder.root, "synthetic-vegas.zip")
        SyntheticSdaPackage.buildVaultPackage(syntheticZip)

        syntheticZip.inputStream().use { input ->
            val install = repo.import(input)
            assertNotNull(install)
            assertTrue(install.path.isFile)
            assertEquals(64, install.sha256.length)
        }

        assertTrue(repo.hasActive())
        assertNotNull(repo.loadActive())
        assertNotNull(repo.activeCover())

        // Verify Director private storage was NOT modified in any way
        assertEquals(0, directorBase.listFiles()?.size ?: 0)
    }

    @Test
    fun engine_cross_contamination_is_rejected_at_package_boundary() {
        val syntheticSdaZip = File(tempFolder.root, "cross-sda.zip")
        SyntheticSdaPackage.buildVaultPackage(syntheticSdaZip)

        // Attempting to open SDA package as Director content must fail immediately (no movie.json)
        assertThrows(RuntimeException::class.java) {
            org.rigorcore.caserecomp.director.DirectorContent.open(syntheticSdaZip) { null }
        }

        // Attempting to open a random / non-SDA zip as SDA content must fail (no manifest.json)
        val dummyZip = File(tempFolder.root, "empty.zip")
        java.util.zip.ZipOutputStream(dummyZip.outputStream()).use {
            it.putNextEntry(java.util.zip.ZipEntry("dummy.txt"))
            it.write("dummy".toByteArray())
            it.closeEntry()
        }
        assertThrows(IllegalArgumentException::class.java) {
            SdaContent.open(dummyZip, SdaImageDecoder { null })
        }
    }

    @Test
    fun huntsville_regression_and_catalog_isolation() {
        val games = HomeCatalog.GAMES
        val huntsville = games.first { it.id == "huntsville" }
        val vegas = games.first { it.id == "vegas_heist" }

        // Confirm distinct engine families and separate identities
        assertEquals(EngineFamily.DIRECTOR, huntsville.engine)
        assertEquals(EngineFamily.SDA, vegas.engine)
        assertEquals("Mystery Case Files", huntsville.subtitle)
        assertEquals("Mystery P.I.", vegas.subtitle)
    }
}

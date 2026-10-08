package org.rigorcore.caserecomp.app

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.rigorcore.caserecomp.director.DirectorContent
import org.rigorcore.caserecomp.director.ImageDecoder
import java.io.File

class DirectorPresentationProfilesUnitTest {
    private val profile = DirectorPresentationProfiles.HUNTSVILLE_ES
    private val known = BundleFingerprint(
        "59eaebfb1dc6b258872eccfc1532ae85895c51f9c7f3bf4c74d66bb49a212d6c",
        "da9c8c41c04cedc8b4cfd30f95f2c0aa905f207238d2ee9e8fd082daf2e5a718",
    )

    @Test fun complete_known_bundle_pair_selects_reviewed_profile() {
        assertSame(profile, DirectorPresentationProfiles.select(known))
        assertEquals(1, profile.schemaVersion)
        assertEquals(3, profile.revision)
    }

    @Test fun user_interface_alignment_is_scoped_to_verified_sprites_and_members() {
        assertEquals(0 to 6, profile.scopedNudges[33 to "enterNameField1"])
        assertEquals(12 to 6, profile.scopedNudges[31 to "cursor"])
        assertEquals(6 to 0, profile.scopedNudges[19 to "changeUserA"])
        assertEquals(6 to 0, profile.scopedNudges[19 to "changeUserB"])
        for (row in 1..5) assertEquals(0 to 6, profile.scopedNudges[(276 + row) to "enterNameField$row"])
        assertEquals(2, profile.interactiveNudges.size)
        assertTrue(profile.interactiveNudges.keys.all { it.first == 19 })
        assertTrue(DirectorPresentationProfiles.GENERIC.scopedNudges.isEmpty())
        assertTrue(DirectorPresentationProfiles.GENERIC.interactiveNudges.isEmpty())
    }

    @Test fun matching_one_bundle_or_claiming_source_identity_cannot_enable_profile() {
        assertSame(DirectorPresentationProfiles.GENERIC,
            DirectorPresentationProfiles.select(known.copy(movieSha256 = "a".repeat(64))))
        assertSame(DirectorPresentationProfiles.GENERIC,
            DirectorPresentationProfiles.select(known.copy(lingoSha256 = "a".repeat(64))))
        val claimed = """{"title":"Huntsville","source_sha256":"${known.movieSha256}"}""".toByteArray()
        assertSame(DirectorPresentationProfiles.GENERIC,
            DirectorPresentationProfiles.select(BundleFingerprint.of(claimed, claimed)))
    }

    @Test fun synthetic_and_unknown_movies_do_not_inherit_homonymous_font_calibrations() {
        val generic = DirectorPresentationProfiles.select(BundleFingerprint.of(byteArrayOf(1), byteArrayOf(2)))
        for (font in listOf("Tekton Italic *", "Palatino *", "Times New Roman", "Readout *", "Typewriter")) {
            assertEquals(font, FontPresentation(), generic.text.font(font))
        }
        assertNull(generic.text.boxes)
        assertFalse(generic.text.embeddedStyleFromFaceName)
        assertTrue(generic.legacyNudges("""{"nudge":{"cluesFoundLabel":[-5,0]}}""").isEmpty())
    }

    @Test fun known_font_values_and_rule_precedence_match_frozen_calibrations() {
        val samples = mapOf(
            "Tekton Italic *" to FontPresentation(0.95f, 0.68f, 1.32f),
            "Tekton *" to FontPresentation(1.15f, 0.8f, 1.12f),
            "Palatino *" to FontPresentation(0.88f, 1f, 1.08f),
            "Times New Roman" to FontPresentation(0.86f, 1f, 1.08f),
            "Typewriter" to FontPresentation(0.9f, 1f, 1.25f),
            "Readout *" to FontPresentation(0.9f, 0.95f, 1.25f, -0.18f),
            "Arial" to FontPresentation(linePitchScale = 1.25f),
            // Preserve the old independent precedence of width/size/pitch, including overlapping aliases.
            "Typewriter Palatino Tekton Italic Readout" to FontPresentation(0.9f, 0.68f, 1.08f, -0.18f),
        )
        for ((font, expected) in samples) assertEquals(font, expected, profile.text.font(font))
        assertEquals(TextBoxPresentation(6, 0.75f, 0.78f, 0.75f,
            listOf(1f, 0.95f, 0.9f, 0.85f), listOf(0.9f, 0.8f, 0.7f), 4), profile.text.boxes)
        assertTrue(profile.text.embeddedStyleFromFaceName)
    }

    @Test fun legacy_offsets_preserve_only_the_reviewed_request_and_remain_optional() {
        assertEquals(mapOf("cluesFoundLabel" to (-5 to 0)),
            profile.legacyNudges("""{"nudge":{"cluesFoundLabel":[-5,0]}}"""))
        assertTrue(profile.legacyNudges("""{"nudge":{}}""").isEmpty())
    }

    @Test fun manipulated_configuration_cannot_select_profiles_or_run_scripts() {
        for (json in listOf(
            """{"nudge":{"cluesFoundLabel":[50,0]}}""",
            """{"nudge":{"foreignMember":[-5,0]}}""",
            """{"nudge":{"cluesFoundLabel":[-5,0]},"profile":"huntsville"}""",
            """{"nudge":{},"script":"quit()"}""",
            """{"nudge":{"cluesFoundLabel":[-5.0,0]}}""",
            """{"nudge":{"cluesFoundLabel":[-5,0,1]}}""",
        )) assertThrows(json, RuntimeException::class.java) { profile.legacyNudges(json) }
    }

    @Test fun private_existing_packages_are_recognized_without_reimport_or_zip_changes() {
        val root = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .map { File(it, "private/huntsville/huntsville-m3") }.firstOrNull { it.isDirectory } ?: File("missing-private-fixtures")
        val packages = listOf("huntsville.director.v1.zip", "huntsville.director.v2-nocover.zip", "huntsville.director.zip")
            .map { File(root, it) }
        assumeTrue("Private owned packages are not CI fixtures", packages.all { it.isFile })
        for (file in packages) {
            val before = file.length() to file.lastModified()
            DirectorContent.open(file, ImageDecoder { null }).use { content ->
                content.verifyAll(requireSourceBinding = true)
                assertSame(file.name, profile, DirectorPresentationProfiles.resolve(content))
            }
            assertEquals(before, file.length() to file.lastModified())
        }
    }
}

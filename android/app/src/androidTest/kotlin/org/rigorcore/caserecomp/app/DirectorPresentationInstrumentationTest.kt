package org.rigorcore.caserecomp.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.rigorcore.caserecomp.director.*
import org.rigorcore.caserecomp.lingo.LingoBundle
import org.rigorcore.caserecomp.lingo.LingoValue.LInt

/** Synthetic text only. Runs the old and new Android rasterizers on the same actual device. */
@RunWith(AndroidJUnit4::class)
class DirectorPresentationInstrumentationTest {
    private fun member(font: String, text: String, alignment: String = "left"): CastMember {
        val m = MemberData(1, "synthetic label", "xtra", xtra = "text", width = 125, height = 41,
            text = text, textStyle = TextStyle(alignment, font, 15, listOf("bold", "italic"), 0xff123456.toInt(), 2, 1, 1))
        val movie = DirectorMovie(160, 100, 30, emptyList(), listOf(CastLibData(1, "Internal", "")),
            CastFile("", mapOf(1 to m)), emptyMap(), 1, 6, emptyList(), emptyList())
        return DirectorRuntime(movie, LingoBundle(emptyList(), emptyList())).castLib(1)!!.member(1)
    }

    @Test fun calibrated_profile_is_pixel_and_metric_identical_to_previous_android_rasterizer() {
        val old = LegacyDirectorTextReference()
        val migrated = AndroidDirectorText(DirectorPresentationProfiles.HUNTSVILLE_ES.text)
        val fonts = listOf("Tekton Italic *", "Tekton *", "Palatino *", "Times New Roman",
            "Typewriter", "Readout *", "Arial", "Typewriter Palatino Tekton Italic Readout")
        val texts = listOf("Synthetic centered panel caption with several words",
            "\nLeading blank paragraph\nSecond line with accents: \u00e1\u00e9\u00f1",
            "First\tSecond\nVeryLongUnbrokenSyntheticWordForOverflow")
        for (font in fonts) for (text in texts) for (alignment in listOf("left", "center", "right")) {
            val m = member(font, text, alignment)
            for (fixed in listOf(0, 18)) {
                m.setProp("fixedlinespace", LInt(fixed))
                assertEquals("width $font", old.width(m, text), migrated.width(m, text))
                assertEquals("pitch $font", old.lineHeight(m), migrated.lineHeight(m))
                for (scale in listOf(1, 2)) for ((w, h) in listOf(125 to 41, 48 to 12)) {
                    val expected = old.render(m, w, h, scale)!!
                    val actual = migrated.render(m, w, h, scale)!!
                    val label = "$font/$alignment/$fixed/$scale/${w}x$h"
                    assertEquals(label, expected.width, actual.width)
                    assertEquals(label, expected.height, actual.height)
                    assertArrayEquals(label, expected.pixels, actual.pixels)
                }
                // Both renderers must reset fit state before the next independent measurement.
                assertEquals(old.width(m, text), migrated.width(m, text))
            }
        }
    }

    @Test fun unknown_movie_with_same_face_uses_uncalibrated_android_text() {
        val m = member("Palatino *", "Synthetic text", "center")
        val generic = AndroidDirectorText()
        val calibrated = AndroidDirectorText(DirectorPresentationProfiles.HUNTSVILLE_ES.text)
        assertNotEquals(generic.width(m, m.text), calibrated.width(m, m.text))
        val ordinary = generic.render(m, 125, 20)!!
        assertEquals(20, ordinary.height)
        assertFalse(DirectorPresentationProfiles.GENERIC.text.embeddedStyleFromFaceName)
    }

    @Test fun existing_save_namespace_remains_readable_and_writable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val id = "c".repeat(64)
        val key = "profile-isolation-synthetic-check"
        val prefs = context.getSharedPreferences("case-recomp-director-user-data", Context.MODE_PRIVATE)
        val stored = "$id/$key"
        try {
            assertTrue(prefs.edit().putString(stored, "existing-save").commit())
            val store = AndroidDirectorStore(context, id)
            assertEquals("existing-save", store.get(key))
            store.put(key, "updated-save")
            assertEquals("updated-save", prefs.getString(stored, null))
            assertNull(AndroidDirectorStore(context, "d".repeat(64)).get(key))
        } finally { prefs.edit().remove(stored).commit() }
    }
}

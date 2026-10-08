package org.rigorcore.caserecomp.director

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.rigorcore.caserecomp.lingo.LingoError
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DirectorContentUnitTest {
    @get:Rule val tmp = TemporaryFolder()

    private val movieJson = """{"format":"case-recomp-movie-bundle","version":1,"stage":{"width":64,"height":48,"tempo":15},
        "labels":[],"cast_libs":[{"number":1,"name":"Internal","file_path":""},{"number":2,"name":"art","file_path":"art.cst"}],
        "internal_members":[{"number":3,"name":"logo","type":"bitmap","width":2,"height":1},
          {"number":6,"name":"welcome","type":"xtra","xtra":"text","text":"aa bb cc","alignment":"center",
           "style":{"font":"Typewriter","font_size":18,"font_style":["bold"],"color":"#20284b"}},
          {"number":7,"name":"label","type":"xtra","xtra":"text","text":"aaaa bb","alignment":"left",
           "indent":{"left":1,"right":0,"first":2}}],
        "external_casts":[{"file":"Art.CCT","sha256":"x","members":[{"number":1,"name":"tile","type":"bitmap","width":2,"height":1}]}],
        "score":{"frame_count":1,"channel_count":6,"sprite_record_size":48,"sprites":[],"spans":[]}}"""
    private val lingoJson = """{"format":"case-recomp-lingo-bundle","version":1,"names":[],"scripts":[]}"""

    private fun sha(data: ByteArray) = MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }

    private fun pack(files: Map<String, ByteArray>, listed: Map<String, ByteArray> = files, extra: String = ""): File {
        val file = tmp.newFile()
        val entries = listed.entries.joinToString(",") { (path, data) -> """{"path":"$path","bytes":${data.size},"sha256":"${sha(data)}"}""" }
        val manifest = """{"format":"case-recomp-director-content","version":1,"entries":[$entries$extra]}"""
        ZipOutputStream(file.outputStream()).use { zip ->
            for ((path, data) in files + ("manifest.json" to manifest.toByteArray())) {
                zip.putNextEntry(ZipEntry(path)); zip.write(data); zip.closeEntry()
            }
        }
        return file
    }

    /** Test decoder: a "PNG" here is width, height, then ARGB ints. */
    private val decoder = ImageDecoder { bytes ->
        val w = bytes[0].toInt(); val h = bytes[1].toInt()
        LingoImage(w, h, 32, IntArray(w * h) { i -> java.nio.ByteBuffer.wrap(bytes, 2 + i * 4, 4).int })
    }

    private fun image(vararg argb: Int): ByteArray {
        val buffer = java.nio.ByteBuffer.allocate(2 + argb.size * 4)
        buffer.put(argb.size.toByte()); buffer.put(1)
        argb.forEach { buffer.putInt(it) }
        return buffer.array()
    }

    private val files = mapOf(
        "movie.json" to movieJson.toByteArray(), "lingo.json" to lingoJson.toByteArray(),
        "media/internal/3.png" to image(0x00FFFFFF, 0xFF000000.toInt()),
        "media/art/1.png" to image(0xFF112233.toInt(), 0x80445566.toInt()),
        "media/internal/4.swf" to "FWS".toByteArray(),
        "media/internal/5.mp3" to "ID3".toByteArray(),
    )

    @Test fun reads_bundles_and_member_media_by_cast_file() {
        DirectorContent.open(pack(files), decoder).use { content ->
            assertEquals(64, content.movie.stageWidth)
            assertTrue(content.lingo.scripts.isEmpty())
            val logo = content.movie.internalCast.members.getValue(3)
            assertEquals(0xFF000000.toInt(), content.image("", logo)!!.pixels[1])
            val tile = content.movie.externalCast("art")!!.members.getValue(1)
            assertEquals(0x80445566.toInt(), content.image("C:\\game\\data\\Art.CCT", tile)!!.pixels[1])
            assertArrayEquals("FWS".toByteArray(), content.flash("", MemberData(4, "dialog", "xtra", xtra = "flash")))
            assertEquals("mp3", content.sound("", MemberData(5, "click", "sound"))!!.second)
            assertNull(content.image("", MemberData(9, "absent", "bitmap")))

            // The runtime draws and hit-tests with the decoded media through its cache.
            val rt = DirectorRuntime(content.movie, content.lingo, content)
            val member = rt.member(org.rigorcore.caserecomp.lingo.LingoValue.LString("logo"), null)
            assertEquals(0x00FFFFFF, member.pixels!!.pixels[0])
            assertTrue(member.pixels === member.pixels)
        }
    }

    @Test fun bitmaps_made_at_run_time_register_at_their_centre() {
        DirectorContent.open(pack(files), decoder).use { content ->
            val rt = DirectorRuntime(content.movie, content.lingo, content)
            val logo = rt.member(org.rigorcore.caserecomp.lingo.LingoValue.LString("logo"), null)
            val tile = logo.lib.newMember("bitmap")
            tile.setProp("image", LingoImage(70, 60))
            assertEquals(listOf(35, 30), listOf(tile.regX, tile.regY))
            assertEquals("authored members keep their registration", listOf(0, 0), listOf(logo.regX, logo.regY))
        }
    }

    @Test fun bundled_cast_files_report_a_size_from_their_packaged_media() {
        DirectorContent.open(pack(files), decoder).use { content ->
            // Header plus media/art/*: big enough that a cast is not mistaken for a missing download stub.
            assertEquals(1024 + files.getValue("media/art/1.png").size, content.castFileSize("C:\\game\\Art.cct"))
            assertEquals(-1, content.castFileSize("missing.cct"))
        }
    }

    @Test fun text_members_carry_their_authored_style_into_layout() {
        DirectorContent.open(pack(files), decoder).use { content ->
            val rt = DirectorRuntime(content.movie, content.lingo, content)
            val member = rt.member(org.rigorcore.caserecomp.lingo.LingoValue.LString("welcome"), null)
            assertEquals(listOf("center", "Typewriter", "18"), listOf(member.alignment, member.font, member.fontSize.toString()))
            assertTrue(member.bold)
            assertEquals(0xFF20284B.toInt(), member.textColor)
            // One unit per character: "aa bb" (5) fits a 6-wide box, "cc" wraps and is centred.
            val metrics = object : TextMetrics {
                override fun width(member: CastMember, text: String) = text.length
                override fun lineHeight(member: CastMember) = 1
            }
            val lines = TextLayout.lines(member, 6, metrics)
            assertEquals(listOf("aa bb", "cc"), lines.map { it.text })
            assertEquals(listOf(0, 2), lines.map { it.x })
            member.setProp("alignment", org.rigorcore.caserecomp.lingo.LingoValue.LSymbol("right"))
            assertEquals(listOf(1, 4), TextLayout.lines(member, 6, metrics).map { it.x })
            // Indents: the first line starts at left + first and is narrower, so "aaaa bb" wraps there.
            val label = rt.member(org.rigorcore.caserecomp.lingo.LingoValue.LString("label"), null)
            val indented = TextLayout.lines(label, 8, metrics)
            assertEquals(listOf("aaaa", "bb"), indented.map { it.text })
            assertEquals(listOf(3, 1), indented.map { it.x })
        }
    }

    @Test fun html_tables_become_tabbed_rows_with_font_and_tab_stops() {
        DirectorContent.open(pack(files), decoder).use { content ->
            val rt = DirectorRuntime(content.movie, content.lingo, content)
            val member = rt.member(org.rigorcore.caserecomp.lingo.LingoValue.LString("label"), null)
            member.setProp("html", org.rigorcore.caserecomp.lingo.LingoValue.LString(
                "<TABLE><TR><TD WIDTH=10><font size=5 color=#FFFFFF face='Arial'>A</FONT></TD><TD WIDTH=4></TD>" +
                    "<TD WIDTH=20>B</TD></TR><TR><TD>C</TD><TD></TD><TD>D</TD></TR></TABLE>"))
            // The table opens its own paragraph after the (empty) body text.
            assertEquals("\rA\t\tB\rC\t\tD", member.text)
            assertEquals(listOf(10, 14), member.tabStops)
            assertEquals(listOf(18, 0xFFFFFFFF.toInt(), "Arial"), listOf(member.fontSize, member.textColor, member.font))
            val metrics = object : TextMetrics {
                override fun width(member: CastMember, text: String) = text.length
                override fun lineHeight(member: CastMember) = 1
            }
            assertEquals(listOf(0 to "A", 10 to "", 14 to "B"), TextLayout.lines(member, 40, metrics)[1].segments)
            member.setProp("html", org.rigorcore.caserecomp.lingo.LingoValue.LString("<p>Top</p><table><tr><td>X</td></tr></table>"))
            assertEquals("no extra line when the table already starts a line", "Top\rX", member.text)
        }
    }

    @Test fun rejects_missing_unlisted_and_duplicate_manifest_paths() {
        assertThrows(LingoError::class.java) {
            DirectorContent.open(pack(files, listed = files - "media/internal/3.png"), decoder)
        }
        val duplicate = """{"path":"movie.json","bytes":123,"sha256":"${sha(movieJson.toByteArray())}"}"""
        assertThrows(LingoError::class.java) {
            DirectorContent.open(pack(files, extra = ",$duplicate"), decoder)
        }
    }

    @Test fun streaming_verification_rejects_corruption_in_rarely_used_media() {
        val damaged = files.toMutableMap().also { it["media/internal/4.swf"] = byteArrayOf(0, 1, 2) }
        DirectorContent.open(pack(damaged, listed = files), decoder).use { content ->
            assertThrows(LingoError::class.java) { content.verifyAll() }
        }
        DirectorContent.open(pack(files), decoder).use { content ->
            content.verifyAll()
            assertThrows(LingoError::class.java) { content.verifyAll(requireSourceBinding = true) }
        }
    }

    @Test fun rejects_tampered_unlisted_and_unsafe_entries() {
        val tampered = files.toMutableMap().also { it["media/art/1.png"] = image(0, 0) }
        DirectorContent.open(pack(tampered, listed = files), decoder).use { content ->
            val tile = content.movie.externalCast("art")!!.members.getValue(1)
            assertThrows(LingoError::class.java) { content.image("art.cct", tile) }
        }
        assertThrows(LingoError::class.java) {
            DirectorContent.open(pack(files, extra = ""","""+"""{"path":"../escape","bytes":1,"sha256":"00"}"""), decoder)
        }
        assertThrows(LingoError::class.java) { DirectorContent.open(pack(files - "movie.json", listed = files - "movie.json"), decoder) }
        val wrongFormat = tmp.newFile()
        ZipOutputStream(wrongFormat.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json")); zip.write("""{"format":"other","version":1}""".toByteArray()); zip.closeEntry()
        }
        assertThrows(LingoError::class.java) { DirectorContent.open(wrongFormat, decoder) }
    }
}

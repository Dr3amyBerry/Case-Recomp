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
        "internal_members":[{"number":3,"name":"logo","type":"bitmap","width":2,"height":1}],
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

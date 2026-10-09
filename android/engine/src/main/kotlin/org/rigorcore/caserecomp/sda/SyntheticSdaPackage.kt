package org.rigorcore.caserecomp.sda

import org.rigorcore.caserecomp.MiniJson
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Generates synthetic, self-contained SDA content packages for vertical slice testing,
 * without bundling any commercial or proprietary game assets.
 */
object SyntheticSdaPackage {

    private fun sha256(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    /**
     * Minimal 16x16 valid uncompressed PNG with solid/alpha pixels.
     */
    fun createSamplePng(width: Int = 16, height: Int = 16): ByteArray {
        // Minimal 1x1 or 16x16 PNG header and IDAT
        val raw = ByteArrayOutputStream()
        // PNG Signature
        raw.write(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a))
        // IHDR chunk: 16x16, 8-bit RGBA
        fun writeChunk(type: String, data: ByteArray) {
            val len = data.size
            raw.write((len ushr 24) and 0xff)
            raw.write((len ushr 16) and 0xff)
            raw.write((len ushr 8) and 0xff)
            raw.write(len and 0xff)
            val typeBytes = type.toByteArray(Charsets.US_ASCII)
            raw.write(typeBytes)
            raw.write(data)
            val crc = java.util.zip.CRC32()
            crc.update(typeBytes)
            crc.update(data)
            val crcVal = crc.value.toInt()
            raw.write((crcVal ushr 24) and 0xff)
            raw.write((crcVal ushr 16) and 0xff)
            raw.write((crcVal ushr 8) and 0xff)
            raw.write(crcVal and 0xff)
        }

        val ihdr = ByteArrayOutputStream().apply {
            write((width ushr 24) and 0xff)
            write((width ushr 16) and 0xff)
            write((width ushr 8) and 0xff)
            write(width and 0xff)
            write((height ushr 24) and 0xff)
            write((height ushr 16) and 0xff)
            write((height ushr 8) and 0xff)
            write(height and 0xff)
            write(8) // bit depth
            write(6) // color type RGBA
            write(0) // compression
            write(0) // filter
            write(0) // interlace
        }.toByteArray()
        writeChunk("IHDR", ihdr)

        // Raw pixel data with filter byte 0 per scanline
        val scanline = ByteArray(1 + width * 4) { i ->
            if (i == 0) 0 else if (i % 4 == 0) 255.toByte() else 128.toByte() // RGBA
        }
        val uncompressed = ByteArrayOutputStream()
        repeat(height) { uncompressed.write(scanline) }
        val deflater = java.util.zip.Deflater()
        deflater.setInput(uncompressed.toByteArray())
        deflater.finish()
        val idatBuf = ByteArray(64 * 1024)
        val idatOut = ByteArrayOutputStream()
        while (!deflater.finished()) {
            val count = deflater.deflate(idatBuf)
            idatOut.write(idatBuf, 0, count)
        }
        deflater.end()
        writeChunk("IDAT", idatOut.toByteArray())
        writeChunk("IEND", ByteArray(0))
        return raw.toByteArray()
    }

    /**
     * Builds a complete, valid synthetic SDA package zip containing SCENE_VAULT.
     */
    fun buildVaultPackage(targetFile: File) {
        val coverBytes = createSamplePng(16, 16)
        val textureBytes = createSamplePng(32, 32)
        val stringsBytes = """
            ID_VAULT_SAFE = "Caja fuerte"
            ID_VAULT_CHIP = "Ficha de casino"
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val vaultTxtBytes = """
            ID_NOTE = "Nota secreta"
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val vaultXuiBytes = """
            <xui>
              <texture id="tex_vault" uri="textures/vault.png"/>
              <eyespyimage id="safe" x="100" y="100" tex="tex_vault"/>
              <eyespyimage id="chip" x="200" y="150" tex="tex_vault"/>
              <eyespyset objects="safe" itemnamelist="@ID_VAULT_SAFE"/>
              <eyespyset objects="chip" itemnamelist="@ID_VAULT_CHIP"/>
            </xui>
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val fileEntries = mutableMapOf<String, ByteArray>(
            "cover.png" to coverBytes,
            "textures/vault.png" to textureBytes,
            "STRINGS.TXT" to stringsBytes,
            "SCENE_VAULT.TXT" to vaultTxtBytes,
            "SCENE_VAULT.MSL" to vaultXuiBytes,
        )

        // Build manifest with exact sizes and hashes
        val filesMeta = mutableMapOf<String, List<Any>>()
        for ((name, data) in fileEntries) {
            filesMeta[name] = listOf(data.size.toLong(), sha256(data))
        }

        val manifestMap = mapOf(
            "format" to "case-recomp-sda-content",
            "version" to 1,
            "game_id" to "vegas_heist",
            "cover" to "cover.png",
            "files" to filesMeta
        )
        val manifestJson = MiniJson.canonical(manifestMap).toByteArray(Charsets.UTF_8)
        fileEntries["manifest.json"] = manifestJson

        targetFile.parentFile?.mkdirs()
        ZipOutputStream(targetFile.outputStream()).use { zos ->
            for ((path, bytes) in fileEntries) {
                val entry = ZipEntry(path)
                entry.size = bytes.size.toLong()
                entry.time = System.currentTimeMillis()
                zos.putNextEntry(entry)
                zos.write(bytes)
                zos.closeEntry()
            }
        }
    }
}

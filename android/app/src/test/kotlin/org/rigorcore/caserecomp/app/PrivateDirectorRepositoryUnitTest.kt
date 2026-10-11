package org.rigorcore.caserecomp.app

import android.content.SharedPreferences
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.lang.reflect.Proxy
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PrivateDirectorRepositoryUnitTest {
    @get:Rule val tmp = TemporaryFolder()
    private val values = linkedMapOf<String, String>()
    private val prime = "210a54856729bf4db9a28ed85d1b41a9c08967fce50b4acca20b95f1ddff869b"
    private val huntsville = "0ef4a73bceae060970d2513f5f6132663843064d7106b724db46cac810a4b28d"
    private fun hash(data: ByteArray) = MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }

    private fun preferences(): SharedPreferences = Proxy.newProxyInstance(
        SharedPreferences::class.java.classLoader, arrayOf(SharedPreferences::class.java),
    ) { _, method, args ->
        when (method.name) {
            "getString" -> values[args[0] as String] ?: args[1]
            "edit" -> {
                val staged = linkedMapOf<String, String?>()
                var editor: Any? = null
                editor = Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader,
                    arrayOf(SharedPreferences.Editor::class.java)) { _, edit, arguments ->
                    when (edit.name) {
                        "putString" -> { staged[arguments[0] as String] = arguments[1] as String?; editor }
                        "remove" -> { staged[arguments[0] as String] = null; editor }
                        "commit" -> { for ((key, value) in staged) if (value == null) values.remove(key) else values[key] = value; true }
                        else -> error("unexpected preference operation: ${edit.name}")
                    }
                }
                editor
            }
            else -> error("unexpected preference operation: ${method.name}")
        }
    } as SharedPreferences

    /** Original-code-free package with valid size/hash/source binding. */
    private fun pack(source: String): ByteArray {
        val files = mapOf(
            "movie.json" to """{"format":"case-recomp-movie-bundle","version":1,"source_sha256":"$source",
              "stage":{"width":64,"height":48,"tempo":30},"labels":[],"cast_libs":[{"number":1,"name":"Internal","file_path":""}],
              "internal_members":[],"external_casts":[],"score":{"frame_count":1,"channel_count":6,"sprite_record_size":48,"sprites":[],"spans":[]}}""".toByteArray(),
            "lingo.json" to """{"format":"case-recomp-lingo-bundle","version":1,"source_sha256":"$source","names":[],"scripts":[]}""".toByteArray(),
        )
        val entries = files.entries.joinToString(",") { (path, data) -> """{"path":"$path","bytes":${data.size},"sha256":"${hash(data)}"}""" }
        val manifest = """{"format":"case-recomp-director-content","version":1,"source_sha256":{"internal":"$source"},"entries":[$entries]}""".toByteArray()
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            for ((name, data) in files + ("manifest.json" to manifest)) {
                zip.putNextEntry(ZipEntry(name)); zip.write(data); zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    @Test fun imports_activate_only_the_selected_title_and_survive_repository_recreation() {
        val root = tmp.newFolder()
        val repository = PrivateDirectorRepository(root, preferences())
        val hunt = repository.import(ByteArrayInputStream(pack(huntsville)), "huntsville")
        val primeInstall = repository.import(ByteArrayInputStream(pack(prime)), "prime_suspects")
        assertEquals(hunt.sha256, values["active"])
        assertEquals(primeInstall.sha256, values["active/prime_suspects"])
        val reopened = PrivateDirectorRepository(root, preferences())
        assertEquals(hunt, reopened.loadActive())
        assertEquals(primeInstall, reopened.loadActive("prime_suspects"))
        assertTrue(reopened.hasActive("huntsville"))
        assertTrue(reopened.hasActive("prime_suspects"))
    }

    @Test fun legacy_huntsville_selection_is_preserved_when_prime_is_imported() {
        val root = tmp.newFolder()
        val oldBytes = pack(huntsville)
        val sha = hash(oldBytes)
        val file = File(root, "private-director/packages/$sha.zip")
        file.parentFile.mkdirs(); file.writeBytes(oldBytes)
        values["active"] = sha
        val repository = PrivateDirectorRepository(root, preferences())
        repository.import(ByteArrayInputStream(pack(prime)), "prime_suspects")
        assertEquals(sha, repository.loadActive()!!.sha256)
        assertArrayEquals(oldBytes, file.readBytes())
    }

    @Test fun wrong_title_or_corrupt_package_cannot_replace_an_active_selection() {
        val repository = PrivateDirectorRepository(tmp.newFolder(), preferences())
        val hunt = repository.import(ByteArrayInputStream(pack(huntsville)), "huntsville")
        assertThrows(IllegalArgumentException::class.java) {
            repository.import(ByteArrayInputStream(pack(prime)), "huntsville")
        }
        assertEquals(hunt, repository.loadActive())
        assertFalse(repository.hasActive("prime_suspects"))
        assertThrows(Exception::class.java) { repository.import(ByteArrayInputStream(byteArrayOf(1, 2, 3)), "prime_suspects") }
        assertEquals(hunt, repository.loadActive())
    }

    @Test fun damaged_stored_prime_package_does_not_prevent_loading_huntsville() {
        val repository = PrivateDirectorRepository(tmp.newFolder(), preferences())
        val hunt = repository.import(ByteArrayInputStream(pack(huntsville)), "huntsville")
        val primeInstall = repository.import(ByteArrayInputStream(pack(prime)), "prime_suspects")
        primeInstall.path.appendBytes(byteArrayOf(1))
        assertNull(repository.loadActive("prime_suspects"))
        assertEquals(hunt, repository.loadActive())
    }

    @Test fun user_data_is_isolated_and_prime_package_updates_keep_its_existing_save() {
        val prefs = preferences()
        val huntHash = "a".repeat(64)
        val hunt = AndroidDirectorStore(prefs, huntHash)
        val primeId = DirectorPresentationProfiles.storeId("prime_suspects", prime, huntHash)
        val firstPrime = AndroidDirectorStore(prefs, primeId)
        hunt.put("registry/shared/player", "Hunt Player")
        firstPrime.put("registry/shared/player", "Prime Player")
        val updatedPrime = AndroidDirectorStore(prefs,
            DirectorPresentationProfiles.storeId("prime_suspects", prime, "b".repeat(64)))
        assertEquals("Prime Player", updatedPrime.get("registry/shared/player"))
        assertEquals("Hunt Player", hunt.get("registry/shared/player"))
        assertEquals("Hunt Player", values["$huntHash/registry/shared/player"])
        updatedPrime.remove("registry/shared/player")
        assertEquals("Hunt Player", hunt.get("registry/shared/player"))
    }
}

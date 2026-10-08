package org.rigorcore.caserecomp.app

import android.content.Context
import org.rigorcore.caserecomp.director.DirectorContent
import org.rigorcore.caserecomp.director.ImageDecoder
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * Separate content store for Phase 10. The Director ZIP is NOT the Phase 5 .crcontent format.
 * Imported only by the user through ACTION_OPEN_DOCUMENT; never bundled with the APK.
 *
 * The compressed ZIP is copied with a global cap, validated entry-by-entry (including
 * the source SHA binding), then committed to app-private storage under its own digest.
 */
internal class PrivateDirectorRepository(context: Context) {
    private val base = File(context.filesDir, "private-director")
    private val packages = File(base, "packages")
    private val preferences = context.getSharedPreferences("case-recomp-director", Context.MODE_PRIVATE)

    data class Install(val path: File, val sha256: String)

    @Synchronized fun import(input: InputStream): Install {
        require(packages.isDirectory || packages.mkdirs()) { "cannot create private Director storage" }
        val stage = File(base, ".staging-" + System.nanoTime() + ".zip")
        try {
            val hash = MessageDigest.getInstance("SHA-256")
            var size = 0L
            stage.outputStream().use { out ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    size += count
                    require(size <= MAX_ZIP_BYTES) { "private Director archive is too large" }
                    hash.update(buffer, 0, count)
                    out.write(buffer, 0, count)
                }
                out.flush()
            }
            require(size > 0) { "private Director archive is empty" }
            val sha = hash.digest().toHex()
            // This also catches tampering in *unused* media members, not just movie/lingo.json.
            val cover = DirectorContent.open(stage, ImageDecoder { null }).use {
                it.verifyAll(requireSourceBinding = true)
                it.coverPng()
            }
            val destination = File(packages, "$sha.zip")
            if (destination.exists()) {
                require(destination.isFile && digest(destination) == sha) { "stored Director archive is damaged" }
            } else {
                require(stage.renameTo(destination)) { "cannot commit private Director archive" }
            }
            saveCover(sha, cover)
            require(preferences.edit().putString(ACTIVE, sha).commit()) { "cannot activate Director archive" }
            return Install(destination, sha)
        } finally {
            stage.delete()
        }
    }

    /** Cheap check for the home screen: an imported package is active (verified again on load). */
    fun hasActive(): Boolean = preferences.getString(ACTIVE, null)?.let { SHA.matches(it) && File(packages, "$it.zip").isFile } == true

    /** The active package's cover PNG, saved at import from the package's own media. */
    fun activeCover(): File? = preferences.getString(ACTIVE, null)?.takeIf(SHA::matches)
        ?.let { File(packages, "$it.cover.png") }?.takeIf { it.isFile && it.length() in 1..MAX_COVER_BYTES }

    private fun saveCover(sha: String, png: ByteArray?) {
        val file = File(packages, "$sha.cover.png")
        if (png == null || png.size > MAX_COVER_BYTES) { file.delete(); return }
        val partial = File(packages, "$sha.cover.partial")
        partial.writeBytes(png)
        if (!partial.renameTo(file)) { file.delete(); partial.renameTo(file) }
    }

    /** Verify the stored container bytes again at every load before constructing the VM. */
    fun loadActive(): Install? {
        val sha = preferences.getString(ACTIVE, null) ?: return null
        if (!SHA.matches(sha)) return null
        val file = File(packages, "$sha.zip")
        if (!file.isFile || file.length() !in 1..MAX_ZIP_BYTES || digest(file) != sha) return null
        return Install(file, sha)
    }

    private fun digest(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                md.update(buffer, 0, n)
            }
        }
        return md.digest().toHex()
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

    companion object {
        private const val ACTIVE = "active"
        private const val MAX_ZIP_BYTES = 768L * 1024 * 1024
        private const val MAX_COVER_BYTES = 8L * 1024 * 1024
        private val SHA = Regex("[a-f0-9]{64}")
    }
}

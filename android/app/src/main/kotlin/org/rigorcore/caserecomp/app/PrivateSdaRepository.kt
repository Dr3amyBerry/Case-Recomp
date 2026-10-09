package org.rigorcore.caserecomp.app

import android.content.Context
import org.rigorcore.caserecomp.sda.SdaContent
import org.rigorcore.caserecomp.sda.SdaImageDecoder
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

internal interface SdaPreferences {
    fun getString(key: String, def: String?): String?
    fun setString(key: String, value: String): Boolean
}

internal class AndroidSdaPreferences(private val prefs: android.content.SharedPreferences) : SdaPreferences {
    override fun getString(key: String, def: String?): String? = prefs.getString(key, def)
    override fun setString(key: String, value: String): Boolean = prefs.edit().putString(key, value).commit()
}

internal class InMemorySdaPreferences : SdaPreferences {
    private val map = mutableMapOf<String, String>()
    override fun getString(key: String, def: String?): String? = map[key] ?: def
    override fun setString(key: String, value: String): Boolean { map[key] = value; return true }
}

/**
 * Isolated content store for SDA games (e.g. Mystery P.I.: The Vegas Heist).
 * Completely separated from PrivateDirectorRepository:
 * - Uses filesDir/private-sda storage.
 * - Uses "case-recomp-sda" SharedPreferences.
 * - Never shares namespaces, packages, or saves with Director titles.
 */
internal class PrivateSdaRepository internal constructor(
    private val base: File,
    private val preferences: SdaPreferences,
) {
    constructor(context: Context) : this(
        File(context.filesDir, "private-sda"),
        AndroidSdaPreferences(context.getSharedPreferences("case-recomp-sda", Context.MODE_PRIVATE))
    )

    private val packages = File(base, "packages")

    data class SdaInstall(val path: File, val sha256: String)

    @Synchronized
    fun import(input: InputStream): SdaInstall {
        require(packages.isDirectory || packages.mkdirs()) { "cannot create private SDA storage" }
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
                    require(size <= MAX_ZIP_BYTES) { "private SDA archive is too large" }
                    hash.update(buffer, 0, count)
                    out.write(buffer, 0, count)
                }
                out.flush()
            }
            require(size > 0) { "private SDA archive is empty" }
            val sha = hash.digest().toHex()

            // Validate package entries against manifest
            val cover = SdaContent.open(stage, SdaImageDecoder { null }).use {
                it.verifyAll()
                it.coverPng()
            }

            val destination = File(packages, "$sha.zip")
            if (destination.exists()) {
                require(destination.isFile && digest(destination) == sha) { "stored SDA archive is damaged" }
            } else {
                require(stage.renameTo(destination)) { "cannot commit private SDA archive" }
            }
            saveCover(sha, cover)
            require(preferences.setString(ACTIVE, sha)) { "cannot activate SDA archive" }
            return SdaInstall(destination, sha)
        } finally {
            stage.delete()
        }
    }

    fun hasActive(): Boolean =
        preferences.getString(ACTIVE, null)?.let { SHA.matches(it) && File(packages, "$it.zip").isFile } == true

    fun activeCover(): File? = preferences.getString(ACTIVE, null)?.takeIf(SHA::matches)
        ?.let { File(packages, "$it.cover.png") }?.takeIf { it.isFile && it.length() in 1..MAX_COVER_BYTES }

    private fun saveCover(sha: String, png: ByteArray?) {
        val file = File(packages, "$sha.cover.png")
        if (png == null || png.size > MAX_COVER_BYTES) { file.delete(); return }
        val partial = File(packages, "$sha.cover.partial")
        partial.writeBytes(png)
        if (!partial.renameTo(file)) { file.delete(); partial.renameTo(file) }
    }

    fun loadActive(): SdaInstall? {
        val sha = preferences.getString(ACTIVE, null) ?: return null
        if (!SHA.matches(sha)) return null
        val file = File(packages, "$sha.zip")
        if (!file.isFile || file.length() !in 1..MAX_ZIP_BYTES || digest(file) != sha) return null
        return SdaInstall(file, sha)
    }

    val profileStorage = org.rigorcore.caserecomp.sda.SdaProfileStorage(File(base, "profiles"))

    fun getActiveProfile(): org.rigorcore.caserecomp.sda.SdaProfile {
        val id = preferences.getString("active_profile_id", null)
        val profile = id?.let { profileStorage.getProfile(it) }
        if (profile != null) return profile
        val defaultProfile = org.rigorcore.caserecomp.sda.SdaProfile("default_pi", "Detective Principal")
        profileStorage.saveProfile(defaultProfile)
        preferences.setString("active_profile_id", defaultProfile.id)
        return defaultProfile
    }

    fun setActiveProfile(profile: org.rigorcore.caserecomp.sda.SdaProfile): Boolean {
        profileStorage.saveProfile(profile)
        return preferences.setString("active_profile_id", profile.id)
    }

    fun saveCheckpoint(stateJson: String): Boolean =
        preferences.setString(CHECKPOINT, stateJson)

    fun loadCheckpoint(): String? =
        preferences.getString(CHECKPOINT, null)

    fun clearCheckpoint(): Boolean =
        preferences.setString(CHECKPOINT, "")

    fun saveCampaignCheckpoint(json: String): Boolean =
        preferences.setString("active_campaign_checkpoint", json)

    fun loadCampaignCheckpoint(): String? =
        preferences.getString("active_campaign_checkpoint", null)

    fun clearCampaignCheckpoint(): Boolean =
        preferences.setString("active_campaign_checkpoint", "")

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
        private const val ACTIVE = "active-package-sha256"
        private const val CHECKPOINT = "session-checkpoint"
        private const val MAX_ZIP_BYTES = 128 * 1024 * 1024L
        private const val MAX_COVER_BYTES = 4 * 1024 * 1024
        private val SHA = Regex("^[0-9a-f]{64}$")
    }
}

package org.rigorcore.caserecomp.sda

import org.rigorcore.caserecomp.MiniJson
import java.io.File

/**
 * Generic player profile for SDA games.
 * Decoupled from game-specific logic and completely isolated from Director profiles.
 */
data class SdaProfile(
    val id: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val totalScore: Long = 0L,
    val currentLevelIndex: Int = 0,
    val completedCampaigns: Int = 0,
    val avatar: String = "generic",
) {
    fun toJson(): String {
        val map = mapOf(
            "id" to id,
            "name" to name,
            "createdAt" to createdAt,
            "totalScore" to totalScore,
            "currentLevelIndex" to currentLevelIndex.toLong(),
            "completedCampaigns" to completedCampaigns.toLong(),
            "avatar" to avatar,
        )
        return MiniJson.canonical(map)
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(text: String): SdaProfile {
            val map = MiniJson.parse(text) as Map<String, Any?>
            return SdaProfile(
                id = map["id"] as String,
                name = map["name"] as String,
                createdAt = (map["createdAt"] as Number).toLong(),
                totalScore = (map["totalScore"] as Number).toLong(),
                currentLevelIndex = (map["currentLevelIndex"] as Number).toInt(),
                completedCampaigns = (map["completedCampaigns"] as Number).toInt(),
                avatar = map["avatar"] as? String ?: "generic",
            )
        }
    }
}

/**
 * Profile storage manager inside SDA private files directory.
 */
class SdaProfileStorage(private val storageDir: File) {

    init {
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }
    }

    private val safeId = Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,79}")
    private fun profileFile(id: String): File? =
        if (safeId.matches(id)) File(storageDir, "$id.json") else null

    @Synchronized fun listProfiles(): List<SdaProfile> {
        val files = storageDir.listFiles { f -> f.extension == "json" } ?: return emptyList()
        return files.mapNotNull { getProfile(it.nameWithoutExtension) }.sortedByDescending { it.createdAt }
    }

    @Synchronized fun getProfile(id: String): SdaProfile? {
        val file = profileFile(id) ?: return null
        if (!file.isFile) return null
        return runCatching { SdaProfile.fromJson(file.readText(Charsets.UTF_8)) }
            .getOrNull()?.takeIf { it.id == id }
    }

    @Synchronized fun saveProfile(profile: SdaProfile): Boolean {
        val file = profileFile(profile.id) ?: return false
        return runCatching {
            check(storageDir.isDirectory || storageDir.mkdirs())
            val stage = File.createTempFile("profile-", ".partial", storageDir)
            try {
                java.io.FileOutputStream(stage).use {
                    it.write(profile.toJson().toByteArray(Charsets.UTF_8))
                    it.fd.sync()
                }
                try {
                    java.nio.file.Files.move(stage.toPath(), file.toPath(),
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                } catch (e: java.nio.file.AtomicMoveNotSupportedException) {
                    java.nio.file.Files.move(stage.toPath(), file.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                }
            } finally { stage.delete() }
            true
        }.getOrDefault(false)
    }

    @Synchronized fun deleteProfile(id: String): Boolean {
        val file = profileFile(id) ?: return false
        return if (file.exists()) file.delete() else false
    }
}
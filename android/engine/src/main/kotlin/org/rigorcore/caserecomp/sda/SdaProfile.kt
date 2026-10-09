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
) {
    fun toJson(): String {
        val map = mapOf(
            "id" to id,
            "name" to name,
            "createdAt" to createdAt,
            "totalScore" to totalScore,
            "currentLevelIndex" to currentLevelIndex.toLong(),
            "completedCampaigns" to completedCampaigns.toLong(),
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

    fun listProfiles(): List<SdaProfile> {
        val files = storageDir.listFiles { f -> f.extension == "json" } ?: return emptyList()
        return files.mapNotNull { file ->
            runCatching { SdaProfile.fromJson(file.readText(Charsets.UTF_8)) }.getOrNull()
        }.sortedByDescending { it.createdAt }
    }

    fun getProfile(id: String): SdaProfile? {
        val file = File(storageDir, "$id.json")
        if (!file.isFile) return null
        return runCatching { SdaProfile.fromJson(file.readText(Charsets.UTF_8)) }.getOrNull()
    }

    fun saveProfile(profile: SdaProfile): Boolean {
        val file = File(storageDir, "${profile.id}.json")
        return runCatching {
            file.writeText(profile.toJson(), Charsets.UTF_8)
            true
        }.getOrDefault(false)
    }

    fun deleteProfile(id: String): Boolean {
        val file = File(storageDir, "$id.json")
        return if (file.exists()) file.delete() else false
    }
}

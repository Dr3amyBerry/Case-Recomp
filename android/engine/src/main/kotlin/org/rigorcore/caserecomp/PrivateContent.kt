package org.rigorcore.caserecomp

import java.security.MessageDigest

private const val CONTENT_FORMAT = "case-recomp-private-content"
private const val HASH_LEN = 64
private val HEX = Regex("[0-9a-f]{64}")

fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes).joinToString("") { "%02x".format(it) }
fun sha256Hex(text: String): String = sha256Hex(text.toByteArray(Charsets.UTF_8))

private fun safePath(value: String): String {
    require(value.isNotBlank() && !value.startsWith('/') && '\\' !in value && value.length <= 512)
    val parts = value.split('/')
    require(parts.none { it.isBlank() || it == "." || it == ".." })
    return value
}
private fun hash(value: String): String { require(value.length == HASH_LEN && HEX.matches(value)); return value }

data class ContentAssetV1(val id: String, val path: String, val sha256: String, val bytes: Long, val mediaType: String) {
    init {
        require(id == "sha256-${hash(sha256)}")
        safePath(path); require(bytes >= 0L)
        require(mediaType in setOf("image/png", "audio/wav", "audio/mpeg"))
    }
}
data class ContentBindingsV1(
    val sceneBackgrounds: Map<String, String> = emptyMap(),
    val targets: Map<String, Map<String, String>> = emptyMap(),
    val audio: Map<String, String> = emptyMap(),
)
data class PrivateContentManifestV1(
    val packageId: String,
    val scenarioPath: String,
    val scenarioSha256: String,
    val scenarioId: String,
    val assets: List<ContentAssetV1>,
    val bindings: ContentBindingsV1,
    val conversionManifestSha256: String,
    val tracePlanPath: String? = null,
    val tracePlanSha256: String? = null,
) {
    init {
        hash(packageId); safePath(scenarioPath); hash(scenarioSha256); hash(conversionManifestSha256)
        require(scenarioId.isNotBlank() && assets.isNotEmpty())
        require(assets.map { it.id }.toSet().size == assets.size)
        require(assets.map { it.path }.toSet().size == assets.size)
        val ids = assets.map { it.id }.toSet()
        val bound = bindings.sceneBackgrounds.values + bindings.targets.values.flatMap { it.values } + bindings.audio.values
        require(bound.all { it in ids })
        require(bindings.audio.keys.all { it in setOf("START", "TARGET_FOUND", "SCENE_COMPLETE", "SCENARIO_COMPLETE") })
        require((tracePlanPath == null) == (tracePlanSha256 == null))
        tracePlanPath?.let(::safePath); tracePlanSha256?.let(::hash)
    }
    fun asset(id: String): ContentAssetV1? = assets.find { it.id == id }
}

object PrivateContentManifestParser {
    fun parseAndMigrate(text: String): PrivateContentManifestV1 {
        val root = MiniJson.parse(text).jsonObject("manifest")
        require(root["format"] == CONTENT_FORMAT) { "unsupported private-content format" }
        return when (root["version"].jsonInt("version")) {
            0 -> migrateV0(root)
            1 -> parseV1(root, validatePackageId = true)
            else -> error("unsupported private-content manifest version")
        }
    }

    private fun parseV1(root: Map<String, Any?>, validatePackageId: Boolean): PrivateContentManifestV1 {
        val scenario = root["scenario"].jsonObject("scenario")
        val assets = root["assets"].jsonList("assets").map { row ->
            val obj = row.jsonObject("asset")
            ContentAssetV1(obj["id"].jsonString("asset.id"), obj["path"].jsonString("asset.path"),
                obj["sha256"].jsonString("asset.sha256"), (obj["bytes"] as Number).toLong(), obj["media_type"].jsonString("asset.media_type"))
        }
        val bindings = bindings(root["bindings"].jsonObject("bindings"))
        val source = root["source"].jsonObject("source")
        val trace = root["trace_plan"]?.jsonObject("trace_plan")
        val manifest = PrivateContentManifestV1(
            packageId = root["package_id"].jsonString("package_id"), scenarioPath = scenario["path"].jsonString("scenario.path"),
            scenarioSha256 = scenario["sha256"].jsonString("scenario.sha256"), scenarioId = scenario["id"].jsonString("scenario.id"),
            assets = assets, bindings = bindings,
            conversionManifestSha256 = source["conversion_manifest_sha256"].jsonString("source conversion hash"),
            tracePlanPath = trace?.get("path") as? String, tracePlanSha256 = trace?.get("sha256") as? String,
        )
        if (validatePackageId) {
            val base = root.filterKeys { it != "package_id" }
            require(sha256Hex(MiniJson.canonical(base) + "\n") == manifest.packageId) { "private-content package id mismatch" }
        }
        return manifest
    }

    /** Historical synthetic v0 is migration-only; the Python packager emits v1 exclusively. */
    private fun migrateV0(root: Map<String, Any?>): PrivateContentManifestV1 {
        val assetsV1 = root["assets"].jsonList("assets").map { row ->
            val obj = row.jsonObject("asset")
            mapOf("id" to obj["id"], "path" to obj["path"], "sha256" to obj["sha256"],
                "bytes" to obj["size"], "media_type" to obj["mime"])
        }
        val migratedBase = linkedMapOf<String, Any?>(
            "format" to CONTENT_FORMAT, "version" to 1L,
            "scenario" to mapOf("path" to root["scenario_file"], "sha256" to root["scenario_sha256"], "id" to root["scenario_id"]),
            "assets" to assetsV1, "bindings" to root["bindings"],
            "source" to mapOf("conversion_manifest_sha256" to root["source_manifest_sha256"]),
        )
        val packageId = sha256Hex(MiniJson.canonical(migratedBase) + "\n")
        return parseV1(migratedBase + ("package_id" to packageId), validatePackageId = true)
    }

    private fun bindings(obj: Map<String, Any?>): ContentBindingsV1 {
        fun stringMap(value: Any?, name: String): Map<String, String> = value.jsonObject(name).mapValues { (_, v) -> v.jsonString(name) }
        val backgrounds = stringMap(obj["scene_backgrounds"], "scene_backgrounds")
        val targets = obj["targets"].jsonObject("targets").mapValues { (scene, value) -> stringMap(value, "targets.$scene") }
        val audio = stringMap(obj["audio"], "audio")
        return ContentBindingsV1(backgrounds, targets, audio)
    }
}

object ScenarioJsonV1 {
    fun parse(text: String): ScenarioV1 {
        val root = MiniJson.parse(text).jsonObject("scenario")
        require(root["format"] == "case-recomp-scenario" && root["version"].jsonInt("version") == 1)
        val design = root["design"].jsonObject("design")
        val scenes = root["scenes"].jsonList("scenes").map { item ->
            val scene = item.jsonObject("scene")
            val targets = scene["targets"].jsonList("targets").map { targetItem ->
                val target = targetItem.jsonObject("target")
                val rect = target["rect"].jsonList("rect").map { it.jsonInt("rect") }
                require(rect.size == 4)
                ScenarioTargetV1(target["id"].jsonString("target.id"), GameRect(rect[0].toFloat(), rect[1].toFloat(), rect[2].toFloat(), rect[3].toFloat()), target["z"].jsonInt("target.z"))
            }
            ScenarioSceneV1(scene["id"].jsonString("scene.id"), scene["frame_start"].jsonInt("frame_start"), scene["frame_end"].jsonInt("frame_end"), targets)
        }
        val events = root["events"].jsonList("events").map { item ->
            val event = item.jsonObject("event")
            ScenarioEventV1(event["id"].jsonString("event.id"), event["kind"].jsonString("event.kind"),
                event["scene"] as? String, event["target"] as? String, (event["frame"] as? Number)?.toInt(), event["to_scene"] as? String)
        }
        return ScenarioV1(id = root["id"].jsonString("scenario.id"), designWidth = design["width"].jsonInt("design.width"),
            designHeight = design["height"].jsonInt("design.height"), scenes = scenes, events = events)
    }
}

package org.rigorcore.caserecomp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateContentUnitTest {
    private val h = "a".repeat(64)
    private val c = "b".repeat(64)

    private fun baseWithoutId(): String = """{"assets":[{"bytes":3,"id":"sha256-$h","media_type":"image/png","path":"assets/$h.png","sha256":"$h"}],"bindings":{"audio":{},"scene_backgrounds":{},"targets":{}},"format":"case-recomp-private-content","scenario":{"id":"s","path":"scenario.json","sha256":"$c"},"source":{"conversion_manifest_sha256":"$c"},"version":1}"""

    @Test fun json_parser_and_manifest_v1_are_strict() {
        val base = MiniJson.parse(baseWithoutId()).jsonObject("root")
        val id = sha256Hex(MiniJson.canonical(base) + "\n")
        val full = base.toMutableMap().also { it["package_id"] = id }
        val parsed = PrivateContentManifestParser.parseAndMigrate(MiniJson.canonical(full))
        assertEquals(id, parsed.packageId)
        assertEquals("image/png", parsed.assets.single().mediaType)
        assertTrue(runCatching { PrivateContentManifestParser.parseAndMigrate(MiniJson.canonical(full + ("package_id" to "0".repeat(64)))) }.isFailure)
    }

    @Test fun v0_migrates_to_v1() {
        val v0 = mapOf<String, Any?>(
            "format" to "case-recomp-private-content", "version" to 0,
            "scenario_file" to "scenario.json", "scenario_sha256" to c, "scenario_id" to "s",
            "source_manifest_sha256" to c,
            "assets" to listOf(mapOf("id" to "sha256-$h", "path" to "assets/$h.png", "sha256" to h,
                "size" to 3, "mime" to "image/png")),
            "bindings" to mapOf("scene_backgrounds" to emptyMap<String,String>(), "targets" to emptyMap<String,Any>(), "audio" to emptyMap<String,String>()),
        )
        val parsed = PrivateContentManifestParser.parseAndMigrate(MiniJson.canonical(v0))
        assertEquals(1, parsed.assets.size)
        assertEquals(64, parsed.packageId.length)
    }

    @Test fun scenario_json_matches_v1_contract() {
        val text = """{"format":"case-recomp-scenario","version":1,"id":"s","design":{"width":100,"height":50},"scenes":[{"id":"r","frame_start":1,"frame_end":2,"targets":[{"id":"t","rect":[1,2,4,6],"z":3}]}],"events":[]}"""
        val scenario = ScenarioJsonV1.parse(text)
        assertEquals(100, scenario.designWidth)
        assertEquals("t", scenario.scenes.single().targets.single().id)
    }

    @Test fun mini_json_rejects_duplicates_trailing_and_unsafe_manifest_path() {
        assertTrue(runCatching { MiniJson.parse("{\"a\":1,\"a\":2}") }.isFailure)
        assertTrue(runCatching { MiniJson.parse("{} x") }.isFailure)
        val bad = baseWithoutId().replace("assets/$h.png", "../escape.png")
        val base = MiniJson.parse(bad).jsonObject("root")
        val id = sha256Hex(MiniJson.canonical(base) + "\n")
        assertTrue(runCatching { PrivateContentManifestParser.parseAndMigrate(MiniJson.canonical(base + ("package_id" to id))) }.isFailure)
    }
}

package org.rigorcore.caserecomp

private const val PRIVATE_TRACE_FORMAT = "case-recomp-behavior-trace"
private const val PRIVATE_TRACE_VERSION = 1
private const val PRIVATE_TRACE_MAX_STEPS = 10_000
private val PRIVATE_TRACE_SHA256 = Regex("[0-9a-f]{64}")

data class PrivateTracePlanV1(
    val sourceSha256: String,
)

object PrivateTracePlanParser {
    private val rootKeys = setOf(
        "format", "version", "kind", "source_sha256", "steps", "observation_contract",
    )
    private val stepKeys = setOf(
        "frame", "sprite_count", "sprite_sha256", "behavior_count", "script_count", "handler_set_sha256",
    )
    private val contractKeys = setOf("required", "optional")
    private val requiredObservationFields = listOf(
        "frame", "sprite_count", "sprite_sha256", "handler_set_sha256", "observable_state_sha256",
    )
    private val optionalObservationFields = listOf("input_kind", "event_kind")

    private fun strictNonNegativeInt(value: Any?, name: String): Int {
        require(value is Long) { "$name must be an integer JSON number" }
        require(value in 0L..Int.MAX_VALUE.toLong()) { "$name integer range" }
        return value.toInt()
    }

    private fun stringList(value: Any?, name: String): List<String> =
        value.jsonList(name).map { it.jsonString(name) }

    fun parse(text: String): PrivateTracePlanV1 {
        val root = MiniJson.parse(text).jsonObject("private trace plan")
        require(root.keys == rootKeys) { "private trace plan fields" }
        require(root["format"] == PRIVATE_TRACE_FORMAT) { "private trace plan format" }
        require(root["version"] is Long && root["version"] == PRIVATE_TRACE_VERSION.toLong()) {
            "private trace plan version"
        }
        require(root["kind"] == "private-plan") { "private trace plan kind" }

        val sourceSha256 = root["source_sha256"].jsonString("source_sha256")
        require(PRIVATE_TRACE_SHA256.matches(sourceSha256)) { "private trace plan source hash" }

        val contract = root["observation_contract"].jsonObject("observation_contract")
        require(contract.keys == contractKeys) { "private trace observation contract fields" }
        require(stringList(contract["required"], "observation_contract.required") == requiredObservationFields) {
            "private trace required observation fields"
        }
        require(stringList(contract["optional"], "observation_contract.optional") == optionalObservationFields) {
            "private trace optional observation fields"
        }

        val steps = root["steps"].jsonList("steps")
        require(steps.size in 1..PRIVATE_TRACE_MAX_STEPS) { "private trace plan step count" }
        val seenFrames = mutableSetOf<Int>()
        steps.forEach { item ->
            val row = item.jsonObject("trace step")
            require(row.keys == stepKeys) { "private trace plan step fields" }
            val frame = strictNonNegativeInt(row["frame"], "frame")
            require(frame >= 1 && seenFrames.add(frame)) { "private trace plan frame" }
            for (name in listOf("sprite_count", "behavior_count", "script_count")) {
                strictNonNegativeInt(row[name], name)
            }
            for (name in listOf("sprite_sha256", "handler_set_sha256")) {
                require(PRIVATE_TRACE_SHA256.matches(row[name].jsonString(name))) { "private trace plan digest" }
            }
        }

        return PrivateTracePlanV1(sourceSha256)
    }
}

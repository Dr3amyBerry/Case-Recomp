package org.rigorcore.caserecomp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateTracePlanUnitTest {
    private fun plan(extra: Pair<String, Any?>? = null): String {
        val root = linkedMapOf<String, Any?>(
            "format" to "case-recomp-behavior-trace",
            "version" to 1L,
            "kind" to "private-plan",
            "source_sha256" to "a".repeat(64),
            "steps" to listOf(
                linkedMapOf<String, Any?>(
                    "frame" to 1L,
                    "sprite_count" to 2L,
                    "sprite_sha256" to "b".repeat(64),
                    "behavior_count" to 3L,
                    "script_count" to 4L,
                    "handler_set_sha256" to "c".repeat(64),
                ),
            ),
            "observation_contract" to linkedMapOf(
                "required" to listOf(
                    "frame", "sprite_count", "sprite_sha256", "handler_set_sha256", "observable_state_sha256",
                ),
                "optional" to listOf("input_kind", "event_kind"),
            ),
        )
        if (extra != null) root[extra.first] = extra.second
        return MiniJson.canonical(root)
    }

    @Test fun exact_private_trace_plan_exposes_source_digest() {
        assertEquals("a".repeat(64), PrivateTracePlanParser.parse(plan()).sourceSha256)
    }

    @Test fun malformed_contract_extra_fields_fractional_numbers_and_duplicate_frames_fail_closed() {
        assertTrue(runCatching { PrivateTracePlanParser.parse(plan("unexpected" to true)) }.isFailure)

        val fractional = MiniJson.parse(plan()).jsonObject("root").toMutableMap()
        fractional["version"] = 1.0
        assertTrue(runCatching { PrivateTracePlanParser.parse(MiniJson.canonical(fractional)) }.isFailure)

        val wrongContract = MiniJson.parse(plan()).jsonObject("root").toMutableMap()
        val contract = wrongContract["observation_contract"].jsonObject("contract").toMutableMap()
        contract["required"] = emptyList<String>()
        wrongContract["observation_contract"] = contract
        assertTrue(runCatching { PrivateTracePlanParser.parse(MiniJson.canonical(wrongContract)) }.isFailure)

        val duplicate = MiniJson.parse(plan()).jsonObject("root").toMutableMap()
        val steps = duplicate["steps"].jsonList("steps")
        duplicate["steps"] = steps + steps
        assertTrue(runCatching { PrivateTracePlanParser.parse(MiniJson.canonical(duplicate)) }.isFailure)
    }
}

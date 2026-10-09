package org.rigorcore.caserecomp.sda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.rigorcore.caserecomp.MiniJson
import java.io.File

/**
 * Differential parity test comparing Kotlin SDA runtime execution directly against
 * the canonical deterministic trace emitted by tools/sda-prototype.
 */
class SdaDifferentialUnitTest {

    @Suppress("UNCHECKED_CAST")
    private fun loadTrace(): Map<String, Any?> {
        val candidates = listOf(
            File("../../fixtures/sda_differential_trace.json"),
            File("../fixtures/sda_differential_trace.json"),
            File("fixtures/sda_differential_trace.json"),
            File("../../../fixtures/sda_differential_trace.json")
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("fixtures/sda_differential_trace.json not found in candidate paths")
        val text = file.readText(Charsets.UTF_8)
        return MiniJson.parse(text) as Map<String, Any?>
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun differential_rng_parity() {
        val trace = loadTrace()
        val rngData = trace["rng"] as Map<String, List<Any?>>
        for ((seedStr, expectedList) in rngData) {
            val seed = seedStr.toLong()
            val rng = SdaRng(seed)
            val expected = expectedList.map { (it as Number).toInt() }
            val actual = (1..expected.size).map { rng.next() }
            assertEquals("RNG parity mismatch for seed $seed", expected, actual)
        }
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun differential_shuffle_parity() {
        val trace = loadTrace()
        val shuffleList = trace["shuffle"] as List<Map<String, Any?>>
        for (entry in shuffleList) {
            val seed = (entry["seed"] as Number).toLong()
            val start = (entry["start"] as Number).toInt()
            val expected = (entry["result"] as List<Number>).map { it.toInt() }
            val items = (0 until 20).toList()
            val actual = sdaShuffle(items, seed, start = start)
            assertEquals("Shuffle parity mismatch for seed $seed, start $start", expected, actual)
        }
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun differential_target_deck_parity() {
        val trace = loadTrace()
        val deckData = trace["deck"] as Map<String, Any?>
        val deck = SdaTargetDeck((0 until 25).toList())
        val b1 = deck.nextBatch(42L)
        assertEquals((deckData["batch1"] as List<Number>).map { it.toInt() }, b1)
        assertEquals((deckData["cursor1"] as Number).toInt(), deck.cursor)

        val b2 = deck.nextBatch(99L)
        assertEquals((deckData["batch2"] as List<Number>).map { it.toInt() }, b2)
        assertEquals((deckData["cursor2"] as Number).toInt(), deck.cursor)
        assertEquals((deckData["order"] as List<Number>).map { it.toInt() }, deck.order)
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun differential_score_parity() {
        val trace = loadTrace()
        val scoreEvents = trace["score"] as List<Map<String, Any?>>
        val score = SdaScore(points = 10000)
        for (event in scoreEvents) {
            when (event["action"]) {
                "found" -> {
                    val fast = event["fast"] as Boolean
                    val gain = score.found(fast)
                    assertEquals((event["gain"] as Number).toInt(), gain)
                    assertEquals((event["points"] as Number).toInt(), score.points)
                }
                "miss" -> {
                    val ms = (event["ms"] as Number).toLong()
                    val penalized = score.miss(ms)
                    assertEquals(event["penalized"] as Boolean, penalized)
                    assertEquals((event["points"] as Number).toInt(), score.points)
                }
                "hint" -> {
                    score.hint()
                    assertEquals((event["points"] as Number).toInt(), score.points)
                }
            }
        }
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun differential_clock_parity() {
        val trace = loadTrace()
        val clockEvents = trace["clock"] as List<Map<String, Any?>>
        val clock = SdaClock(limit = 1320.0f, elapsed = 1130.0f)
        for (entry in clockEvents) {
            val step = (entry["step"] as Number).toFloat()
            val events = clock.advance(step)
            val expectedEventList = (entry["events"] as List<Any?>).map { item ->
                when (item) {
                    "timeout" -> SdaClockEvent.Timeout
                    is Number -> SdaClockEvent.Warning(item.toInt())
                    else -> error("unknown event $item")
                }
            }
            assertEquals("Clock event mismatch at step $step", expectedEventList, events)
            assertEquals((entry["displaySeconds"] as Number).toInt(), clock.displaySeconds())
            assertEquals(entry["text"] as String, clock.text())
        }
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun differential_motion_parity() {
        val trace = loadTrace()
        val samples = trace["motion"] as List<Map<String, Any?>>
        val motion = SdaFoundMotion(originalX = 20, originalY = 200, width = 8, height = 8)
        var sampleIndex = 0
        for (frame in 0 until 120) {
            motion.update(0.04f)
            if (sampleIndex < samples.size && (samples[sampleIndex]["frame"] as Number).toInt() == frame) {
                val s = samples[sampleIndex]
                assertEquals("Motion X mismatch at frame $frame", (s["x"] as Number).toInt(), motion.x)
                assertEquals("Motion Y mismatch at frame $frame", (s["y"] as Number).toInt(), motion.y)
                assertEquals("Motion drawWidth mismatch at frame $frame", (s["drawWidth"] as Number).toInt(), motion.drawWidth)
                assertEquals("Motion drawHeight mismatch at frame $frame", (s["drawHeight"] as Number).toInt(), motion.drawHeight)
                assertEquals("Motion scale mismatch at frame $frame", (s["scale"] as Number).toFloat(), motion.scale, 0.001f)
                assertEquals("Motion phase mismatch at frame $frame", (s["phase"] as Number).toInt(), motion.phase)
                assertEquals("Motion pulses mismatch at frame $frame", (s["pulses"] as Number).toInt(), motion.pulses)
                assertEquals("Motion removed mismatch at frame $frame", s["removed"] as Boolean, motion.removed)
                sampleIndex++
            }
        }
        assertEquals("Not all motion samples verified", samples.size, sampleIndex)
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun differential_scene_parity() {
        val trace = loadTrace()
        val sceneSteps = trace["scene"] as List<Map<String, Any?>>

        // Build mock scene matching FixtureResources from test_selection.py
        val alphasA = ByteArray(3 * 3) { 255.toByte() }
        val alphasB = ByteArray(3 * 3) { 255.toByte() }
        val spriteA = SdaSprite("a", 20, 20, SdaBufferPixelSource(3, 3, alphasA))
        val spriteB = SdaSprite("b", 30, 20, SdaBufferPixelSource(3, 3, alphasB))
        val objects = mapOf("a" to spriteA, "b" to spriteB)
        val captions = mapOf(listOf("a", "b") to listOf("Two samples", "One sample"))
        val scene = SdaScene(objects, listOf(listOf("a", "b")), captions)

        // Step 0: initial
        assertEquals(sceneSteps[0]["remaining"] as List<String>, scene.remainingCaptions())
        assertEquals(sceneSteps[0]["saved"] as List<String>, scene.savedCaptions())

        // Step 1: click a
        val resA = scene.click(20, 20)
        assertTrue(resA is SdaClickResult.Found)
        assertEquals(7500, (resA as SdaClickResult.Found).gain)
        assertEquals(sceneSteps[1]["remaining"] as List<String>, scene.remainingCaptions())
        assertEquals(sceneSteps[1]["saved"] as List<String>, scene.savedCaptions())

        // Step 2: advance 50 frames
        repeat(50) { scene.advance(0.04f) }
        assertEquals(sceneSteps[2]["remaining"] as List<String>, scene.remainingCaptions())
        assertEquals(sceneSteps[2]["saved"] as List<String>, scene.savedCaptions())

        // Step 3: click b
        val resB = scene.click(30, 20)
        assertTrue(resB is SdaClickResult.Found)
        assertEquals(8500, (resB as SdaClickResult.Found).gain)
        assertEquals(sceneSteps[3]["remaining"] as List<String>, scene.remainingCaptions())
        assertEquals(sceneSteps[3]["saved"] as List<String>, scene.savedCaptions())

        // Step 4: advance 50 frames
        repeat(50) { scene.advance(0.04f) }
        assertEquals(sceneSteps[4]["remaining"] as List<String>, scene.remainingCaptions())
        assertEquals(sceneSteps[4]["saved"] as List<String>, scene.savedCaptions())
        assertEquals((sceneSteps[4]["points"] as Number).toInt(), scene.score.points)
        assertEquals(sceneSteps[4]["batch_retired"] as Boolean, scene.batchRetired)
    }
}

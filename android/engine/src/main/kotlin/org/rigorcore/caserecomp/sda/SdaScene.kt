package org.rigorcore.caserecomp.sda

/**
 * Pixel source abstraction for SDA hit testing and alpha channel checks.
 */
interface SdaPixelSource {
    val width: Int
    val height: Int
    fun getAlpha(px: Int, py: Int): Int
}

/**
 * Memory buffer backed pixel source for unit testing and offline rendering.
 */
class SdaBufferPixelSource(
    override val width: Int,
    override val height: Int,
    private val alphas: ByteArray,
) : SdaPixelSource {
    override fun getAlpha(px: Int, py: Int): Int {
        if (px !in 0 until width || py !in 0 until height) return 0
        return alphas[py * width + px].toInt() and 0xFF
    }
}

/**
 * An eyespy object in an SDA scene.
 */
class SdaSprite(
    val identity: String,
    val x: Int,
    val y: Int,
    val image: SdaPixelSource,
    var found: Boolean = false,
    var motion: SdaFoundMotion? = null,
    var hidden: Boolean = false,
) {
    /**
     * 0x004251f0 / 0x00473e0e: half-open rectangle bounding box, then nonzero alpha pixel check.
     */
    fun hit(clickX: Int, clickY: Int): Boolean {
        val px = clickX - x
        val py = clickY - y
        return !found && !hidden &&
                px in 0 until image.width &&
                py in 0 until image.height &&
                image.getAlpha(px, py) != 0
    }
}

sealed class SdaClickResult {
    object Outside : SdaClickResult()
    data class Found(val id: String, val gain: Int) : SdaClickResult()
    data class Miss(val penalty: Boolean) : SdaClickResult()
}

/**
 * Base SDA Scene logic: manages active targets, click hit testing, scoring, and found motion updates.
 */
class SdaScene(
    val objects: Map<String, SdaSprite>,
    val targetSets: List<List<String>>,
    val captions: Map<List<String>, List<String>> = emptyMap(),
    seed: Long? = null,
) {
    val score = SdaScore()
    var elapsed: Float = 0f
    var sinceFound: Float = 0f
    val foundOrder = mutableListOf<String>()
    val deck: SdaTargetDeck<List<String>>? = if (seed != null) SdaTargetDeck(targetSets) else null
    var activeSets: List<List<String>> = if (deck != null && seed != null) deck.nextBatch(seed) else targetSets
    var targets: List<String> = activeSets.flatten()

    fun click(x: Int, y: Int): SdaClickResult {
        if (x !in 0 until 800 || y !in 0 until 600) {
            return SdaClickResult.Outside
        }
        for (identity in targets) {
            val sprite = objects[identity] ?: continue
            if (sprite.hit(x, y)) {
                sprite.found = true
                sprite.motion = SdaFoundMotion(sprite.x, sprite.y, sprite.image.width, sprite.image.height)
                foundOrder.add(identity)
                val isFast = sinceFound < 3.0f
                val gain = score.found(isFast)
                sinceFound = 0f
                return SdaClickResult.Found(identity, gain)
            }
        }
        val penalty = score.miss((elapsed * 1000f).toLong())
        return SdaClickResult.Miss(penalty)
    }

    fun advance(seconds: Float) {
        require(seconds.isFinite() && seconds >= 0f) { "invalid duration" }
        elapsed += seconds
        sinceFound += seconds
        for (identity in foundOrder) {
            objects[identity]?.motion?.update(seconds)
        }
    }

    fun remainingCaptions(): List<String> {
        val result = mutableListOf<String>()
        for (ids in activeSets) {
            val foundCount = ids.count { objects[it]?.found == true }
            if (foundCount < ids.size) {
                val captionList = captions[ids]
                if (captionList != null && foundCount < captionList.size) {
                    result.add(captionList[foundCount])
                }
            }
        }
        return result
    }

    fun savedCaptions(): List<String> {
        val result = mutableListOf<String>()
        for (ids in activeSets) {
            val removedCount = ids.count { objects[it]?.motion?.removed == true }
            if (removedCount < ids.size) {
                val captionList = captions[ids]
                if (captionList != null && removedCount < captionList.size) {
                    result.add(captionList[removedCount])
                }
            }
        }
        return result
    }
}

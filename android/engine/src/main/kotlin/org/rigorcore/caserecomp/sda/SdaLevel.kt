package org.rigorcore.caserecomp.sda

import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Generic representation of an SDA campaign level parsed from level XUI (e.g. LEVELS_1.XUI).
 * Unbiased and decoupled from any specific game title.
 */
data class SdaLevel(
    val clue: Int,
    val time: Float,
    val objects: Int,
    val scenes: List<String>,
    val title: String,
    val bonus: String,
    val bonusImage: String = "",
    /** UNLIMITED.XUI setsinscene: sets to find per scene, aligned with [scenes]. */
    val sceneQuotas: List<Int> = emptyList(),
)

object SdaLevels {
    /** No-limit clock for the unlimited search: 99:59:59 shown counting up. */
    const val UNLIMITED_TIME = 359999f

    /** UNLIMITED.XUI: one level listing every scene with its set count; it has no time or bonus. */
    fun parseUnlimited(raw: ByteArray): SdaLevel {
        val root = SdaXml.parse(raw).documentElement
        val nodes = root.getElementsByTagNameNS("*", "level").let { list -> (0 until list.length).map { list.item(it) as Element } }
            .ifEmpty { root.getElementsByTagName("mpi:level").let { list -> (0 until list.length).map { list.item(it) as Element } } }
        val level = nodes.singleOrNull() ?: throw IllegalArgumentException("unlimited definition needs one level")
        val scenes = level.getAttribute("scenes").split(',').map { it.trim().lowercase() }
        val quotas = level.getAttribute("setsinscene").split(',').map { it.trim().toIntOrNull() ?: throw IllegalArgumentException("invalid setsinscene") }
        require(scenes.isNotEmpty() && scenes.size == quotas.size && scenes.distinct().size == scenes.size &&
            scenes.all { it.matches(Regex("[a-z0-9_]+")) } && quotas.all { it > 0 }) { "invalid unlimited scene list" }
        return SdaLevel(1, UNLIMITED_TIME, quotas.sum(), scenes, "@ID_UNLIMITEDMODE", "", "", quotas)
    }

    fun parse(raw: ByteArray): List<SdaLevel> {
        val doc = SdaXml.parse(raw)
        val root = doc.documentElement

        val levels = mutableListOf<SdaLevel>()

        fun walk(node: Node) {
            if (node.nodeType == Node.ELEMENT_NODE) {
                val elem = node as Element
                val tag = elem.localName ?: elem.tagName.substringAfter(':')
                if (tag.equals("level", ignoreCase = true)) {
                    val clue = elem.getAttribute("clue").trim().toIntOrNull() ?: throw IllegalArgumentException("missing or invalid level clue")
                    val time = elem.getAttribute("time").trim().toFloatOrNull() ?: throw IllegalArgumentException("missing or invalid level time")
                    val objects = elem.getAttribute("objects").trim().toIntOrNull() ?: throw IllegalArgumentException("missing or invalid level objects")
                    val scenesRaw = elem.getAttribute("scenes").trim()
                    val scenes = scenesRaw.split(',').map { it.trim().lowercase() }
                    val title = elem.getAttribute("levelname").trim()
                    val bonus = elem.getAttribute("bonus").trim()
                    val bonusImage = elem.getAttribute("bonusimage").trim()

                    require(clue > 0 && objects > 0 && time.isFinite() && time > 0) { "invalid level parameters" }
                    require(scenes.isNotEmpty() && scenes.distinct().size == scenes.size &&
                        scenes.all { it.matches(Regex("[a-z0-9_]+")) }) { "invalid level scene list" }
                    levels.add(SdaLevel(clue, time, objects, scenes, title, bonus, bonusImage))
                }
            }
            val children = node.childNodes
            for (i in 0 until children.length) {
                walk(children.item(i))
            }
        }

        walk(root)
        require(levels.isNotEmpty()) { "no valid levels found in level definition" }
        return levels
    }
}

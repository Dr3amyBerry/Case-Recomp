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
)

object SdaLevels {
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

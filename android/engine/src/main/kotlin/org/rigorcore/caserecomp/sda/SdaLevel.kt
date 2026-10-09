package org.rigorcore.caserecomp.sda

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

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
        require(raw.isNotEmpty()) { "level definition cannot be empty" }
        val text = String(raw, Charsets.UTF_8).removePrefix("\uFEFF")

        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isExpandEntityReferences = false
        }
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)))
        val root = doc.documentElement

        val levels = mutableListOf<SdaLevel>()

        fun walk(node: Node) {
            if (node.nodeType == Node.ELEMENT_NODE) {
                val elem = node as Element
                val tag = elem.localName ?: elem.tagName.substringAfter(':')
                if (tag.equals("level", ignoreCase = true)) {
                    val clue = elem.getAttribute("clue").trim().toIntOrNull() ?: 1
                    val time = elem.getAttribute("time").trim().toFloatOrNull() ?: 1320f
                    val objects = elem.getAttribute("objects").trim().toIntOrNull() ?: 10
                    val scenesRaw = elem.getAttribute("scenes").trim()
                    val scenes = scenesRaw.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
                    val title = elem.getAttribute("levelname").trim()
                    val bonus = elem.getAttribute("bonus").trim()
                    val bonusImage = elem.getAttribute("bonusimage").trim()

                    if (scenes.isNotEmpty() && objects > 0 && time > 0) {
                        levels.add(SdaLevel(clue, time, objects, scenes, title, bonus, bonusImage))
                    }
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

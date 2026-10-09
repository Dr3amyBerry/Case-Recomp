package org.rigorcore.caserecomp.sda

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

data class SdaXuiTexture(val id: String, val uri: String)

data class SdaXuiImage(
    val id: String,
    val x: Int,
    val y: Int,
    val tex: String,
    val isEyeSpy: Boolean,
)

data class SdaXuiSet(
    val objects: List<String>,
    val itemNameList: String,
    val attributes: Map<String, String>,
)

data class SdaXuiDocument(
    val textures: Map<String, SdaXuiTexture>,
    val images: List<SdaXuiImage>,
    val eyeSpyImages: Map<String, SdaXuiImage>,
    val targetSets: List<SdaXuiSet>,
)

/**
 * Safe XUI parser: enforces size limit, rejects DTD/entities, handles namespaces and BOM.
 */
object SdaXui {
    private const val MAX_SIZE = 4_000_000

    fun parse(raw: ByteArray): SdaXuiDocument {
        if (raw.size > MAX_SIZE) throw IllegalArgumentException("unsupported XUI size")
        val rawLatin1 = String(raw, Charsets.ISO_8859_1).uppercase()
        if ("<!DOCTYPE" in rawLatin1 || "<!ENTITY" in rawLatin1) {
            throw IllegalArgumentException("unsupported XUI declaration")
        }

        var text = String(raw, Charsets.UTF_8).removePrefix("\uFEFF")
        if ("mpi:" in text && "xmlns:mpi" !in text) {
            text = text.replaceFirst("<xui>", "<xui xmlns:mpi=\"urn:spintop\">")
        }

        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)))
        val root = doc.documentElement

        val textures = mutableMapOf<String, SdaXuiTexture>()
        val images = mutableListOf<SdaXuiImage>()
        val eyeSpyImages = mutableMapOf<String, SdaXuiImage>()
        val targetSets = mutableListOf<SdaXuiSet>()

        fun walk(node: Node) {
            if (node.nodeType == Node.ELEMENT_NODE) {
                val element = node as Element
                val tag = element.localName ?: element.tagName.substringAfter(':')
                when (tag) {
                    "texture" -> {
                        val id = element.getAttribute("id")
                        val uri = element.getAttribute("uri")
                        if (id.isNotEmpty()) {
                            textures[id] = SdaXuiTexture(id, uri)
                        }
                    }
                    "image", "eyespyimage" -> {
                        val id = element.getAttribute("id")
                        val x = element.getAttribute("x").toIntOrNull() ?: 0
                        val y = element.getAttribute("y").toIntOrNull() ?: 0
                        val tex = element.getAttribute("tex")
                        val isEyeSpy = tag == "eyespyimage"
                        val img = SdaXuiImage(id, x, y, tex, isEyeSpy)
                        images.add(img)
                        if (isEyeSpy) {
                            if (id.isEmpty() || id in eyeSpyImages) {
                                throw IllegalArgumentException("missing or duplicate object id: $id")
                            }
                            eyeSpyImages[id] = img
                        }
                    }
                    "eyespyset" -> {
                        val objectsAttr = element.getAttribute("objects")
                        val itemList = element.getAttribute("itemnamelist")
                        val objectIds = objectsAttr.split(Regex("[\\s,]+")).filter { it.isNotEmpty() }
                        val attrs = mutableMapOf<String, String>()
                        for (i in 0 until element.attributes.length) {
                            val attr = element.attributes.item(i)
                            attrs[attr.nodeName] = attr.nodeValue
                        }
                        targetSets.add(SdaXuiSet(objectIds, itemList, attrs))
                    }
                }
            }
            val children = node.childNodes
            for (i in 0 until children.length) {
                walk(children.item(i))
            }
        }

        walk(root)
        return SdaXuiDocument(textures, images, eyeSpyImages, targetSets)
    }
}

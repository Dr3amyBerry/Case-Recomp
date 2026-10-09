package org.rigorcore.caserecomp.sda

import org.w3c.dom.Document
import org.w3c.dom.Node
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import javax.xml.parsers.DocumentBuilderFactory

/** Shared bounded XML reader for all SDA data, never resolving external resources. */
internal object SdaXml {
    fun parse(raw: ByteArray): Document {
        require(raw.isNotEmpty() && raw.size <= 4_000_000) { "unsupported SDA XML size" }
        try {
            val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw))
                .toString().removePrefix("\uFEFF")
            require(!text.contains("<!DOCTYPE", ignoreCase = true) &&
                !text.contains("<!ENTITY", ignoreCase = true)) { "unsupported SDA XML declaration" }
            // SDA resources also use undeclared literal mpi: prefixes. Names are split locally.
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = false
                isExpandEntityReferences = false
            }
            for ((name, enabled) in listOf(
                "http://apache.org/xml/features/disallow-doctype-decl" to true,
                "http://xml.org/sax/features/external-general-entities" to false,
                "http://xml.org/sax/features/external-parameter-entities" to false,
                "http://apache.org/xml/features/nonvalidating/load-external-dtd" to false,
                javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING to true,
            )) {
                // Android/JVM implementations expose different features; declaration rejection
                // and the resolver remain mandatory, independent defenses.
                runCatching { factory.setFeature(name, enabled) }
            }
            val builder = factory.newDocumentBuilder()
            builder.setEntityResolver { _, _ -> throw SAXException("external SDA XML resource") }
            builder.setErrorHandler(object : DefaultHandler() {
                override fun error(e: org.xml.sax.SAXParseException) { throw e }
                override fun fatalError(e: org.xml.sax.SAXParseException) { throw e }
            })
            val doc = builder.parse(InputSource(StringReader(text)))
            val pending = java.util.ArrayDeque<Pair<Node, Int>>()
            pending.add(doc.documentElement to 1)
            var count = 0
            while (pending.isNotEmpty()) {
                val (node, depth) = pending.removeLast()
                require(depth <= 128 && ++count <= 100_000) { "SDA XML structural budget exceeded" }
                for (index in 0 until node.childNodes.length) {
                    pending.add(node.childNodes.item(index) to depth + 1)
                }
            }
            return doc
        } catch (e: IllegalArgumentException) { throw e }
        catch (e: Exception) { throw IllegalArgumentException("invalid SDA XML", e) }
    }
}

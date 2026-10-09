package org.rigorcore.caserecomp.sda

import org.w3c.dom.Element

/** JSW definitions and ENVS bindings. This adapter describes the audited resource schema. */
object SdaJigsawResources {
    private fun elements(raw: ByteArray): List<Element> {
        val nodes = SdaXml.parse(raw).getElementsByTagName("*")
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }
    private fun type(e: Element) = e.localName ?: e.tagName.substringAfter(':')
    private fun number(e: Element, name: String): Int = e.getAttribute(name).toIntOrNull()
        ?: throw IllegalArgumentException("invalid jigsaw $name")
    fun load(content: SdaContent, resource: String, seed: Long, bonusImage: String,
             checkpoint: Map<String, Any?>?): SdaJigsawGame {
        val jsw = elements(content.read(resource) ?: throw IllegalArgumentException("missing JSW"))
        val env = elements(content.read("ENVS.MSE") ?: throw IllegalArgumentException("missing jigsaw environment"))
        val textures = (env + jsw).filter { type(it) == "texture" }.associate { it.getAttribute("id") to it.getAttribute("uri") }
        fun texture(id: String) = content.decodeImage(textures[id] ?: throw IllegalArgumentException("missing jigsaw texture: $id"))
        val backgroundNode = env.singleOrNull { it.getAttribute("id") == "jigsaw_$bonusImage" }
            ?: throw IllegalArgumentException("missing jigsaw background")
        val background = texture(backgroundNode.getAttribute("tex"))
        val ox = number(backgroundNode,"x"); val oy = number(backgroundNode,"y")
        val infos = jsw.filter { type(it) == "jswimageinfo" }.associateBy { it.getAttribute("id") }
        val pieces = jsw.filter { type(it) == "jswgamepiece" }
        require(pieces.size in 1..256 && pieces.map { it.getAttribute("id") }.distinct().size == pieces.size)
        val images = linkedMapOf<String, SdaArgbPixelSource>()
        val definitions = pieces.map { node ->
            val info = infos[node.getAttribute("imageinfo")] ?: throw IllegalArgumentException("missing jigsaw imageinfo")
            val mask = texture(info.getAttribute("alpha"))
            // Emboss and shadow are present and verified, but their native blend/offsets remain pending.
            texture(info.getAttribute("emboss")); texture(info.getAttribute("shadow"))
            val id = node.getAttribute("id")
            images[id] = SdaJigsawRaster.crop(background, mask, number(info,"x"), number(info,"y"))
            SdaJigsawPiece(id, Math.addExact(ox,number(node,"x")), Math.addExact(oy,number(node,"y")), mask.width,mask.height)
        }
        val tray = env.singleOrNull { type(it) == "jigsawpdainterface" } ?: throw IllegalArgumentException("missing jigsaw tray")
        val arrowImages = mutableMapOf<String, SdaPixelSource>()
        fun arrow(name: String): SdaJigsawTrayRect {
            val node = env.singleOrNull { it.getAttribute("id") == tray.getAttribute(name) }
                ?: throw IllegalArgumentException("missing jigsaw arrow")
            val image = texture(node.getAttribute("texnormal"))
            arrowImages[node.getAttribute("id")] = image
            arrowImages[node.getAttribute("id") + "_disabled"] = texture(node.getAttribute("texdisabled"))
            return SdaJigsawTrayRect(node.getAttribute("id"),number(node,"x"),number(node,"y"),image.width,image.height)
        }
        return SdaJigsawGame(resource, definitions, images,
            SdaJigsawTrayDefinition(number(tray,"x"),number(tray,"y"),number(tray,"w"),number(tray,"h"),number(tray,"scaleditemsno"),31),
            seed, bonusImage, checkpoint, ox, oy, background, arrow("arrowup"), arrow("arrowdown"), arrowImages)
    }
}

package org.rigorcore.caserecomp.sda

import org.w3c.dom.Element

data class SdaRiddleDestination(val x: Float,val y: Float,val scrollUp: Int,val fadeTime: Float,val finalAlpha: Float)
data class SdaRiddleDefinition(val pieces: List<SdaRiddlePiece>,val required: Int,val tray: SdaRiddleTrayDefinition,
    val imageUris: Map<String,String>,val captions: Map<String,String>,val destinations: Map<String,SdaRiddleDestination>,
    val backgroundUri: String,val backgroundX: Int,val backgroundY: Int,val timeLimit: Float)

/** Resource binding for the audited first-riddle schema. IDs/resource paths belong to the caller's profile. */
object SdaRiddleResources {
    private fun type(e: Element)=e.localName ?: e.tagName.substringAfter(':')
    private fun int(e: Element,name: String): Int = e.getAttribute(name).toIntOrNull()
        ?: throw IllegalArgumentException("invalid riddle $name")
    private fun float(e: Element,name: String): Float = e.getAttribute(name).toFloatOrNull()?.also {
        require(it.isFinite() && it in -32768f..32768f) { "invalid riddle $name" }
    } ?: throw IllegalArgumentException("invalid riddle $name")
    fun load(content: SdaContent,resource: String,controllerId: String,stringsResource: String="STRINGS.TXT"): SdaRiddleDefinition {
        val raw=content.read(resource) ?: throw IllegalArgumentException("missing riddle resource")
        val nodes=SdaXml.parse(raw).getElementsByTagName("*")
        val all=(0 until nodes.length).map { nodes.item(it) as Element }
        val controller=all.singleOrNull { it.getAttribute("id")==controllerId && type(it)=="riddle" }
            ?: throw IllegalArgumentException("missing supported riddle controller")
        val childNodes=controller.getElementsByTagName("*")
        val children=(0 until childNodes.length).map { childNodes.item(it) as Element }
        fun node(id: String): Element = children.singleOrNull { it.getAttribute("id")==id && id.isNotEmpty() }
            ?: throw IllegalArgumentException("missing or ambiguous riddle binding: $id")
        val textures=all.filter { type(it)=="texture" }.groupBy { it.getAttribute("id") }
        fun uri(image: Element): String {
            val candidates=textures[image.getAttribute("tex")] ?: throw IllegalArgumentException("missing riddle texture")
            val uris=candidates.map { it.getAttribute("uri") }.distinct()
            require(uris.size==1 && uris.single().isNotEmpty()) { "ambiguous riddle texture" }
            val result=uris.single()
            require(content.read(result)!=null) { "missing riddle image: $result" }
            return result
        }
        val strings=SdaStrings.parse(content.read(stringsResource) ?: throw IllegalArgumentException("missing riddle strings"))
        val bindings=children.filter { type(it)=="riddlepiece" }
        require(bindings.size in 1..256 && bindings.map { it.getAttribute("image") }.distinct().size==bindings.size)
        val images=linkedMapOf<String,String>();val captions=linkedMapOf<String,String>()
        val destinations=linkedMapOf<String,SdaRiddleDestination>()
        val pieces=bindings.map { binding ->
            val id=binding.getAttribute("image")
            images[id]=uri(node(id))
            val caption=binding.getAttribute("caption")
            if(caption.isNotEmpty()) {
                val resolved=SdaStrings.resolve(caption,strings)
                require(!resolved.startsWith("@")) { "unresolved riddle caption" };captions[id]=resolved
            }
            if(binding.getAttribute("target").isEmpty()) SdaRiddlePiece(id,-1,0,0,0,0,false) else {
                val target=node(binding.getAttribute("target"))
                require(type(target)=="ridledescription") { "unsupported riddle target" }
                val fade=float(target,"fadetime");val alpha=float(target,"finalalpha")
                require(fade >= 0f && alpha in 0f..1f)
                destinations[id]=SdaRiddleDestination(float(target,"destinationx"),float(target,"destinationy"),
                    int(target,"screenscrollup"),fade,alpha)
                SdaRiddlePiece(id,int(target,"placeorder"),int(target,"hotspotx"),int(target,"hotspoty"),
                    int(target,"hotspotwidth"),int(target,"hotspotheight"))
            }
        }
        val area=node(controller.getAttribute("itemlistarea"))
        val tray=SdaRiddleTrayDefinition(int(area,"x"),int(area,"y"),int(area,"w"),int(area,"h"),int(controller,"nitemsshown"))
        val required=int(controller,"itemstobeplaced")
        // Validate definitions without guessing the native RNG state or regenerating any saved board.
        SdaRiddleBoard(pieces,required)
        require(tray.shown==4 && tray.width in 1..4096 && tray.height in 4..4096 &&
            tray.x in -32768..32768 && tray.y in -32768..32768) { "unsupported riddle tray" }
        val background=node(controller.getAttribute("backgroundimage"))
        val time=if(controller.hasAttribute("timelimit")) float(controller,"timelimit") else 1500f // 00468000 default.
        require(time>0f)
        return SdaRiddleDefinition(pieces,required,tray,images,captions,destinations,uri(background),
            int(background,"x"),int(background,"y"),time)
    }
}

package org.rigorcore.caserecomp.sda

import org.w3c.dom.Element

data class SdaRiddleDestination(val x: Float,val y: Float,val scrollUp: Int,val fadeTime: Float,val finalAlpha: Float)
data class SdaRiddleCaptionLayout(val paperUri:String,val paperX:Int,val paperY:Int,val x:Int,val y:Int,val width:Int,val height:Int)
data class SdaRiddleDefinition(val pieces: List<SdaRiddlePiece>,val required: Int,val tray: SdaRiddleTrayDefinition,
    val imageUris: Map<String,String>,val captions: Map<String,String>,val destinations: Map<String,SdaRiddleDestination>,
    val backgroundUri: String,val backgroundX: Int,val backgroundY: Int,val timeLimit: Float?,
    val arrows: Map<String,SdaRiddleArrow> = emptyMap(),val captionLayout:SdaRiddleCaptionLayout?=null,
    val targetImageUris:Map<String,String> = emptyMap(),val trayImageUris:Map<String,String> = emptyMap())

/** Resource binding for the audited riddle schemas. IDs/resource paths belong to the caller's profile. */
object SdaRiddleResources {
    private fun type(e: Element)=e.localName ?: e.tagName.substringAfter(':')
    private fun int(e: Element,name: String): Int = e.getAttribute(name).toIntOrNull()
        ?: throw IllegalArgumentException("invalid riddle $name")
    private fun float(e: Element,name: String): Float = e.getAttribute(name).toFloatOrNull()?.also {
        require(it.isFinite() && it in -32768f..32768f) { "invalid riddle $name" }
    } ?: throw IllegalArgumentException("invalid riddle $name")
    fun load(content: SdaContent,resource: String,controllerId: String,stringsResource: String="STRINGS.TXT"): SdaRiddleDefinition {
        return loadDefinition(content,resource,controllerId,stringsResource,"riddle")
    }
    fun loadSecond(content:SdaContent,resource:String,controllerId:String,stringsResource:String="STRINGS.TXT"): SdaRiddleDefinition =
        loadDefinition(content,resource,controllerId,stringsResource,"riddlephase2")
    private fun loadDefinition(content:SdaContent,resource:String,controllerId:String,stringsResource:String,controllerType:String): SdaRiddleDefinition {
        val raw=content.read(resource) ?: throw IllegalArgumentException("missing riddle resource")
        val nodes=SdaXml.parse(raw).getElementsByTagName("*")
        val all=(0 until nodes.length).map { nodes.item(it) as Element }
        val controller=all.singleOrNull { it.getAttribute("id")==controllerId && type(it)==controllerType }
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
        fun textureUri(id:String):String {
            val image=controller.cloneNode(false) as Element
            image.setAttribute("tex",id);return uri(image)
        }
        val targetImages=linkedMapOf<String,String>();val trayImages=linkedMapOf<String,String>()
        val strings=SdaStrings.parse(content.read(stringsResource) ?: throw IllegalArgumentException("missing riddle strings"))
        val bindings=children.filter { type(it)=="riddlepiece" }
        require(bindings.size in 1..256 && bindings.map { it.getAttribute("image") }.distinct().size==bindings.size)
        val images=linkedMapOf<String,String>();val captions=linkedMapOf<String,String>()
        val destinations=linkedMapOf<String,SdaRiddleDestination>()
        val pieces=bindings.map { binding ->
            val id=binding.getAttribute("image")
            images[id]=uri(node(id))
            if(controllerType=="riddlephase2") {
                targetImages[id]=textureUri(binding.getAttribute("targettex"))
                trayImages[id]=textureUri(binding.getAttribute("textureshowninpda"))
            }
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
                if(controllerType=="riddlephase2") {
                    val image=content.decodeImage(images.getValue(id))
                    SdaRiddleDropZone.piece(id,int(target,"placeorder"),float(target,"destinationx"),float(target,"destinationy"),
                        int(target,"tolerance"),image.width,image.height)
                } else SdaRiddlePiece(id,int(target,"placeorder"),int(target,"hotspotx"),int(target,"hotspoty"),
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
        val arrows=mapOf("up" to "arrowup","down" to "arrowdown").mapValues { (_,attribute) ->
            val button=node(controller.getAttribute(attribute))
            require(type(button)=="allbutton") { "unsupported riddle arrow" }
            fun texture(attribute:String): String {
                val image=button.cloneNode(false) as Element
                image.setAttribute("tex",button.getAttribute(attribute));return uri(image)
            }
            SdaRiddleArrow(int(button,"x"),int(button,"y"),texture("texnormal"),texture("texdisabled"))
        }
        val paper=node(controller.getAttribute("paper"));val label=node(controller.getAttribute("riddlelabel"))
        require(type(paper)=="image" && type(label)=="label")
        val captionLayout=SdaRiddleCaptionLayout(uri(paper),int(paper,"x"),int(paper,"y"),int(label,"x"),
            int(label,"y"),int(label,"w"),int(label,"h"))
        require(captionLayout.width in 1..4096 && captionLayout.height in 1..4096)
        // 00467cc0 (phase two) does not read a timelimit; never inherit the first-phase default.
        val time=if(controllerType=="riddlephase2") null else
            if(controller.hasAttribute("timelimit")) float(controller,"timelimit") else 1500f // 00468000.
        require(time==null || time>0f)
        return SdaRiddleDefinition(pieces,required,tray,images,captions,destinations,uri(background),
            int(background,"x"),int(background,"y"),time,arrows,captionLayout,targetImages,trayImages)
    }
}

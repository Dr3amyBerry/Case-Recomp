package org.rigorcore.caserecomp.sda

import org.w3c.dom.Element

/** Resource-described interactive items; controller names and commercial callbacks stay with the profile. */
object SdaInteractiveResources {
 private fun type(node:Element)=node.localName ?: node.tagName.substringAfter(':')
 private fun children(node:Element):List<Element> = (0 until node.childNodes.length).mapNotNull { node.childNodes.item(it) as? Element }
 fun load(content:SdaContent,resource:String,containerId:String):List<SdaInteractiveItemDefinition> {
  val raw=content.read(resource) ?: throw IllegalArgumentException("missing interactive resource")
  val all=SdaXml.parse(raw).getElementsByTagName("*")
  val nodes=(0 until all.length).map { all.item(it) as Element }
  val container=nodes.singleOrNull { it.getAttribute("id")==containerId && type(it)=="container" }
   ?: throw IllegalArgumentException("missing interactive item container")
  val textures=nodes.filter { type(it)=="texture" }.groupBy { it.getAttribute("id") }
  val images=mutableMapOf<String,SdaPixelSource>()
  fun number(node:Element,key:String,fallback:Int):Int=if(node.hasAttribute(key)) node.getAttribute(key).toIntOrNull()
   ?: throw IllegalArgumentException("invalid interactive $key") else fallback
  return children(container).filter { type(it)=="interactiveitem" }.map { item ->
   val parts=children(item)
   val pictures=parts.filter { type(it)=="image" }.map { image ->
    val tex=image.getAttribute("tex")
    val uri=if(tex.isEmpty()) null else textures[tex]?.map { it.getAttribute("uri") }?.distinct()?.singleOrNull()?.takeIf { it.isNotEmpty() }
     ?: throw IllegalArgumentException("missing or ambiguous interactive texture")
    val pixels=uri?.let { images.getOrPut(it) { content.decodeImage(it) } }
    SdaInteractiveImage(number(image,"x",0),number(image,"y",0),number(image,"w",pixels?.width ?: 0),number(image,"h",pixels?.height ?: 0),pixels,uri)
   }
   val steps=parts.filter { type(it)=="interactivestep" }.map { step ->
    val time=if(step.hasAttribute("frametime")) step.getAttribute("frametime").toFloatOrNull()
     ?: throw IllegalArgumentException("invalid interactive frame time") else .125f // 00468c90.
    val condition=when(step.getAttribute("condition")) {
     "" -> SdaStepCondition.ALWAYS
     "completed" -> SdaStepCondition.COMPLETED
     "notcompleted" -> SdaStepCondition.NOT_COMPLETED
     "never" -> SdaStepCondition.NEVER
     else -> throw IllegalArgumentException("unsupported interactive condition")
    }
    val interactive=step.getAttribute("interactive");require(interactive in setOf("","true","false"))
    SdaInteractiveStep(number(step,"imageindex",0),number(step,"frameindex",0),number(step,"framewidth",-1),
     interactive=="true",time,condition,step.getAttribute("item"),step.getAttribute("step"),step.getAttribute("name"))
   }
   SdaInteractiveItemDefinition(item.getAttribute("name"),pictures,steps,number(item,"firststep",0))
  }.also { SdaInteractiveItems(it) } // All source rectangles and initial states are validated before use.
 }
}

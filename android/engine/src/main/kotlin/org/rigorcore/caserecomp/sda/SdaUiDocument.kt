package org.rigorcore.caserecomp.sda
import org.w3c.dom.Element

data class SdaUiNode(val type:String,val attributes:Map<String,String>,val children:List<SdaUiNode>) {
 fun number(key:String,default:Int=0):Int=attributes[key]?.let { value ->
  val n=value.toIntOrNull() ?: throw IllegalArgumentException("invalid UI $key")
  require(n in -32768..32768);n
 } ?: default
}
/** Resource-only visual bindings. Component IDs, layout tables and activation policy belong to profiles. */
class SdaUiDocument(raw:ByteArray,private val strings:Map<String,String>) {
 private val all:List<SdaUiNode>
 init {
  fun node(e:Element):SdaUiNode {
   val attrs=(0 until e.attributes.length).associate { val a=e.attributes.item(it);a.nodeName to a.nodeValue }
   val children=(0 until e.childNodes.length).mapNotNull { e.childNodes.item(it) as? Element }.map(::node)
   return SdaUiNode(e.localName?:e.tagName.substringAfter(':'),attrs,children)
  }
  fun flatten(n:SdaUiNode):List<SdaUiNode> = listOf(n)+n.children.flatMap(::flatten)
  all=flatten(node(SdaXml.parse(raw).documentElement))
 }
 fun nodes(type:String):List<SdaUiNode> = all.filter { it.type==type }
 fun component(id:String):SdaUiNode=all.singleOrNull { it.attributes["id"]==id && it.type!="texture" }
  ?: throw IllegalArgumentException("missing or ambiguous UI component: $id")
 fun texture(id:String):String {
  val uris=all.filter { it.type=="texture" && it.attributes["id"]==id }.map { it.attributes["uri"] }.distinct()
  require(uris.size==1 && !uris.single().isNullOrEmpty()) { "missing or ambiguous UI texture: $id" }
  return uris.single()!!
 }
 fun caption(node:SdaUiNode):String=SdaStrings.resolve(node.attributes["caption"].orEmpty(),strings)
 fun resolve(text:String):String=SdaStrings.resolve(text,strings)
}

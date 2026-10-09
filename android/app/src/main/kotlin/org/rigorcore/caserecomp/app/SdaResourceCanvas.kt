package org.rigorcore.caserecomp.app
import android.graphics.*
import org.rigorcore.caserecomp.sda.*

/** Android drawing services over generic XUI bindings; no title IDs or campaign rules. */
class SdaResourceCanvas(val document:SdaUiDocument,private val content:SdaContent) {
 private val cache=mutableMapOf<String,Bitmap>()
 private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.WHITE;textSize=12f }
 fun bitmap(texture:String):Bitmap=cache.getOrPut(texture) {
  val source=content.decodeImage(document.texture(texture))
  source.nativeImage as? Bitmap ?: Bitmap.createBitmap(IntArray(source.width*source.height) { i -> source.getArgb(i%source.width,i/source.width) },source.width,source.height,Bitmap.Config.ARGB_8888)
 }
 fun image(canvas:Canvas,node:SdaUiNode,x:Int=node.number("x"),y:Int=node.number("y")) {
  val texture=node.attributes["tex"] ?: return
  require(texture.isNotEmpty()) { "empty UI texture" }
  val bitmap=bitmap(texture)
  val w=node.number("w",bitmap.width);val h=node.number("h",bitmap.height)
  if(w<=0 || h<=0) return
  val source=Rect(0,0,minOf(w,bitmap.width),minOf(h,bitmap.height))
  canvas.drawBitmap(bitmap,source,Rect(x,y,x+source.width(),y+source.height()),null)
 }
 fun label(canvas:Canvas,node:SdaUiNode,text:String=document.caption(node),x:Int=node.number("x"),y:Int=node.number("y"),width:Int=node.number("w"),height:Int=node.number("h")) {
  if(width<=0 || height<=0) return
  canvas.save();canvas.clipRect(x,y,x+width,y+height)
  val runs=SdaCaptionRuns.parse(text,maxOf(1,(paint.descent()-paint.ascent()).toInt()))
  val total=runs.lastOrNull()?.verticalAdvance?.toFloat() ?: 0f
  val baseline=when(node.attributes["valign"]) { "top" -> y-paint.ascent();"bottom" -> y+height-total-paint.descent();else -> y+height/2f-(paint.ascent()+paint.descent())/2f-total/2f }
  for(run in runs) {
   val xx=when(node.attributes["halign"]) { "center" -> x+(width-paint.measureText(run.text))/2;"right" -> x+width-paint.measureText(run.text);else -> x.toFloat() }
   canvas.drawText(run.text,xx,baseline+run.verticalAdvance,paint)
  }
  canvas.restore()
 }
 fun button(canvas:Canvas,node:SdaUiNode,enabled:Boolean=true) {
  val texture=node.attributes[if(enabled) "texnormal" else "texdisabled"] ?: node.attributes.getValue("texnormal")
  val bitmap=bitmap(texture);val x=node.number("x");val y=node.number("y")
  canvas.drawBitmap(bitmap,x.toFloat(),y.toFloat(),null)
  label(canvas,node.copy(attributes=node.attributes+mapOf("halign" to "center","valign" to "middle")),x=x+node.number("globalcaptionoffsetx"),y=y+node.number("globalcaptionoffsety"),width=bitmap.width,height=bitmap.height)
 }
 fun rect(node:SdaUiNode):Rect {
  val image=bitmap(node.attributes.getValue("texnormal"));val x=node.number("x");val y=node.number("y")
  return Rect(x,y,x+image.width,y+image.height)
 }
}

interface SdaVisualProfile {
 fun drawMap(canvas:Canvas,campaign:SdaCampaign)
 fun drawHud(canvas:Canvas,campaign:SdaCampaign?,scene:SdaScene,clock:SdaClock?)
 fun drawBonusBase(canvas:Canvas,campaign:SdaCampaign)
 fun drawTileBonus(canvas:Canvas,campaign:SdaCampaign):Boolean
 fun sceneAt(campaign:SdaCampaign,x:Int,y:Int):String?
 val returnMapRect:Rect
 val solveRect:Rect
}

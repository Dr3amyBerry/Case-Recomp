package org.rigorcore.caserecomp.app
import android.graphics.*
import org.rigorcore.caserecomp.sda.*

/** Android drawing services over generic XUI bindings; no title IDs or campaign rules. */
class SdaResourceCanvas(val document:SdaUiDocument,private val content:SdaContent) {
 private val cache=mutableMapOf<String,Bitmap>()
 private val photographs=Paint(Paint.FILTER_BITMAP_FLAG)
 private val glyphPaint=Paint().apply { isFilterBitmap=false }
 private val fonts=mutableMapOf<String,Pair<SdaAtlasFont,Bitmap>>()
 private fun font(name:String):Pair<SdaAtlasFont,Bitmap> = fonts.getOrPut(name) {
  val node=document.nodes("font").single { it.attributes["id"]==name };val a=node.attributes
  val atlas=bitmap(a.getValue("tex"))
  val source=object:SdaPixelSource {
   override val width=atlas.width;override val height=atlas.height
   override fun getArgb(px:Int,py:Int)=atlas.getPixel(px,py)
   override fun getAlpha(px:Int,py:Int)=getArgb(px,py) ushr 24
  }
  val kerns=document.nodes("kern").filter { it.attributes["font"]==name }.map {
   SdaGlyphKern(it.attributes.getValue("char1").toByteArray(Charsets.UTF_8).first().toInt() and 255,it.attributes.getValue("char2").toByteArray(Charsets.UTF_8).first().toInt() and 255,it.attributes.getValue("spacing").toFloat())
  }
  SdaAtlasFont(source,a["characterset"].orEmpty(),a["spacing"]?.toFloat() ?: 1f,a["spacewidth"]?.toInt() ?: source.height/2,a["baseline"]?.toInt() ?: 0,kerns) to atlas
 }
 val bitmapBytes:Long get()=cache.values.sumOf { it.allocationByteCount.toLong() }
 private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.WHITE;textSize=12f }
 fun bitmap(texture:String):Bitmap=cache.getOrPut(texture) {
  val source=content.decodeImage(document.texture(texture))
  source.nativeImage as? Bitmap ?: Bitmap.createBitmap(IntArray(source.width*source.height) { i -> source.getArgb(i%source.width,i/source.width) },source.width,source.height,Bitmap.Config.ARGB_8888)
 }
 fun image(canvas:Canvas,node:SdaUiNode,x:Int=node.number("x"),y:Int=node.number("y"),photographic:Boolean=false) {
  val texture=node.attributes["tex"] ?: return
  require(texture.isNotEmpty()) { "empty UI texture" }
  val bitmap=bitmap(texture)
  val w=node.number("w",bitmap.width);val h=node.number("h",bitmap.height)
  if(w<=0 || h<=0) return
  val source=Rect(0,0,minOf(w,bitmap.width),minOf(h,bitmap.height))
  canvas.drawBitmap(bitmap,source,Rect(x,y,x+source.width(),y+source.height()),if(photographic) photographs else null)
 }
 /** Native atlas frame, drawn directly from the original bitmap without making enlarged/cropped copies. */
 fun spriteFrame(canvas:Canvas,bitmap:Bitmap,frameWidth:Int,frameHeight:Int,index:Int,x:Int,y:Int,opacity:Float=1f) {
  require(frameWidth>0 && frameHeight>0 && frameWidth<=bitmap.width && frameHeight<=bitmap.height)
  val columns=bitmap.width/frameWidth;val rows=bitmap.height/frameHeight
  require(index in 0 until columns*rows && opacity.isFinite() && opacity in 0f..1f)
  val left=index%columns*frameWidth;val top=index/columns*frameHeight
  canvas.drawBitmap(bitmap,Rect(left,top,left+frameWidth,top+frameHeight),Rect(x,y,x+frameWidth,y+frameHeight),Paint().apply { alpha=(opacity*255).toInt() })
 }
 /** Native-size nine-part panel: repeat edges/center and crop the last tile, never stretch. */
 fun tiledPanel(canvas:Canvas,textures:List<String>,bounds:Rect) {
  require(textures.size==9)
  val parts=textures.map(::bitmap)
  val left=parts[0].width;val right=parts[2].width
  val top=parts[0].height;val bottom=parts[6].height
  require(bounds.width()>=left+right && bounds.height()>=top+bottom)
  val xs=listOf(bounds.left,bounds.left+left,bounds.right-right,bounds.right)
  val ys=listOf(bounds.top,bounds.top+top,bounds.bottom-bottom,bounds.bottom)
  for(row in 0..2) for(col in 0..2) {
   val image=parts[row*3+col]
   val region=Rect(xs[col],ys[row],xs[col+1],ys[row+1])
   var y=region.top
   while(y<region.bottom) {
    var x=region.left
    while(x<region.right) {
     val w=minOf(image.width,region.right-x);val h=minOf(image.height,region.bottom-y)
     canvas.drawBitmap(image,Rect(0,0,w,h),Rect(x,y,x+w,y+h),null)
     x+=w
    }
    y+=minOf(image.height,region.bottom-y)
   }
  }
 }
 /** Draw atlas text at an explicit anchor; clipping and title-specific offsets belong to callers. */
 fun atlasText(canvas:Canvas,name:String,text:String,x:Int,y:Int,halign:Int=0,valign:Int=0,opacity:Float=1f) {
  require(opacity.isFinite() && opacity in 0f..1f)
  if(opacity==0f) return
  glyphPaint.alpha=(255*opacity).toInt()
  val (metrics,atlas)=font(name)
  for(g in metrics.layout(text,x,y,halign,valign)) canvas.drawBitmap(atlas,
   Rect(g.run.start,0,g.run.start+g.run.width,atlas.height),Rect(g.x,g.y,g.x+g.run.width,g.y+atlas.height),glyphPaint)
 }
 fun label(canvas:Canvas,node:SdaUiNode,text:String=document.caption(node),x:Int=node.number("x"),y:Int=node.number("y"),width:Int=node.number("w"),height:Int=node.number("h"),opacity:Float=1f,clipToBounds:Boolean=true) {
  require(opacity.isFinite() && opacity in 0f..1f)
  if(width<=0 || height<=0 || opacity==0f) return
  glyphPaint.alpha=(255*opacity).toInt()
  canvas.save();if(clipToBounds) canvas.clipRect(x,y,x+width,y+height)
  val name=node.attributes["font"] ?: node.attributes["fontidle"]
  if(name!=null) {
   val ha=when(node.attributes["halign"]) { "center" -> 1;"right" -> 2;else -> 0 }
   val va=when(node.attributes["valign"]) { "top" -> 3;"bottom" -> 0;"baseline" -> 1;else -> 2 }
   val xx=x+when(ha) { 1 -> width/2-1;2 -> width-1;else -> 0 }
   val yy=y+when(va) { 2 -> height/2-1;0 -> height-1;else -> 0 }
   atlasText(canvas,name,text,xx,yy,ha,va,opacity)
  } else {
   // Generic diagnostic labels without a declared atlas only.
   paint.alpha=(255*opacity).toInt()
   canvas.drawText(text,x.toFloat(),y+height/2f-(paint.ascent()+paint.descent())/2f,paint)
  }
  canvas.restore()
 }
 /** Solid XUI frame; coordinates and RGBA are resource data. */
 fun frame(canvas:Canvas,node:SdaUiNode) {
  val rgba=Regex("rgba\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\)").matchEntire(node.attributes.getValue("color"))
   ?: error("unsupported frame color")
  val c=rgba.groupValues.drop(1).map { it.toInt().also { n -> require(n in 0..255) } }
  val paint=Paint().apply { color=Color.argb(c[3],c[0],c[1],c[2]) }
  val x=node.number("x");val y=node.number("y")
  canvas.drawRect(x.toFloat(),y.toFloat(),(x+node.number("w")).toFloat(),(y+node.number("h")).toFloat(),paint)
 }
 fun button(canvas:Canvas,node:SdaUiNode,enabled:Boolean=true,state:SdaButtonState=SdaButtonState.NORMAL) {
  val binding=node.buttonPresentation(if(enabled) state else SdaButtonState.DISABLED)
  val bounds=rect(node)
  binding.texture?.let { canvas.drawBitmap(bitmap(it),bounds.left.toFloat(),bounds.top.toFloat(),null) }
  val attributes=node.attributes+mapOf("halign" to "center","valign" to "middle")
  val font=binding.font
  val labelNode=node.copy(attributes=if(font==null) attributes-"font" else attributes+("font" to font))
  label(canvas,labelNode,x=bounds.left+binding.captionX,y=bounds.top+binding.captionY,width=bounds.width(),height=bounds.height())
 }
 fun rect(node:SdaUiNode):Rect {
  val images=SdaButtonState.entries.mapNotNull { node.attributes[it.textureKey] }.distinct().map(::bitmap)
  val x=node.number("x");val y=node.number("y")
  val w=node.number("w",images.maxOfOrNull { it.width } ?: 0);val h=node.number("h",images.maxOfOrNull { it.height } ?: 0)
  return Rect(x,y,x+w,y+h)
 }

}

enum class SdaMenuAction { RESUME, RETURN_TO_CATALOGUE, OPTIONS, INSTRUCTIONS, UNAVAILABLE }
enum class SdaMenuEntry { PAUSE, MAIN }

data class SdaMusicRequest(val stream:String,val loop:Boolean)

interface SdaVisualProfile {
 fun campaignMusic(phase:SdaCampaignPhase):SdaMusicRequest? = null
 fun audioSession(context:android.content.Context):SdaAudioSession? = null
 fun menuView(context:android.content.Context,campaign:SdaCampaign?,entry:SdaMenuEntry,audio:SdaAudioSession?=null,onAction:(SdaMenuAction)->Unit):android.view.View? = null
 fun sceneFeedback(found:Boolean):String? = null
 fun hintPolicy():SdaHintPolicy? = null
 fun immediateHintRecharge(context:android.content.Context):Boolean = false
 fun hintRect(campaign:SdaCampaign):Rect? = null
 fun hintSound():String? = null
 fun pointer(x:Int?,y:Int?,pressed:Boolean) {}
 fun menuRect(campaign:SdaCampaign):Rect? = null
 fun pauseRect(campaign:SdaCampaign):Rect? = null
 fun drawPause(canvas:Canvas) {}
 fun drawMap(canvas:Canvas,campaign:SdaCampaign)
 fun drawHud(canvas:Canvas,campaign:SdaCampaign?,scene:SdaScene,clock:SdaClock?,paused:Boolean=false)
 fun interactiveRiddle(content:SdaContent,seed:Long,checkpoint:Map<String,Any?>?):SdaInteractiveRiddleGame? = null
 fun drawCampaignComplete(canvas:Canvas,campaign:SdaCampaign):Boolean = false
 fun interactiveRiddleOpacity(game:SdaInteractiveRiddleGame):Float = 1f
 fun interactiveRiddleItemOffset(game:SdaInteractiveRiddleGame,index:Int):Pair<Int,Int> = 0 to 0
 fun drawInteractiveRiddleBase(canvas:Canvas,campaign:SdaCampaign,game:SdaInteractiveRiddleGame) {}
 fun drawInteractiveRiddleOverlay(canvas:Canvas,game:SdaInteractiveRiddleGame) {}
 fun interactiveRiddleSound(game:SdaInteractiveRiddleGame,key:String):String? = null
 fun riddleDialog(context:android.content.Context,campaign:SdaCampaign,onConfirm:()->Unit):android.view.View? = null
 fun drawRiddleDecorations(canvas:Canvas,game:SdaPlacementRiddleGame) {}
 fun drawRiddlePlaced(canvas:Canvas,game:SdaPlacementRiddleGame,id:String):Boolean = false
 fun drawRiddleBase(canvas:Canvas,campaign:SdaCampaign,game:SdaPlacementRiddleGame) {}
 fun drawRiddleCaption(canvas:Canvas,game:SdaPlacementRiddleGame):Boolean = false
 fun drawLevelComplete(canvas:Canvas,campaign:SdaCampaign):Boolean = false
 fun levelCompleteRect(campaign:SdaCampaign):Rect? = null
 fun bonusSolveRect(campaign:SdaCampaign):Rect? = solveRect
 fun drawBonusBase(canvas:Canvas,campaign:SdaCampaign)
 fun drawWordSearch(canvas:Canvas,campaign:SdaCampaign):Boolean = false
 fun drawTileBonus(canvas:Canvas,campaign:SdaCampaign):Boolean
 fun sceneAt(campaign:SdaCampaign,x:Int,y:Int):String?
 val returnMapRect:Rect
 val solveRect:Rect
}

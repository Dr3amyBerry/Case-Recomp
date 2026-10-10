package org.rigorcore.caserecomp.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import org.rigorcore.caserecomp.sda.*

/** XUI dialog renderer/input. Resource IDs, dimensions and action semantics come from the profile. */
class SdaResourceMenuView(context:Context,private val ui:SdaResourceCanvas,
 private var container:SdaUiNode,private val panelTextures:List<String>,
 private val logicalWidth:Int,private val logicalHeight:Int,private val onAction:(Int)->Unit):View(context) {
 var onSoundEffect:((String)->Unit)?=null
 var onSliderValue:((SdaUiNode,Int)->Unit)?=null
 var onClose:(()->Unit)?=null
 private val sliderValues=mutableMapOf<SdaUiNode,Int>()
 private var sliderCaptured:SdaUiNode?=null
 private var hovered:SdaUiNode?=null
 private var sliderGrab=0
 private var sliderInitial=0
 val sliders get()=container.children.filter { it.type=="slider" }
 fun sliderValue(node:SdaUiNode)=sliderValues[node] ?: node.number("value").coerceIn(node.number("min"),node.number("max"))
 fun setSliderValue(node:SdaUiNode,value:Int) { sliderValues[node]=value.coerceIn(node.number("min"),node.number("max"));invalidate() }
 private fun travel(node:SdaUiNode)=maxOf(0,ui.bitmap(node.attributes.getValue("texback")).width-ui.bitmap(node.attributes.getValue("texnob")).width)
 private fun knobBounds(node:SdaUiNode):Rect {
  val knob=ui.bitmap(node.attributes.getValue("texnob"))
  val range=node.number("max")-node.number("min")
  val offset=if(range>0) kotlin.math.round((sliderValue(node)-node.number("min"))*travel(node).toFloat()/range).toInt() else 0
  val x=node.number("x")+node.number("noboffsetx")+offset
  val y=node.number("y")+node.number("noboffsety")
  return Rect(x,y,x+knob.width,y+knob.height)
 }
 fun sliderKnobBounds(node:SdaUiNode)=knobBounds(node).apply { offset(container.number("x"),container.number("y")) }
 private fun updateSlider(node:SdaUiNode,x:Int) {
  val width=travel(node);if(width<=0) return
  val offset=(x-container.number("x")-node.number("x")-node.number("noboffsetx")-sliderGrab).coerceIn(0,width)
  val value=node.number("min")+kotlin.math.round(offset*(node.number("max")-node.number("min")).toFloat()/width).toInt()
  if(value!=sliderValue(node)) { setSliderValue(node,value);onSliderValue?.invoke(node,value) }
 }
 override fun onDetachedFromWindow() { onClose?.invoke();super.onDetachedFromWindow() }
 val screenId get()=container.attributes["id"].orEmpty()
 val buttons get()=container.children.filter { (it.type=="allbutton" && it.attributes["value"]!=null) || it.type=="quitbutton" }
 private var background:List<SdaUiNode> = emptyList()
 fun show(node:SdaUiNode,background:List<SdaUiNode> = emptyList()) {
  container=node;this.background=background;captured=null;sliderCaptured=null;hovered=null;pressed=false;invalidate()
 }
 private var captured:SdaUiNode?=null
 private var pressed=false
 init { tag="sda-resource-menu";isFocusable=true }
 fun buttonBounds(node:SdaUiNode)=ui.rect(node).apply { offset(container.number("x"),container.number("y")) }
 override fun onDraw(canvas:Canvas) {
  super.onDraw(canvas)
  val scale=minOf(width/logicalWidth.toFloat(),height/logicalHeight.toFloat())
  canvas.save();canvas.translate((width-logicalWidth*scale)/2,(height-logicalHeight*scale)/2);canvas.scale(scale,scale)
  for(node in background) ui.image(canvas,node,photographic=true)
  canvas.translate(container.number("x").toFloat(),container.number("y").toFloat())
  val panel=container.children.singleOrNull { it.type=="dialogimg" }
  if(panel!=null) ui.tiledPanel(canvas,panelTextures,Rect(0,0,panel.number("w"),panel.number("h")))
  for(node in container.children) when(node.type) {
   "image" -> ui.image(canvas,node,photographic=true)
   "label" -> ui.label(canvas,node,opacity=if(node.attributes["disabled"]=="true") .45f else 1f)
   "checkbox" -> {
    val icon=ui.bitmap(node.attributes.getValue("texoff"))
    val paint=android.graphics.Paint().apply { alpha=if(node.attributes["disabled"]=="true") 115 else 255 }
    canvas.drawBitmap(icon,node.number("x").toFloat(),node.number("y").toFloat(),paint)
   }
   "slider" -> {
    canvas.drawBitmap(ui.bitmap(node.attributes.getValue("texback")),(node.number("x")+node.number("backoffsetx")).toFloat(),(node.number("y")+node.number("backoffsety")).toFloat(),null)
    val knob=knobBounds(node)
    val texture=when(node) {
     sliderCaptured -> node.attributes["texnobtrack"] ?: node.attributes.getValue("texnob")
     hovered -> node.attributes["texnobover"] ?: node.attributes.getValue("texnob")
     else -> node.attributes.getValue("texnob")
    }
    canvas.drawBitmap(ui.bitmap(texture),knob.left.toFloat(),knob.top.toFloat(),null)
   }
   "allbutton","quitbutton" -> ui.button(canvas,node,state=if(node==captured && pressed) SdaButtonState.PRESSED else if(node==hovered) SdaButtonState.HOVER else SdaButtonState.NORMAL)
  }
  canvas.restore()
 }
 override fun onHoverEvent(event:MotionEvent):Boolean {
  val scale=minOf(width/logicalWidth.toFloat(),height/logicalHeight.toFloat());if(scale<=0f) return false
  val x=((event.x-(width-logicalWidth*scale)/2)/scale).toInt()
  val y=((event.y-(height-logicalHeight*scale)/2)/scale).toInt()
  val next=if(event.actionMasked==MotionEvent.ACTION_HOVER_EXIT) null else
   buttons.firstOrNull { buttonBounds(it).contains(x,y) } ?: sliders.firstOrNull { sliderKnobBounds(it).contains(x,y) }
  if(next!=hovered) { hovered=next;next?.attributes?.get("sfxrollover")?.let { onSoundEffect?.invoke(it) };invalidate() }
  return true
 }
 override fun onTouchEvent(event:MotionEvent):Boolean {
  val scale=minOf(width/logicalWidth.toFloat(),height/logicalHeight.toFloat())
  if(scale<=0f) return false
  val x=((event.x-(width-logicalWidth*scale)/2)/scale).toInt()
  val y=((event.y-(height-logicalHeight*scale)/2)/scale).toInt()
  when(event.actionMasked) {
   MotionEvent.ACTION_DOWN -> {
    sliderCaptured=sliders.firstOrNull { sliderKnobBounds(it).contains(x,y) }
    sliderCaptured?.let { sliderGrab=x-sliderKnobBounds(it).left;sliderInitial=sliderValue(it);it.attributes["clicknobsfx"]?.let { sound -> onSoundEffect?.invoke(sound) } }
    captured=if(sliderCaptured==null) buttons.firstOrNull { buttonBounds(it).contains(x,y) } else null;pressed=captured!=null
   }
   MotionEvent.ACTION_MOVE -> { sliderCaptured?.let { updateSlider(it,x) };pressed=captured?.let { buttonBounds(it).contains(x,y) } ?: false }
   MotionEvent.ACTION_CANCEL -> { sliderCaptured?.let { setSliderValue(it,sliderInitial);onSliderValue?.invoke(it,sliderInitial) };sliderCaptured=null;captured=null;pressed=false }
   MotionEvent.ACTION_UP -> {
    sliderCaptured?.let { updateSlider(it,x) };sliderCaptured=null
    val selected=captured?.takeIf { buttonBounds(it).contains(x,y) }
    captured=null;pressed=false;invalidate()
    if(selected!=null) { performClick();selected.attributes["sfx"]?.let { onSoundEffect?.invoke(it) };onAction(selected.number("value",-1)) }
   }
  }
  invalidate();return true
 }
 override fun performClick():Boolean { super.performClick();return true }
}

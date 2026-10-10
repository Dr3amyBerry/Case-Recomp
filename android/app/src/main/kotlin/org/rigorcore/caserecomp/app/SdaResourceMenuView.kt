package org.rigorcore.caserecomp.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import org.rigorcore.caserecomp.sda.*

/** XUI dialog renderer/input. Resource IDs, dimensions and action semantics come from the profile. */
class SdaResourceMenuView(context:Context,private val ui:SdaResourceCanvas,
 private val container:SdaUiNode,private val panelTextures:List<String>,
 private val logicalWidth:Int,private val logicalHeight:Int,private val onAction:(Int)->Unit):View(context) {
 val buttons=container.children.filter { it.type=="allbutton" }
 private var captured:SdaUiNode?=null
 private var pressed=false
 init { tag="sda-resource-menu";isFocusable=true }
 fun buttonBounds(node:SdaUiNode)=ui.rect(node).apply { offset(container.number("x"),container.number("y")) }
 override fun onDraw(canvas:Canvas) {
  super.onDraw(canvas)
  val scale=minOf(width/logicalWidth.toFloat(),height/logicalHeight.toFloat())
  canvas.save();canvas.translate((width-logicalWidth*scale)/2,(height-logicalHeight*scale)/2);canvas.scale(scale,scale)
  canvas.translate(container.number("x").toFloat(),container.number("y").toFloat())
  val panel=container.children.single { it.type=="dialogimg" }
  ui.tiledPanel(canvas,panelTextures,Rect(0,0,panel.number("w"),panel.number("h")))
  for(node in container.children) when(node.type) {
   "label" -> ui.label(canvas,node)
   "allbutton" -> ui.button(canvas,node,state=if(node==captured && pressed) SdaButtonState.PRESSED else SdaButtonState.NORMAL)
  }
  canvas.restore()
 }
 override fun onTouchEvent(event:MotionEvent):Boolean {
  val scale=minOf(width/logicalWidth.toFloat(),height/logicalHeight.toFloat())
  if(scale<=0f) return false
  val x=((event.x-(width-logicalWidth*scale)/2)/scale).toInt()
  val y=((event.y-(height-logicalHeight*scale)/2)/scale).toInt()
  when(event.actionMasked) {
   MotionEvent.ACTION_DOWN -> { captured=buttons.firstOrNull { buttonBounds(it).contains(x,y) };pressed=captured!=null }
   MotionEvent.ACTION_MOVE -> pressed=captured?.let { buttonBounds(it).contains(x,y) } ?: false
   MotionEvent.ACTION_CANCEL -> { captured=null;pressed=false }
   MotionEvent.ACTION_UP -> {
    val selected=captured?.takeIf { buttonBounds(it).contains(x,y) }
    captured=null;pressed=false;invalidate()
    if(selected!=null) { performClick();onAction(selected.number("value")) }
   }
  }
  invalidate();return true
 }
 override fun performClick():Boolean { super.performClick();return true }
}

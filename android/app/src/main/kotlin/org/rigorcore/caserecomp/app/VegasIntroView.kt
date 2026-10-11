package org.rigorcore.caserecomp.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import org.rigorcore.caserecomp.sda.*

/**
 * GAMESTART.XUI startup splash. The star/bulb timelines come from the native splash animator
 * (0046dd30/0046dfc0): bulbs alternate 0.1 s hidden/shown until 4.0 s, the unlit star overlay
 * flickers .2 shown/.4 hidden/.2/.4/.2 then stays hidden, and all bulbs turn on at 2.5 s (00454470).
 * Splash fade durations are not recovered and are calibrated here. Distributor/publisher images
 * come from outside Resources.dll ("std:") and are never shown.
 */
class VegasIntroView(context:Context,content:SdaContent,private val onDone:()->Unit):View(context) {
 private val doc=SdaUiDocument(requireNotNull(content.read("GAMESTART.XUI")),content.loadStrings("GAMESTART.XUI"))
 private val ui=SdaResourceCanvas(doc,content)
 private val paint=Paint(Paint.FILTER_BITMAP_FLAG)
 private var start=-1L
 private var finished=false
 private val splashEnd=3f;private val deluxeStart=3f;private val crossfadeStart=5f;private val crossfadeEnd=6.5f
 private val animationSeconds=6f;private val endFade=.5f
 private val total=crossfadeStart+animationSeconds+endFade

 private fun image(canvas:Canvas,texture:String,x:Int,y:Int,alpha:Float) {
  if(alpha<=0f) return
  paint.alpha=(alpha.coerceIn(0f,1f)*255).toInt()
  canvas.drawBitmap(ui.bitmap(texture),x.toFloat(),y.toFloat(),paint)
 }
 private fun node(id:String)=doc.component(id)
 private fun at(canvas:Canvas,id:String,alpha:Float) { val n=node(id);image(canvas,n.attributes.getValue("tex"),n.number("x"),n.number("y"),alpha) }

 override fun onDraw(canvas:Canvas) {
  super.onDraw(canvas)
  canvas.drawColor(Color.BLACK)
  val now=System.nanoTime()
  if(start<0) start=now
  val t=(now-start)/1e9f
  val scale=minOf(width/800f,height/600f)
  canvas.save();canvas.translate((width-800*scale)/2,(height-600*scale)/2);canvas.scale(scale,scale)
  canvas.clipRect(0,0,800,600)
  if(t<splashEnd) {
   image(canvas,"spintopsplash",0,0,minOf(t/.5f,(splashEnd-t)/.5f,1f))
  } else {
   val title=(t-crossfadeStart).coerceAtLeast(0f)
   if(t>=crossfadeStart) {
    image(canvas,"mpi_title",0,0,1f)
    // Star overlay is the unlit neon; hidden phases show the lit star from mpi_title.
    val starShown=title<.2f || (title>=.6f && title<.8f) || (title>=1.2f && title<1.4f)
    if(starShown) at(canvas,"starout",1f)
    if(title<4f && (title/.1f).toInt()%2==1) at(canvas,"bulblights",1f)
    if(title>=2.5f) at(canvas,"allbulblightson",1f)
   }
   val deluxe=when {
    t<deluxeStart+.5f -> (t-deluxeStart)/.5f
    t<crossfadeStart -> 1f
    else -> 1f-(t-crossfadeStart)/(crossfadeEnd-crossfadeStart)
   }
   image(canvas,"mpi_titleoverlay",0,0,deluxe)
   val fade=(t-(total-endFade))/endFade
   if(fade>0f) canvas.drawColor(Color.argb((fade.coerceIn(0f,1f)*255).toInt(),0,0,0))
  }
  canvas.restore()
  if(t>=total) finish() else postInvalidateOnAnimation()
 }
 private fun finish() { if(!finished) { finished=true;onDone() } }
 override fun onTouchEvent(event:MotionEvent):Boolean {
  if(event.actionMasked==MotionEvent.ACTION_UP) { performClick();finish() }
  return true
 }
 override fun performClick():Boolean { super.performClick();return true }
}

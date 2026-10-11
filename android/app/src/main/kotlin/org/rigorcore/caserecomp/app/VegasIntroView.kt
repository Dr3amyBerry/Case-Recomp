package org.rigorcore.caserecomp.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import org.rigorcore.caserecomp.sda.*

/**
 * GAMESTART.XUI startup splash. Star/bulb timelines come from the native splash animator (0046dd30/0046dfc0):
 * bulbs alternate 0.1 hidden/shown until 4.0, the unlit star overlay flickers .2 shown/.4 hidden/.2/.4/.2 then
 * stays hidden, all bulbs turn on 2.5 s after the animator starts (00454470). The animator steps 0.05 per
 * ~40 ms update, so its 6.0 nominal seconds play in 4.8 s. Screen fades were timed on the original:
 * 0.84 s fades, SpinTop held 0.6 s, Deluxe crossfading into the title over 0.84 s, 0.96 s fade to black and
 * 0.95 s of black. The distributor/publisher screens live outside Resources.dll ("std:") and are never shown.
 */
class VegasIntroView(context:Context,content:SdaContent,private val onDone:()->Unit):View(context) {
 private val doc=SdaUiDocument(requireNotNull(content.read("GAMESTART.XUI")),content.loadStrings("GAMESTART.XUI"))
 private val ui=SdaResourceCanvas(doc,content)
 private val paint=Paint(Paint.FILTER_BITMAP_FLAG)
 private var start=-1L
 private var finished=false
 private val fade=.84f;private val spinTopEnd=fade*2+.6f;private val animationStart=spinTopEnd+.96f
 private val animationSeconds=4.8f;private val nominalRate=1.25f;private val fadeOut=.96f;private val black=.95f
 private val total=animationStart+animationSeconds-.1f+fadeOut+black

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
  if(t<spinTopEnd) {
   image(canvas,"spintopsplash",0,0,minOf(t/fade,(spinTopEnd-t)/fade,1f))
  } else {
   val real=t-animationStart
   if(real>=0f) {
    val nominal=real*nominalRate
    image(canvas,"mpi_title",0,0,1f)
    // Star overlay is the unlit neon; hidden phases show the lit star from mpi_title.
    val starShown=nominal<.2f || (nominal>=.6f && nominal<.8f) || (nominal>=1.2f && nominal<1.4f)
    if(starShown) at(canvas,"starout",1f)
    if(nominal<4f && (nominal/.1f).toInt()%2==1) at(canvas,"bulblights",1f)
    if(real>=2.5f) at(canvas,"allbulblightson",1f)
   }
   val deluxe=if(real<0f) ((t-spinTopEnd)/fade).coerceAtMost(1f) else 1f-real/fade
   image(canvas,"mpi_titleoverlay",0,0,deluxe)
   val out=(t-(animationStart+animationSeconds-.1f))/fadeOut
   if(out>0f) canvas.drawColor(Color.argb((out.coerceIn(0f,1f)*255).toInt(),0,0,0))
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

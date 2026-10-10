package org.rigorcore.caserecomp.sda

/** Title adapters own native callbacks; the SDA wrapper owns input/update/checkpoint identity. */
interface SdaInteractiveController {
 val items:SdaInteractiveItems
 val started:Boolean
 val isSolved:Boolean
 fun start()
 fun visible(index:Int):Boolean
 fun move(x:Int,y:Int):Boolean = false
 fun click(x:Int,y:Int):Boolean
 fun advance(seconds:Float)
 fun state():Map<String,Any?>
 fun drainSounds():List<String> = emptyList()
}
class SdaInteractiveRiddleGame(override val resourceName:String,val controllerId:String,
 val controller:SdaInteractiveController,checkpoint:Map<String,Any?>?=null):SdaBonusGame {
 override val kind="interactive_riddle"
 override val rows=1
 override val cols get()=controller.items.definitions.size
 override val isSolved get()=controller.isSolved
 override var points=0
  set(value) { require(value==0) { "no certified interactive-riddle reward" };field=value }
 init {
  checkpoint?.let { require(it["kind"]==kind && it["resourceName"]==resourceName && it["controllerId"]==controllerId &&
   (it["points"]==0L || it["points"]==0)) { "interactive-riddle identity mismatch" } }
 }
 override fun clickPixel(x:Int,y:Int)=controller.click(x,y)
 override fun solve():Unit=throw UnsupportedOperationException("interactive riddle cannot be skipped")
 override fun state():Map<String,Any?> = mapOf("kind" to kind,"resourceName" to resourceName,"controllerId" to controllerId,
  "points" to 0L,"controller" to controller.state())
}

package org.rigorcore.caserecomp.sda

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.rigorcore.caserecomp.games.vegas.VegasThirdRiddleController
import java.io.File
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import org.rigorcore.caserecomp.MiniJson

/** Real third-phase XUI graph/component test, not campaign/Android/ending acceptance. */
class SdaPrivateInteractiveItemsUnitTest {
 @Test fun native_coin_unlocks_arm_reels_generate_code_and_restore_mid_spin() {
  val file=listOf(File("../../local-output/vegas_full.zip"),File("../local-output/vegas_full.zip"),File("local-output/vegas_full.zip")).firstOrNull { it.isFile }
  assumeTrue("private resources required",file!=null)
  SdaContent.open(file!!,SdaImageDecoder { bytes -> ImageIO.read(ByteArrayInputStream(bytes))?.let { image -> object:SdaPixelSource {
   override val width=image.width;override val height=image.height
   override fun getArgb(px:Int,py:Int)=image.getRGB(px,py)
   override fun getAlpha(px:Int,py:Int)=getArgb(px,py) ushr 24
  } } }).use { content ->
   val definitions=SdaInteractiveResources.load(content,"ENVS.MSE","thirdriddleelementscontainer")
   var controller=VegasThirdRiddleController(definitions,21)
   fun id(name:String)=definitions.indexOfFirst { it.name==name }
   fun click(name:String):Boolean {
    val frame=controller.items.frame(id(name));val pixels=frame.image.pixels
    val point=(0 until frame.height).asSequence().flatMap { y -> (0 until frame.width).asSequence().map { x -> x to y } }.first { (x,y) -> pixels==null || pixels.getAlpha(frame.sourceX+x,frame.sourceY+y)!=0 }
    return controller.click(frame.image.x+point.first,frame.image.y+point.second)
   }
   assertFalse(click("coin"));controller.start()
   assertFalse(click("slotarm"));assertFalse(controller.visible(id("slotarm")))
   repeat(4) { controller.advance(.126f) }
   assertTrue(controller.visible(id("slotarm")));assertFalse(controller.visible(id("slotarmblockedanimation")))
   assertEquals(0,controller.items.index(id("slotarmblockedanimation")))
   // The next coin step is gated by power, even while its image is visible.
   assertFalse(click("coin"))
   assertTrue(click("plugin"));repeat(3) { controller.advance(.126f) }
   assertEquals(listOf(7,6,4,0),controller.symbols.toList())
   assertTrue(click("coin"));assertEquals(1,controller.items.index(id("slotarm")))
   assertTrue(click("slotarm"));repeat(4) { controller.advance(.126f) }
   val saved=MiniJson.canonical(controller.state())
   @Suppress("UNCHECKED_CAST") val state=MiniJson.parse(saved) as Map<String,Any?>
   val uninterrupted=controller
   controller=VegasThirdRiddleController(definitions,999,state)
   assertEquals(saved,MiniJson.canonical(controller.state()))
   repeat(30) { controller.advance(.126f);uninterrupted.advance(.126f) }
   assertTrue(controller.reelsFinished);assertTrue(controller.symbols.all { it in 0..8 })
   assertEquals(MiniJson.canonical(uninterrupted.state()),MiniJson.canonical(controller.state()))
   assertFalse(controller.isSolved)
   click("fingerprint");assertTrue(controller.fingerprintHeld)
   assertTrue(controller.move(620,400));assertFalse(controller.click(620,400)) // Reader is not powered yet.
   assertTrue(controller.fingerprintReturning);controller.advance(.5f);assertTrue(controller.fingerprintReturning)
   val returning=controller.state();controller=VegasThirdRiddleController(definitions,0,returning)
   controller.advance(.001f);assertFalse(controller.fingerprintHeld);assertFalse(controller.fingerprintReturning)
   assertTrue(click("hammer"));repeat(7) { controller.advance(.126f) }
   assertTrue(click("hourglass"));repeat(15) { controller.advance(.126f) }
   assertTrue(click("fingerprint"));assertTrue(controller.move(651,400))
   assertFalse(controller.click(651,400)) // Exact native reader upper boundary excluded.
   controller.advance(.501f)
   assertTrue(click("fingerprint"));assertTrue(controller.move(620,400))
   val held=MiniJson.canonical(controller.state())
   @Suppress("UNCHECKED_CAST") val heldState=MiniJson.parse(held) as Map<String,Any?>
   controller=VegasThirdRiddleController(definitions,0,heldState)
   assertEquals(held,MiniJson.canonical(controller.state()));assertTrue(controller.click(620,400))
   repeat(6) { controller.advance(.126f) }
   assertTrue(controller.keypadEnabled);assertTrue(controller.items.completed("fingerprint","notplaced"))
   assertEquals(listOf(0,0,0,0),controller.ledFrames)
   val wrong=(controller.symbols.first()+1)%9
   repeat(4) { assertTrue(controller.enterSymbol(wrong)) }
   assertTrue(controller.keypadError);assertEquals(listOf(10,10,10,10),controller.ledFrames)
   assertFalse(controller.enterSymbol(0));controller.advance(1.65f);assertTrue(controller.keypadError)
   controller.advance(.001f);assertFalse(controller.keypadError);assertEquals(listOf(0,0,0,0),controller.ledFrames)
   val code=controller.symbols
   assertTrue(controller.enterSymbol(code[0]))
   val partial=controller.state();controller=VegasThirdRiddleController(definitions,0,partial)
   code.drop(1).forEach { assertTrue(controller.enterSymbol(it)) }
   controller.advance(.126f);assertTrue(controller.doorOpening);assertFalse(controller.isSolved)
   assertFalse(controller.click(620,400))
   assertThrows(IllegalArgumentException::class.java) { VegasThirdRiddleController(definitions,0,controller.state()+mapOf("rng" to -1L)) }
   println("PRIVATE THIRD CONTROLLER: original blocked arm, coin insertion, power, four reels, native RNG and mid-spin restore; fingerprint/keypad/ending not certified")
  }
 }
 @Test fun original_hammer_hourglass_scale_reader_and_plugin_follow_steps_without_forcing_flags() {
  val file=listOf(File("../../local-output/vegas_full.zip"),File("../local-output/vegas_full.zip"),File("local-output/vegas_full.zip")).firstOrNull { it.isFile }
  assumeTrue("private resources required",file!=null)
  SdaContent.open(file!!,SdaImageDecoder { bytes -> ImageIO.read(ByteArrayInputStream(bytes))?.let { image -> object:SdaPixelSource {
   override val width=image.width;override val height=image.height
   override fun getArgb(px:Int,py:Int)=image.getRGB(px,py)
   override fun getAlpha(px:Int,py:Int)=getArgb(px,py) ushr 24
  } } }).use { content ->
   val d=SdaInteractiveResources.load(content,"ENVS.MSE","thirdriddleelementscontainer")
   assertEquals(21,d.size);assertEquals(2,d.count { it.name=="keypad" })
   assertEquals(13,d.first { it.name=="hourglass" }.steps.size)
   assertEquals(142,d.first { it.name=="hourglass" }.steps[1].frameWidth)
   assertTrue(d.flatMap { it.steps }.all { it.frameTime==.125f })
   assertEquals(2,d.first { it.name=="hourglass" }.steps[1].frameIndex)
   val events=mutableListOf<String>()
   fun create(saved:Map<String,Any?>?=null)=SdaInteractiveItems(d,saved,onCompleted={ _,_,step -> events.add(step.name);true })
   var m=create()
   fun id(name:String)=d.indexOfFirst { it.name==name }
   fun click(name:String) {
    val frame=m.frame(id(name));val pixels=frame.image.pixels
    val point=(0 until frame.height).asSequence().flatMap { y -> (0 until frame.width).asSequence().map { x -> x to y } }.first { (x,y) -> pixels==null || pixels.getAlpha(frame.sourceX+x,frame.sourceY+y)!=0 }
    assertTrue(m.click(frame.image.x+point.first,frame.image.y+point.second))
   }
   repeat(10) { m.advance(.126f) }
   assertEquals(0,m.index(id("hammer")));assertEquals(0,m.index(id("hourglass")))
   click("hammer")
   repeat(7) { m.advance(.126f) }
   assertTrue(m.completed("hammer","breakhourglass"));assertEquals(1,m.index(id("hourglass")))
   click("hourglass");m.advance(.1f)
   val json=MiniJson.canonical(m.state())
   @Suppress("UNCHECKED_CAST") val saved=MiniJson.parse(json) as Map<String,Any?>
   m=create(saved);assertEquals(json,MiniJson.canonical(m.state()))
   repeat(15) { m.advance(.126f) }
   assertTrue(m.completed("hourglass","fillscup"))
   assertEquals(1,m.index(id("scale")));assertEquals(1,m.index(id("leftcup")));assertEquals(1,m.index(id("rightcup")))
   assertEquals(1,m.index(id("lever")));assertEquals(1,m.index(id("reader")))
   assertEquals(1,m.index(id("lock")));assertEquals(0,m.index(d.indexOfLast { it.name=="keypad" }))
   assertFalse(m.completed("lock","dooropen"))
   click("plugin");repeat(3) { m.advance(.126f) }
   assertTrue(m.completed("plugin","off"));assertEquals(1,m.index(id("slotmachine")))
   assertEquals(0,m.index(id("slotarm")));assertEquals(0,m.index(id("reelspin1")))
   assertTrue("hammerreleasedstep" in events);assertTrue("hourglassbegindrop" in events)
   assertThrows(IllegalArgumentException::class.java) { SdaInteractiveResources.load(content,"ENVS.MSE","missing-container") }
   println("PRIVATE INTERACTIVE ITEMS: 21 original items, duplicate keypad order, alpha clicks, .125 strict native timing, hammer -> hourglass -> scale -> lever -> reader, plugin -> slot power, mid-animation restore; native controller callbacks and Android ending remain pending")
  }
 }
}

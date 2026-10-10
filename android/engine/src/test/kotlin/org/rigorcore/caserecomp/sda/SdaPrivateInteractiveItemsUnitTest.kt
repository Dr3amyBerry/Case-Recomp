package org.rigorcore.caserecomp.sda

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import org.rigorcore.caserecomp.MiniJson

/** Real third-phase XUI graph/component test, not campaign/Android/ending acceptance. */
class SdaPrivateInteractiveItemsUnitTest {
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

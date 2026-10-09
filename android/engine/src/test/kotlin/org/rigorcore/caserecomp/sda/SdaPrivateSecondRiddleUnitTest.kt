package org.rigorcore.caserecomp.sda
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.rigorcore.caserecomp.MiniJson
import java.io.File
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import org.w3c.dom.Element

/** Native placement kernel/resource probe, not a campaign or Android finale completion test. */
class SdaPrivateSecondRiddleUnitTest {
 @Test fun original_eight_destinations_use_native_tolerance_and_resume_without_fake_mechanisms() {
  val file=listOf(File("../../local-output/vegas_full.zip"),File("../local-output/vegas_full.zip"),File("local-output/vegas_full.zip")).firstOrNull { it.isFile }
  assumeTrue("private package required",file!=null)
  SdaContent.open(file!!,SdaImageDecoder { bytes -> ImageIO.read(ByteArrayInputStream(bytes))?.let { image -> object:SdaPixelSource {
   override val width=image.width;override val height=image.height
   override fun getArgb(px:Int,py:Int)=image.getRGB(px,py)
   override fun getAlpha(px:Int,py:Int)=getArgb(px,py) ushr 24
  } } }).use { content ->
   val definition=SdaRiddleResources.loadSecond(content,"ENVS.MSE","secondriddle")
   assertEquals(8,definition.pieces.size);assertEquals(8,definition.required)
   assertTrue(definition.pieces.all { it.hasTarget && it.placeOrder == -1 })
   assertEquals(8,definition.targetImageUris.size);assertEquals(8,definition.trayImageUris.size)
   assertNull(definition.timeLimit)
   assertEquals(144,definition.backgroundX);assertEquals(0,definition.backgroundY)
   assertThrows(IllegalArgumentException::class.java) { SdaRiddleResources.load(content,"ENVS.MSE","secondriddle") }
   val nodeList=SdaXml.parse(content.read("ENVS.MSE")!!).getElementsByTagName("*")
   val nodes=(0 until nodeList.length).map { nodeList.item(it) as Element }
   val controller=nodes.single { it.getAttribute("id")=="secondriddle" }
   val nested=controller.getElementsByTagName("*")
   val children=(0 until nested.length).map { nested.item(it) as Element }
   val bindings=children.filter { it.tagName.substringAfter(':')=="riddlepiece" }
   var interaction=SdaRiddleInteraction(definition.pieces,definition.required,8,definition.tray)
   fun pick(id:String) {
    while(interaction.cells().none { it.id==id }) {
     val direction=if(interaction.board.available.indexOf(id)<interaction.firstVisible) -1 else 1
     assertTrue(interaction.scroll(direction))
    }
    val cell=interaction.cells().first { it.id==id };assertTrue(interaction.pickPixel(cell.x,cell.y))
   }
   for(binding in bindings.reversed()) {
    val id=binding.getAttribute("image")
    val target=children.single { it.getAttribute("id")==binding.getAttribute("target") }
    val source=content.decodeImage(definition.imageUris.getValue(id))
    val tx=target.getAttribute("destinationx").toFloat().toInt();val ty=target.getAttribute("destinationy").toFloat().toInt()
    val tolerance=target.getAttribute("tolerance").toInt()
    val centerX=144+tx+source.width/2;val centerY=ty+source.height/2
    pick(id)
    assertFalse(interaction.dropScreen(centerX-tolerance,centerY,144,0)) // Native lower edge excluded.
    assertFalse(id in interaction.board.placed)
    pick(id)
    val held=MiniJson.canonical(interaction.state())
    @Suppress("UNCHECKED_CAST") val state=MiniJson.parse(held) as Map<String,Any?>
    interaction=SdaRiddleInteraction(definition.pieces,8,999,definition.tray,state)
    assertEquals(held,MiniJson.canonical(interaction.state()))
    assertTrue(interaction.dropScreen(centerX+tolerance,centerY,144,0)) // Native upper edge included.
    assertTrue(id in interaction.board.placed)
    content.decodeImage(definition.targetImageUris.getValue(id));content.decodeImage(definition.trayImageUris.getValue(id))
   }
   assertTrue(interaction.board.isSolved);assertEquals(8,interaction.board.placed.size)
   assertTrue(interaction.board.available.isEmpty())
   println("PRIVATE SECOND RIDDLE: 8 real resource bindings, native pointer-relative destination tolerance, both boundary sides, reverse order, shuffled tray, held restore; no campaign/Android/animation claim")
  }
 }
}

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
   var game=SdaSecondRiddleGame.load(content,SdaRiddleBinding("ENVS.MSE","secondriddle"),8)
   assertFalse(game.started);assertFalse(game.clickPixel(11,117))
   game=SdaSecondRiddleGame.load(content,SdaRiddleBinding("ENVS.MSE","secondriddle"),999,game.state())
   assertFalse(game.started);game.start()
   for(piece in game.definition.pieces.reversed()) {
    while(game.interaction.cells().none { it.id==piece.id }) {
     val key=if(game.interaction.board.available.indexOf(piece.id)<game.interaction.firstVisible) "up" else "down"
     val arrow=game.arrowRect(key)!!;assertTrue(game.clickPixel(arrow.x+1,arrow.y+1))
    }
    val cell=game.interaction.cells().first { it.id==piece.id };assertTrue(game.clickPixel(cell.x+1,cell.y+1))
    val held=game.state();game=SdaSecondRiddleGame.load(content,SdaRiddleBinding("ENVS.MSE","secondriddle"),8,held)
    assertEquals(piece.id,game.interaction.board.selected);assertEquals(held,game.state())
    val image=game.images.getValue(piece.id);val dest=game.definition.destinations.getValue(piece.id)
    assertTrue(game.clickPixel(144+dest.x.toInt()+image.width/2,dest.y.toInt()+image.height/2))
   }
   assertTrue(game.isSolved);assertEquals(0,game.points)
   assertThrows(UnsupportedOperationException::class.java) { game.solve() }
   val earned=File(file.parentFile,"sda-earned-first-riddle.json")
   assertTrue("earned first-riddle entry checkpoint required",earned.isFile)
   val camp=SdaCampaign(SdaLevels.parse(content.read("LEVELS_1.XUI")!!),firstRiddle=SdaRiddleBinding("ENVS.MSE","firstriddle"),secondRiddle=SdaRiddleBinding("ENVS.MSE","secondriddle"),interactiveRiddleFactory={ c,seed,cp -> org.rigorcore.caserecomp.games.vegas.VegasThirdRiddleController.loadGame(c,seed,cp) })
   camp.restore(SdaCampaignState.fromJson(earned.readText()),content)
   val before=camp.snapshot().toJson()
   assertThrows(IllegalArgumentException::class.java) { camp.continueFirstRiddle(content) }
   assertEquals(before,camp.snapshot().toJson())
   val first=camp.bonusGame as SdaFirstRiddleGame
   for(piece in first.definition.pieces.filter { it.hasTarget }.sortedBy { it.placeOrder }) {
    while(first.interaction.cells().none { it.id==piece.id }) {
     val key=if(first.interaction.board.available.indexOf(piece.id)<first.interaction.firstVisible) "up" else "down"
     val arrow=first.arrowRect(key)!!;assertTrue(camp.clickBonus(arrow.x+1,arrow.y+1))
    }
    val cell=first.interaction.cells().first { it.id==piece.id };assertTrue(camp.clickBonus(cell.x+1,cell.y+1))
    assertTrue(camp.clickBonus(144+piece.hotspotX+1,first.backgroundY+piece.hotspotY+1))
   }
   val finishedFirst=camp.snapshot().toJson()
   val unsupported=SdaCampaign(camp.levels,firstRiddle=camp.firstRiddle,secondRiddle=SdaRiddleBinding("ENVS.MSE","missing-controller"))
   unsupported.restore(SdaCampaignState.fromJson(finishedFirst),content)
   assertThrows(IllegalArgumentException::class.java) { unsupported.continueFirstRiddle(content) }
   assertEquals(finishedFirst,unsupported.snapshot().toJson())
   val invalid=game.state()+mapOf("started" to false)
   assertThrows(IllegalArgumentException::class.java) { SdaSecondRiddleGame.load(content,SdaRiddleBinding("ENVS.MSE","secondriddle"),8,invalid) }
   val score=camp.points
   camp.continueFirstRiddle(content);assertEquals(SdaCampaignPhase.FINALE_2,camp.phase)
   assertEquals(score,camp.points);assertFalse((camp.bonusGame as SdaSecondRiddleGame).started)
   val waiting=camp.snapshot().toJson();camp.restore(SdaCampaignState.fromJson(waiting),content)
   assertEquals(waiting,camp.snapshot().toJson())
   (camp.bonusGame as SdaSecondRiddleGame).start()
   val ready=camp.snapshot().toJson();camp.restore(SdaCampaignState.fromJson(ready),content)
   assertEquals(ready,camp.snapshot().toJson())
   val second=camp.bonusGame as SdaSecondRiddleGame
   val beforeSecond=camp.snapshot().toJson()
   assertThrows(IllegalArgumentException::class.java) { camp.continueSecondRiddle(content) }
   assertEquals(beforeSecond,camp.snapshot().toJson())
   for(piece in second.definition.pieces) {
    while(second.interaction.cells().none { it.id==piece.id }) {
     val key=if(second.interaction.board.available.indexOf(piece.id)<second.interaction.firstVisible) "up" else "down"
     val arrow=second.arrowRect(key)!!;assertTrue(camp.clickBonus(arrow.x+1,arrow.y+1))
    }
    val cell=second.interaction.cells().first { it.id==piece.id };assertTrue(camp.clickBonus(cell.x+1,cell.y+1))
    val target=second.definition.destinations.getValue(piece.id);val image=second.images.getValue(piece.id)
    assertTrue(camp.clickBonus(144+target.x.toInt()+image.width/2,target.y.toInt()+image.height/2))
   }
   assertTrue(second.isSolved);camp.continueSecondRiddle(content)
   assertEquals(SdaCampaignPhase.FINALE_3,camp.phase);assertEquals(score,camp.points)
   val third=camp.bonusGame as SdaInteractiveRiddleGame
   assertFalse(third.controller.started);third.controller.start()
   camp.advance(.126f)
   val thirdSave=camp.snapshot().toJson();camp.restore(SdaCampaignState.fromJson(thirdSave),content)
   assertEquals(thirdSave,camp.snapshot().toJson());assertFalse(camp.bonusGame!!.isSolved)
   assertThrows(UnsupportedOperationException::class.java) { camp.bonusGame!!.solve() }
   assertThrows(IllegalArgumentException::class.java) { camp.confirmInteractiveRiddleComplete() }
   assertEquals(thirdSave,camp.snapshot().toJson())
   println("PRIVATE SECOND RIDDLE: 8 real resource bindings, native pointer-relative destination tolerance, both boundary sides, reverse order, shuffled tray, held restore; no campaign/Android/animation claim")
  }
 }
}

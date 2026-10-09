package org.rigorcore.caserecomp.sda
import org.junit.Assert.*
import org.junit.Test
import org.rigorcore.caserecomp.MiniJson
class SdaFirstRiddleGameUnitTest {
 private val pieces=listOf(SdaRiddlePiece("ownA",0,200,800,20,20),SdaRiddlePiece("ownB",1,200,800,20,20),SdaRiddlePiece("decoy",-1,0,0,0,0,false))
 private val definition=SdaRiddleDefinition(pieces,2,SdaRiddleTrayDefinition(10,116,131,273),
  pieces.associate { it.id to it.id },mapOf("ownA" to "first own caption","ownB" to "second own caption"),
  mapOf("ownA" to SdaRiddleDestination(10f,20f,100,0f,1f),"ownB" to SdaRiddleDestination(30f,40f,0,0f,1f)),"ownbg",144,-713,1500f)
 private val images=pieces.associate { it.id to SdaArgbPixelSource(2,2,IntArray(4) { -1 }) }
 private fun game(saved:Map<String,Any?>?=null)=SdaFirstRiddleGame("own.xui","own-controller",definition,images,SdaArgbPixelSource(2,2,IntArray(4)),8,saved)
 @Test fun coordinates_order_decoy_and_held_save_do_not_manufacture_completion() {
  var g=game()
  fun pick(id:String) { val cell=g.interaction.cells().first { it.id==id }; assertTrue(g.clickPixel(cell.x,cell.y)) }
  repeat(20) { assertFalse(g.clickPixel(0,0)) }
  pick("decoy");assertFalse(g.clickPixel(350,90));assertEquals(0,g.interaction.board.placed.size)
  pick("ownB");assertFalse(g.clickPixel(350,90));assertEquals(0,g.interaction.board.placed.size)
  pick("ownA");assertTrue(g.movePixel(350,90))
  val saved=MiniJson.canonical(g.state())
  @Suppress("UNCHECKED_CAST") val state=MiniJson.parse(saved) as Map<String,Any?>
  g=game(state);assertEquals(saved,MiniJson.canonical(g.state()))
  assertEquals("first own caption",g.caption)
  assertTrue(g.clickPixel(350,90));assertEquals(-613,g.backgroundY)
  assertEquals("second own caption",g.caption)
  pick("ownB");assertTrue(g.clickPixel(350,190));assertTrue(g.isSolved)
  assertEquals(listOf("decoy"),g.interaction.board.available)
  assertEquals(0,g.points)
  assertThrows(UnsupportedOperationException::class.java) { g.solve() }
 }
}

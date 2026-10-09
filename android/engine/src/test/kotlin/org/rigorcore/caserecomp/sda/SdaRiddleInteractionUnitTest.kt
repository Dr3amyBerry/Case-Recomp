package org.rigorcore.caserecomp.sda
import org.junit.Assert.*
import org.junit.Test
import org.rigorcore.caserecomp.MiniJson
class SdaRiddleInteractionUnitTest {
 private val pieces=(0..5).map { SdaRiddlePiece("own$it",it,500,100,20,20) }
 private val geometry=SdaRiddleTrayDefinition(10,116,131,273)
 @Test fun native_shuffle_cells_and_last_page_return_are_preserved() {
  val game=SdaRiddleInteraction(pieces,6,8,geometry)
  assertEquals(listOf("own2","own3","own0","own1","own4","own5"),game.board.available)
  assertEquals(2831887398L,game.finalRngState)
  assertEquals(listOf(116,184,252,320),game.cells().map { it.y })
  assertFalse(game.pickPixel(10,388)) // Last remainder pixel is outside every slot.
  assertTrue(game.pickPixel(10,116)) // Whole cell: no invented alpha requirement.
  assertFalse(game.scroll(1))
  assertFalse(game.dropScreen(0,0,144,-713))
  assertTrue(game.scroll(1));assertTrue(game.scroll(1));assertFalse(game.scroll(1))
  assertTrue(game.pickPixel(10,320))
  assertEquals("own5",game.board.selected)
  assertEquals(1,game.firstVisible)
  assertFalse(game.dropScreen(0,0,144,-713))
  assertEquals(1,game.firstVisible) // Native return clamps, unlike Jigsaw's last-item increment.
 }
 @Test fun held_checkpoint_restores_without_consuming_rng_or_changing_viewport() {
  val game=SdaRiddleInteraction(pieces,6,8,geometry)
  game.scroll(1);game.pickPixel(10,116)
  val text=MiniJson.canonical(game.state())
  @Suppress("UNCHECKED_CAST") val saved=MiniJson.parse(text) as Map<String,Any?>
  val restored=SdaRiddleInteraction(pieces,6,999,geometry,saved)
  assertEquals(text,MiniJson.canonical(restored.state()))
  assertThrows(IllegalArgumentException::class.java) {
   SdaRiddleInteraction(pieces,6,8,geometry,saved+("firstVisible" to 99L))
  }
 }
}

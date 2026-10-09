package org.rigorcore.caserecomp.sda
import org.junit.Assert.*
import org.junit.Test
import org.rigorcore.caserecomp.MiniJson
class SdaRiddleBoardUnitTest {
 private val pieces=listOf(SdaRiddlePiece("ownA",0,10,20,30,40),SdaRiddlePiece("ownB",1,80,90,20,20))
 @Test fun native_order_and_background_relative_half_open_hotspot_control_placement() {
  val game=SdaRiddleBoard(pieces,2,listOf("ownB","ownA"))
  assertFalse(game.dropScreen(0,0,0,0))
  assertTrue(game.select("ownB"))
  assertFalse(game.dropScreen(80,90,0,0)) // Correct hotspot, wrong placeorder.
  assertEquals(listOf("ownB","ownA"),game.available)
  assertTrue(game.select("ownA"))
  assertFalse(game.dropScreen(-60,70,-100,50)) // exclusive right boundary
  assertTrue(game.select("ownA"))
  assertTrue(game.dropScreen(-90,70,-100,50))
  assertEquals(1,game.currentOrder)
  assertFalse(game.select("ownA"))
  assertTrue(game.select("ownB"))
  assertTrue(game.dropScreen(80,90,0,0))
  assertTrue(game.isSolved)
 }
 @Test fun held_checkpoint_keeps_removed_tray_item_and_rejects_inconsistent_history() {
  val game=SdaRiddleBoard(pieces,2,listOf("ownB","ownA"))
  game.select("ownA")
  val before=MiniJson.canonical(game.state())
  @Suppress("UNCHECKED_CAST") val saved=MiniJson.parse(before) as Map<String,Any?>
  val restored=SdaRiddleBoard(pieces,2,listOf("ownB","ownA"),saved)
  assertEquals(before,MiniJson.canonical(restored.state()))
  assertFalse(restored.dropScreen(Int.MAX_VALUE,Int.MAX_VALUE,Int.MIN_VALUE,Int.MIN_VALUE))
  assertEquals(listOf("ownB","ownA"),restored.available)
  assertThrows(IllegalArgumentException::class.java) {
   SdaRiddleBoard(pieces,2,listOf("ownB","ownA"),saved+("placed" to listOf("ownB")))
  }
  assertEquals(before,MiniJson.canonical(game.state()))
 }
 @Test fun targetless_decoy_is_returned_and_never_advances_the_riddle() {
  val game=SdaRiddleBoard(listOf(pieces.first(),SdaRiddlePiece("decoy",-1,0,0,0,0,false)),1)
  assertTrue(game.select("decoy"));assertFalse(game.dropScreen(0,0,0,0))
  assertEquals(0,game.currentOrder);assertFalse(game.isSolved)
  assertEquals(listOf("ownA","decoy"),game.available)
 }
 @Test fun native_minus_one_placeorder_is_a_wildcard_not_a_different_required_index() {
  val game=SdaRiddleBoard(listOf(SdaRiddlePiece("own",-1,0,0,10,10)),1)
  assertTrue(game.select("own"));assertTrue(game.dropScreen(0,0,0,0));assertTrue(game.isSolved)
 }
}

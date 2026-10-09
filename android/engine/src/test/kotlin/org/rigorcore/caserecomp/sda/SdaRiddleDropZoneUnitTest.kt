package org.rigorcore.caserecomp.sda
import org.junit.Assert.*
import org.junit.Test
class SdaRiddleDropZoneUnitTest {
 @Test fun native_destination_inside_pointer_rectangle_has_asymmetric_integer_edges() {
  val piece=SdaRiddleDropZone.piece("own",-1,6f,0f,100,145,144)
  val expectedX=6+145/2;val expectedY=144/2
  for(dx in listOf(-101,-100,-99,0,99,100,101)) for(dy in listOf(-101,-100,-99,0,99,100,101)) {
   val board=SdaRiddleBoard(listOf(piece),1)
   board.select("own")
   // Independent native expression: destination point is inside the pointer-centered tolerance rectangle.
   val x=expectedX+dx;val y=expectedY+dy
   val left=x-145/2-100;val top=y-144/2-100
   val native=6>=left && 6<left+200 && 0>=top && 0<top+200
   assertEquals("$dx,$dy",native,board.dropScreen(x+144,y,144,0))
  }
 }
 @Test fun signed_fractional_destinations_truncate_and_invalid_parameters_are_rejected() {
  val piece=SdaRiddleDropZone.piece("own",0,-6.75f,3.5f,10,5,7)
  assertEquals(-13,piece.hotspotX);assertEquals(-3,piece.hotspotY)
  assertThrows(IllegalArgumentException::class.java) { SdaRiddleDropZone.piece("own",0,Float.NaN,0f,10,5,7) }
  assertThrows(IllegalArgumentException::class.java) { SdaRiddleDropZone.piece("own",0,0f,0f,0,5,7) }
 }
}

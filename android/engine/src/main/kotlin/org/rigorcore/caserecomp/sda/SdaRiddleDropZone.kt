package org.rigorcore.caserecomp.sda

/** 00450c9c–00450d7c: destination is inside a 2*tolerance rectangle around held image top-left. */
object SdaRiddleDropZone {
 fun piece(id:String,placeOrder:Int,destinationX:Float,destinationY:Float,tolerance:Int,width:Int,height:Int): SdaRiddlePiece {
  require(destinationX.isFinite() && destinationY.isFinite() && destinationX in -32768f..32768f && destinationY in -32768f..32768f)
  require(tolerance in 1..2048 && width in 1..4096 && height in 1..4096)
  // Original positive image dimensions divide by two with integer truncation. ftol truncates destinations.
  // Inverting the native half-open destination test makes the pointer's lower edge exclusive.
  val x=destinationX.toInt()+width/2-tolerance+1
  val y=destinationY.toInt()+height/2-tolerance+1
  val piece=SdaRiddlePiece(id,placeOrder,x,y,tolerance*2,tolerance*2)
  SdaRiddleBoard(listOf(piece),1) // Apply the same bounded definition checks as the shared interaction.
  return piece
 }
}

package org.rigorcore.caserecomp.app

/** Recovered context selection from native 00405080, not a per-scene playlist. */
internal class VegasMusicState {
 private var mode=0
 private var alternate=false
 private var track:String?=null
 fun select(context:Int):String? {
  if(mode==context) return track
  mode=context
  track=when(context) {
   1 -> "mainmenu"
   2 -> (if(alternate) "eyespy1" else "eyespy2").also { alternate=!alternate }
   3 -> "bonusgame"
   else -> null
  }
  return track
 }
}

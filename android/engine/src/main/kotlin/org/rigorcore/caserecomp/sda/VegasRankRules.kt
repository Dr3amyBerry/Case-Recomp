package org.rigorcore.caserecomp.sda

/** Vegas player rank (native 004189c0): 1..15 from total score; 0 falls in the original's boundary gaps. */
object VegasRankRules {
 // Exclusive (low, high) score windows for ranks 1..14, copied from the native comparisons.
 private val windows=listOf(0 to 250000,250001 to 800000,800001 to 1350000,1350001 to 1900000,1900001 to 2450000,
  2450001 to 3000000,3000001 to 3550000,3550001 to 4100000,4100001 to 4800000,4800001 to 5500000,5500001 to 6200000,
  6200001 to 6950000,6950001 to 7700000,7700001 to 8500000)
 fun rankFor(score:Int,keys:Int,chips:Int):Int {
  for((index,window) in windows.withIndex()) if(score>window.first && score<window.second) return index+1
  return if(score>8500001 && keys==25 && chips==25) 15 else 0
 }
 /** The stored rank only rises (004150c0/004162e0); a new player starts at rank 1 (004191e0). */
 fun displayed(stored:Int,score:Int,keys:Int,chips:Int):Int=maxOf(stored,1,rankFor(score,keys,chips))
}

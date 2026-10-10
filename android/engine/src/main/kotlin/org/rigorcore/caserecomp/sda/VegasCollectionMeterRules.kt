package org.rigorcore.caserecomp.sda

/** Vegas PDA cropping recovered from 00443ea0 and virtual renderer 0041d6d0. */
object VegasCollectionMeterRules {
 fun keyCropTop(count:Int):Int {
  require(count in 0..25)
  // x87 multiplies the original float constant without rounding each intermediate to Float.
  return 50+(count.toDouble()*.04f.toDouble()*-45.0).toInt()
 }
 fun chipCropTop(count:Int):Int {
  require(count in 0..25)
  return 43+(count.toDouble()/25.0*-37.0).toInt()
 }
}

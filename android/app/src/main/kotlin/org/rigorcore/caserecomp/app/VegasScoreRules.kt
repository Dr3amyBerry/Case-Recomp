package org.rigorcore.caserecomp.app

/** Vegas scoring policy recovered from native bonus-result processing (0045b150/0045ba60). */
internal object VegasScoreRules {
 fun bonusTimeReward(remainingSeconds:Float):Int {
  require(remainingSeconds.isFinite() && remainingSeconds>=0f)
  return Math.multiplyExact(kotlin.math.ceil(remainingSeconds.toDouble()).toInt(),50)
 }
}

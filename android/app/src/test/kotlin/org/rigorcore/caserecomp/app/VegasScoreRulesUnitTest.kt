package org.rigorcore.caserecomp.app
import org.junit.Assert.*
import org.junit.Test
class VegasScoreRulesUnitTest {
 @Test fun original_bonus_time_reward_rounds_up_without_hour_wrapping() {
  assertEquals(0,VegasScoreRules.bonusTimeReward(0f))
  assertEquals(50,VegasScoreRules.bonusTimeReward(.01f))
  assertEquals(500,VegasScoreRules.bonusTimeReward(10f))
  assertEquals(550,VegasScoreRules.bonusTimeReward(10.25f))
  assertEquals(180050,VegasScoreRules.bonusTimeReward(3600.25f))
 }
}

package org.rigorcore.caserecomp.sda
import org.junit.Assert.*
import org.junit.Test

/** Component fixtures; these do not certify campaign/UI playthroughs. */
class SdaBonusCompletionPolicyUnitTest {
 private fun campaign(reward:(Float)->Int):SdaCampaign {
  val c=SdaCampaign(listOf(SdaLevel(1,10.25f,1,listOf("own"),"Own","own.trg")),bonusTimeReward=reward)
  c.phase=SdaCampaignPhase.BONUS
  c.bonusGame=SdaTileRotGame("own.trg",rows=1,cols=1,rotations=intArrayOf(1))
  return c
 }
 @Test fun earned_completion_credits_time_policy_once_and_stores_total() {
  var calls=0
  val c=campaign { remaining -> calls++;assertEquals(10.25f,remaining);550 }
  val baseline=campaign { 0 }
  assertTrue(baseline.clickBonus(180,100))
  assertTrue(c.clickBonus(180,100))
  assertEquals(baseline.points+550,c.points)
  val earned=c.points
  assertFalse(c.clickBonus(180,100))
  assertEquals(earned,c.points);assertEquals(1,calls)
  assertEquals(earned,c.snapshot().points)
 }
 @Test fun skip_does_not_credit_time_policy() {
  val c=campaign { error("skip must not invoke completion reward") }
  c.solveBonus()
  assertEquals(0,c.points)
  assertEquals(SdaCampaignPhase.LEVEL_COMPLETE,c.phase)
 }
}

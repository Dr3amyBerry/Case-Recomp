package org.rigorcore.caserecomp.sda

import org.junit.Assert.*
import org.junit.Test
import org.rigorcore.caserecomp.MiniJson

class SdaInteractiveItemsUnitTest {
 private val pixel=SdaArgbPixelSource(3,2,intArrayOf(0,-1,-1,-1,-1,-1))
 private fun definition()=listOf(
  SdaInteractiveItemDefinition("first",listOf(SdaInteractiveImage(10,20,3,2,pixel)),listOf(
   SdaInteractiveStep(name="pressed",interactive=true),SdaInteractiveStep(frameTime=.25f,name="done"))),
  SdaInteractiveItemDefinition("follower",listOf(SdaInteractiveImage(20,20,3,2,pixel)),listOf(
   SdaInteractiveStep(name="waiting"),SdaInteractiveStep(condition=SdaStepCondition.COMPLETED,item="first",step="pressed"))),
  SdaInteractiveItemDefinition("never",listOf(SdaInteractiveImage(30,20,3,2,pixel)),listOf(
   SdaInteractiveStep(),SdaInteractiveStep(condition=SdaStepCondition.NEVER))))
 @Test fun native_next_condition_strict_time_one_step_per_update_and_alpha_input() {
  val m=SdaInteractiveItems(definition())
  m.advance(1f);assertEquals(0,m.index(0));assertEquals(0,m.index(1));assertEquals(0,m.index(2))
  assertFalse(m.click(10,20));assertTrue(m.click(11,20))
  assertEquals(1,m.index(0));assertTrue(m.completed("first","pressed"))
  m.advance(.125f);assertEquals(1,m.index(1));assertFalse(m.completed("first","done"))
  m.advance(.125f);assertFalse(m.completed("first","done")) // Strictly greater, not >=.
  m.advance(.001f);assertTrue(m.completed("first","done"));assertEquals(0,m.index(2))
  assertFalse(m.click(11,20))
 }
 @Test fun held_animation_checkpoint_preserves_elapsed_flags_and_duplicate_names() {
  val definitions=definition()+definition().first().copy(name="first")
  val original=SdaInteractiveItems(definitions);assertTrue(original.click(11,20));original.advance(.1f)
  val json=MiniJson.canonical(original.state())
  @Suppress("UNCHECKED_CAST") val saved=MiniJson.parse(json) as Map<String,Any?>
  val restored=SdaInteractiveItems(definitions,saved)
  assertEquals(json,MiniJson.canonical(restored.state()))
  assertThrows(IllegalArgumentException::class.java) { SdaInteractiveItems(definitions,saved+mapOf("items" to emptyList<Any>())) }
  original.advance(.16f);restored.advance(.16f);assertEquals(original.state(),restored.state())
  assertThrows(IllegalArgumentException::class.java) { restored.advance(Float.NaN) }
 }
 @Test fun completion_callback_can_remain_incomplete_while_the_animation_advances() {
  val events=mutableListOf<String>()
  val m=SdaInteractiveItems(definition(),onCompleted={ _,_,step -> events.add(step.name);step.name!="pressed" })
  assertTrue(m.click(11,20));assertEquals(1,m.index(0));assertFalse(m.completed("first","pressed"))
  m.advance(1f);assertEquals(0,m.index(1));assertEquals(listOf("pressed","done"),events)
 }
}

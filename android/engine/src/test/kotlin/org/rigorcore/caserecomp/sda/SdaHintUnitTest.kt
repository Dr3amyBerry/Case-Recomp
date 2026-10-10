package org.rigorcore.caserecomp.sda

import org.junit.Assert.*
import org.junit.Test

class SdaHintUnitTest {
 private val policy=SdaHintPolicy(20f,1.5f,67.5f)
 @Test fun cooldown_grows_after_recharge_and_caps_at_native_limit() {
  val hint=SdaHint(policy)
  assertTrue(hint.consume())
  assertFalse(hint.consume())
  hint.advance(19.9f);assertFalse(hint.ready)
  hint.advance(.1f);assertTrue(hint.ready);assertEquals(30f,hint.delay,0.001f)
  assertTrue(hint.consume());hint.advance(30f);assertEquals(45f,hint.delay,0f)
  assertTrue(hint.consume());hint.advance(45f);assertEquals(67.5f,hint.delay,0f)
  assertTrue(hint.consume());hint.advance(67.5f);assertEquals(67.5f,hint.delay,0f)
 }
 @Test fun partial_recharge_roundtrips_without_restoring_a_free_hint() {
  val hint=SdaHint(policy);hint.consume();hint.advance(8f)
  val restored=SdaHint(policy);restored.restore(hint.state())
  assertFalse(restored.ready);assertEquals(.4f,restored.progress,0.001f)
  restored.advance(12f);assertTrue(restored.ready)
 }
 @Test fun corrupt_timer_rejected_before_mutating_live_controller() {
  val hint=SdaHint(policy);hint.consume();hint.advance(3f)
  val before=hint.state()
  try { hint.restore(before+mapOf("delay" to -1f));fail("negative delay") } catch(expected:IllegalArgumentException) {}
  assertEquals(before,hint.state())
 }
}

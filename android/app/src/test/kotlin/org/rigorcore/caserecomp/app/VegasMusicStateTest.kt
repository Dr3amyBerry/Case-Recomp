package org.rigorcore.caserecomp.app

import org.junit.Assert.*
import org.junit.Test

class VegasMusicStateTest {
 @Test fun investigation_tracks_change_only_when_context_changes() {
  val music=VegasMusicState()
  assertEquals("mainmenu",music.select(1))
  assertEquals("eyespy2",music.select(2))
  repeat(20) { assertEquals("eyespy2",music.select(2)) }
  assertEquals("bonusgame",music.select(3))
  assertEquals("bonusgame",music.select(3))
  assertEquals("eyespy1",music.select(2))
  assertEquals("bonusgame",music.select(3))
  assertEquals("eyespy2",music.select(2))
 }
 @Test fun missing_finale_track_is_not_replaced_with_an_invented_theme() {
  val music=VegasMusicState()
  assertNull(music.select(0))
  music.select(3)
  assertNull(music.select(4))
  assertNull(music.select(4))
 }
}

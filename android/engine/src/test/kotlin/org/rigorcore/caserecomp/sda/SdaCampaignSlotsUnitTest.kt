package org.rigorcore.caserecomp.sda

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SdaCampaignSlotsUnitTest {
 @get:Rule val folder=TemporaryFolder()
 @Test fun independent_profiles_and_titles_survive_new_storage_instance() {
  val store=SdaCampaignSlots(folder.root)
  assertTrue(store.save("edition_a","player_a","{\"phase\":\"SCENE\"}"))
  assertTrue(store.save("edition_a","player_b","{\"phase\":\"MAP\"}"))
  assertTrue(store.save("edition_b","player_a","{\"phase\":\"BONUS\"}"))
  val reopened=SdaCampaignSlots(folder.root)
  assertEquals("{\"phase\":\"SCENE\"}",reopened.load("edition_a","player_a"))
  assertEquals("{\"phase\":\"MAP\"}",reopened.load("edition_a","player_b"))
  assertEquals("{\"phase\":\"BONUS\"}",reopened.load("edition_b","player_a"))
  assertNull(reopened.load("edition_a","new_player"))
 }
 @Test fun unsafe_paths_and_invalid_json_cannot_replace_a_save() {
  val store=SdaCampaignSlots(folder.root)
  assertTrue(store.save("edition_a","player_a","{}"))
  assertFalse(store.save("edition_a","player_a","bad json"))
  assertEquals("{}",store.load("edition_a","player_a"))
  for(id in listOf("../outside","a/b","a\\b","", ".", "..")) {
   assertFalse(store.save(id,"player_a","{}"))
   assertFalse(store.save("edition_a",id,"{}"))
   assertNull(store.load("edition_a",id))
  }
 }
 @Test fun deleted_slot_loads_as_absent_and_other_players_keep_theirs() {
  val store=SdaCampaignSlots(folder.root)
  assertTrue(store.save("edition_a-unlimited","player_a","{}"))
  assertTrue(store.save("edition_a-unlimited","player_b","{}"))
  assertTrue(store.delete("edition_a-unlimited","player_a"))
  assertNull(store.load("edition_a-unlimited","player_a"))
  assertEquals("{}",store.load("edition_a-unlimited","player_b"))
  assertTrue(store.delete("edition_a-unlimited","player_a"))
  assertFalse(store.delete("edition_a-unlimited","../outside"))
 }
}

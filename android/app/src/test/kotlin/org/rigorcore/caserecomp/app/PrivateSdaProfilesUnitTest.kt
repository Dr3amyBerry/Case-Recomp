package org.rigorcore.caserecomp.app

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.rigorcore.caserecomp.sda.SdaProfile

class PrivateSdaProfilesUnitTest {
 @get:Rule val folder=TemporaryFolder()
 @Test fun switching_preserves_departing_save_and_starts_new_profile_without_overwriting_it() {
  val prefs=InMemorySdaPreferences()
  val repository=PrivateSdaRepository(folder.root,prefs)
  repository.configureCampaignSlots("edition_a")
  val original=repository.getActiveProfile()
  val next=SdaProfile("other_pi","Other Detective",avatar="female")
  val old="{\"phase\":\"SCENE\",\"points\":7654}"
  assertTrue(repository.saveCampaignCheckpoint(old))
  assertTrue(repository.switchCampaignProfile(next))
  assertEquals("",repository.loadCampaignCheckpoint())
  assertEquals(next,repository.getActiveProfile())
  val newer="{\"phase\":\"BONUS\",\"points\":1234}"
  assertTrue(repository.saveCampaignCheckpoint(newer))
  assertTrue(repository.switchCampaignProfile(original))
  assertEquals(old,repository.loadCampaignCheckpoint())
  val reopened=PrivateSdaRepository(folder.root,prefs)
  reopened.configureCampaignSlots("edition_a")
  assertTrue(reopened.switchCampaignProfile(next))
  assertEquals(newer,reopened.loadCampaignCheckpoint())
 }
 @Test fun owned_mirror_is_not_reused_for_a_different_title() {
  val prefs=InMemorySdaPreferences()
  val repository=PrivateSdaRepository(folder.root,prefs)
  repository.configureCampaignSlots("edition_a")
  assertTrue(repository.saveCampaignCheckpoint("{\"phase\":\"BONUS\"}"))
  val other=PrivateSdaRepository(folder.root,prefs)
  other.configureCampaignSlots("edition_b")
  assertNull(other.loadCampaignCheckpoint())
  assertTrue(other.saveCampaignCheckpoint("{\"phase\":\"MAP\"}"))
  assertEquals("{\"phase\":\"BONUS\"}",repository.loadCampaignCheckpoint())
 }
 @Test fun legacy_unowned_checkpoint_is_adopted_without_changing_its_contents() {
  val prefs=InMemorySdaPreferences()
  prefs.setString("active_campaign_checkpoint","{\"phase\":\"SCENE\"}")
  val repository=PrivateSdaRepository(folder.root,prefs)
  repository.configureCampaignSlots("edition_a")
  assertEquals("{\"phase\":\"SCENE\"}",repository.loadCampaignCheckpoint())
  assertTrue(repository.switchCampaignProfile(SdaProfile("new_pi","New")))
  assertTrue(repository.switchCampaignProfile(repository.profileStorage.getProfile("default_pi")!!))
  assertEquals("{\"phase\":\"SCENE\"}",repository.loadCampaignCheckpoint())
 }
}

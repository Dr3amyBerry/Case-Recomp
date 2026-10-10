package org.rigorcore.caserecomp.app
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.rigorcore.caserecomp.sda.SdaProfile
import java.io.File

class PrivateSdaProfileRemovalUnitTest {
 @get:Rule val folder=TemporaryFolder()
 @Test fun removing_active_player_archives_only_their_slots_and_restores_other_player() {
  val prefs=InMemorySdaPreferences();val repo=PrivateSdaRepository(folder.root,prefs)
  repo.configureCampaignSlots("edition_a")
  val first=repo.getActiveProfile()
  assertTrue(repo.saveCampaignCheckpoint("{\"points\":123}"))
  val second=SdaProfile("second_pi","Other")
  assertTrue(repo.switchCampaignProfile(second));assertTrue(repo.saveCampaignCheckpoint("{\"points\":456}"))
  val other=PrivateSdaRepository(folder.root,prefs).apply { configureCampaignSlots("edition_b") }
  assertTrue(other.saveCampaignCheckpoint("{\"points\":789}"))
  assertTrue(repo.removeProfile(second.id))
  assertEquals(first.id,repo.getActiveProfile().id)
  assertEquals("{\"points\":123}",repo.loadCampaignCheckpoint())
  assertNull(repo.profileStorage.getProfile(second.id))
  val archived=File(folder.root,"removed-profiles").walkTopDown().filter { it.isFile }.toList()
  assertEquals(3,archived.size)
  assertTrue(archived.any { it.readText()==second.toJson() })
  assertTrue(archived.any { it.readText()=="{\"points\":456}" })
  assertTrue(archived.any { it.readText()=="{\"points\":789}" })
 }
 @Test fun removing_last_player_bootstraps_fresh_profile_without_reusing_deleted_save() {
  val repo=PrivateSdaRepository(folder.root,InMemorySdaPreferences()).apply { configureCampaignSlots("edition_a") }
  val first=repo.getActiveProfile();assertTrue(repo.saveCampaignCheckpoint("{\"points\":123}"))
  assertTrue(repo.removeProfile(first.id))
  assertEquals(1,repo.profileStorage.listProfiles().size)
  assertEquals("",repo.loadCampaignCheckpoint())
  assertTrue(File(folder.root,"removed-profiles").walkTopDown().any { it.isFile && it.readText()=="{\"points\":123}" })
 }
 @Test fun failed_preference_commit_restores_original_profile_and_slots() {
  val memory=InMemorySdaPreferences();var reject=false
  val prefs=object:SdaPreferences {
   override fun getString(key:String,def:String?)=memory.getString(key,def)
   override fun setString(key:String,value:String)=memory.setString(key,value)
   override fun setStrings(values:Map<String,String>)=if(reject) false else memory.setStrings(values)
  }
  val repo=PrivateSdaRepository(folder.root,prefs).apply { configureCampaignSlots("edition_a") }
  val first=repo.getActiveProfile();assertTrue(repo.saveCampaignCheckpoint("{\"points\":123}"))
  reject=true
  assertFalse(repo.removeProfile(first.id))
  assertEquals(first,repo.getActiveProfile())
  assertEquals("{\"points\":123}",repo.loadCampaignCheckpoint())
  assertTrue(File(folder.root,"campaigns/edition_a/${first.id}.json").isFile)
 }
 @Test fun removing_inactive_player_leaves_active_checkpoint_and_identity_unchanged() {
  val repo=PrivateSdaRepository(folder.root,InMemorySdaPreferences()).apply { configureCampaignSlots("edition_a") }
  val active=repo.getActiveProfile();val checkpoint="{\"points\":123}"
  assertTrue(repo.saveCampaignCheckpoint(checkpoint))
  val inactive=SdaProfile("inactive_pi","Inactive")
  assertTrue(repo.profileStorage.saveProfile(inactive))
  assertTrue(org.rigorcore.caserecomp.sda.SdaCampaignSlots(File(folder.root,"campaigns")).save("edition_a",inactive.id,"{\"points\":456}"))
  assertTrue(repo.removeProfile(inactive.id))
  assertEquals(active,repo.getActiveProfile());assertEquals(checkpoint,repo.loadCampaignCheckpoint())
  assertNull(repo.profileStorage.getProfile(inactive.id))
 }
 @Test fun invalid_namespace_or_missing_player_does_not_remove_profile() {
  val repo=PrivateSdaRepository(folder.root,InMemorySdaPreferences())
  val active=repo.getActiveProfile()
  repo.configureCampaignSlots("../outside")
  assertFalse(repo.removeProfile(active.id));assertEquals(active,repo.getActiveProfile())
  repo.configureCampaignSlots("edition_a")
  assertFalse(repo.removeProfile("missing"));assertFalse(repo.removeProfile("../outside"))
  assertEquals(active,repo.getActiveProfile())
 }
}
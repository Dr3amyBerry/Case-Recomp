package org.rigorcore.caserecomp.sda

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SdaProfileSafetyUnitTest {
 @get:Rule val folder=TemporaryFolder()
 @Test fun unsafe_ids_cannot_read_write_or_delete_outside_profile_directory() {
  val root=folder.newFolder("profiles")
  val storage=SdaProfileStorage(root)
  val external=File(folder.root,"victim.json")
  val original=SdaProfile("victim","Existing").toJson()
  external.writeText(original)
  assertNull(storage.getProfile("../victim"))
  assertFalse(storage.saveProfile(SdaProfile("../victim","Replacement")))
  assertFalse(storage.deleteProfile("../victim"))
  assertEquals(original,external.readText())
 }
 @Test fun valid_replacement_preserves_all_metadata_and_leaves_no_staging_files() {
  val storage=SdaProfileStorage(folder.root)
  val original=SdaProfile("pi_existing","Ágata",createdAt=123,totalScore=900000,currentLevelIndex=24,completedCampaigns=2,avatar="original-avatar")
  assertTrue(storage.saveProfile(original))
  val update=original.copy(name="Ágata nueva")
  assertTrue(storage.saveProfile(update))
  assertEquals(update,storage.getProfile(original.id))
  assertEquals(listOf("pi_existing.json"),folder.root.listFiles()!!.map { it.name })
 }
 @Test fun mismatched_json_identity_is_not_returned_as_another_player() {
  val storage=SdaProfileStorage(folder.root)
  File(folder.root,"pi_a.json").writeText(SdaProfile("pi_b","Another player").toJson())
  assertNull(storage.getProfile("pi_a"))
  assertTrue(storage.listProfiles().isEmpty())
 }
}

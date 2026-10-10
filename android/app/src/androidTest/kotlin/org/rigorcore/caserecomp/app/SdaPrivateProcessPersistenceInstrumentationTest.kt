package org.rigorcore.caserecomp.app

import android.app.ActivityOptions
import android.content.Intent
import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.rigorcore.caserecomp.sda.*
import java.io.File

/** External prepare/force-stop/verify/cleanup; earned-checkpoint replay, not catalogue E2E. */
@RunWith(AndroidJUnit4::class)
class SdaPrivateProcessPersistenceInstrumentationTest {
 @Test fun saved_ui_session_survives_external_process_stop() {
  val instrumentation=InstrumentationRegistry.getInstrumentation()
  val context=instrumentation.targetContext
  val args=InstrumentationRegistry.getArguments()
  val stage=args.getString("processStage")
  assumeTrue(stage in setOf("prepare","verify","cleanup"))
  val prefs=context.getSharedPreferences("case-recomp-sda",0)
  val root=File(context.filesDir,"sda-process-qa").apply { mkdirs() }
  val pending=File(root,"pending.json")
  val expected=File(root,"expected.json")
  val dataRoot=File(context.filesDir,"private-sda")
  fun hash(file:File)=java.security.MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
  fun originalFiles():JSONObject {
   val hashes=JSONObject()
   for(name in listOf("profiles","campaigns")) {
    val directory=File(dataRoot,name)
    if(directory.exists()) directory.walkTopDown().filter { it.isFile }.forEach { hashes.put(it.relativeTo(dataRoot).invariantSeparatorsPath,hash(it)) }
   }
   return hashes
  }
  fun saveJournal(value:JSONObject) {
   java.io.FileOutputStream(pending).use { stream->stream.write(value.toString().toByteArray(Charsets.UTF_8));stream.fd.sync() }
  }
  val output=File(context.getExternalFilesDir(null),"process-persistence").apply { mkdirs() }
  if(stage=="cleanup") {
   if(!pending.isFile) return
   val metadata=JSONObject(pending.readText());val edit=prefs.edit().clear()
   val values=metadata.getJSONObject("preferences")
   for(key in values.keys()) {
    val node=values.getJSONObject(key)
    when(node.getString("type")) {
     "string"->edit.putString(key,node.getString("value"))
     "boolean"->edit.putBoolean(key,node.getBoolean("value"))
     "int"->edit.putInt(key,node.getInt("value"))
     "long"->edit.putLong(key,node.getLong("value"))
     "float"->edit.putFloat(key,node.getDouble("value").toFloat())
     "set"->{ val array=node.getJSONArray("value");edit.putStringSet(key,(0 until array.length()).map { array.getString(it) }.toSet()) }
     else->error("unknown preference type")
    }
   }
   assertTrue(edit.commit())
   val id=metadata.getString("profile");require(Regex("processqa-[0-9a-f-]+").matches(id))
   for(relative in listOf("profiles/$id.json","campaigns/vegas_heist/$id.json")) {
    val file=File(context.filesDir,"private-sda/$relative");if(file.exists()) assertTrue(file.delete())
   }
   val original=metadata.getJSONObject("files")
   val after=originalFiles()
   assertEquals(original.length(),after.length())
   for(key in original.keys()) assertEquals("original user file must be unchanged: $key",original.getString(key),after.getString(key))
   for(key in values.keys()) {
    val node=values.getJSONObject(key)
    assertTrue(prefs.contains(key))
    when(node.getString("type")) {
    "string"->assertEquals(node.getString("value"),prefs.getString(key,null))
    "boolean"->assertEquals(node.getBoolean("value"),prefs.getBoolean(key,false))
    "int"->assertEquals(node.getInt("value"),prefs.getInt(key,0))
    "long"->assertEquals(node.getLong("value"),prefs.getLong(key,0L))
    "float"->assertEquals(node.getDouble("value").toFloat(),prefs.getFloat(key,0f),0f)
    "set"->assertEquals(node.getJSONArray("value").toString(),org.json.JSONArray(prefs.getStringSet(key,null)!!.sorted()).toString())
   }
   }
   assertEquals(values.length(),prefs.all.size)
   File(output,"${metadata.getString("case")}-cleanup.json").writeText(JSONObject().put("originalFilesUnchanged",true).put("preferencesRestored",true).toString())
   if(expected.exists()) assertTrue(expected.delete())
   assertTrue(pending.delete());return
  }
  val path=args.getString("privateSdaPackage")!!;require(File(path).isFile)
  val display=args.getString("visualDisplayId")?.toInt() ?: 2;require(display==2)
  fun game(activity:SdaLauncherActivity)=SdaLauncherActivity::class.java.getDeclaredField("gameView").apply { isAccessible=true }.get(activity) as SdaGameView
  fun panel(activity:SdaLauncherActivity):SdaResourceMenuView {
   val dialog=SdaLauncherActivity::class.java.getDeclaredField("hostMenu").apply { isAccessible=true }.get(activity) as android.app.Dialog
   return dialog.window!!.decorView.findViewWithTag("sda-resource-menu")
  }
  fun click(view:android.view.View,x:Int,y:Int) {
   val scale=minOf(view.width/800f,view.height/600f);assertTrue(scale>0)
   for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)) {
    val event=MotionEvent.obtain(SystemClock.uptimeMillis(),SystemClock.uptimeMillis(),action,(view.width-800*scale)/2+(x+.5f)*scale,(view.height-600*scale)/2+(y+.5f)*scale,0)
    try { val accepted=view.dispatchTouchEvent(event);if(action==MotionEvent.ACTION_DOWN) assertTrue(accepted) } finally { event.recycle() }
   }
  }
  lateinit var metadata:JSONObject
  if(stage=="prepare") {
   check(!pending.exists()) { "cleanup pending QA session first; never overwrite backup" }
   val earned=File(args.getString("earnedProcessCheckpoint")!!).readText();SdaCampaignState.fromJson(earned)
   val values=JSONObject()
   prefs.all.forEach { (key,value)->
    val type=when(value) { is String->"string";is Boolean->"boolean";is Int->"int";is Long->"long";is Float->"float";is Set<*>->"set";else->error("unsupported preference") }
    values.put(key,JSONObject().put("type",type).put("value",JSONObject.wrap(if(value is Set<*>) value.map { it as String }.sorted() else value)))
   }
   val id="processqa-"+java.util.UUID.randomUUID()
   metadata=JSONObject().put("profile",id).put("preferences",values).put("preparePid",android.os.Process.myPid()).put("case",args.getString("processCase") ?: "map").put("files",originalFiles())
   saveJournal(metadata)
   val repository=PrivateSdaRepository(context)
   assertTrue(repository.profileStorage.saveProfile(SdaProfile(id,"Process persistence QA")))
   assertTrue(prefs.edit().putString("active_profile_id",id).putString("active_campaign_checkpoint",earned).putString("campaign_checkpoint_namespace","vegas_heist").remove("session-checkpoint").commit())
   repository.configureCampaignSlots("vegas_heist");assertTrue(repository.saveCampaignCheckpoint(earned))
  } else {
   check(pending.isFile);metadata=JSONObject(pending.readText())
   assertNotEquals("fresh process required",metadata.getInt("preparePid"),android.os.Process.myPid())
  }
  val intent=Intent(context,SdaLauncherActivity::class.java).putExtra(SdaLauncherActivity.EXTRA_PACKAGE_PATH,path)
  val scenario=ActivityScenario.launch<SdaLauncherActivity>(intent,ActivityOptions.makeBasic().setLaunchDisplayId(display).toBundle())
  try {
   instrumentation.waitForIdleSync();SystemClock.sleep(250)
   scenario.onActivity { activity->
    assertEquals(2,activity.display!!.displayId)
    val view=game(activity);val camp=view.campaign!!
    assertEquals(metadata.getString("profile"),PrivateSdaRepository(context).getActiveProfile().id)
    assertEquals("mainmenuunderlay",panel(activity).screenId)
    if(stage=="verify") assertEquals("all persisted gameplay fields",expected.readText(),camp.snapshot().toJson())
    else if(metadata.getString("case") in setOf("scene","collectors")) {
     val menu=panel(activity);val r=menu.buttonBounds(menu.buttons.single { it.number("value")==299 });click(menu,r.centerX(),r.centerY())
     assertEquals(SdaCampaignPhase.MAP,camp.phase)
    }
   }
   if(stage=="prepare" && metadata.getString("case") in setOf("scene","collectors")) {
    instrumentation.waitForIdleSync();SystemClock.sleep(250)
    scenario.onActivity { activity ->
     val view=game(activity);val camp=view.campaign!!
     val profile=view.visuals!!
     val card=(0 until 600 step 10).asSequence().flatMap { y->(144 until 800 step 10).asSequence().map { x->x to y } }.first { (x,y)->profile.sceneAt(camp,x,y)==camp.currentLevel.scenes.first() }
     click(view,card.first,card.second)
    }
    instrumentation.waitForIdleSync();SystemClock.sleep(100)
    scenario.onActivity { activity->
     val view=game(activity);val camp=view.campaign!!;val scene=camp.currentScene!!
     val target=scene.targets.map { scene.objects.getValue(it) }.first { obj->(obj.y until obj.y+obj.image.height).any { y->y in 0 until 550 && (obj.x until obj.x+obj.image.width).any { x->x in 144 until 800 && obj.hit(x,y) } } }
     val pixel=(target.y until target.y+target.image.height).asSequence().flatMap { y->(target.x until target.x+target.image.width).asSequence().map { x->x to y } }.first { (x,y)->x in 144 until 800 && y in 0 until 550 && target.hit(x,y) }
     if(metadata.getString("case")=="collectors") {
      val before=camp.points;val completed=camp.completedObjects
      for(item in scene.collectibles) {
       assertTrue(camp.collectibleAvailable(item))
       val image=item.sprite.image
       val point=(0 until image.width*image.height).asSequence().map { item.sprite.x+it%image.width to item.sprite.y+it/image.width }.first { (x,y)->
        x in 174 until 800 && y in 0 until 600 && item.sprite.hit(x,y)
       }
       click(view,point.first,point.second)
       assertEquals(1,camp.collectedCount(item.definition.kind))
       assertFalse(camp.collectibleAvailable(item))
      }
      assertEquals(setOf("key","chip"),scene.collectibles.map { it.definition.kind }.toSet())
      assertEquals(before,camp.points);assertEquals(completed,camp.completedObjects)
     } else {
      val before=camp.points;click(view,pixel.first,pixel.second)
      var optionalInputs=0
      while(!target.found && optionalInputs++<scene.collectibles.size) click(view,pixel.first,pixel.second)
      assertTrue(camp.points>before)
     }
     val menu=view.visuals!!.menuRect(camp)!!;click(view,menu.centerX(),menu.centerY())
    }
   }
   instrumentation.waitForIdleSync()
   scenario.onActivity { activity->
    val state=game(activity).campaign!!.snapshot().toJson()
    val repository=PrivateSdaRepository(context).apply { configureCampaignSlots("vegas_heist") }
    assertEquals(state,repository.loadCampaignCheckpoint())
    assertEquals(state,File(context.filesDir,"private-sda/campaigns/vegas_heist/${metadata.getString("profile")}.json").readText())
    if(stage=="prepare") {
     // Never rewrite the immutable original-data backup after changing preferences.
     java.io.FileOutputStream(expected).use { stream->stream.write(state.toByteArray(Charsets.UTF_8));stream.fd.sync() }
    }
    val evidence=JSONObject().put("stage",stage).put("pid",android.os.Process.myPid()).put("previousPid",metadata.getInt("preparePid")).put("case",metadata.getString("case")).put("state",JSONObject(state))
    File(output,"${metadata.getString("case")}-${stage}.json").writeText(evidence.toString(2))
   }
  } finally { scenario.close() }
 }
}

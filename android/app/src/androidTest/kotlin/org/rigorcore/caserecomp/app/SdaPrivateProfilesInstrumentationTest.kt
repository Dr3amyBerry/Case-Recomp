package org.rigorcore.caserecomp.app

import android.app.ActivityOptions
import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.inputmethod.EditorInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.rigorcore.caserecomp.sda.*
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Original player menus, Android touch/IME inputs and independent saved campaigns. */
@RunWith(AndroidJUnit4::class)
class SdaPrivateProfilesInstrumentationTest {
 private fun profileMenu(view:SdaGameView,camp:SdaCampaign)=view.visuals!!.menuRect(camp)!!
 @Test fun original_profiles_create_validate_switch_and_restore_independent_campaigns() {
  val instrumentation=InstrumentationRegistry.getInstrumentation()
  val context=instrumentation.targetContext
  val args=InstrumentationRegistry.getArguments()
  val path=args.getString("privateSdaPackage")
  assumeTrue(path!=null && File(path).isFile)
  val prefs=context.getSharedPreferences("case-recomp-sda",0)
  val before=prefs.all.toMap()
  val roots=listOf(File(context.filesDir,"private-sda/profiles"),File(context.filesDir,"private-sda/campaigns"),File(context.filesDir,"private-sda/removed-profiles"))
  val files=roots.flatMap { root -> if(root.exists()) root.walkTopDown().filter { it.isFile }.map { it to it.readBytes() }.toList() else emptyList() }.toMap()
  val intent=Intent(context,SdaLauncherActivity::class.java).putExtra(SdaLauncherActivity.EXTRA_PACKAGE_PATH,path)
  fun launch()=ActivityScenario.launch<SdaLauncherActivity>(intent,ActivityOptions.makeBasic().setLaunchDisplayId(args.getString("visualDisplayId")?.toInt() ?: 0).toBundle())
  fun game(activity:SdaLauncherActivity)=SdaLauncherActivity::class.java.getDeclaredField("gameView").apply { isAccessible=true }.get(activity) as SdaGameView
  fun panel(activity:SdaLauncherActivity):SdaResourceMenuView {
   val dialog=SdaLauncherActivity::class.java.getDeclaredField("hostMenu").apply { isAccessible=true }.get(activity) as android.app.Dialog
   return dialog.window!!.decorView.findViewWithTag("sda-resource-menu")
  }
  fun click(view:android.view.View,x:Int,y:Int) {
   val scale=minOf(view.width/800f,view.height/600f)
   assertTrue(scale>0)
   for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)) {
    val event=MotionEvent.obtain(SystemClock.uptimeMillis(),SystemClock.uptimeMillis(),action,(view.width-800*scale)/2+(x+.5f)*scale,(view.height-600*scale)/2+(y+.5f)*scale,0)
    try { val handled=view.dispatchTouchEvent(event);if(action==MotionEvent.ACTION_DOWN) assertTrue(handled) } finally { event.recycle() }
   }
  }
  fun settle() { instrumentation.waitForIdleSync();SystemClock.sleep(250) }
  fun button(scenario:ActivityScenario<SdaLauncherActivity>,value:Int,screen:String) {
   settle();scenario.onActivity { activity ->
    val view=panel(activity);assertEquals(screen,view.screenId)
    val node=view.buttons.single { it.number("value")==value }
    assertNotEquals("true",node.attributes["disabled"])
    val rect=view.buttonBounds(node);click(view,rect.centerX(),rect.centerY())
   };settle()
  }
  fun row(scenario:ActivityScenario<SdaLauncherActivity>,id:String) {
   repeat(20) {
    var found=false
    scenario.onActivity { activity ->
     val view=panel(activity);val rect=view.listRowBounds(id)
     if(rect!=null) { click(view,rect.centerX(),rect.centerY());found=true }
     else { val down=view.buttonBounds(view.buttons.single { it.number("value")==62 });click(view,down.centerX(),down.centerY()) }
    }
    if(found) { settle();return }
   }
   fail("profile row unreachable through original arrow controls: $id")
  }
  fun type(scenario:ActivityScenario<SdaLauncherActivity>,text:String) {
   scenario.onActivity { activity ->
    val view=panel(activity);assertEquals("newplayer",view.screenId)
    val rect=view.editBounds()!!;click(view,rect.centerX(),rect.centerY())
    val input=view.onCreateInputConnection(EditorInfo())!!
    assertTrue(input.deleteSurroundingText(100,0));assertTrue(input.commitText(text,1));assertTrue(input.performEditorAction(EditorInfo.IME_ACTION_DONE))
    assertEquals(text,view.editText)
    assertFalse(view.labels.any { it.attributes["caption"] in setOf("@ID_PLAYER_MSG3","@ID_PLAYER_MSG4") })
   };settle()
  }
  fun capture(scenario:ActivityScenario<SdaLauncherActivity>,name:String) {
   settle();lateinit var window:android.view.Window;lateinit var bitmap:Bitmap
   scenario.onActivity { activity ->
    window=(SdaLauncherActivity::class.java.getDeclaredField("hostMenu").apply { isAccessible=true }.get(activity) as android.app.Dialog).window!!
    bitmap=Bitmap.createBitmap(window.decorView.width,window.decorView.height,Bitmap.Config.ARGB_8888)
   }
   val copied=CountDownLatch(1);var result=-1
   PixelCopy.request(window,bitmap,{ result=it;copied.countDown() },android.os.Handler(android.os.Looper.getMainLooper()))
   assertTrue(copied.await(10,TimeUnit.SECONDS));assertEquals(PixelCopy.SUCCESS,result)
   File(context.getExternalFilesDir(null),name).outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) };bitmap.recycle()
  }
  var originalId="";var originalName="";var originalPoints=0;var originalPhase=""
  var createdId="";var createdSeed=0L;var createdPoints=0;var foundIds=emptyList<String>()
  val newName="AndroidQA"+(SystemClock.uptimeMillis()%100000)
  try {
   launch().use { scenario ->
    settle()
    originalId=PrivateSdaRepository(context).getActiveProfile().id
    originalName=PrivateSdaRepository(context).getActiveProfile().name
    scenario.onActivity { originalPoints=game(it).campaign!!.points;originalPhase=game(it).campaign!!.phase.name }
    button(scenario,6,"mainmenuunderlay")
    capture(scenario,"profiles-select-original.png")
    row(scenario,"")
    button(scenario,1,"newplayer") // Blank name is rejected in the original dialog.
    scenario.onActivity { assertEquals("newplayer",panel(it).screenId);assertTrue(panel(it).labels.any { node -> node.attributes["caption"]=="@ID_PLAYER_MSG4" }) }
    capture(scenario,"profiles-empty-name-original.png")
    type(scenario,newName)
    scenario.onActivity { activity ->
     val view=panel(activity);val female=view.checkboxes.single { it.attributes["id"]=="femalecheck" }
     val rect=view.checkboxBounds(female);click(view,rect.centerX(),rect.centerY())
     assertTrue(view.checkboxValue(female));assertEquals(1,view.checkboxes.count(view::checkboxValue))
    }
    capture(scenario,"profiles-create-original.png")
    button(scenario,1,"newplayer")
    scenario.onActivity { activity ->
     assertEquals("mainmenuunderlay",panel(activity).screenId)
     val active=PrivateSdaRepository(context).getActiveProfile();createdId=active.id
     assertEquals(newName,active.name);assertEquals("female",active.avatar)
     val camp=game(activity).campaign!!;assertEquals(SdaCampaignPhase.MAP,camp.phase);assertEquals(0,camp.points);assertEquals(1,camp.currentLevel.clue);createdSeed=camp.seed
    }
    capture(scenario,"profiles-new-mainmenu.png")
    button(scenario,299,"mainmenuunderlay")
    scenario.onActivity { activity ->
     val view=game(activity);val camp=view.campaign!!;val profile=view.visuals!!
     val card=(0 until 600 step 10).asSequence().flatMap { y -> (144 until 800 step 10).asSequence().map { x -> x to y } }.first { (x,y) -> profile.sceneAt(camp,x,y)==camp.currentLevel.scenes.first() }
     click(view,card.first,card.second);assertEquals(SdaCampaignPhase.SCENE,camp.phase)
    }
    settle()
    scenario.onActivity { activity ->
     val view=game(activity);val camp=view.campaign!!;val scene=camp.currentScene!!
     val target=scene.targets.map { scene.objects.getValue(it) }.first { obj -> (obj.x until obj.x+obj.image.width).any { x -> x>=144 && x<800 && (obj.y until obj.y+obj.image.height).any { y -> y in 0 until 550 && obj.hit(x,y) } } }
     val pixel=(target.y until target.y+target.image.height).asSequence().flatMap { y -> (target.x until target.x+target.image.width).asSequence().map { x -> x to y } }.first { (x,y) -> x in 144 until 800 && y in 0 until 550 && target.hit(x,y) }
     click(view,pixel.first,pixel.second)
     assertTrue("actual Android object input must earn points",camp.points>0)
     foundIds=scene.objects.filterValues { it.found }.keys.toList();assertTrue(foundIds.isNotEmpty());createdPoints=camp.points
     val menu=profileMenu(view,camp);click(view,menu.centerX(),menu.centerY())
    }
    button(scenario,80,"menudlg2")
    button(scenario,6,"mainmenuunderlay")
    row(scenario,"");type(scenario,originalName)
    button(scenario,1,"newplayer") // Duplicate rejected; no extra profile/save created.
    scenario.onActivity { assertEquals("newplayer",panel(it).screenId);assertTrue(panel(it).labels.any { node -> node.attributes["caption"]=="@ID_PLAYER_MSG3" }) }
    button(scenario,9,"newplayer")
    row(scenario,originalId);button(scenario,3,"selectplayer")
    scenario.onActivity { activity ->
     assertEquals(originalId,PrivateSdaRepository(context).getActiveProfile().id)
     val camp=game(activity).campaign!!;assertEquals(originalPoints,camp.points);assertEquals(originalPhase,camp.phase.name)
    }
    button(scenario,6,"mainmenuunderlay")
    row(scenario,createdId);button(scenario,3,"selectplayer")
    scenario.onActivity { activity ->
     val camp=game(activity).campaign!!;assertEquals(createdSeed,camp.seed);assertEquals(createdPoints,camp.points);assertEquals(SdaCampaignPhase.SCENE,camp.phase)
     assertTrue(foundIds.all { camp.currentScene!!.objects.getValue(it).found })
    }
   }
   // Activity closed and reopened; this is not Android process death.
   launch().use { scenario ->
    settle();scenario.onActivity { activity ->
     assertEquals(createdId,PrivateSdaRepository(context).getActiveProfile().id)
     assertEquals(createdSeed,game(activity).campaign!!.seed)
     assertEquals(createdPoints,game(activity).campaign!!.points)
     assertTrue(foundIds.all { game(activity).campaign!!.currentScene!!.objects.getValue(it).found })
     assertEquals("mainmenuunderlay",panel(activity).screenId)
    }
    button(scenario,6,"mainmenuunderlay")
    row(scenario,createdId);button(scenario,2,"selectplayer")
    scenario.onActivity { activity ->
     assertEquals("deletedlg",panel(activity).screenId)
     assertTrue(panel(activity).labels.any { it.attributes["caption"]=="\"$newName\"" })
    }
    button(scenario,8,"deletedlg")
    assertNotNull(PrivateSdaRepository(context).profileStorage.getProfile(createdId))
    button(scenario,2,"selectplayer")
    // A failed checkpoint write must preserve the live campaign and reject deletion.
    val repositoryField=SdaLauncherActivity::class.java.getDeclaredField("repository").apply { isAccessible=true }
    lateinit var originalRepository:PrivateSdaRepository
    lateinit var liveCampaign:SdaCampaign
    scenario.onActivity { activity ->
     originalRepository=repositoryField.get(activity) as PrivateSdaRepository
     liveCampaign=game(activity).campaign!!
     val failing=PrivateSdaRepository(File(context.filesDir,"private-sda"),object:SdaPreferences {
      override fun getString(key:String,def:String?)=prefs.getString(key,def)
      override fun setString(key:String,value:String)=false
      override fun setStrings(values:Map<String,String>)=if("active_profile_id" in values) AndroidSdaPreferences(prefs).setStrings(values) else false
     })
     val namespaceField=PrivateSdaRepository::class.java.getDeclaredField("campaignNamespace").apply { isAccessible=true }
     failing.configureCampaignSlots(namespaceField.get(originalRepository) as String)
     repositoryField.set(activity,failing)
    }
    try {
     button(scenario,5,"deletedlg")
     scenario.onActivity { activity ->
      assertEquals("deletedlg",panel(activity).screenId)
      assertSame(liveCampaign,game(activity).campaign)
      assertNotNull(originalRepository.profileStorage.getProfile(createdId))
      assertEquals(createdId,originalRepository.getActiveProfile().id)
     }
    } finally { scenario.onActivity { repositoryField.set(it,originalRepository) } }
    button(scenario,5,"deletedlg")
    scenario.onActivity { activity ->
     assertEquals("selectplayer",panel(activity).screenId)
     assertNull(PrivateSdaRepository(context).profileStorage.getProfile(createdId))
     assertNotEquals(createdId,PrivateSdaRepository(context).getActiveProfile().id)
     assertTrue(File(context.filesDir,"private-sda/removed-profiles").walkTopDown().any { it.isFile && it.name=="$createdId.json" })
    }
    row(scenario,originalId);button(scenario,3,"selectplayer")
    scenario.onActivity { activity ->
     assertEquals(originalId,PrivateSdaRepository(context).getActiveProfile().id)
     assertEquals(originalPoints,game(activity).campaign!!.points)
     assertEquals(originalPhase,game(activity).campaign!!.phase.name)
    }
   }
  } finally {
   // Preserve all user profiles/slots, removing only files introduced by this test.
   for(root in roots) if(root.exists()) for(file in root.walkTopDown().filter { it.isFile }.toList()) if(file !in files) assertTrue(file.delete())
   files.forEach { (file,bytes) -> file.parentFile.mkdirs();file.writeBytes(bytes) }
   val edit=prefs.edit().clear()
   for((key,value) in before) when(value) {
    is String -> edit.putString(key,value);is Int -> edit.putInt(key,value);is Long -> edit.putLong(key,value);is Boolean -> edit.putBoolean(key,value);is Float -> edit.putFloat(key,value)
   }
   assertTrue(edit.commit())
  }
 }
}

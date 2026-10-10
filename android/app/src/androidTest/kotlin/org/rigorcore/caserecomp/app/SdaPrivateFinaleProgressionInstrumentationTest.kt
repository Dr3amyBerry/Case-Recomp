package org.rigorcore.caserecomp.app

import android.app.ActivityOptions
import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.MotionEvent
import android.view.PixelCopy
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

/** Android ending replay from an explicitly supplied earned checkpoint; not catalogue E2E. */
@RunWith(AndroidJUnit4::class)
class SdaPrivateFinaleProgressionInstrumentationTest {
 @Test fun earned_first_phase_dialog_to_eight_second_phase_placements_and_saved_reopen() {
  val instrumentation=InstrumentationRegistry.getInstrumentation()
  val context=instrumentation.targetContext
  val args=InstrumentationRegistry.getArguments()
  val path=args.getString("privateSdaPackage")
  val earned=args.getString("earnedRiddleCheckpoint")
  assumeTrue(path!=null && earned!=null && File(path).isFile && File(earned).isFile)
  val display=args.getString("visualDisplayId")?.toInt() ?: 0
  val prefs=context.getSharedPreferences("case-recomp-sda",0)
  val keys=listOf("active_campaign_checkpoint","session-checkpoint","active-package-sha256","active_profile_id","campaign_checkpoint_namespace")
  val before=keys.associateWith { prefs.getString(it,null) }
  val settings=context.getSharedPreferences("case-recomp-vegas-options",0)
  val optionsBefore=settings.all.toMap()
  val qaId="endingqa-"+java.util.UUID.randomUUID()
  val repository=PrivateSdaRepository(context)
  val qaProfile=File(context.filesDir,"private-sda/profiles/$qaId.json")
  val qaSlot=File(context.filesDir,"private-sda/campaigns/vegas_heist/$qaId.json")
  check(!qaProfile.exists() && !qaSlot.exists())
  val intent=Intent(context,SdaLauncherActivity::class.java).putExtra(SdaLauncherActivity.EXTRA_PACKAGE_PATH,path)
  fun launch()=ActivityScenario.launch<SdaLauncherActivity>(intent,ActivityOptions.makeBasic().setLaunchDisplayId(display).toBundle())
  fun game(activity:SdaLauncherActivity)=SdaLauncherActivity::class.java.getDeclaredField("gameView").apply { isAccessible=true }.get(activity) as SdaGameView
  fun panel(activity:SdaLauncherActivity):SdaResourceMenuView {
   val dialog=SdaLauncherActivity::class.java.getDeclaredField("hostMenu").apply { isAccessible=true }.get(activity) as android.app.Dialog
   return dialog.window!!.decorView.findViewWithTag("sda-resource-menu")
  }
  fun click(view:android.view.View,x:Int,y:Int) {
   val scale=minOf(view.width/800f,view.height/600f)
   assertTrue(scale>0)
   for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)) {
    val event=MotionEvent.obtain(0,SystemClock.uptimeMillis(),action,(view.width-800*scale)/2+(x+.5f)*scale,(view.height-600*scale)/2+(y+.5f)*scale,0)
    try { if(action==MotionEvent.ACTION_DOWN) assertTrue(view.dispatchTouchEvent(event)) else view.dispatchTouchEvent(event) } finally { event.recycle() }
   }
  }
  fun confirm(scenario:ActivityScenario<SdaLauncherActivity>,value:Int,expectedId:String?=null) {
   instrumentation.waitForIdleSync()
   scenario.onActivity { activity ->
    val view=panel(activity);if(expectedId!=null) assertEquals(expectedId,view.screenId)
    val rect=view.buttonBounds(view.buttons.single { it.number("value")==value });click(view,rect.centerX(),rect.centerY())
   }
   instrumentation.waitForIdleSync();SystemClock.sleep(100)
  }
  fun capture(scenario:ActivityScenario<SdaLauncherActivity>,name:String,dialog:Boolean=false) {
   instrumentation.waitForIdleSync();SystemClock.sleep(100)
   lateinit var window:android.view.Window;lateinit var bitmap:Bitmap
   scenario.onActivity { activity ->
    window=if(dialog) (SdaLauncherActivity::class.java.getDeclaredField("hostMenu").apply { isAccessible=true }.get(activity) as android.app.Dialog).window!! else activity.window
    bitmap=Bitmap.createBitmap(window.decorView.width,window.decorView.height,Bitmap.Config.ARGB_8888)
   }
   val copied=CountDownLatch(1);var result=-1
   PixelCopy.request(window,bitmap,{ result=it;copied.countDown() },android.os.Handler(android.os.Looper.getMainLooper()))
   assertTrue(copied.await(10,TimeUnit.SECONDS));assertEquals(PixelCopy.SUCCESS,result)
   File(context.getExternalFilesDir(null),name).outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) };bitmap.recycle()
  }
  fun memory(scenario:ActivityScenario<SdaLauncherActivity>,name:String) {
   scenario.onActivity { activity ->
    val shown=game(activity)
    val sources=when(val riddle=shown.campaign!!.bonusGame) {
     is SdaPlacementRiddleGame -> (riddle.images.values+riddle.trayImages.values+riddle.placedImages.values+riddle.arrowImages.values+listOfNotNull(riddle.background,riddle.captionPaper))
     is SdaInteractiveRiddleGame -> riddle.controller.items.definitions.flatMap { it.images }.mapNotNull { it.pixels }
     else -> emptyList()
    }.mapNotNull { it.nativeImage as? Bitmap }.distinct()
    val profile=shown.visuals as VegasVisualProfile
    val ui=VegasVisualProfile::class.java.getDeclaredField("ui").apply { isAccessible=true }.get(profile) as SdaResourceCanvas
    val info=android.os.Debug.MemoryInfo();android.os.Debug.getMemoryInfo(info)
    File(context.getExternalFilesDir(null),"finale-second-memory.tsv").appendText("$name\t${info.totalPss}\t${sources.sumOf { it.allocationByteCount.toLong() }}\t${ui.bitmapBytes}\n")
   }
  }
  fun select(scenario:ActivityScenario<SdaLauncherActivity>,id:String) {
   var selected=false
   repeat(30) {
    if(!selected) scenario.onActivity { activity ->
     val view=game(activity);val riddle=view.campaign!!.bonusGame as SdaPlacementRiddleGame
     val cell=riddle.interaction.cells().firstOrNull { it.id==id }
     if(cell!=null) { click(view,cell.x+1,cell.y+1);assertEquals(id,riddle.interaction.board.selected);selected=true }
     else {
      val key=if(riddle.interaction.board.available.indexOf(id)<riddle.interaction.firstVisible) "up" else "down"
      val arrow=riddle.arrowRect(key)!!;click(view,arrow.x+1,arrow.y+1)
     }
    }
    instrumentation.waitForIdleSync()
   }
   assertTrue("piece not reachable through Android arrows",selected)
  }
  var originalPoints=0
  var heldId:String?=null
  try {
   assertTrue(repository.profileStorage.saveProfile(SdaProfile(qaId,"Ending replay QA")))
   assertTrue(prefs.edit().putString("active_profile_id",qaId).putString("campaign_checkpoint_namespace","vegas_heist").putString("active_campaign_checkpoint",File(earned!!).readText()).remove("session-checkpoint").commit())
   launch().use { scenario ->
    confirm(scenario,299)
    scenario.onActivity { activity ->
     val camp=game(activity).campaign!!;assertEquals(SdaCampaignPhase.FINALE_1,camp.phase)
     assertEquals(25,camp.currentLevel.clue);assertEquals(0,(camp.bonusGame as SdaFirstRiddleGame).interaction.board.placed.size)
     originalPoints=camp.points
    }
    capture(scenario,"finale-first-entry-real.png")
    val pieces=mutableListOf<SdaRiddlePiece>()
    scenario.onActivity { pieces.addAll((game(it).campaign!!.bonusGame as SdaFirstRiddleGame).definition.pieces.filter { it.hasTarget }.sortedBy { it.placeOrder }) }
    for(piece in pieces) {
     select(scenario,piece.id)
     scenario.onActivity { activity ->
      val view=game(activity);val first=view.campaign!!.bonusGame as SdaFirstRiddleGame
      click(view,first.definition.backgroundX+piece.hotspotX+1,first.backgroundY+piece.hotspotY+1)
      assertTrue(piece.id in first.interaction.board.placed)
     }
     instrumentation.waitForIdleSync()
    }
    capture(scenario,"finale-first-completion-dialog.png",true)
    confirm(scenario,1015,"riddlesdialogcompletecontainer")
    capture(scenario,"finale-second-start-dialog.png",true)
    scenario.onActivity { assertEquals(SdaCampaignPhase.FINALE_2,game(it).campaign!!.phase);assertFalse((game(it).campaign!!.bonusGame as SdaSecondRiddleGame).started) }
    confirm(scenario,1016,"riddlesdialogcontainer2")
    capture(scenario,"finale-second-entry-real.png")
    File(context.getExternalFilesDir(null),"finale-second-memory.tsv").writeText("state\tprocess_pss_kib\triddle_bitmap_bytes\tui_cache_bytes\n")
    memory(scenario,"second-entry")
    scenario.onActivity { activity -> heldId=(game(activity).campaign!!.bonusGame as SdaSecondRiddleGame).interaction.cells().first().id }
    select(scenario,heldId!!)
    scenario.onActivity { File(context.getExternalFilesDir(null),"finale-second-earned-held.json").writeText(game(it).campaign!!.snapshot().toJson()) }
    capture(scenario,"finale-second-held-real.png")
   }
   launch().use { scenario ->
    confirm(scenario,299)
    scenario.onActivity { activity ->
     val view=game(activity);val second=view.campaign!!.bonusGame as SdaSecondRiddleGame
     assertEquals(heldId,second.interaction.board.selected);assertTrue(second.started)
     val id=heldId!!;val d=second.definition.destinations.getValue(id);val image=second.images.getValue(id)
     click(view,144+d.x.toInt()+image.width/2,d.y.toInt()+image.height/2)
     assertTrue(id in second.interaction.board.placed)
    }
    val ids=mutableListOf<String>()
    scenario.onActivity { ids.addAll((game(it).campaign!!.bonusGame as SdaSecondRiddleGame).interaction.board.available) }
    for(id in ids) {
     select(scenario,id)
     scenario.onActivity { activity ->
      val view=game(activity);val second=view.campaign!!.bonusGame as SdaSecondRiddleGame
      val d=second.definition.destinations.getValue(id);val image=second.images.getValue(id)
      click(view,144+d.x.toInt()+image.width/2,d.y.toInt()+image.height/2)
      assertTrue(id in second.interaction.board.placed)
     }
     instrumentation.waitForIdleSync()
    }
    capture(scenario,"finale-second-completion-dialog.png",true)
    scenario.onActivity { activity ->
     val camp=game(activity).campaign!!;val second=camp.bonusGame as SdaSecondRiddleGame
     assertEquals(8,second.interaction.board.placed.size);assertTrue(second.isSolved)
     assertEquals(originalPoints,camp.points);assertEquals(SdaCampaignPhase.FINALE_2,camp.phase)
     val view=panel(activity);assertEquals("riddlesdialogcompletecontainer2",view.screenId)
     assertFalse(view.buttons.single().attributes["disabled"]=="true")
     // Capture the underlying placement surface without the modal window layered above it.
     val dialog=SdaLauncherActivity::class.java.getDeclaredField("hostMenu").apply { isAccessible=true }.get(activity) as android.app.Dialog
     dialog.dismiss()
    }
    capture(scenario,"finale-second-placed-real.png")
    memory(scenario,"second-eight-placed")
    scenario.onActivity { activity ->
     val second=game(activity).campaign!!.bonusGame as SdaSecondRiddleGame
     val screenshot=android.graphics.BitmapFactory.decodeFile(File(context.getExternalFilesDir(null),"finale-second-placed-real.png").absolutePath)
     val scale=minOf(screenshot.width/800f,screenshot.height/600f)
     val offsetX=(screenshot.width-800*scale)/2;val offsetY=(screenshot.height-600*scale)/2
     for(spec in listOf(listOf("finale2hourglass",142,186,134),listOf("finale2slotarm",0,710,0))) {
      val id=spec[0] as String;val sourceX=spec[1] as Int;val originX=spec[2] as Int;val originY=spec[3] as Int
      val image=second.placedImages.getValue(id);var compared=0
      for(y in 10 until 95 step 7) for(x in 10 until 80 step 7) {
       val px=(offsetX+(originX+x+.5f)*scale).toInt();val py=(offsetY+(originY+y+.5f)*scale).toInt()
       // Align original texels to the actual device-pixel center before comparison.
       val sx=sourceX+(px+.5f-offsetX)/scale-originX-.5f;val sy=(py+.5f-offsetY)/scale-originY-.5f
       val ix=kotlin.math.floor(sx).toInt();val iy=kotlin.math.floor(sy).toInt()
       val texels=listOf(ix to iy,(ix+1) to iy,ix to (iy+1),(ix+1) to (iy+1))
       if(texels.any { (tx,ty) -> image.getAlpha(tx,ty)<255 }) continue
       val fx=sx-ix;val fy=sy-iy;val weights=listOf((1-fx)*(1-fy),fx*(1-fy),(1-fx)*fy,fx*fy)
       val actual=screenshot.getPixel(px,py)
       assertTrue("aligned native atlas frame differs for $id at $x,$y",listOf(16,8,0).all { shift ->
        val expected=texels.indices.sumOf { index -> (((image.getArgb(texels[index].first,texels[index].second) ushr shift) and 255)*weights[index]).toDouble() }
        kotlin.math.abs(((actual ushr shift) and 255)-expected)<=26
       })
       compared++
      }
      assertTrue("opaque native frame samples missing for $id",compared>=6)
     }
     screenshot.recycle()
    }

    scenario.onActivity { activity ->
     val view=game(activity);val rect=view.visuals!!.menuRect(view.campaign!!)!!;click(view,rect.centerX(),rect.centerY())
    }
    confirm(scenario,215) // Actual native menu RESUME reopens the completed-phase dialog.
    confirm(scenario,1017,"riddlesdialogcompletecontainer2")
    capture(scenario,"finale-third-start-dialog.png",true)
    confirm(scenario,1018,"riddlesdialogcontainer3")
    fun third(activity:SdaLauncherActivity)=(game(activity).campaign!!.bonusGame as SdaInteractiveRiddleGame).controller as org.rigorcore.caserecomp.games.vegas.VegasThirdRiddleController
    fun waitFor(message:String,predicate:(org.rigorcore.caserecomp.games.vegas.VegasThirdRiddleController)->Boolean) {
     val deadline=SystemClock.uptimeMillis()+5000
     var satisfied=false
     while(!satisfied && SystemClock.uptimeMillis()<deadline) {
      scenario.onActivity { satisfied=predicate(third(it)) };if(!satisfied) SystemClock.sleep(50)
     }
     assertTrue(message,satisfied)
    }
    fun clickItem(name:String) {
     scenario.onActivity { activity ->
      val controller=third(activity);val index=controller.items.definitions.indexOfFirst { it.name==name }
      val frame=controller.items.frame(index);val pixels=frame.image.pixels
      val point=(0 until frame.height).asSequence().flatMap { y -> (0 until frame.width).asSequence().map { x -> x to y } }.first { (x,y) -> pixels==null || pixels.getAlpha(frame.sourceX+x,frame.sourceY+y)!=0 }
      click(game(activity),frame.image.x+point.first,frame.image.y+point.second)
     }
    }
    scenario.onActivity { assertEquals(SdaCampaignPhase.FINALE_3,game(it).campaign!!.phase);assertTrue(third(it).started) }
    capture(scenario,"finale-third-initial-real.png")
    clickItem("hammer")
    waitFor("hammer must break hourglass through Android ticks") { it.items.completed("hammer","breakhourglass") }
    waitFor("hourglass interactive frame") { it.items.index(it.items.definitions.indexOfFirst { it.name=="hourglass" })==1 }
    clickItem("hourglass")
    waitFor("reader powered by authentic hourglass/scale chain") { it.items.index(it.items.definitions.indexOfFirst { it.name=="reader" })==1 }
    capture(scenario,"finale-third-hourglass-real.png")
    clickItem("plugin")
    waitFor("slot power uses original symbols") { it.symbols==listOf(7,6,4,0) }
    capture(scenario,"finale-third-powered-real.png")
    clickItem("coin");clickItem("slotarm")
    capture(scenario,"finale-third-spinning-real.png")
    waitFor("four reels generate code through Android ticks") { it.reelsFinished }
    scenario.onActivity { activity ->
     val controller=third(activity)
     assertTrue(controller.symbols.all { it in 0..8 });assertTrue((0..3).all(controller::symbolVisible))
     assertFalse(controller.isSolved);assertEquals(originalPoints,game(activity).campaign!!.points)
     File(context.getExternalFilesDir(null),"finale-third-earned-code.json").writeText(game(activity).campaign!!.snapshot().toJson())
    }
    capture(scenario,"finale-third-code-real.png")

    scenario.onActivity { activity ->
     val view=game(activity);val camp=view.campaign!!
     val rect=view.visuals!!.menuRect(camp)!!;click(view,rect.centerX(),rect.centerY())
    }
    confirm(scenario,80) // Original pause-menu returns to the original main menu.
    scenario.onActivity { activity ->
     val view=panel(activity);assertEquals("mainmenuunderlay",view.screenId)
     val rect=view.buttonBounds(view.buttons.single { it.type=="quitbutton" });click(view,rect.centerX(),rect.centerY())
    }
    instrumentation.waitForIdleSync()
    val saved=SdaCampaignState.fromJson(prefs.getString("active_campaign_checkpoint",null)!!)
    assertEquals(SdaCampaignPhase.FINALE_3.name,saved.phase)
    assertEquals(originalPoints,saved.points)
   }
   launch().use { reopened ->
    confirm(reopened,299)
    reopened.onActivity { activity ->
     val camp=game(activity).campaign!!;assertEquals(SdaCampaignPhase.FINALE_3,camp.phase)
     val restored=(camp.bonusGame as SdaInteractiveRiddleGame).controller as org.rigorcore.caserecomp.games.vegas.VegasThirdRiddleController
     assertTrue(restored.started);assertTrue(restored.reelsFinished);assertEquals(originalPoints,camp.points)
     val earnedState=SdaCampaignState.fromJson(File(context.getExternalFilesDir(null),"finale-third-earned-code.json").readText())
     @Suppress("UNCHECKED_CAST") val nativeState=earnedState.bonusGameState!!.getValue("controller") as Map<String,Any?>
     assertEquals(nativeState["symbols"],restored.symbols.map { it.toLong() })
    }
    capture(reopened,"finale-third-restored-real.png")
    fun native(activity:SdaLauncherActivity)=(game(activity).campaign!!.bonusGame as SdaInteractiveRiddleGame).controller as org.rigorcore.caserecomp.games.vegas.VegasThirdRiddleController
    fun awaitState(message:String,timeoutMillis:Long=5000,condition:(org.rigorcore.caserecomp.games.vegas.VegasThirdRiddleController)->Boolean) {
     val deadline=SystemClock.uptimeMillis()+timeoutMillis;var passed=false
     while(!passed && SystemClock.uptimeMillis()<deadline) { reopened.onActivity { passed=condition(native(it)) };if(!passed) SystemClock.sleep(40) }
     assertTrue(message,passed)
    }
    fun pickFingerprint() {
     reopened.onActivity { activity ->
      val c=native(activity);val index=c.items.definitions.indexOfFirst { it.name=="fingerprint" };val frame=c.items.frame(index)
      val source=frame.image.pixels!!
      val point=(0 until frame.height).asSequence().flatMap { y -> (0 until frame.width).asSequence().map { x -> x to y } }.first { (x,y) -> source.getAlpha(frame.sourceX+x,frame.sourceY+y)!=0 }
      click(game(activity),frame.image.x+point.first,frame.image.y+point.second);assertTrue(c.fingerprintHeld)
     }
    }
    pickFingerprint();capture(reopened,"finale-third-fingerprint-held.png")
    reopened.onActivity { activity -> click(game(activity),651,400);assertTrue(native(activity).fingerprintReturning) }
    awaitState("invalid reader edge returns original print") { !it.fingerprintReturning }
    pickFingerprint()
    reopened.onActivity { activity -> click(game(activity),620,400);assertFalse(native(activity).fingerprintHeld) }
    awaitState("native reader/keypad enable after fingerprint") { it.keypadEnabled }
    capture(reopened,"finale-third-keypad-enabled.png")
    var actualCode=emptyList<Int>()
    reopened.onActivity { actualCode=native(it).symbols }
    val wrong=(actualCode.first()+1)%9
    repeat(4) { reopened.onActivity { activity -> val bounds=native(activity).keyBounds[wrong];click(game(activity),bounds.x+bounds.width/2,bounds.y+bounds.height/2) } }
    reopened.onActivity { assertTrue(native(it).keypadError);assertEquals(listOf(10,10,10,10),native(it).ledFrames) }
    capture(reopened,"finale-third-keypad-error.png")
    awaitState("native wrong-code timer clears error") { !it.keypadError }
    for(symbol in actualCode) reopened.onActivity { activity -> val bounds=native(activity).keyBounds[symbol];click(game(activity),bounds.x+bounds.width/2,bounds.y+bounds.height/2) }
    awaitState("earned reel code triggers native door step") { it.doorOpening }
    reopened.onActivity { assertFalse(native(it).isSolved);assertEquals(originalPoints,game(it).campaign!!.points) }
    capture(reopened,"finale-ending-door.png")
    awaitState("original moneyroom transition",12000) { it.endingState==9 }
    capture(reopened,"finale-ending-moneyroom.png");memory(reopened,"ending-moneyroom")
    awaitState("original newspaper reaches full visibility",12000) { it.endingState==12 }
    capture(reopened,"finale-ending-newspaper.png")
    awaitState("native end sequence completes before final dialog",12000) { it.isSolved }
    instrumentation.waitForIdleSync()
    capture(reopened,"finale-ending-complete-dialog.png",true)
    confirm(reopened,1019,"riddlesdialogcompletecontainer3")
    reopened.onActivity { activity ->
     val camp=game(activity).campaign!!;assertEquals(SdaCampaignPhase.CAMPAIGN_COMPLETE,camp.phase)
     assertNull(camp.bonusGame);assertEquals(originalPoints,camp.points)
     assertEquals("mainmenuunderlay",panel(activity).screenId)
     assertEquals("true",panel(activity).buttons.single { it.number("value")==299 }.attributes["disabled"])
     File(context.getExternalFilesDir(null),"finale-ending-earned-complete.json").writeText(camp.snapshot().toJson())
    }
    capture(reopened,"finale-ending-mainmenu.png");memory(reopened,"ending-complete-mainmenu")

   }
   launch().use { finished ->
    finished.onActivity { activity ->
     val camp=game(activity).campaign!!;assertEquals(SdaCampaignPhase.CAMPAIGN_COMPLETE,camp.phase)
     assertEquals(originalPoints,camp.points);assertNull(camp.bonusGame)
    }
    instrumentation.waitForIdleSync()
    capture(finished,"finale-ending-restored-mainmenu.png")
   }
  } finally {
   val editor=prefs.edit();before.forEach { (key,value) -> if(value==null) editor.remove(key) else editor.putString(key,value) };assertTrue(editor.commit())
   for(file in listOf(qaProfile,qaSlot)) if(file.exists()) assertTrue(file.delete())
   assertEquals(optionsBefore,settings.all.toMap())
   before.forEach { (key,value) -> assertEquals(value,prefs.getString(key,null)) }
  }
 }
}

package org.rigorcore.caserecomp.app

import android.app.Activity
import android.app.ActivityOptions
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.rigorcore.caserecomp.sda.*
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real catalogue, DocumentsUI, profile and campaign inputs; no forced engine states or solve calls. */
@RunWith(AndroidJUnit4::class)
class SdaPrivateCampaignE2EInstrumentationTest {
 @Test fun catalogue_document_import_new_player_and_all_levels_reach_earned_finale() {
  val instrumentation=InstrumentationRegistry.getInstrumentation()
  val context=instrumentation.targetContext
  val args=InstrumentationRegistry.getArguments()
  val importName=args.getString("privateImportName")
  assumeTrue("supply a private ZIP already copied into Downloads",importName!=null && importName.endsWith(".zip") && '/' !in importName)
  assumeTrue(android.os.Build.VERSION.SDK_INT>=30)
  val display=args.getString("visualDisplayId")?.toInt() ?: 2
  val limit=args.getString("campaignLevels")?.toInt() ?: 25
  require(limit in 1..25)
  val output=File(context.getExternalFilesDir(null),"campaign-e2e").apply { mkdirs() }
  val journal=File(output,"journey.jsonl");journal.writeText("")
  fun record(kind:String,fields:Map<String,Any?> = emptyMap()) { journal.appendText(org.json.JSONObject(fields+mapOf("event" to kind,"uptime_ms" to SystemClock.uptimeMillis())).toString()+"\n") }
  fun hash(file:File):String { val digest=MessageDigest.getInstance("SHA-256");file.inputStream().use { stream -> val b=ByteArray(65536);while(true) { val n=stream.read(b);if(n<0) break;digest.update(b,0,n) } };return digest.digest().joinToString("") { "%02x".format(it) } }
  val prefs=context.getSharedPreferences("case-recomp-sda",0);val before=prefs.all.toMap()
  val roots=listOf(File(context.filesDir,"private-sda/profiles"),File(context.filesDir,"private-sda/campaigns"))
  val savedFiles=roots.flatMap { root -> if(root.exists()) root.walkTopDown().filter { it.isFile }.map { it to it.readBytes() }.toList() else emptyList() }.toMap()
  val packages=File(context.filesDir,"private-sda/packages")
  val originalPackages=if(packages.exists()) packages.walkTopDown().filter { it.isFile }.associateWith(::hash) else emptyMap()
  val covers=originalPackages.keys.filter { it.name.endsWith(".cover.png") }.associateWith { it.readBytes() }
  fun <T> main(block:()->T):T { var result:T?=null;instrumentation.runOnMainSync { result=block() };@Suppress("UNCHECKED_CAST") return result as T }
  fun waitFor(label:String,seconds:Int=20,condition:()->Boolean) {
   val until=SystemClock.uptimeMillis()+seconds*1000
   while(SystemClock.uptimeMillis()<until) { if(main { runCatching(condition).getOrDefault(false) }) return;SystemClock.sleep(50) }
   record("failure",mapOf("waiting" to label));fail("timed out waiting for $label")
  }
  fun activity():SdaLauncherActivity=ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<SdaLauncherActivity>().single()
  fun game()=SdaLauncherActivity::class.java.getDeclaredField("gameView").apply { isAccessible=true }.get(activity()) as SdaGameView
  fun campaign()=game().campaign!!
  fun panel():SdaResourceMenuView {
   val dialog=SdaLauncherActivity::class.java.getDeclaredField("hostMenu").apply { isAccessible=true }.get(activity()) as android.app.Dialog
   return dialog.window!!.decorView.findViewWithTag("sda-resource-menu")
  }
  fun menuPresent():Boolean=runCatching { panel().isShown }.getOrDefault(false)
  fun touch(view:View,action:Int,x:Int,y:Int,buttons:Int=0) {
   val scale=minOf(view.width/800f,view.height/600f);assertTrue(scale>0)
   val properties=arrayOf(MotionEvent.PointerProperties().apply { id=0;toolType=if(buttons==0) MotionEvent.TOOL_TYPE_FINGER else MotionEvent.TOOL_TYPE_MOUSE })
   val coords=arrayOf(MotionEvent.PointerCoords().apply { this.x=(view.width-800*scale)/2+(x+.5f)*scale;this.y=(view.height-600*scale)/2+(y+.5f)*scale;pressure=1f;size=1f })
   val event=MotionEvent.obtain(0,SystemClock.uptimeMillis(),action,1,properties,coords,0,buttons,1f,1f,0,0,if(buttons==0) android.view.InputDevice.SOURCE_TOUCHSCREEN else android.view.InputDevice.SOURCE_MOUSE,0)
   try { val handled=view.dispatchTouchEvent(event);if(action==MotionEvent.ACTION_DOWN) assertTrue(handled) } finally { event.recycle() }
  }
  fun click(view:View,x:Int,y:Int) { touch(view,MotionEvent.ACTION_DOWN,x,y);touch(view,MotionEvent.ACTION_UP,x,y) }
  fun menuButton(value:Int,screen:String) {
   waitFor(screen) { menuPresent() && panel().screenId==screen }
   main { val view=panel();val node=view.buttons.single { it.number("value",-1)==value };assertNotEquals("true",node.attributes["disabled"]);val r=view.buttonBounds(node);click(view,r.centerX(),r.centerY()) }
   instrumentation.waitForIdleSync()
  }
  fun capture(name:String,windowProvider:()->android.view.Window={ activity().window }) {
   SystemClock.sleep(100)
   lateinit var window:android.view.Window;lateinit var bitmap:Bitmap
   main { window=windowProvider();bitmap=Bitmap.createBitmap(window.decorView.width,window.decorView.height,Bitmap.Config.ARGB_8888) }
   val done=CountDownLatch(1);var result=-1
   PixelCopy.request(window,bitmap,{ result=it;done.countDown() },android.os.Handler(android.os.Looper.getMainLooper()))
   assertTrue(done.await(10,TimeUnit.SECONDS));assertEquals(PixelCopy.SUCCESS,result)
   File(output,"$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) };bitmap.recycle()
  }
  fun allViews(view:View):List<View> = listOf(view)+(if(view is ViewGroup) (0 until view.childCount).flatMap { allViews(view.getChildAt(it)) } else emptyList())
  fun catalogueClick(home:HomeActivity,text:String,importAction:Boolean=false) {
   val title=HomeCatalog.GAMES.single { it.id=="vegas_heist" }.title
   val target=allViews(home.window.decorView).filterIsInstance<TextView>().first { view ->
    view.text.toString()==text && (!importAction || (view.parent as? ViewGroup)?.let { parent -> allViews(parent).filterIsInstance<TextView>().any { it.text.toString()==title } }==true)
   }
   target.requestRectangleOnScreen(Rect(0,0,target.width,target.height),true)
   val position=IntArray(2);target.getLocationInWindow(position)
   assertTrue(target.isShown)
   for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)) {
    val e=MotionEvent.obtain(0,SystemClock.uptimeMillis(),action,position[0]+target.width/2f,position[1]+target.height/2f,0)
    try { assertTrue(home.window.decorView.dispatchTouchEvent(e)) } finally { e.recycle() }
   }
  }
  fun nodes(node:AccessibilityNodeInfo):List<AccessibilityNodeInfo> = listOf(node)+(0 until node.childCount).flatMap { node.getChild(it)?.let(::nodes) ?: emptyList() }
  val automation=instrumentation.uiAutomation
  val serviceBefore=automation.serviceInfo
  automation.serviceInfo=automation.serviceInfo.apply { flags=flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS }
  fun documentNodes():List<AccessibilityNodeInfo> = automation.windowsOnAllDisplays.get(display).orEmpty().mapNotNull { it.root }.filter { it.packageName?.toString()?.contains("documentsui")==true }.flatMap(::nodes)
  fun accessibleClick(node:AccessibilityNodeInfo):Boolean {
   var current:AccessibilityNodeInfo?=node
   repeat(10) { val selected=current ?: return false;if(selected.isClickable && selected.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;current=selected.parent }
   return false
  }
  var homeScenario:ActivityScenario<HomeActivity>?=null
  var completionEvents=0
  var testedPlayer=""
  try {
   homeScenario=ActivityScenario.launch(Intent(context,HomeActivity::class.java),ActivityOptions.makeBasic().setLaunchDisplayId(display).toBundle())
   instrumentation.waitForIdleSync()
   // If the beta notice is present, dismiss its real accessible OK control.
   automation.windowsOnAllDisplays.get(display).orEmpty().mapNotNull { it.root }.flatMap(::nodes).firstOrNull { it.text?.toString()=="Entendido" }?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
   lateinit var catalogueWindow:android.view.Window
   homeScenario.onActivity { home -> catalogueWindow=home.window }
   capture("catalogue-before-import") { catalogueWindow }
   homeScenario.onActivity { home -> catalogueClick(home,if(PrivateSdaRepository(home).hasActive()) "Importar de nuevo" else "Importar paquete SDA",true) }
   record("document-picker-opened",mapOf("display" to display,"file" to importName))
   val pickerUntil=SystemClock.uptimeMillis()+30000
   var chosen=false;var openedDownloads=false
   while(!chosen && SystemClock.uptimeMillis()<pickerUntil) {
    val ns=documentNodes()
    val file=ns.firstOrNull { it.text?.toString()==importName }
    if(file!=null) { chosen=accessibleClick(file) }
    else {
     val search=ns.firstOrNull { it.viewIdResourceName?.endsWith("search_src_text")==true }
     val searchButton=ns.firstOrNull { it.viewIdResourceName?.endsWith("option_menu_search")==true }
     if(search!=null) search.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,android.os.Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,importName) })
     else if(searchButton!=null) accessibleClick(searchButton)
     val downloads=ns.firstOrNull { it.text?.toString() in setOf("Downloads","Descargas") }
     if(downloads!=null && !openedDownloads) { openedDownloads=accessibleClick(downloads) }
     else if(!openedDownloads) ns.firstOrNull { it.viewIdResourceName?.endsWith("roots_toolbar") ==true || it.contentDescription?.toString() in setOf("Show roots","Mostrar ra\u00edces","Mostrar ubicaciones") }?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
     else ns.firstOrNull { it.isScrollable }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }
    SystemClock.sleep(200)
   }
   if(!chosen) record("picker-failure",mapOf("documentNodeCount" to documentNodes().size,"visibleText" to documentNodes().mapNotNull { it.text?.toString() }.take(20)))
   assertTrue("original DocumentsUI ZIP not selectable on display$display",chosen)
   waitFor("real document import",60) { PrivateSdaRepository(context).loadActive()?.sha256==args.getString("privateImportSha") && ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).any { it is HomeActivity } }
   SystemClock.sleep(300)
   val installed=PrivateSdaRepository(context).loadActive()!!
   record("document-imported",mapOf("sha256" to installed.sha256))
   homeScenario.onActivity { home -> catalogueClick(home,"Jugar (SDA)") }
   waitFor("Vegas main menu") { runCatching { menuPresent() }.getOrDefault(false) }
   menuButton(6,"mainmenuunderlay")
   waitFor("new-player row") { panel().screenId=="selectplayer" }
   repeat(30) {
    if(main { panel().screenId=="selectplayer" }) main { val view=panel();val r=view.listRowBounds("");if(r!=null) click(view,r.centerX(),r.centerY()) else { val arrow=view.buttonBounds(view.buttons.single { it.number("value")==62 });click(view,arrow.centerX(),arrow.centerY()) } }
   }
   waitFor("newplayer") { panel().screenId=="newplayer" }
   main { val view=panel();val input=view.onCreateInputConnection(android.view.inputmethod.EditorInfo())!!;input.commitText("CampaignQA"+(SystemClock.uptimeMillis()%100000),1) }
   menuButton(1,"newplayer")
   waitFor("fresh profile main menu") { menuPresent() && panel().screenId=="mainmenuunderlay" && campaign().phase==SdaCampaignPhase.MAP && campaign().points==0 && campaign().currentLevel.clue==1 }
   main { testedPlayer=PrivateSdaRepository(context).getActiveProfile().id }
   record("new-profile",mapOf("id" to testedPlayer))
   menuButton(299,"mainmenuunderlay")
   val observedViews=java.util.IdentityHashMap<SdaGameView,Boolean>()
   fun observeCompletions() { main { val view=game();if(!observedViews.containsKey(view)) { observedViews[view]=true;val previous=view.onSceneCompleteListener;view.onSceneCompleteListener={ completionEvents++;previous?.invoke() } } } }
   observeCompletions()
   for(level in 1..limit) {
    waitFor("level$level map") { campaign().phase==SdaCampaignPhase.MAP && campaign().currentLevel.clue==level }
    val started=SystemClock.uptimeMillis()
    capture("level-%02d-map".format(level))
    var sceneIndex=0
    while(main { campaign().phase!=SdaCampaignPhase.OBJECTS_COMPLETE }) {
     main {
      val view=game();val camp=campaign();assertEquals(SdaCampaignPhase.MAP,camp.phase)
      val name=camp.currentLevel.scenes.getOrNull(sceneIndex++) ?: error("not enough scene objectives at level$level")
      val point=(0 until 600 step 4).asSequence().flatMap { y -> (144 until 800 step 4).asSequence().map { x -> x to y } }.first { (x,y) -> view.visuals!!.sceneAt(camp,x,y)==name }
      click(view,point.first,point.second)
     }
     waitFor("level$level scene") { campaign().phase==SdaCampaignPhase.SCENE }
     val name=main { campaign().currentSceneName!! }
     capture("level-%02d-scene-%s".format(level,name))
     val ids=main { campaign().currentScene!!.activeSets.take(campaign().remainingObjects).flatten() }
     val beforeEvents=main { completionEvents }
     for(id in ids) main {
      val view=game();val scene=campaign().currentScene!!;val sprite=scene.objects.getValue(id)
      if(!sprite.found) {
       val p=(0 until sprite.image.width*sprite.image.height).asSequence().map { sprite.x+it%sprite.image.width to sprite.y+it/sprite.image.width }.firstOrNull { (x,y) -> x in 144 until 800 && y in 0 until 600 && scene.targets.firstOrNull { scene.objects.getValue(it).hit(x,y) }==id }
       if(p==null) { record("unreachable-object",mapOf("level" to level,"scene" to name,"id" to id,"x" to sprite.x,"y" to sprite.y,"width" to sprite.image.width,"height" to sprite.image.height));error("no visible Android hit pixel for $id in $name") }
       click(view,p.first,p.second);assertTrue("object$id must be found by Android touch",sprite.found)
      }
     }
     waitFor("level$level scene retirement",20) { campaign().phase in setOf(SdaCampaignPhase.SCENE_COMPLETE,SdaCampaignPhase.OBJECTS_COMPLETE) }
     SystemClock.sleep(350)
     assertEquals("completion must notify once, not every frame",beforeEvents+1,main { completionEvents })
     record("scene-completed",main { mapOf("level" to level,"scene" to name,"objectInputs" to ids.size,"completedRows" to campaign().completedObjects,"points" to campaign().points,"elapsed" to campaign().clock.elapsed) })
     if(main { campaign().phase==SdaCampaignPhase.SCENE_COMPLETE }) main { val view=game();val r=view.visuals!!.returnMapRect;click(view,r.centerX(),r.centerY()) }
    }
    main { val view=game();val button=requireNotNull(view.visuals!!.objectsCompleteRect());click(view,button.centerX(),button.centerY()) }
    waitFor("level$level bonus") { campaign().phase==SdaCampaignPhase.BONUS }
    capture("level-%02d-bonus".format(level))
    val beforeBonusPoints=main { campaign().points }
    val bonus=main { campaign().bonusGame!! }
    File(output,"earned-level-$level-bonus-entry.json").writeText(main { campaign().snapshot().toJson() })
    var partialSaved=false
    fun partialCheckpoint() {
     if(!partialSaved) {
      File(output,"earned-level-$level-bonus-partial.json").writeText(campaign().snapshot().toJson())
      partialSaved=true
     }
    }
    when(bonus) {
     is SdaTileRotGame -> for(i in bonus.tileRotations.indices) repeat(main { bonus.tileRotations[i] }) { main { click(game(),172+i%bonus.cols*612/bonus.cols+1,95+i/bonus.cols*408/bonus.rows+1);partialCheckpoint() } }
     is SdaWordSearchGame -> for(cells in bonus.board.placements.values) main {
      fun x(c:Int)=bonus.originX+c%bonus.cols*bonus.cellWidth+1
      fun y(c:Int)=bonus.originY+c/bonus.cols*bonus.cellHeight+1
      touch(game(),MotionEvent.ACTION_DOWN,x(cells.first()),y(cells.first()));touch(game(),MotionEvent.ACTION_MOVE,x(cells.last()),y(cells.last()));partialCheckpoint();touch(game(),MotionEvent.ACTION_UP,x(cells.last()),y(cells.last()))
     }
     is SdaTileSwapGame -> for(i in bonus.tilePositions.indices) main {
      if(!bonus.lockedTiles[i]) { val source=bonus.tilePositions.indexOf(i);click(game(),172+i%bonus.cols*612/bonus.cols+1,96+i/bonus.cols*408/bonus.rows+1);partialCheckpoint();click(game(),172+source%bonus.cols*612/bonus.cols+1,96+source/bonus.cols*408/bonus.rows+1) }
     }
     is SdaJigsawGame -> for(id in bonus.interaction.board.trayOrder) {
      repeat(40) {
       if(main { bonus.interaction.board.selected!=id }) main {
        val view=game();val rect=bonus.trayRectangles().firstOrNull { it.id==id }
        if(rect!=null) { val image=bonus.image(id,true);val pixel=(0 until image.width*image.height).first { image.getAlpha(it%image.width,it/image.width)==255 };click(view,rect.x+pixel%image.width,rect.y+pixel/image.width) }
        else { val arrow=bonus.arrowDown!!;click(view,arrow.x+1,arrow.y+1) }
       }
      }
      main {
       assertEquals(id,bonus.interaction.board.selected)
       partialCheckpoint()
       while(bonus.interaction.board.quarterTurns.getValue(id)!=0) { touch(game(),MotionEvent.ACTION_DOWN,0,0,MotionEvent.BUTTON_SECONDARY);touch(game(),MotionEvent.ACTION_UP,0,0,MotionEvent.BUTTON_SECONDARY) }
       val piece=bonus.interaction.board.pieces.getValue(id);touch(game(),MotionEvent.ACTION_MOVE,piece.x+piece.width/2,piece.y+piece.height/2);click(game(),piece.x+piece.width/2,piece.y+piece.height/2)
       assertTrue("Jigsaw$id must be earned",id in bonus.interaction.board.placed)
      }
     }
     else -> error("unsupported native bonus at level$level: $bonus")
    }
    waitFor("level$level result") { campaign().phase==SdaCampaignPhase.LEVEL_COMPLETE && bonus.isSolved }
    val expectedTimeReward=main { VegasScoreRules.bonusTimeReward(maxOf(0f,campaign().clock.limit-campaign().clock.elapsed)) }
    val placementPoints=when(bonus) {
     is SdaTileRotGame -> bonus.linePoints
     is SdaTileSwapGame -> bonus.placementPoints
     is SdaWordSearchGame -> bonus.placementPoints
     is SdaJigsawGame -> bonus.placementPoints
     else -> error("unsupported bonus score")
    }
    assertEquals("native bonus reward must be credited once",beforeBonusPoints+placementPoints+bonus.points+expectedTimeReward,main { campaign().points })
    record("bonus-reward-verified",mapOf("level" to level,"placementPoints" to placementPoints,"completionPoints" to bonus.points,"timePoints" to expectedTimeReward))
    File(output,"earned-level-$level-result.json").writeText(main { campaign().snapshot().toJson() })
    capture("level-%02d-result".format(level))
    val info=android.os.Debug.MemoryInfo();android.os.Debug.getMemoryInfo(info)
    record("level-completed",main { mapOf("level" to level,"bonusResource" to campaign().currentLevel.bonus,"bonusImage" to campaign().currentLevel.bonusImage,"family" to bonus.javaClass.simpleName,"points" to campaign().points,"elapsed" to campaign().clock.elapsed,"pss_kib" to info.totalPss,"availableScenes" to campaign().currentLevel.scenes,"javaHeapBytes" to Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory(),"nativeHeapBytes" to android.os.Debug.getNativeHeapAllocatedSize(),"duration_ms" to SystemClock.uptimeMillis()-started) })
    main { val view=game();val r=view.visuals!!.levelCompleteRect(campaign())!!;click(view,r.centerX(),r.centerY()) }
    waitFor("next level boundary") { campaign().phase==if(level==25) SdaCampaignPhase.FINALE_1 else SdaCampaignPhase.MAP }
    File(output,"earned-level-$level-checkpoint.json").writeText(main { campaign().snapshot().toJson() })
    if(level%5==0 && level<25 && level<limit) {
     val previous=main { campaign().snapshot().toJson() }
     main { val view=game();val r=view.visuals!!.menuRect(campaign())!!;click(view,r.centerX(),r.centerY()) }
     menuButton(80,"menudlg2");menuButton(-1,"mainmenuunderlay")
     waitFor("saved exit to catalogue") { ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).any { it is HomeActivity } }
     homeScenario.onActivity { home -> catalogueClick(home,"Jugar (SDA)") }
     waitFor("saved campaign main menu") { menuPresent() && panel().screenId=="mainmenuunderlay" }
     assertEquals("saved score/timer/profile progression differs after activity reopen",previous,main { campaign().snapshot().toJson() })
     record("activity-reopened",mapOf("afterLevel" to level,"pid" to android.os.Process.myPid(),"checkpointUnchanged" to true))
     menuButton(299,"mainmenuunderlay");observeCompletions()
    }
   }
   val checkpoint=main { campaign().snapshot().toJson() }
   File(output,"earned-level-$limit-checkpoint.json").writeText(checkpoint)
   record("route-complete",main { mapOf("levels" to limit,"phase" to campaign().phase.name,"points" to campaign().points,"profile" to testedPlayer) })
  } finally {
   main { ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<SdaLauncherActivity>().forEach { it.finish() } }
   instrumentation.waitForIdleSync();homeScenario?.close()
   for(root in roots) if(root.exists()) for(file in root.walkTopDown().filter { it.isFile }.toList()) if(file !in savedFiles) assertTrue(file.delete())
   savedFiles.forEach { (file,bytes) -> file.parentFile.mkdirs();file.writeBytes(bytes) }
   if(packages.exists()) for(file in packages.walkTopDown().filter { it.isFile }.toList()) if(file !in originalPackages) assertTrue(file.delete())
   covers.forEach { (file,bytes) -> file.writeBytes(bytes) }
   for((file,digest) in originalPackages) assertEquals("original SDA package changed",digest,hash(file))
   val edit=prefs.edit().clear();for((key,value) in before) when(value) { is String -> edit.putString(key,value);is Int -> edit.putInt(key,value);is Long -> edit.putLong(key,value);is Boolean -> edit.putBoolean(key,value);is Float -> edit.putFloat(key,value) };assertTrue(edit.commit())
   automation.serviceInfo=serviceBefore
  }
 }
}

package org.rigorcore.caserecomp.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.rigorcore.caserecomp.sda.*
import java.io.File

/** Real device framebuffer captures; preparation uses normal campaign inputs, never forced states. */
@RunWith(AndroidJUnit4::class)
class SdaPrivateVisualInstrumentationTest {
 @Test fun launcher_menu_resume_and_saved_exit() {
  val instrumentation=InstrumentationRegistry.getInstrumentation()
  val context=instrumentation.targetContext
  val path=InstrumentationRegistry.getArguments().getString("privateSdaPackage")
  assumeTrue(path!=null && File(path).isFile)
  val display=InstrumentationRegistry.getArguments().getString("visualDisplayId")?.toInt() ?: 0
  val preferences=context.getSharedPreferences("case-recomp-sda",android.content.Context.MODE_PRIVATE)
  val keys=listOf("active_campaign_checkpoint","session-checkpoint")
  val saved=keys.associateWith { preferences.getString(it,null) }
  try {
   val intent=android.content.Intent(context,SdaLauncherActivity::class.java).putExtra(SdaLauncherActivity.EXTRA_PACKAGE_PATH,path)
   ActivityScenario.launch<SdaLauncherActivity>(intent,android.app.ActivityOptions.makeBasic().setLaunchDisplayId(display).toBundle()).use { scenario ->
    lateinit var shown:SdaGameView
    fun find(view:android.view.View):SdaGameView? {
     if(view is SdaGameView) return view
     if(view is android.view.ViewGroup) for(i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
     return null
    }
    scenario.onActivity { shown=checkNotNull(find(it.window.decorView)) }
    fun clickMenu() {
     scenario.onActivity { activity ->
      val rect=checkNotNull(shown.visuals?.menuRect(checkNotNull(shown.campaign)))
      val scale=minOf(shown.width/800f,shown.height/600f)
      val x=(shown.width-800*scale)/2+rect.centerX()*scale;val y=(shown.height-600*scale)/2+rect.centerY()*scale
      for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)) {
       val event=MotionEvent.obtain(SystemClock.uptimeMillis(),SystemClock.uptimeMillis(),action,x,y,0)
       try { assertTrue(shown.dispatchTouchEvent(event)) } finally { event.recycle() }
      }
     }
     instrumentation.waitForIdleSync()
    }
    fun menu():android.app.Dialog {
     lateinit var dialog:android.app.Dialog
     scenario.onActivity { activity ->
      val field=SdaLauncherActivity::class.java.getDeclaredField("hostMenu").apply { isAccessible=true }
      dialog=field.get(activity) as android.app.Dialog
     }
     return dialog
    }
    fun capture(dialog:android.app.Dialog,name:String) {
    SystemClock.sleep(100)
    lateinit var bitmap:Bitmap
    lateinit var window:android.view.Window
    scenario.onActivity { window=checkNotNull(dialog.window);bitmap=Bitmap.createBitmap(window.decorView.width,window.decorView.height,Bitmap.Config.ARGB_8888) }
    val done=java.util.concurrent.CountDownLatch(1);var status=-1
    android.view.PixelCopy.request(window,bitmap,{ status=it;done.countDown() },android.os.Handler(android.os.Looper.getMainLooper()))
    assertTrue(done.await(10,java.util.concurrent.TimeUnit.SECONDS));assertEquals(android.view.PixelCopy.SUCCESS,status)
    File(context.getExternalFilesDir(null),name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
    }
    // Campaign launch now opens the original internal main menu before the map.
    scenario.onActivity { assertTrue(shown.isPaused) }
    val initial=menu()
    capture(initial,"vegas-main-menu.png")
    scenario.onActivity { assertEquals("mainmenuunderlay",initial.window!!.decorView.findViewWithTag<SdaResourceMenuView>("sda-resource-menu").screenId) }
    selectInitial@ run {
     scenario.onActivity {
      val menu=initial.window!!.decorView.findViewWithTag<SdaResourceMenuView>("sda-resource-menu")
      val rect=menu.buttonBounds(menu.buttons.single { it.number("value")==299 })
      val scale=minOf(menu.width/800f,menu.height/600f)
      val x=(menu.width-800*scale)/2+rect.centerX()*scale;val y=(menu.height-600*scale)/2+rect.centerY()*scale
      for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)) {
       val event=MotionEvent.obtain(SystemClock.uptimeMillis(),SystemClock.uptimeMillis(),action,x,y,0)
       try { assertTrue(menu.dispatchTouchEvent(event)) } finally { event.recycle() }
      }
     }
     instrumentation.waitForIdleSync();assertFalse(initial.isShowing)
    }
    clickMenu();val dialog=menu()
    scenario.onActivity {
     assertTrue(shown.isPaused)
     assertTrue(dialog.window!!.decorView.findViewWithTag<android.view.View>("sda-resource-menu") is SdaResourceMenuView)
    }
    capture(dialog,"vegas-host-menu.png")
    fun select(dialog:android.app.Dialog,index:Int,dismissed:Boolean=true) {
     scenario.onActivity {
      val menu=dialog.window!!.decorView.findViewWithTag<SdaResourceMenuView>("sda-resource-menu")
      val node=menu.buttons.single { it.number("value",-1)==index }
      val bounds=menu.buttonBounds(node)
      val scale=minOf(menu.width/800f,menu.height/600f)
      val x=(menu.width-800*scale)/2+bounds.centerX()*scale
      val y=(menu.height-600*scale)/2+bounds.centerY()*scale
      for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)) {
       val event=MotionEvent.obtain(SystemClock.uptimeMillis(),SystemClock.uptimeMillis(),action,x,y,0)
       try { assertTrue(menu.dispatchTouchEvent(event)) } finally { event.recycle() }
      }
     }
     if(!dismissed) { instrumentation.waitForIdleSync();assertTrue(dialog.isShowing);return }
     val deadline=SystemClock.uptimeMillis()+3000
     while(dialog.isShowing && SystemClock.uptimeMillis()<deadline) { instrumentation.waitForIdleSync();SystemClock.sleep(50) }
     assertFalse("menu selection must dismiss the real dialog",dialog.isShowing)
     instrumentation.waitForIdleSync()
    }
    // Release outside and CANCEL must leave the menu open and campaign paused.
    scenario.onActivity {
     val menu=dialog.window!!.decorView.findViewWithTag<SdaResourceMenuView>("sda-resource-menu")
     val resume=menu.buttonBounds(menu.buttons.single { it.number("value")==215 })
     val scale=minOf(menu.width/800f,menu.height/600f)
     val x=(menu.width-800*scale)/2+resume.centerX()*scale
     val y=(menu.height-600*scale)/2+resume.centerY()*scale
     for(ending in listOf(MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL)) {
      for((action,xx,yy) in listOf(Triple(MotionEvent.ACTION_DOWN,x,y),Triple(ending,0f,0f))) {
       val event=MotionEvent.obtain(SystemClock.uptimeMillis(),SystemClock.uptimeMillis(),action,xx,yy,0)
       try { assertTrue(menu.dispatchTouchEvent(event)) } finally { event.recycle() }
      }
      assertTrue(dialog.isShowing);assertTrue(shown.isPaused)
     }
    }
    select(dialog,215)
    scenario.onActivity { assertFalse(shown.isPaused) }
    clickMenu();val navigation=menu();select(navigation,80,false)
    fun screen()=navigation.window!!.decorView.findViewWithTag<SdaResourceMenuView>("sda-resource-menu").screenId
    scenario.onActivity { assertEquals("mainmenuunderlay",screen());assertTrue(shown.isPaused) }
    select(navigation,200,false)
    capture(navigation,"vegas-help-mainoverlaydlg.png")
    scenario.onActivity { assertEquals("mainoverlaydlg",screen()) }
    for((value,id) in listOf(15 to "mainoverlaydlg2",17 to "mainoverlaydlg3",19 to "mainoverlaydlg4",20 to "mainoverlaydlg3",18 to "mainoverlaydlg2",16 to "mainoverlaydlg")) {
     select(navigation,value,false);scenario.onActivity { assertEquals(id,screen());assertTrue(shown.isPaused) }
     capture(navigation,"vegas-help-$id.png")
    }
    select(navigation,201,false);scenario.onActivity { assertEquals("mainmenuunderlay",screen()) }
    select(navigation,-1)
    val deadline=SystemClock.uptimeMillis()+3000
    while(scenario.state!=androidx.lifecycle.Lifecycle.State.DESTROYED && SystemClock.uptimeMillis()<deadline) { instrumentation.waitForIdleSync();SystemClock.sleep(50) }
    assertEquals(androidx.lifecycle.Lifecycle.State.DESTROYED,scenario.state)
    val checkpoint=preferences.getString("active_campaign_checkpoint",null)
    assertFalse(checkpoint.isNullOrBlank());SdaCampaignState.fromJson(checkpoint!!)
   }
  } finally {
   val edit=preferences.edit()
   for(key in keys) saved[key]?.let { edit.putString(key,it) } ?: edit.remove(key)
   assertTrue(edit.commit())
   for(key in keys) assertEquals("preserve preexisting checkpoint",saved[key],preferences.getString(key,null))
  }
 }

 @Test fun original_dialog_tiles_repeat_without_stretching() {
  val path=InstrumentationRegistry.getArguments().getString("privateSdaPackage")
  assumeTrue(path!=null && File(path).isFile)
  SdaContent.open(File(path!!),SdaImageDecoder { bytes ->
   BitmapFactory.decodeByteArray(bytes,0,bytes.size)?.let { bmp -> object:SdaPixelSource {
    override val width=bmp.width;override val height=bmp.height
    override fun getArgb(px:Int,py:Int)=bmp.getPixel(px,py)
    override fun getAlpha(px:Int,py:Int)=getArgb(px,py) ushr 24
    override val nativeImage:Any=bmp
   } }
  }).use { content ->
   val ui=SdaResourceCanvas(SdaUiDocument(content.read("ENVS.MSE")!!,content.loadStrings("ENVS.MSE")),content)
   val textures=listOf("mpi_diag_tleft","mpi_diag_tmid","mpi_diag_tright","mpi_diag_left","mpi_diag_mid",
    "mpi_diag_right","mpi_diag_bleft","mpi_diag_bmid","mpi_diag_bright")
   val parts=textures.map(ui::bitmap)
   for((width,height) in listOf(325 to 320,470 to 430,560 to 420)) {
    val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
    ui.tiledPanel(Canvas(bitmap),textures,android.graphics.Rect(0,0,width,height))
    val xs=listOf(0,83,width-83,width);val ys=listOf(0,83,height-83,height)
    for(row in 0..2) for(col in 0..2) {
     val source=parts[row*3+col]
     for(y in ys[row] until ys[row+1] step 3) for(x in xs[col] until xs[col+1] step 3) {
      assertEquals("panel $width x $height at $x,$y",source.getPixel((x-xs[col])%source.width,(y-ys[row])%source.height),bitmap.getPixel(x,y))
     }
    }
    bitmap.recycle()
   }
  }
 }

 @Test fun original_resources_map_scene_and_four_bonuses() {
  val instrumentation=InstrumentationRegistry.getInstrumentation()
  val path=InstrumentationRegistry.getArguments().getString("privateSdaPackage")
  assumeTrue(path!=null && File(path).isFile)
  val content=SdaContent.open(File(path!!),SdaImageDecoder { bytes ->
   BitmapFactory.decodeByteArray(bytes,0,bytes.size)?.let { bmp -> object:SdaPixelSource {
    override val width=bmp.width;override val height=bmp.height
    override fun getArgb(px:Int,py:Int)=bmp.getPixel(px,py)
    override fun getAlpha(px:Int,py:Int)=getArgb(px,py) ushr 24
    override val nativeImage:Any=bmp
   } }
  })
  content.use {
   val levels=SdaLevels.parse(content.read("LEVELS_1.XUI")!!)
   val requestedLevels=InstrumentationRegistry.getArguments().getString("campaignLevels")?.toInt() ?: 4
   require(requestedLevels in 1..levels.size)
   val campaign=SdaCampaign(levels,8,firstRiddle=SdaRiddleBinding("ENVS.MSE","firstriddle"))
   val coverage=org.json.JSONArray()
   val profile=VegasVisualProfile(content)
   var unusedView:SdaGameView?=null
    fun finishObjects() {
     for (name in campaign.currentLevel.scenes) {
      val scene = campaign.enterScene(name,content)
      for (group in scene.activeSets.take(campaign.remainingObjects)) {
       for (id in group) {
        val sprite=scene.objects.getValue(id)
        if(sprite.found) continue
        var input: Pair<Int,Int>?=null
        search@ for(y in 0 until sprite.image.height) for(x in 0 until sprite.image.width) {
         val px=sprite.x+x; val py=sprite.y+y
         if(px !in 174 until 800 || py !in 0 until 600) continue
         if(scene.targets.firstOrNull { scene.objects.getValue(it).hit(px,py) }==id) { input=px to py; break@search }
        }
        assertNotNull(input)
        val targetView = unusedView
        if (targetView == null) assertTrue(campaign.clickScene(input!!.first,input.second) is SdaClickResult.Found)
        else {
            targetView.scene=scene
            val event=MotionEvent.obtain(0,SystemClock.uptimeMillis(),MotionEvent.ACTION_DOWN,input!!.first.toFloat(),input.second.toFloat(),0)
            try { assertTrue(targetView.onTouchEvent(event)) } finally { event.recycle() }
            assertTrue(scene.objects.getValue(id).found)
        }
       }
       repeat(100) { campaign.advance(.04f) }
      }
      if(campaign.phase==SdaCampaignPhase.SCENE_COMPLETE) campaign.confirmSceneComplete()
      if(campaign.phase==SdaCampaignPhase.OBJECTS_COMPLETE) break
     }
     assertEquals(SdaCampaignPhase.OBJECTS_COMPLETE,campaign.phase)
    }

   val displayId=InstrumentationRegistry.getArguments().getString("visualDisplayId")?.toInt() ?: 0
   ActivityScenario.launch(HomeActivity::class.java, android.app.ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle()).use { scenario ->
    fun captureWindow():Bitmap {
     lateinit var window:android.view.Window
     lateinit var bitmap:Bitmap
     scenario.onActivity { activity ->
      window=activity.window
      bitmap=Bitmap.createBitmap(window.decorView.width,window.decorView.height,Bitmap.Config.ARGB_8888)
     }
     val copied=java.util.concurrent.CountDownLatch(1)
     var status=-1
     // PixelCopy reads the real Android window surface on any display, never View.draw.
     android.view.PixelCopy.request(window,bitmap,{ result -> status=result;copied.countDown() },android.os.Handler(android.os.Looper.getMainLooper()))
     assertTrue("window surface capture timed out",copied.await(10,java.util.concurrent.TimeUnit.SECONDS))
     assertEquals("window surface capture failed",android.view.PixelCopy.SUCCESS,status)
     return bitmap
    }
    var menuRequests=0
    lateinit var shown:SdaGameView
    fun touch(action:Int,x:Int,y:Int,buttons:Int=0) {
     scenario.onActivity {
      val scale=minOf(shown.width/800f,shown.height/600f)
      val properties=arrayOf(MotionEvent.PointerProperties().apply { id=0;toolType=MotionEvent.TOOL_TYPE_MOUSE })
      val coords=arrayOf(MotionEvent.PointerCoords().apply { this.x=(shown.width-800f*scale)/2+(x+.5f)*scale;this.y=(shown.height-600f*scale)/2+(y+.5f)*scale;pressure=1f;size=1f })
      val event=MotionEvent.obtain(0,SystemClock.uptimeMillis(),action,1,properties,coords,0,buttons,1f,1f,0,0,android.view.InputDevice.SOURCE_MOUSE,0)
      try { assertTrue(if(action==MotionEvent.ACTION_HOVER_MOVE || action==MotionEvent.ACTION_HOVER_EXIT) shown.onHoverEvent(event) else shown.onTouchEvent(event)) } finally { event.recycle() }
     }
    }
    fun display(name:String) {
     if(name in setOf("rotation","wordsearch","jigsaw","swap")) assertNull(profile.bonusSolveRect(campaign))
     scenario.onActivity { activity ->
      val scene=campaign.scenes.values.firstOrNull() ?: content.loadScene("SCENE_${campaign.currentLevel.scenes.first().uppercase()}.MSL",8)
      val view=SdaGameView(activity,scene,campaign=campaign,visuals=profile)
      shown=view
      view.onMenuListener={ menuRequests++ }
      activity.setContentView(view)
      view.onSceneSelectedListener={ selected -> view.scene=campaign.enterScene(selected,content);view.invalidate() }
      view.onReturnToMapListener={ campaign.toInvestigationMap();view.invalidate() }
      view.onNextLevelListener={ campaign.confirmLevelComplete(content);view.invalidate() }
     }
     instrumentation.waitForIdleSync()
     // WSA compositor and Android launch splash can outlive the UI idle queue.
     SystemClock.sleep(1000)
     instrumentation.waitForIdleSync()
     var viewport=Triple(0f,0f,1f)
     scenario.onActivity {
      val location=IntArray(2);shown.getLocationInWindow(location)
      val scale=minOf(shown.width/800f,shown.height/600f)
      viewport=Triple(location[0]+(shown.width-800*scale)/2,location[1]+(shown.height-600*scale)/2,scale)
     }
     fun slots(bitmap:Bitmap):List<Int> =(0 until 10).map { row ->
      (0 until 150*20).count { point ->
       val x=(viewport.first+(point%150+.5f)*viewport.third).toInt()
       val y=(viewport.second+(120+row*20+point/150+.5f)*viewport.third).toInt()
       val argb=bitmap.getPixel(x,y)
       android.graphics.Color.red(argb)>160 && android.graphics.Color.green(argb)>110 && android.graphics.Color.blue(argb)<130
      }
     }
     var screenshot=captureWindow()
     if(name=="scene-row-retired") {
      // Capture real compositor frames until remaining row ink is visible, or fail.
      repeat(8) {
       if(slots(screenshot).dropLast(1).any { it<=20 }) {
        screenshot.recycle();SystemClock.sleep(150)
        scenario.onActivity { shown.invalidate() };instrumentation.waitForIdleSync()
        screenshot=captureWindow()
       }
      }
      val ink=slots(screenshot)
      assertTrue("remaining objectives must compact upward: $ink",ink.dropLast(1).all { it>20 })
      assertTrue("last objective slot must clear: $ink",ink.last()<5)
     }
     if(name=="map") {
      val background=content.decodeImage("map_backgroundend.jpg")
      // A cold WSA launch can expose the previous compositor frame after UI idle.
      // Wait for the requested map, retaining the exact assertions below.
      repeat(8) {
       val expected=background.getArgb(750-144,100)
       val actual=screenshot.getPixel((viewport.first+750.5f*viewport.third).toInt(),(viewport.second+100.5f*viewport.third).toInt())
       if(listOf(16,8,0).any { shift -> kotlin.math.abs(((actual ushr shift) and 255)-((expected ushr shift) and 255))>26 }) {
        screenshot.recycle();SystemClock.sleep(150)
        scenario.onActivity { shown.invalidate() };instrumentation.waitForIdleSync()
        screenshot=captureWindow()
       }
      }
      for((x,y) in listOf(750 to 100,750 to 450,200 to 100)) {
       val expected=background.getArgb(x-144,y)
       val actual=screenshot.getPixel((viewport.first+(x+.5f)*viewport.third).toInt(),(viewport.second+(y+.5f)*viewport.third).toInt())
       val channels=listOf(android.graphics.Color.red(actual)-android.graphics.Color.red(expected),android.graphics.Color.green(actual)-android.graphics.Color.green(expected),android.graphics.Color.blue(actual)-android.graphics.Color.blue(expected))
       assertTrue("stable map background differs at $x,$y: $channels",channels.all { kotlin.math.abs(it)<=26 })
      }
     }
     if(name=="scene") {
      val levelInk=(10 until 142).sumOf { x -> (42 until 64).count { y ->
       val p=screenshot.getPixel((viewport.first+(x+.5f)*viewport.third).toInt(),(viewport.second+(y+.5f)*viewport.third).toInt())
       android.graphics.Color.red(p)>160 && android.graphics.Color.green(p)>160 && android.graphics.Color.blue(p)>160
      } }
      assertTrue("original PDA level header missing: $levelInk",levelInk>100)
      val numericInk=(20 until 132).sumOf { x -> (81 until 86).count { y ->
       val p=screenshot.getPixel((viewport.first+(x+.5f)*viewport.third).toInt(),(viewport.second+(y+.5f)*viewport.third).toInt())
       android.graphics.Color.red(p)>75 && android.graphics.Color.green(p)>110 && android.graphics.Color.blue(p)>150
      } }
      assertTrue("numeric score must be visible below its caption and above instructions: $numericInk",numericInk>8)

     }
     if(name in listOf("rotation","wordsearch","jigsaw","swap")) {
      val background=content.decodeImage("minigame_background.jpg")
      for((x,y) in listOf(750 to 550,200 to 550,750 to 25)) {
       val expected=background.getArgb(x-144,y)
       val actual=screenshot.getPixel((viewport.first+(x+.5f)*viewport.third).toInt(),(viewport.second+(y+.5f)*viewport.third).toInt())
       val channels=listOf(android.graphics.Color.red(actual)-android.graphics.Color.red(expected),android.graphics.Color.green(actual)-android.graphics.Color.green(expected),android.graphics.Color.blue(actual)-android.graphics.Color.blue(expected))
       assertTrue("original bonus frame missing in $name at $x,$y: $channels",channels.all { kotlin.math.abs(it)<=26 })
      }
     }
     if(name=="jigsaw") {
      for((uri,originY,y) in listOf(Triple("ui_empypda.jpg",88,200),Triple("ui_empybtmpda.jpg",340,400))) {
       val expected=content.decodeImage(uri).getArgb(2,y-originY)
       val actual=screenshot.getPixel((viewport.first+12.5f*viewport.third).toInt(),(viewport.second+(y+.5f)*viewport.third).toInt())
       for(shift in listOf(16,8,0)) assertTrue("Jigsaw PDA backing missing: $uri",kotlin.math.abs(((actual ushr shift) and 255)-((expected ushr shift) and 255))<=26)
      }
     }
     if(name=="wordsearch") {
      val game=campaign.bonusGame as SdaWordSearchGame
      val ink=(0 until game.cellWidth*game.cellHeight).count { point ->
       val x=(viewport.first+(game.originX+point%game.cellWidth+.5f)*viewport.third).toInt()
       val y=(viewport.second+(game.originY+point/game.cellWidth+.5f)*viewport.third).toInt()
       val argb=screenshot.getPixel(x,y)
       android.graphics.Color.red(argb)>200 && android.graphics.Color.green(argb)>200 && android.graphics.Color.blue(argb)>200
      }
      assertTrue("original white atlas letter missing: $ink",ink>20)
     }
     assertTrue(screenshot.width>0 && screenshot.height>0)
     File(instrumentation.targetContext.getExternalFilesDir(null),"vegas-$name.png").outputStream().use { stream ->
      assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG,100,stream))
     }
     screenshot.recycle()
    }
    fun captureState(name:String) {
     scenario.onActivity { shown.invalidate() };instrumentation.waitForIdleSync()
     SystemClock.sleep(500)
     val screenshot=captureWindow()
     if(name=="rotation-retired" || name=="swap-retired") {
      val rotation=campaign.bonusGame as? SdaTileRotGame
      val swap=campaign.bonusGame as? SdaTileSwapGame
      val cols=rotation?.cols ?: swap!!.cols;val rows=rotation?.rows ?: swap!!.rows
      val locked=rotation?.lockedTiles ?: swap!!.lockedTiles
      val i=locked.indexOfFirst { it };assertTrue(i>=0)
      val photo=content.decodeImage("mini_${campaign.currentLevel.bonusImage}.jpg")
      val sx=i%cols*612/cols+612/cols/2;val sy=i/cols*408/rows+408/rows/2
      val location=IntArray(2);scenario.onActivity { shown.getLocationInWindow(location) }
      val scale=minOf(shown.width/800f,shown.height/600f)
      val ox=location[0]+(shown.width-800*scale)/2;val oy=location[1]+(shown.height-600*scale)/2
      val actual=screenshot.getPixel((ox+(172+sx+.5f)*scale).toInt(),(oy+((if(rotation!=null) 95 else 96)+sy+.5f)*scale).toInt())
      val expected=photo.getArgb(sx,sy)
      for(shift in listOf(16,8,0)) assertTrue("retired tile must reveal dimmed photo, not black",kotlin.math.abs(((actual ushr shift) and 255)-((expected ushr shift) and 255)*155f/255f)<26f)
     }
     if(name=="scene-resumed") {
      val location=IntArray(2)
      scenario.onActivity { shown.getLocationInWindow(location) }
      val scale=minOf(shown.width/800f,shown.height/600f)
      val ox=location[0]+(shown.width-800*scale)/2;val oy=location[1]+(shown.height-600*scale)/2
      val targetInk=(10 until 142).sumOf { x -> (120 until 320).count { y ->
       val p=screenshot.getPixel((ox+(x+.5f)*scale).toInt(),(oy+(y+.5f)*scale).toInt())
       val r=android.graphics.Color.red(p);val g=android.graphics.Color.green(p);val b=android.graphics.Color.blue(p)
       r>40 && r>2*b && 2*g>3*b
      } }
      assertTrue("objective captions must return after resume: $targetInk",targetInk>100)
     }
     if(name=="scene-paused") {
      val location=IntArray(2)
      scenario.onActivity { shown.getLocationInWindow(location) }
      val scale=minOf(shown.width/800f,shown.height/600f)
      val ox=location[0]+(shown.width-800*scale)/2;val oy=location[1]+(shown.height-600*scale)/2
      fun pixel(image:Bitmap,x:Int,y:Int)=image.getPixel((ox+(x+.5f)*scale).toInt(),(oy+(y+.5f)*scale).toInt())
      val targetInk=(10 until 142).sumOf { x -> (120 until 320).count { y ->
       val p=pixel(screenshot,x,y)
       val r=android.graphics.Color.red(p);val g=android.graphics.Color.green(p);val b=android.graphics.Color.blue(p)
       r>40 && r>2*b && 2*g>3*b
      } }
      assertEquals("original pause hides objective captions",0,targetInk)
      val ink=(150 until 800).sumOf { x -> (250 until 305).count { y ->
       val p=pixel(screenshot,x,y)
       android.graphics.Color.red(p)>190 && android.graphics.Color.green(p)>190 && android.graphics.Color.blue(p)>190
      } }
      assertTrue("original pause title missing: $ink",ink>200)
      val before=BitmapFactory.decodeFile(File(instrumentation.targetContext.getExternalFilesDir(null),"vegas-scene.png").absolutePath)
      try {
       for((x,y) in listOf(750 to 50,600 to 100,200 to 450)) {
        val a=pixel(before,x,y);val b=pixel(screenshot,x,y)
        for(shift in listOf(16,8,0)) {
         val expected=((a ushr shift) and 255)*90f/255f
         assertTrue("pause RGBA frame alpha differs",kotlin.math.abs(((b ushr shift) and 255)-expected)<=3f)
        }
       }
      } finally { before.recycle() }
     }
     File(instrumentation.targetContext.getExternalFilesDir(null),"vegas-$name.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG,100,it) }
     screenshot.recycle()
    }
    display("map")
    val menu=profile.menuRect(campaign)
    touch(MotionEvent.ACTION_DOWN,menu.centerX(),menu.centerY())
    assertEquals(0,menuRequests)
    touch(MotionEvent.ACTION_UP,menu.centerX(),menu.centerY())
    assertEquals(1,menuRequests);assertTrue(shown.isPaused)
    val menuClock=campaign.clock.elapsed
    scenario.onActivity { shown.step(2f) };assertEquals(menuClock,campaign.clock.elapsed,0f)
    scenario.onActivity { shown.resumeFromMenu() };assertFalse(shown.isPaused)
    val card=(0 until 600).asSequence().flatMap { y -> (0 until 800).asSequence().map { x -> x to y } }.first { (x,y) -> profile.sceneAt(campaign,x,y)==campaign.currentLevel.scenes.first() }
    touch(MotionEvent.ACTION_DOWN,card.first+20,card.second+20)
    assertEquals(SdaCampaignPhase.SCENE,campaign.phase)
    display("scene")
    val pauseElapsed=campaign.clock.elapsed
    val pausePoints=campaign.points
    val pauseSceneAge=campaign.currentScene!!.sinceFound
    touch(MotionEvent.ACTION_HOVER_MOVE,110,574)
    captureState("pause-hover")
    touch(MotionEvent.ACTION_DOWN,110,574)
    assertFalse("original pause activates on release",shown.isPaused)
    captureState("pause-pressed")
    touch(MotionEvent.ACTION_MOVE,-10,574)
    touch(MotionEvent.ACTION_UP,-10,574)
    assertFalse("release outside must cancel pause",shown.isPaused)
    captureState("pause-cancelled")
    touch(MotionEvent.ACTION_DOWN,110,574)
    touch(MotionEvent.ACTION_UP,110,574)
    scenario.onActivity { shown.step(5f) }
    assertEquals("pause must freeze clock",pauseElapsed,campaign.clock.elapsed,0f)
    assertEquals("pause must freeze scene",pauseSceneAge,campaign.currentScene!!.sinceFound,0f)
    assertEquals(pausePoints,campaign.points)
    captureState("scene-paused")
    touch(MotionEvent.ACTION_DOWN,475,326)
    touch(MotionEvent.ACTION_UP,475,326)
    scenario.onActivity { shown.step(1f) }
    assertEquals("resume must advance clock",pauseElapsed+1f,campaign.clock.elapsed,0f)
    assertEquals(pausePoints,campaign.points)
    captureState("scene-resumed")
    val targetScene=campaign.currentScene!!
    val firstGroup=targetScene.activeSets.first()
    for(id in firstGroup) {
     val sprite=targetScene.objects.getValue(id)
     val input=(0 until sprite.image.width*sprite.image.height).asSequence().map {
      sprite.x+it%sprite.image.width to sprite.y+it/sprite.image.width
     }.first { (x,y) -> x>=174 && targetScene.targets.firstOrNull { targetScene.objects.getValue(it).hit(x,y) }==id }
     touch(MotionEvent.ACTION_DOWN,input.first,input.second)
     assertTrue(sprite.found)
    }
    repeat(100) { campaign.advance(.04f) } // component clock, not an E2E lifecycle claim
    assertTrue(targetScene.rows.getValue(firstGroup).removed)
    assertFalse(targetScene.targetPresentation().any { it.index==0 })
    assertEquals(targetScene.activeSets.size-1,targetScene.targetPresentation().size)
    display("scene-row-retired")
    scenario.onActivity {
     val software=Bitmap.createBitmap(shown.width,shown.height,Bitmap.Config.ARGB_8888)
     shown.draw(Canvas(software))
     File(instrumentation.targetContext.getExternalFilesDir(null),"vegas-scene-row-retired-software.png").outputStream().use { software.compress(Bitmap.CompressFormat.PNG,100,it) }
     software.recycle()
    }

    val mapX=profile.returnMapRect.centerX();val mapY=profile.returnMapRect.centerY()
    touch(MotionEvent.ACTION_HOVER_MOVE,mapX,mapY)
    captureState("map-button-hover")
    touch(MotionEvent.ACTION_DOWN,mapX,mapY)
    assertEquals("MAPA must activate on release",SdaCampaignPhase.SCENE,campaign.phase)
    captureState("map-button-pressed")
    touch(MotionEvent.ACTION_MOVE,200,mapY)
    touch(MotionEvent.ACTION_UP,200,mapY)
    assertEquals("MAPA release outside must cancel",SdaCampaignPhase.SCENE,campaign.phase)
    captureState("map-button-cancelled")
    touch(MotionEvent.ACTION_DOWN,mapX,mapY)
    touch(MotionEvent.ACTION_UP,mapX,mapY)
    assertEquals(SdaCampaignPhase.MAP,campaign.phase)
    captureState("map-returned")
    repeat(requestedLevels) { level ->
     assertEquals(level,campaign.levelIndex)
     finishObjects();campaign.startBonus(content)
     val family=when(campaign.bonusGame) {
      is SdaTileRotGame -> "rotation"
      is SdaWordSearchGame -> "wordsearch"
      is SdaJigsawGame -> "jigsaw"
      is SdaTileSwapGame -> "swap"
      else -> error("unsupported bonus at level ${level+1}")
     }
     display(family)
     // Component UI coverage: contextual instructions are reached through actual menu DOWN/UP events.
     lateinit var helpDialog:android.app.Dialog
     lateinit var helpView:SdaResourceMenuView
     scenario.onActivity { activity ->
      helpDialog=android.app.Dialog(activity)
      helpView=profile.menuView(activity,campaign,SdaMenuEntry.PAUSE) { action ->
       if(action==SdaMenuAction.RESUME) helpDialog.dismiss()
      }
      helpDialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
      helpDialog.setContentView(helpView)
      helpDialog.window!!.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
      helpDialog.window!!.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
      helpDialog.show()
      helpDialog.window!!.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT,android.view.ViewGroup.LayoutParams.MATCH_PARENT)
     }
     fun helpClick(value:Int) {
      scenario.onActivity {
       val node=helpView.buttons.single { it.number("value")==value }
       val rect=helpView.buttonBounds(node);val scale=minOf(helpView.width/800f,helpView.height/600f)
       val x=(helpView.width-800*scale)/2+rect.centerX()*scale;val y=(helpView.height-600*scale)/2+rect.centerY()*scale
       for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)) {
        val event=MotionEvent.obtain(SystemClock.uptimeMillis(),SystemClock.uptimeMillis(),action,x,y,0)
        try { assertTrue(helpView.dispatchTouchEvent(event)) } finally { event.recycle() }
       }
      }
      instrumentation.waitForIdleSync()
     }
     try {
      helpClick(209)
      val (expected,doneValue)=when(campaign.bonusGame) {
       is SdaTileRotGame -> "tilerotgameinstructionsdlgoverlay" to 1025
       is SdaTileSwapGame -> "tilegameinstructionsdlgoverlay" to 1026
       is SdaWordSearchGame -> "wordsearchgameinstructionsdlgoverlay" to 1027
       is SdaJigsawGame -> "jigsawgameinstructionsdlgoverlay" to 1028
       else -> error("unexpected bonus")
      }
      scenario.onActivity { assertEquals(expected,helpView.screenId) }
      SystemClock.sleep(100)
      lateinit var pixels:Bitmap;lateinit var window:android.view.Window
      scenario.onActivity { window=helpDialog.window!!;pixels=Bitmap.createBitmap(window.decorView.width,window.decorView.height,Bitmap.Config.ARGB_8888) }
      val latch=java.util.concurrent.CountDownLatch(1);var result=-1
      android.view.PixelCopy.request(window,pixels,{ result=it;latch.countDown() },android.os.Handler(android.os.Looper.getMainLooper()))
      assertTrue(latch.await(10,java.util.concurrent.TimeUnit.SECONDS));assertEquals(android.view.PixelCopy.SUCCESS,result)
      File(instrumentation.targetContext.getExternalFilesDir(null),"vegas-help-bonus-${level+1}.png").outputStream().use { pixels.compress(Bitmap.CompressFormat.PNG,100,it) };pixels.recycle()
      helpClick(doneValue);scenario.onActivity { assertEquals("menudlg2",helpView.screenId) }
      helpClick(215);assertFalse(helpDialog.isShowing)
     } finally { scenario.onActivity { helpDialog.dismiss() } }
     captureState("bonus-level-${level+1}")
     when(val game=campaign.bonusGame) {
      is SdaTileRotGame -> {
       var captured=false
       for(i in game.tileRotations.indices) repeat(game.tileRotations[i]) {
        touch(MotionEvent.ACTION_DOWN,172+i%game.cols*612/game.cols+1,95+i/game.cols*408/game.rows+1)
        if(!captured && game.lockedTiles.any { it } && !game.isSolved) {
         captureState("rotation-retired");captured=true
        }
       }
       assertTrue("capture an earned retired row",captured)
      }
      is SdaWordSearchGame -> for((wordIndex,cells) in game.board.placements.values.withIndex()) {
       fun x(c:Int)=game.originX+c%game.cols*game.cellWidth+1
       fun y(c:Int)=game.originY+c/game.cols*game.cellHeight+1
       touch(MotionEvent.ACTION_DOWN,x(cells.first()),y(cells.first()))
       if(wordIndex==0) {
        assertTrue(game.selectedCells.isNotEmpty())
        captureState("wordsearch-selected")
        touch(MotionEvent.ACTION_MOVE,x(cells.last()),y(cells.last()))
        assertEquals(cells.toSet(),game.selectedCells.toSet())
        captureState("wordsearch-dragging")
       }
       touch(MotionEvent.ACTION_UP,x(cells.last()),y(cells.last()))
       if(wordIndex==0) {
        assertEquals(1,game.foundWords.size)
        captureState("wordsearch-locked")
       }
      }
      is SdaJigsawGame -> for(id in game.interaction.board.trayOrder) {
       val rect=game.trayRectangles().first { it.id==id };val image=game.image(id,true)
       val pixel=(0 until image.width*image.height).first { image.getAlpha(it%image.width,it/image.width)==255 }
       touch(MotionEvent.ACTION_DOWN,rect.x+pixel%image.width,rect.y+pixel/image.width)
       if(id==game.interaction.board.trayOrder.first()) {
        val before=game.interaction.board.quarterTurns.getValue(id)
        scenario.onActivity {
         val properties=Array(2) { index -> MotionEvent.PointerProperties().apply { this.id=index;toolType=MotionEvent.TOOL_TYPE_FINGER } }
         val coordinates=Array(2) { index -> MotionEvent.PointerCoords().apply { x=300f+index*30f;y=300f;pressure=1f;size=1f } }
         for(action in listOf(MotionEvent.ACTION_POINTER_DOWN or (1 shl 8),MotionEvent.ACTION_POINTER_UP or (1 shl 8))) {
          val event=MotionEvent.obtain(0,SystemClock.uptimeMillis(),action,2,properties,coordinates,0,0,1f,1f,0,0,android.view.InputDevice.SOURCE_TOUCHSCREEN,0)
          try { assertTrue(shown.onTouchEvent(event)) } finally { event.recycle() }
         }
        }
        assertNotEquals("two-finger adaptation must rotate held piece",before,game.interaction.board.quarterTurns.getValue(id))
        assertEquals(id,game.interaction.board.selected)
       }
       while(game.interaction.board.quarterTurns.getValue(id)!=0) touch(MotionEvent.ACTION_DOWN,0,0,MotionEvent.BUTTON_SECONDARY)
       val piece=game.interaction.board.pieces.getValue(id)
       touch(MotionEvent.ACTION_MOVE,piece.x+piece.width/2,piece.y+piece.height/2)
       touch(MotionEvent.ACTION_DOWN,piece.x+piece.width/2,piece.y+piece.height/2)
      }
      is SdaTileSwapGame -> for(i in game.tilePositions.indices) {
       if(game.lockedTiles[i]) continue
       val source=game.tilePositions.indexOf(i)
       touch(MotionEvent.ACTION_DOWN,172+i%game.cols*612/game.cols+1,96+i/game.cols*408/game.rows+1)
       if(!game.lockedTiles.any { it }) captureState("swap-selected")
       touch(MotionEvent.ACTION_DOWN,172+source%game.cols*612/game.cols+1,96+source/game.cols*408/game.rows+1)
       if(game.lockedTiles.any { it } && !game.isSolved) captureState("swap-retired")
      }
      else -> fail("unexpected bonus")
     }
     assertEquals("bonus level ${level+1}: ${campaign.bonusGame}",SdaCampaignPhase.LEVEL_COMPLETE,campaign.phase)
     val checkpoint=campaign.snapshot().toJson();campaign.restore(SdaCampaignState.fromJson(checkpoint),content)
     assertEquals(checkpoint,campaign.snapshot().toJson())
     display("result-${level+1}")
     val ok=checkNotNull(profile.levelCompleteRect(campaign))
     touch(MotionEvent.ACTION_DOWN,ok.centerX(),ok.centerY())
     assertEquals("result OK activates on release",SdaCampaignPhase.LEVEL_COMPLETE,campaign.phase)
     touch(MotionEvent.ACTION_UP,ok.centerX(),ok.centerY())
     val expected=if(level==levels.lastIndex) SdaCampaignPhase.FINALE_1 else SdaCampaignPhase.MAP
     assertEquals(expected,campaign.phase)
     coverage.put(org.json.JSONObject().put("level",level+1).put("family",family)
      .put("bonusResource",levels[level].bonus).put("bonusImage",levels[level].bonusImage)
      .put("availableModelScenes",org.json.JSONArray(levels[level].scenes))
      .put("bonusCapture","vegas-bonus-level-${level+1}.png")
      .put("resultCapture","vegas-result-${level+1}.png")
      .put("nextPhase",campaign.phase.name).put("points",campaign.points)
      .put("checkpoint","same-process restore; not process persistence"))
     File(instrumentation.targetContext.getExternalFilesDir(null),"vegas-level-coverage.json").writeText(coverage.toString(2))
    }
    if(requestedLevels==levels.size) {
     assertEquals(SdaCampaignPhase.FINALE_1,campaign.phase)
     display("finale-entry")
    } else assertEquals(requestedLevels+1,campaign.currentLevel.clue)
   }
  }
 }
}

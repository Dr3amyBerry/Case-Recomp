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
   val campaign=SdaCampaign(SdaLevels.parse(content.read("LEVELS_1.XUI")!!),8)
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

   ActivityScenario.launch(HomeActivity::class.java, android.app.ActivityOptions.makeBasic().setLaunchDisplayId(0).toBundle()).use { scenario ->
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
      activity.setContentView(view)
      view.onSceneSelectedListener={ selected -> view.scene=campaign.enterScene(selected,content);view.invalidate() }
      view.onReturnToMapListener={ campaign.toInvestigationMap();view.invalidate() }
      view.onNextLevelListener={ campaign.confirmLevelComplete();view.invalidate() }
     }
     instrumentation.waitForIdleSync()
     // WSA compositor and Android launch splash can outlive the UI idle queue.
     SystemClock.sleep(1000)
     instrumentation.waitForIdleSync()
     var viewport=Triple(0f,0f,1f)
     scenario.onActivity {
      val location=IntArray(2);shown.getLocationOnScreen(location)
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
     var screenshot=checkNotNull(instrumentation.uiAutomation.takeScreenshot())
     if(name=="scene-row-retired") {
      // Capture real compositor frames until remaining row ink is visible, or fail.
      repeat(8) {
       if(slots(screenshot).drop(1).any { it<=20 }) {
        screenshot.recycle();SystemClock.sleep(150)
        scenario.onActivity { shown.invalidate() };instrumentation.waitForIdleSync()
        screenshot=checkNotNull(instrumentation.uiAutomation.takeScreenshot())
       }
      }
      val ink=slots(screenshot)
      assertTrue("retired row still visible: $ink",ink.first()<5)
      assertTrue("remaining row disappeared: $ink",ink.drop(1).all { it>20 })
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
        screenshot=checkNotNull(instrumentation.uiAutomation.takeScreenshot())
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
     val screenshot=checkNotNull(instrumentation.uiAutomation.takeScreenshot())
     if(name=="scene-resumed") {
      val location=IntArray(2)
      scenario.onActivity { shown.getLocationOnScreen(location) }
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
      scenario.onActivity { shown.getLocationOnScreen(location) }
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
    repeat(4) { level ->
     finishObjects();campaign.startBonus(content)
     display(listOf("rotation","wordsearch","jigsaw","swap")[level])
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
     assertEquals(SdaCampaignPhase.MAP,campaign.phase)
    }
    assertEquals(5,campaign.currentLevel.clue)
   }
  }
 }
}

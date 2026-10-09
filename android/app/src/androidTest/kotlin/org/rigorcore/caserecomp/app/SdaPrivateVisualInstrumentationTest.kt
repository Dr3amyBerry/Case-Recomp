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

   ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
    lateinit var shown:SdaGameView
    fun touch(action:Int,x:Int,y:Int,buttons:Int=0) {
     scenario.onActivity {
      val scale=minOf(shown.width/800f,shown.height/600f)
      val properties=arrayOf(MotionEvent.PointerProperties().apply { id=0;toolType=MotionEvent.TOOL_TYPE_MOUSE })
      val coords=arrayOf(MotionEvent.PointerCoords().apply { this.x=(shown.width-800f*scale)/2+(x+.5f)*scale;this.y=(shown.height-600f*scale)/2+(y+.5f)*scale;pressure=1f;size=1f })
      val event=MotionEvent.obtain(0,SystemClock.uptimeMillis(),action,1,properties,coords,0,buttons,1f,1f,0,0,android.view.InputDevice.SOURCE_MOUSE,0)
      try { assertTrue(shown.onTouchEvent(event)) } finally { event.recycle() }
     }
    }
    fun display(name:String) {
     scenario.onActivity { activity ->
      val scene=campaign.scenes.values.firstOrNull() ?: content.loadScene("SCENE_${campaign.currentLevel.scenes.first().uppercase()}.MSL",8)
      val view=SdaGameView(activity,scene,campaign=campaign,visuals=profile)
      shown=view
      activity.setContentView(view)
      view.onSceneSelectedListener={ selected -> view.scene=campaign.enterScene(selected,content);view.invalidate() }
      view.onReturnToMapListener={ campaign.toInvestigationMap();view.invalidate() }
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
     assertTrue(screenshot.width>0 && screenshot.height>0)
     File(instrumentation.targetContext.getExternalFilesDir(null),"vegas-$name.png").outputStream().use { stream ->
      assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG,100,stream))
     }
     screenshot.recycle()
    }
    display("map")
    val card=(0 until 600).asSequence().flatMap { y -> (0 until 800).asSequence().map { x -> x to y } }.first { (x,y) -> profile.sceneAt(campaign,x,y)==campaign.currentLevel.scenes.first() }
    touch(MotionEvent.ACTION_DOWN,card.first+20,card.second+20)
    assertEquals(SdaCampaignPhase.SCENE,campaign.phase)
    display("scene")
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

    touch(MotionEvent.ACTION_DOWN,profile.returnMapRect.centerX(),profile.returnMapRect.centerY())
    assertEquals(SdaCampaignPhase.MAP,campaign.phase)
    repeat(4) { level ->
     finishObjects();campaign.startBonus(content)
     display(listOf("rotation","wordsearch","jigsaw","swap")[level])
     when(val game=campaign.bonusGame) {
      is SdaTileRotGame -> for(i in game.tileRotations.indices) repeat(game.tileRotations[i]) {
       touch(MotionEvent.ACTION_DOWN,172+i%game.cols*612/game.cols+1,95+i/game.cols*408/game.rows+1)
      }
      is SdaWordSearchGame -> for(cells in game.board.placements.values) {
       fun x(c:Int)=game.originX+c%game.cols*game.cellWidth+1
       fun y(c:Int)=game.originY+c/game.cols*game.cellHeight+1
       touch(MotionEvent.ACTION_DOWN,x(cells.first()),y(cells.first()))
       touch(MotionEvent.ACTION_UP,x(cells.last()),y(cells.last()))
      }
      is SdaJigsawGame -> for(id in game.interaction.board.trayOrder) {
       val rect=game.trayRectangles().first { it.id==id };val image=game.image(id,true)
       val pixel=(0 until image.width*image.height).first { image.getAlpha(it%image.width,it/image.width)==255 }
       touch(MotionEvent.ACTION_DOWN,rect.x+pixel%image.width,rect.y+pixel/image.width)
       while(game.interaction.board.quarterTurns.getValue(id)!=0) touch(MotionEvent.ACTION_DOWN,0,0,MotionEvent.BUTTON_SECONDARY)
       val piece=game.interaction.board.pieces.getValue(id)
       touch(MotionEvent.ACTION_MOVE,piece.x+piece.width/2,piece.y+piece.height/2)
       touch(MotionEvent.ACTION_DOWN,piece.x+piece.width/2,piece.y+piece.height/2)
      }
      is SdaTileSwapGame -> for(i in game.tilePositions.indices) {
       if(game.lockedTiles[i]) continue
       val source=game.tilePositions.indexOf(i)
       touch(MotionEvent.ACTION_DOWN,172+i%game.cols*612/game.cols+1,96+i/game.cols*408/game.rows+1)
       touch(MotionEvent.ACTION_DOWN,172+source%game.cols*612/game.cols+1,96+source/game.cols*408/game.rows+1)
      }
      else -> fail("unexpected bonus")
     }
     assertEquals("bonus level ${level+1}: ${campaign.bonusGame}",SdaCampaignPhase.LEVEL_COMPLETE,campaign.phase)
     val checkpoint=campaign.snapshot().toJson();campaign.restore(SdaCampaignState.fromJson(checkpoint),content)
     assertEquals(checkpoint,campaign.snapshot().toJson());campaign.confirmLevelComplete()
    }
    assertEquals(5,campaign.currentLevel.clue)
   }
  }
 }
}

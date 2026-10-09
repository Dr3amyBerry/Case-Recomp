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

/** Optional local commercial package; no bundled media, forced phases, indices or solve calls. */
@RunWith(AndroidJUnit4::class)
class SdaPrivateJigsawInstrumentationTest {
 @Test fun real_first_three_levels_and_jigsaw_view_inputs_reach_four() {
  val path = InstrumentationRegistry.getArguments().getString("privateSdaPackage")
  assumeTrue("supply a privateSdaPackage on the device", path != null && File(path).isFile)
  ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
   scenario.onActivity { activity ->
   val context: Context = activity
   SdaContent.open(File(path!!), SdaImageDecoder { bytes ->
    BitmapFactory.decodeByteArray(bytes,0,bytes.size)?.let { bmp -> object : SdaPixelSource {
     override val width = bmp.width; override val height = bmp.height
     override fun getArgb(px: Int, py: Int) = bmp.getPixel(px,py)
     override fun getAlpha(px: Int, py: Int) = getArgb(px,py) ushr 24
     override val nativeImage: Any = bmp
    } }
   }).use { content ->
    val campaign = SdaCampaign(SdaLevels.parse(content.read("LEVELS_1.XUI")!!),8)
    var objectiveView: SdaGameView? = null
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
        val targetView = objectiveView
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
    repeat(2) {
     finishObjects();campaign.startBonus(content)
     when(val game=campaign.bonusGame) {
      is SdaTileRotGame -> for(i in game.tileRotations.indices) repeat(game.tileRotations[i]) {
       assertTrue(campaign.clickBonus(172+i%game.cols*612/game.cols+1,95+i/game.cols*408/game.rows+1))
      }
      is SdaWordSearchGame -> for(pathCells in game.board.placements.values) {
       fun x(cell:Int)=game.originX+cell%game.cols*game.cellWidth+1
       fun y(cell:Int)=game.originY+cell/game.cols*game.cellHeight+1
       assertTrue(campaign.beginBonusSelection(x(pathCells.first()),y(pathCells.first())))
       assertTrue(campaign.endBonusSelection(x(pathCells.last()),y(pathCells.last())))
      }
      else -> fail("unexpected early bonus")
     }
     assertEquals(SdaCampaignPhase.LEVEL_COMPLETE,campaign.phase);campaign.confirmLevelComplete()
    }
    assertEquals(3,campaign.currentLevel.clue)
    val displayScene = content.loadScene("SCENE_${campaign.currentLevel.scenes.first().uppercase()}.MSL",8)
    objectiveView=SdaGameView(context,displayScene,campaign=campaign)
    objectiveView.layout(0,0,800,600)
    objectiveView.draw(Canvas(Bitmap.createBitmap(800,600,Bitmap.Config.ARGB_8888)))
    finishObjects()
    campaign.startBonus(content)
    var game=campaign.bonusGame as SdaJigsawGame
    val view=SdaGameView(context,displayScene,campaign=campaign)
    activity.setContentView(view)
    assertTrue(view.isAttachedToWindow)
    view.layout(0,0,1000,600)
    val screen=Bitmap.createBitmap(1000,600,Bitmap.Config.ARGB_8888)
    fun draw() { view.draw(Canvas(screen)) }
    fun send(action:Int,x:Int,y:Int,buttons:Int=0) {
     val properties=arrayOf(MotionEvent.PointerProperties().apply { id=0;toolType=MotionEvent.TOOL_TYPE_MOUSE })
     val coords=arrayOf(MotionEvent.PointerCoords().apply { this.x=(x+100).toFloat();this.y=y.toFloat();pressure=1f;size=1f })
     val event=MotionEvent.obtain(0,SystemClock.uptimeMillis(),action,1,properties,coords,0,buttons,1f,1f,0,0,android.view.InputDevice.SOURCE_MOUSE,0)
     try { assertTrue(view.onTouchEvent(event)) } finally { event.recycle() }
    }
    var saved=""
    view.onBonusInputListener={ saved=campaign.snapshot().toJson() }
    var resumed=false
    for(id in game.interaction.board.trayOrder) {
     val rect=game.trayRectangles().first { it.id==id }
     val pixels=game.image(id,true)
     val index=(0 until pixels.width*pixels.height).first { pixels.getAlpha(it%pixels.width,it/pixels.width)==255 }
     draw()
     assertEquals(pixels.getArgb(index%pixels.width,index/pixels.width),screen.getPixel(rect.x+index%pixels.width+100,rect.y+index/pixels.width))
     send(MotionEvent.ACTION_DOWN,rect.x+index%pixels.width,rect.y+index/pixels.width)
     send(MotionEvent.ACTION_UP,rect.x+index%pixels.width,rect.y+index/pixels.width)
     assertEquals(id,game.interaction.board.selected)
     while(game.interaction.board.quarterTurns.getValue(id)!=0) send(MotionEvent.ACTION_DOWN,0,0,MotionEvent.BUTTON_SECONDARY)
     val piece=game.interaction.board.pieces.getValue(id)
     send(MotionEvent.ACTION_MOVE,piece.x+piece.width/2,piece.y+piece.height/2)
     assertEquals(piece.x,game.interaction.board.heldLeft)
     if(!resumed && game.placementPoints>0) {
      val partial=campaign.snapshot().toJson()
      campaign.restore(SdaCampaignState.fromJson(partial),content)
      assertEquals(partial,campaign.snapshot().toJson())
      game=campaign.bonusGame as SdaJigsawGame;resumed=true
     }
     send(MotionEvent.ACTION_DOWN,piece.x+piece.width/2,piece.y+piece.height/2)
     assertEquals(campaign.snapshot().toJson(),saved)
     draw()
    }
    assertTrue(resumed);assertEquals(6000,game.placementPoints)
    assertEquals(SdaCampaignPhase.LEVEL_COMPLETE,campaign.phase)
    val result=campaign.snapshot().toJson()
    campaign.restore(SdaCampaignState.fromJson(result),content)
    assertEquals(result,campaign.snapshot().toJson())
    view.onNextLevelListener={ campaign.confirmLevelComplete() }
    send(MotionEvent.ACTION_DOWN,400,460)
    assertEquals(4,campaign.currentLevel.clue)
    assertEquals(SdaCampaignPhase.MAP,campaign.phase)
    screen.recycle()
   }
  }
  }
 }
}

package org.rigorcore.caserecomp.app
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

/** Private integration: resume the checkpoint earned by the real 25-level JVM input journey. */
@RunWith(AndroidJUnit4::class)
class SdaPrivateFirstRiddleInstrumentationTest {
 @Test fun earned_finale_checkpoint_resumes_and_eight_screen_placements_complete_first_phase() {
  val args=InstrumentationRegistry.getArguments()
  val packagePath=args.getString("privateSdaPackage")
  val checkpointPath=args.getString("earnedRiddleCheckpoint")
  assumeTrue("private package and earned checkpoint required",packagePath!=null && checkpointPath!=null && File(packagePath).isFile && File(checkpointPath).isFile)
  ActivityScenario.launch(HomeActivity::class.java).use { scenario -> scenario.onActivity { activity ->
   SdaContent.open(File(packagePath!!),SdaImageDecoder { bytes ->
    BitmapFactory.decodeByteArray(bytes,0,bytes.size)?.let { bmp -> object : SdaPixelSource {
     override val width=bmp.width;override val height=bmp.height
     override fun getArgb(px:Int,py:Int)=bmp.getPixel(px,py)
     override fun getAlpha(px:Int,py:Int)=getArgb(px,py) ushr 24
     override val nativeImage:Any=bmp
    } }
   }).use { content ->
    val campaign=SdaCampaign(SdaLevels.parse(content.read("LEVELS_1.XUI")!!),firstRiddle=SdaRiddleBinding("ENVS.MSE","firstriddle"))
    campaign.restore(SdaCampaignState.fromJson(File(checkpointPath!!).readText()),content)
    assertEquals(25,campaign.currentLevel.clue);assertEquals(SdaCampaignPhase.FINALE_1,campaign.phase)
    var game=campaign.bonusGame as SdaFirstRiddleGame
    assertEquals(0,game.interaction.board.placed.size)
    val scene=content.loadScene("SCENE_${campaign.currentLevel.scenes.first().uppercase()}.MSL",8)
    val view=SdaGameView(activity,scene,campaign=campaign)
    activity.setContentView(view);assertTrue(view.isAttachedToWindow)
    view.layout(0,0,1000,600)
    val screen=Bitmap.createBitmap(1000,600,Bitmap.Config.ARGB_8888)
    fun draw() { view.draw(Canvas(screen)) }
    fun send(x:Int,y:Int) {
     val event=MotionEvent.obtain(0,SystemClock.uptimeMillis(),MotionEvent.ACTION_DOWN,(x+100).toFloat(),y.toFloat(),0)
     try { assertTrue(view.onTouchEvent(event)) } finally { event.recycle() }
    }
    fun capture(name:String) { draw();File(activity.getExternalFilesDir(null),name).outputStream().use { assertTrue(screen.compress(Bitmap.CompressFormat.PNG,100,it)) } }
    capture("first-riddle-entry.png")
    val before=campaign.points
    var autosaves=0
    view.onBonusInputListener={ autosaves++ }
    for(piece in game.definition.pieces.filter { it.hasTarget }.sortedBy { it.placeOrder }) {
     while(game.interaction.cells().none { it.id==piece.id }) {
      val key=if(game.interaction.board.available.indexOf(piece.id)<game.interaction.firstVisible) "up" else "down"
      val arrow=game.arrowRect(key)!!
      send(arrow.x+1,arrow.y+1)
     }
     val cell=game.interaction.cells().first { it.id==piece.id }
     send(cell.x+1,cell.y+1)
     assertEquals(piece.id,game.interaction.board.selected)
     val held=campaign.snapshot().toJson()
     campaign.restore(SdaCampaignState.fromJson(held),content)
     assertEquals(held,campaign.snapshot().toJson())
     game=campaign.bonusGame as SdaFirstRiddleGame
     draw()
     val x=game.definition.backgroundX+piece.hotspotX+1;val y=game.backgroundY+piece.hotspotY+1
     assertTrue(x in 0 until 800 && y in 0 until 600)
     send(x,y);assertTrue(piece.id in game.interaction.board.placed)
     draw()
    }
    assertTrue(autosaves>=16);assertTrue(game.isSolved)
    assertEquals(8,game.interaction.board.placed.size);assertEquals(17,game.interaction.board.available.size)
    assertEquals(0,game.backgroundY);assertEquals(before,campaign.points)
    assertEquals(SdaCampaignPhase.FINALE_1,campaign.phase) // No invented phase-two transition.
    capture("first-riddle-completed.png")
    screen.recycle()
   }
  } }
 }
}

package org.rigorcore.caserecomp.app

import android.app.ActivityOptions
import android.content.Intent
import android.graphics.*
import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.rigorcore.caserecomp.sda.*
import java.io.File

/** Attached Android screen inputs with private assets; isolated component, not catalogue E2E. */
class SdaPrivateCollectibleGameplayInstrumentationTest {
 @Test fun touch_collects_key_and_chip_and_restored_scene_keeps_them_removed() {
  val instrumentation=InstrumentationRegistry.getInstrumentation()
  val args=InstrumentationRegistry.getArguments()
  val path=args.getString("privateSdaPackage")
  assumeTrue(path!=null && File(path).isFile)
  val display=args.getString("displayId","2").toInt()
  val owned=mutableListOf<Bitmap>()
  try {
   SdaContent.open(File(path!!),SdaImageDecoder { bytes ->
    BitmapFactory.decodeByteArray(bytes,0,bytes.size)?.let { bitmap ->
     owned.add(bitmap)
     object:SdaPixelSource {
      override val width=bitmap.width;override val height=bitmap.height
      override val nativeImage:Any get()=bitmap
      override fun getArgb(x:Int,y:Int)=bitmap.getPixel(x,y)
      override fun getAlpha(x:Int,y:Int)=getArgb(x,y).ushr(24)
     }
    }
   }).use { content ->
    val levels=SdaLevels.parse(requireNotNull(content.read("LEVELS_1.XUI")))
    val profile=VegasVisualProfile(content)
    val campaign=SdaCampaign(levels,seed=8,collectibleLimit=profile::collectibleLimit)
    val name=levels.first().scenes.first { sceneName ->
     SdaXui.parse(requireNotNull(content.read("SCENE_${sceneName.uppercase()}.MSL"))).collectibles.map { it.kind }.toSet().containsAll(setOf("key","chip"))
    }
    val scene=campaign.enterScene(name,content)
    val intent=Intent(instrumentation.targetContext,HomeActivity::class.java)
    ActivityScenario.launch<HomeActivity>(intent,ActivityOptions.makeBasic().setLaunchDisplayId(display).toBundle()).use { scenario ->
     scenario.onActivity { activity ->
      assertEquals(display,activity.display!!.displayId)
      val view=SdaGameView(activity,scene,campaign=campaign,visuals=profile)
      activity.setContentView(view)
      view.layout(0,0,800,600)
      val output=Bitmap.createBitmap(800,600,Bitmap.Config.ARGB_8888)
      val before=campaign.snapshot();val callbacks=mutableListOf<String>()
      view.onCollectibleFoundListener={ callbacks.add(it) }
      try {
       view.draw(Canvas(output))
       for(item in scene.collectibles) {
        val image=item.sprite.image
        val point=(0 until image.width*image.height).asSequence().map { item.sprite.x+it%image.width to item.sprite.y+it/image.width }.first { (x,y) ->
         x in 174 until 800 && y in 0 until 600 && item.sprite.hit(x,y)
        }
        for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)) {
         val event=MotionEvent.obtain(SystemClock.uptimeMillis(),SystemClock.uptimeMillis(),action,point.first.toFloat(),point.second.toFloat(),0)
         try { val accepted=view.onTouchEvent(event);if(action==MotionEvent.ACTION_DOWN) assertTrue(accepted) } finally { event.recycle() }
        }
        assertEquals(1,campaign.collectedCount(item.definition.kind))
        assertFalse(campaign.collectibleAvailable(item))
        view.draw(Canvas(output))
       }
       assertEquals(setOf("key","chip"),callbacks.toSet());assertEquals(2,callbacks.size)
       assertEquals(before.points,campaign.points);assertEquals(before.completedObjects,campaign.completedObjects)
       val checkpoint=File(activity.cacheDir,"collector-component-checkpoint.json")
       try {
        checkpoint.writeText(campaign.snapshot().toJson())
        val restored=SdaCampaign(levels,collectibleLimit=profile::collectibleLimit)
        restored.restore(SdaCampaignState.fromJson(checkpoint.readText()),content)
        assertEquals(1,restored.collectedCount("key"));assertEquals(1,restored.collectedCount("chip"))
        assertTrue(restored.currentScene!!.collectibles.none { restored.collectibleAvailable(it) })
       } finally { checkpoint.delete() }
      } finally { output.recycle() }
     }
    }
   }
  } finally { owned.forEach { it.recycle() } }
 }
}

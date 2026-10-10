package org.rigorcore.caserecomp.app

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.rigorcore.caserecomp.sda.*

/** Synthetic component ownership test, not a commercial visual or campaign fidelity test. */
@RunWith(AndroidJUnit4::class)
class SdaRasterLifetimeInstrumentationTest {
 @Test fun leaving_jigsaw_releases_owned_rasters_and_owner_without_recycling_borrowed_images() {
  val instrumentation=InstrumentationRegistry.getInstrumentation()
  instrumentation.runOnMainSync {
   val view=SdaGameView(instrumentation.targetContext,SdaScene(emptyMap(),emptyList(),emptyMap()))
   view.layout(0,0,800,600)
   val pixels=SdaArgbPixelSource(21,21,IntArray(441) { -1 })
   val game=SdaJigsawGame("test.jsw",listOf(SdaJigsawPiece("piece",200,100,21,21)),mapOf("piece" to pixels),SdaJigsawTrayDefinition(10,88,130,269,4,31),seed=8)
   val owner=SdaGameView::class.java.getDeclaredField("jigsawRasterOwner").apply { isAccessible=true }
   owner.set(view,game)
   val raster=SdaGameView::class.java.getDeclaredMethod("jigsawBitmap",SdaPixelSource::class.java).apply { isAccessible=true }
   val owned=raster.invoke(view,pixels) as Bitmap
   val borrowed=Bitmap.createBitmap(21,21,Bitmap.Config.ARGB_8888)
   val borrowedSource=object:SdaPixelSource {
    override val width=21;override val height=21
    override fun getAlpha(px:Int,py:Int)=255
    override val nativeImage:Any=borrowed
   }
   assertSame(borrowed,raster.invoke(view,borrowedSource))
   val cache=SdaGameView::class.java.getDeclaredField("jigsawBitmaps").apply { isAccessible=true }.get(view) as Map<*,*>
   assertEquals(1,cache.size)
   val target=Bitmap.createBitmap(800,600,Bitmap.Config.ARGB_8888)
   try {
    view.draw(Canvas(target))
    assertTrue("old Jigsaw raster retained after leaving puzzle",cache.isEmpty())
    assertNull("old Jigsaw state retained after leaving puzzle",owner.get(view))
    assertTrue(owned.isRecycled)
    assertFalse("borrowed native image was recycled",borrowed.isRecycled)
   } finally { target.recycle();borrowed.recycle();if(!owned.isRecycled) owned.recycle() }
  }
 }
}

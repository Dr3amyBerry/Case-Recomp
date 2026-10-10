package org.rigorcore.caserecomp.app

import android.graphics.*
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.rigorcore.caserecomp.sda.*
import java.io.File

/** Resource composition evidence, not a Windows fidelity certification. */
class SdaPrivateObjectsCompleteInstrumentationTest {
 @Test fun original_completion_panel_and_button_use_the_same_centered_resource_geometry() {
  val path=InstrumentationRegistry.getArguments().getString("privateSdaPackage")
  assumeTrue(path!=null && File(path).isFile)
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
    val profile=VegasVisualProfile(content)
    val output=Bitmap.createBitmap(800,600,Bitmap.Config.ARGB_8888)
    try {
     assertTrue(profile.drawObjectsComplete(Canvas(output)))
     val button=requireNotNull(profile.objectsCompleteRect())
     assertEquals(318,button.left);assertEquals(348,button.top)
     assertTrue(button.width()>0 && button.height()>0)
     assertTrue(Rect(200,175,600,425).contains(button))
     assertNotEquals(0,output.getPixel(button.centerX(),button.centerY()).ushr(24))
     assertEquals(0,output.getPixel(0,0))
    } finally { output.recycle() }
   }
  } finally { owned.forEach { it.recycle() } }
 }
}

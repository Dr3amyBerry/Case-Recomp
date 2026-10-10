package org.rigorcore.caserecomp.app

import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.rigorcore.caserecomp.sda.*
import java.io.File

/** Private original resource loading/alpha checks only; does not certify collection gameplay or visuals. */
class SdaPrivateCollectibleResourcesInstrumentationTest {
 @Test fun original_scene_collectibles_decode_and_keep_alpha_hit_geometry() {
  val path=InstrumentationRegistry.getArguments().getString("privateSdaPackage")
  assumeTrue(path!=null && File(path).isFile)
  val bitmaps=mutableListOf<android.graphics.Bitmap>()
  try {
   SdaContent.open(File(path!!),SdaImageDecoder { bytes->
    BitmapFactory.decodeByteArray(bytes,0,bytes.size)?.let { bitmap->
     bitmaps.add(bitmap)
     object:SdaPixelSource {
      override val width=bitmap.width
      override val height=bitmap.height
      override val nativeImage:Any get()=bitmap
      override fun getArgb(px:Int,py:Int)=bitmap.getPixel(px,py)
      override fun getAlpha(x:Int,y:Int)=if(x in 0 until width && y in 0 until height) bitmap.getPixel(x,y).ushr(24) else 0
     }
    }
   }).use { content->
    val profile=VegasVisualProfile(content)
    val document=SdaUiDocument(requireNotNull(content.read("ENVS.MSE")),content.loadStrings("ENVS.MSE"))
    for(count in listOf(0,1,5,25)) {
     val output=android.graphics.Bitmap.createBitmap(800,600,android.graphics.Bitmap.Config.ARGB_8888)
     try {
      profile.drawCollectionMeters(android.graphics.Canvas(output),count,count)
      for(id in listOf("keyfull","chipsfull")) {
       val node=document.component(id)
       val image=content.decodeImage(document.texture(node.attributes.getValue("tex")))
       val top=if(id=="keyfull") VegasCollectionMeterRules.keyCropTop(count) else VegasCollectionMeterRules.chipCropTop(count)
       for(y in 0 until image.height) for(x in 0 until minOf(node.number("w"),image.width)) {
        val expected=if(y<top || (id=="chipsfull" && count==0) || image.getAlpha(x,y)==0) 0 else image.getArgb(x,y)
        assertEquals("$id count=$count pixel$x,$y",expected,output.getPixel(node.number("x")+x,node.number("y")+y))
       }
      }
     } finally { output.recycle() }
    }
    val scene=content.loadScene("SCENE_VAULT.MSL",seed=7L)
    assertEquals(setOf("chip","key"),scene.collectibles.map { it.definition.kind }.toSet())
    assertEquals(2,scene.collectibles.size)
    for(item in scene.collectibles) {
     val sprite=item.sprite;val definition=item.definition
     assertEquals(definition.x,sprite.x);assertEquals(definition.y,sprite.y)
     assertFalse(sprite.identity in scene.objects)
     assertFalse(scene.targetSets.any { sprite.identity in it })
     val image=sprite.image
     val pixel=(0 until image.width*image.height).first { image.getAlpha(it%image.width,it/image.width)>0 }
     assertTrue(sprite.hit(sprite.x+pixel%image.width,sprite.y+pixel/image.width))
     assertFalse(sprite.hit(sprite.x-1,sprite.y-1))
    }
   }
  } finally { bitmaps.forEach { it.recycle() } }
 }
}

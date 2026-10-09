package org.rigorcore.caserecomp.sda
import org.junit.Assert.*
import org.junit.Test
class SdaJigsawRasterUnitTest {
 @Test fun jpeg_intensity_not_opaque_alpha_controls_crop_and_transformed_hit_pixels() {
  val background = SdaArgbPixelSource(3, 2, intArrayOf(0xff123456.toInt(),0xffabcdef.toInt(),0xff112233.toInt(),0xff445566.toInt(),0xff778899.toInt(),0xff334455.toInt()))
  val mask = SdaArgbPixelSource(2, 2, intArrayOf(0xff000000.toInt(),0xff808080.toInt(),0xffffffff.toInt(),0xff404040.toInt()))
  val crop = SdaJigsawRaster.crop(background, mask, 1, 0)
  assertEquals(0, crop.getAlpha(0,0))
  assertEquals(0x80112233.toInt(), crop.getArgb(1,0))
  assertEquals(0xff778899.toInt(), crop.getArgb(0,1))
  val rotated = SdaJigsawRaster.transform(crop, 1, 1f)
  assertEquals(255, rotated.getAlpha(0,0))
  assertEquals(0, rotated.getAlpha(1,0))
  assertEquals(128, rotated.getAlpha(1,1))
  assertThrows(IllegalArgumentException::class.java) { SdaJigsawRaster.crop(background, mask, 2, 1) }
 }
}

package org.rigorcore.caserecomp.sda
import org.junit.Assert.*
import org.junit.Test

class SdaAtlasFontUnitTest {
 private fun image():SdaPixelSource=object:SdaPixelSource {
  override val width=12;override val height=4
  override fun getArgb(px:Int,py:Int)=getAlpha(px,py) shl 24
  override fun getAlpha(px:Int,py:Int)=when(px) { 1,2 -> 255;4,5,6 -> 5;8 -> 4;10,11 -> 255;else -> 0 }
 }
 @Test fun native_alpha_threshold_closing_column_spacing_and_duplicate_charset() {
  val font=SdaAtlasFont(image(),"ABA",.63f,7)
  assertEquals(listOf(SdaGlyphRun(1,2),SdaGlyphRun(4,3)),font.runs)
  assertNull(font.glyph('A')) // duplicate overwrites index 0 with index 2, which is unclosed
  assertEquals(SdaGlyphRun(4,3),font.glyph('B'))
  assertEquals(1,font.advance('B'));assertEquals(4,font.advance(' '))
 }
 @Test fun alignment_omits_kern_and_escaped_lines_use_atlas_height() {
  val font=SdaAtlasFont(image(),"AB",1f,2,kerns=listOf(SdaGlyphKern(65,66,3f)))
  val placements=font.layout("AB\\nB",10,20,1,2)
  assertEquals(listOf(8,13,9),placements.map { it.x })
  assertEquals(listOf(18,18,21),placements.map { it.y })
  assertEquals(5,font.measure("AB"))
 }
 @Test fun wide_atlas_retains_pixel_budget_without_square_texture_limit() {
  SdaImageBudget.check(9639,51)
  for ((width,height) in listOf(0 to 1,16385 to 1,8192 to 4096)) {
   try { SdaImageBudget.check(width,height);fail("must reject $width x $height") }
   catch(expected:IllegalArgumentException) { assertEquals("image dimensions exceed budget",expected.message) }
  }
 }
}

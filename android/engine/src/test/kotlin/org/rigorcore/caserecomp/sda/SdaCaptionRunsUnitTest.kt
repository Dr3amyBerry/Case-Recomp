package org.rigorcore.caserecomp.sda
import org.junit.Assert.*
import org.junit.Test
class SdaCaptionRunsUnitTest {
 @Test fun native_two_digit_vertical_advance_matches_python_font_fixture() {
  assertEquals(listOf(SdaCaptionRun("A",0),SdaCaptionRun("B",12)),SdaCaptionRuns.parse("A\\sa12B",20))
  assertEquals(listOf(SdaCaptionRun("A",0),SdaCaptionRun("B",15)),SdaCaptionRuns.parse("A\\nB",20))
  assertThrows(IllegalArgumentException::class.java) { SdaCaptionRuns.parse("A\\sa123B",20) }
  assertThrows(IllegalArgumentException::class.java) { SdaCaptionRuns.parse("A\\sbB",20) }
 }
}

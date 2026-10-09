package org.rigorcore.caserecomp.sda
import org.junit.Assert.*
import org.junit.Test
class SdaButtonPresentationUnitTest {
 private val node=SdaUiNode("button",mapOf("texnormal" to "up","texhover" to "over","texpushed" to "down","texdisabled" to "off","font" to "base","fontnormal" to "normal","fonthover" to "hover","fontpushed" to "pressed","globalcaptionoffsetx" to "-4","globalcaptionoffsety" to "1","captionoffsetx" to "2","captionoffsety" to "3"),emptyList())
 @Test fun states_select_independent_fonts_and_pressed_caption_offsets() {
  assertEquals(SdaButtonPresentation("up","normal",-4,1),node.buttonPresentation(SdaButtonState.NORMAL))
  assertEquals(SdaButtonPresentation("over","hover",-4,1),node.buttonPresentation(SdaButtonState.HOVER))
  assertEquals(SdaButtonPresentation("down","pressed",-2,4),node.buttonPresentation(SdaButtonState.PRESSED))
  assertEquals(SdaButtonPresentation("off","base",-4,1),node.buttonPresentation(SdaButtonState.DISABLED))
  assertEquals(node.buttonPresentation(SdaButtonState.HOVER),node.buttonPresentation(SdaButtonState.SELECTED))
 }
 @Test fun absent_variant_uses_base_font_but_does_not_invent_a_texture() {
  val sparse=node.copy(attributes=node.attributes-"texhover"-"fonthover")
  assertEquals(SdaButtonPresentation(null,"base",-4,1),sparse.buttonPresentation(SdaButtonState.HOVER))
 }
}

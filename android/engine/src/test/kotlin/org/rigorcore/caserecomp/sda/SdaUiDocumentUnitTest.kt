package org.rigorcore.caserecomp.sda
import org.junit.Assert.*
import org.junit.Test
class SdaUiDocumentUnitTest {
 @Test fun component_bindings_preserve_order_coordinates_and_resolved_captions() {
  val doc=SdaUiDocument("<xui><texture id=\"art\" uri=\"own.png\"/><container id=\"panel\" x=\"4\"><image x=\"7\" tex=\"art\"/><label x=\"2\" caption=\"@OWN\"/></container></xui>".toByteArray(),mapOf("OWN" to "Own caption"))
  val panel=doc.component("panel")
  assertEquals(4,panel.number("x"));assertEquals(listOf("image","label"),panel.children.map { it.type })
  assertEquals("own.png",doc.texture(panel.children[0].attributes.getValue("tex")))
  assertEquals("Own caption",doc.caption(panel.children[1]))
  assertThrows(IllegalArgumentException::class.java) { doc.component("missing") }
  assertThrows(IllegalArgumentException::class.java) { doc.texture("missing") }
 }
 @Test fun ambiguous_textures_fail_instead_of_silent_replacement() {
  val doc=SdaUiDocument("<xui><texture id=\"t\" uri=\"one.png\"/><texture id=\"t\" uri=\"two.png\"/></xui>".toByteArray(),emptyMap())
  assertThrows(IllegalArgumentException::class.java) { doc.texture("t") }
 }
}

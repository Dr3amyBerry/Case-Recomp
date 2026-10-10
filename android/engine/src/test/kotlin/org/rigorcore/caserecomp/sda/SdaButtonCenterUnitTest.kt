package org.rigorcore.caserecomp.sda
import org.junit.Assert.*
import org.junit.Test
class SdaButtonCenterUnitTest {
 @Test fun native_center_divides_parent_and_child_separately_and_preserves_explicit_x_without_parent() {
  val node=SdaUiNode("allbutton",mapOf("x" to "118","center" to "true"),emptyList())
  assertEquals(158,node.buttonX(400,84))
  assertEquals(158,node.buttonX(400,85))
  assertEquals(118,node.buttonX(null,84))
  assertEquals(118,node.copy(attributes=node.attributes+("center" to "false")).buttonX(400,84))
 }
}

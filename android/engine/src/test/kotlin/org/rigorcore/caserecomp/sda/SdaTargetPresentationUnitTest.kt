package org.rigorcore.caserecomp.sda
import org.junit.Assert.*
import org.junit.Test

class SdaTargetPresentationUnitTest {
 @Test fun completed_row_fades_in_its_slot_without_shifting_other_targets() {
  val groups=listOf(listOf("a","b"),listOf("c"))
  val sprites=listOf("a","b","c").mapIndexed { i,id -> id to SdaSprite(id,200+i*10,100,SdaBufferPixelSource(2,2,ByteArray(4) { 255.toByte() })) }.toMap()
  val scene=SdaScene(sprites,groups,mapOf(groups[0] to listOf("Two samples","One sample"),groups[1] to listOf("Other target")))
  assertEquals(listOf("Two samples","Other target"),scene.targetPresentation().map { it.caption })
  scene.click(200,100)
  assertEquals("One sample",scene.targetPresentation()[0].caption)
  scene.click(210,100)
  assertEquals("One sample",scene.targetPresentation()[0].caption)
  var faded=false
  repeat(150) {
   scene.advance(.04f)
   if(scene.targetPresentation().any { row -> row.index==0 && row.alpha>0f && row.alpha<1f }) faded=true
  }
  assertTrue(faded)
  val visible=scene.targetPresentation()
  assertEquals(1,visible.size);assertEquals(1,visible[0].index)
  assertEquals("Other target",visible[0].caption)
 }
}

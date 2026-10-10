package org.rigorcore.caserecomp.sda

import org.junit.Assert.*
import org.junit.Test

class SdaCollectibleResourcesUnitTest {
 private fun xml(body:String)="<scene xmlns:mpi='urn:sda'><texture id='asset' uri='own.png'/>$body</scene>".toByteArray()
 @Test fun anonymous_collectibles_preserve_source_geometry_and_layers_without_becoming_targets() {
  val doc=SdaXui.parse(xml("<image tex='asset'/><mpi:chip x='42' y='19' w='40' h='66' tex='asset'/><mpi:key x='70' y='21' endangle='12.5' tex='asset'/><image tex='asset'/>"))
  assertEquals(2,doc.images.size);assertTrue(doc.eyeSpyImages.isEmpty());assertTrue(doc.targetSets.isEmpty())
  assertEquals(listOf("chip","key"),doc.collectibles.map { it.kind })
  assertEquals(listOf(1,1),doc.collectibles.map { it.imageIndex })
  assertEquals(42,doc.collectibles[0].x);assertEquals(19,doc.collectibles[0].y)
  assertEquals("40",doc.collectibles[0].attributes["w"])
  assertEquals("12.5",doc.collectibles[1].attributes["endangle"])
  assertEquals(2,doc.collectibles.map { it.id }.toSet().size)
 }
 @Test fun collector_identity_is_stable_across_parse_and_retains_explicit_id() {
  val bytes=xml("<mpi:key tex='asset'/><mpi:chip id='own-chip' tex='asset'/>")
  assertEquals(SdaXui.parse(bytes).collectibles,SdaXui.parse(bytes).collectibles)
  assertEquals("own-chip",SdaXui.parse(bytes).collectibles.last().id)
 }
 @Test(expected=IllegalArgumentException::class) fun collector_unknown_texture_is_rejected() { SdaXui.parse(xml("<mpi:chip tex='absent'/>")) }
 @Test(expected=IllegalArgumentException::class) fun collector_missing_texture_is_rejected() { SdaXui.parse(xml("<mpi:key/>")) }
 @Test(expected=IllegalArgumentException::class) fun collector_invalid_coordinates_are_rejected() { SdaXui.parse(xml("<mpi:key tex='asset' x='NaN'/>")) }
}

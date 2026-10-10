package org.rigorcore.caserecomp.sda
import org.junit.Assert.*
import org.junit.Test
class VegasCollectionMeterRulesUnitTest {
 @Test fun key_uses_original_float_constants_at_x87_precision_before_integer_truncation() {
  for((count,top) in listOf(0 to 50,1 to 49,5 to 42,10 to 33,25 to 6)) assertEquals(top,VegasCollectionMeterRules.keyCropTop(count))
 }
 @Test fun chip_uses_native_ratio_and_preserves_the_original_top_margin() {
  assertEquals(43,VegasCollectionMeterRules.chipCropTop(0))
  assertEquals(42,VegasCollectionMeterRules.chipCropTop(1))
  assertEquals(26,VegasCollectionMeterRules.chipCropTop(12))
  assertEquals(6,VegasCollectionMeterRules.chipCropTop(25))
 }
 @Test fun invalid_counts_are_rejected() {
  for(count in listOf(-1,26)) {
   try { VegasCollectionMeterRules.keyCropTop(count);fail() } catch(expected:IllegalArgumentException) { }
   try { VegasCollectionMeterRules.chipCropTop(count);fail() } catch(expected:IllegalArgumentException) { }
  }
 }
}

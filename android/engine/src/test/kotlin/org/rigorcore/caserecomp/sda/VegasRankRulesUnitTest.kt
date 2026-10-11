package org.rigorcore.caserecomp.sda

import org.junit.Assert.assertEquals
import org.junit.Test

class VegasRankRulesUnitTest {
 @Test fun native_windows_and_gaps() {
  assertEquals(0,VegasRankRules.rankFor(0,0,0))
  assertEquals(1,VegasRankRules.rankFor(1,0,0))
  assertEquals(1,VegasRankRules.rankFor(249999,0,0))
  // The native comparisons are strict on both sides, leaving one-point gaps.
  assertEquals(0,VegasRankRules.rankFor(250000,0,0))
  assertEquals(0,VegasRankRules.rankFor(250001,0,0))
  assertEquals(2,VegasRankRules.rankFor(250002,0,0))
  assertEquals(14,VegasRankRules.rankFor(8499999,25,25))
  assertEquals(0,VegasRankRules.rankFor(9000000,24,25))
  assertEquals(15,VegasRankRules.rankFor(9000000,25,25))
 }
 @Test fun displayed_rank_never_drops_below_stored_or_one() {
  assertEquals(1,VegasRankRules.displayed(0,0,0,0))
  assertEquals(3,VegasRankRules.displayed(3,250000,0,0))
  assertEquals(2,VegasRankRules.displayed(1,300000,0,0))
 }
}

package org.rigorcore.caserecomp.app

import org.junit.Assert.assertEquals
import org.junit.Test
import org.rigorcore.caserecomp.lingo.LingoLiteralParser
import org.rigorcore.caserecomp.lingo.LingoValue.*
import org.rigorcore.caserecomp.lingo.literalText

class PrimeGemCheckpointTest {
    @Test fun unused_gem_fields_round_trip_with_points_and_board_state() {
        val state = LList(mutableListOf(LInt(1000),
            LPropList(mutableListOf(LSymbol("m_circleArray") to LList(mutableListOf(LInt(0), LInt(1))),
                LSymbol("m_gemPullArray") to Void, LSymbol("m_state") to LSymbol("setGem"))),
            LList(mutableListOf(LPoint(LInt(20), LInt(40))))))
        assertEquals(state.literalText(), LingoLiteralParser.parseOrVoid(PrimeGemCheckpoint.encode(state)).literalText())
    }
}

package org.rigorcore.caserecomp.sda

import org.junit.Assert.*
import org.junit.Test

class SdaXmlUnitTest {
    private fun level(attrs: String = "clue=\"1\" time=\"1320\" objects=\"18\" scenes=\"one,two\"") =
        "<xui><mpi:levels><mpi:level $attrs levelname=\"Sample\" bonus=\"sample.trg\"/></mpi:levels></xui>".toByteArray()

    @Test fun valid_bom_and_literal_mpi_prefix_are_preserved() {
        assertEquals(18, SdaLevels.parse(byteArrayOf(-17, -69, -65) + level()).single().objects)
    }
    @Test fun level_parser_rejects_entities_oversize_and_invalid_required_values() {
        val dtd = "<!DOCTYPE xui [<!ENTITY n '18'>]><xui><level clue=\"1\" time=\"1\" objects=\"&n;\" scenes=\"one\"/></xui>".toByteArray()
        for (raw in listOf(dtd, ByteArray(4_000_001) { 32 }, level("clue=\"1\" objects=\"18\" scenes=\"one\""),
            level("clue=\"1\" time=\"NaN\" objects=\"18\" scenes=\"one\""),
            level("clue=\"1\" time=\"1\" objects=\"18\" scenes=\"one,one\""))) {
            assertThrows(IllegalArgumentException::class.java) { SdaLevels.parse(raw) }
        }
    }
    @Test fun scene_parser_rejects_excessive_nesting() {
        val raw = ("<xui>" + "<container>".repeat(150) + "</container>".repeat(150) + "</xui>").toByteArray()
        assertThrows(IllegalArgumentException::class.java) { SdaXui.parse(raw) }
    }
}

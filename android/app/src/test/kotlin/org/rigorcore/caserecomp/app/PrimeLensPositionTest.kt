package org.rigorcore.caserecomp.app

import org.junit.Assert.*
import org.junit.Test

class PrimeLensPositionTest {
    @Test fun visor_starts_centered_and_stays_at_last_scene_position() {
        val lens = PrimeLensPosition()
        val scene = Any()
        assertEquals(330 to 300, lens.update(scene, -800, -600))
        assertEquals(120 to 240, lens.update(scene, 120, 240))
        assertEquals(120 to 240, lens.update(scene, -800, -600))
        assertEquals(120 to 240, lens.update(scene, 720, 300))
        assertEquals(330 to 300, lens.update(Any(), 120, 240))
        assertNull(lens.update(null, 120, 240))
        assertEquals(330 to 300, lens.update(scene, 120, 240))
    }
}

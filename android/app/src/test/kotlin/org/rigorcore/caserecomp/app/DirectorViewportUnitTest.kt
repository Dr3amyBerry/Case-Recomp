package org.rigorcore.caserecomp.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** No Android dependencies: mapping stage input must agree with aspect-fit rendering. */
class DirectorViewportUnitTest {
    @Test fun landscape_letterbox_remaps_touch_with_matching_viewport() {
        val vp = DirectorViewport(800, 600)
        val box = vp.fit(1920, 1080)
        assertEquals(240f, box.left)
        assertEquals(0f, box.top)
        assertEquals(1440f, box.width)
        assertEquals(1080f, box.height)
        assertEquals(0 to 0, vp.stagePoint(240f, 0f, 1920, 1080))
        assertEquals(400 to 300, vp.stagePoint(960f, 540f, 1920, 1080))
        assertEquals(799 to 599, vp.stagePoint(1679f, 1079f, 1920, 1080))
        assertNull(vp.stagePoint(100f, 500f, 1920, 1080))
        assertNull(vp.stagePoint(1680f, 500f, 1920, 1080))
    }

    @Test fun portrait_letterboxing_is_symmetric_and_empty_view_is_safe() {
        val vp = DirectorViewport(800, 600)
        val box = vp.fit(600, 1000)
        assertEquals(600f, box.width)
        assertEquals(450f, box.height)
        assertEquals(275f, box.top)
        assertEquals(0 to 0, vp.stagePoint(0f, 275f, 600, 1000))
        assertEquals(400 to 300, vp.stagePoint(300f, 500f, 600, 1000))
        assertNull(vp.stagePoint(300f, 200f, 600, 1000))
        assertNull(vp.stagePoint(0f, 0f, 0, 1000))
    }
}

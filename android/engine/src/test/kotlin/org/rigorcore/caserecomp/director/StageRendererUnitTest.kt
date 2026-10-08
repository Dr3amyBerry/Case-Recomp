package org.rigorcore.caserecomp.director

import org.junit.Assert.assertEquals
import org.junit.Test
import org.rigorcore.caserecomp.flash.DialogSwf
import org.rigorcore.caserecomp.lingo.LingoBundle
import org.rigorcore.caserecomp.lingo.LingoValue
import org.rigorcore.caserecomp.lingo.LingoValue.LInt
import org.rigorcore.caserecomp.lingo.LingoValue.LString

class StageRendererUnitTest {
    private val o = DirectorMovie.SPRITE_CHANNEL_OFFSET
    private val red = 0xFFFF0000.toInt()
    private val blue = 0xFF0000FF.toInt()

    private fun movie(textBack: LingoValue = LInt(0)) = DirectorMovie(
        stageWidth = 40, stageHeight = 20, tempo = 30, labels = emptyList(),
        castLibs = listOf(CastLibData(1, "Internal", "")),
        internalCast = CastFile("", listOf(
            MemberData(1, "bg", "bitmap", width = 40, height = 20, regX = 20, regY = 10),
            MemberData(2, "half", "bitmap", width = 4, height = 2, regX = 0, regY = 0),
            MemberData(3, "box", "shape", width = 4, height = 4),
            MemberData(4, "label", "xtra", xtra = "text", text = "Hi"),
            MemberData(5, "dialog", "xtra", xtra = "flash"),
        ).associateBy { it.number }),
        externalCasts = emptyMap(), frameCount = 1, channelCount = 5 + o,
        sprites = listOf(
            SpriteRun(1, 1, 1 + o, 0, 1, 1, 20, 10, 40, 20, 0, false, false),
            SpriteRun(1, 1, 2 + o, 0, 1, 2, 0, 0, 8, 4, 0, false, false),
            SpriteRun(1, 1, 3 + o, 0, 1, 3, 30, 0, 4, 4, 0, false, false),
            SpriteRun(1, 1, 4 + o, 0, 1, 4, 30, 10, 6, 4, 0, false, false, backColor = textBack),
        ),
        spans = (1..4).map { SpanData(1, 1, it + o, emptyList()) },
    )

    private val media = object : DirectorMedia {
        override fun image(castFile: String, member: MemberData) = when (member.name) {
            "bg" -> LingoImage(40, 20, 32, IntArray(800) { blue })
            // Left half transparent, right half red.
            "half" -> LingoImage(4, 2, 32, IntArray(8) { if (it % 4 < 2) 0 else red })
            else -> null
        }
        override fun flash(castFile: String, member: MemberData) = if (member.name == "dialog") DialogSwf.bytes else null
    }

    @Test fun composites_bitmaps_shapes_text_and_flash() {
        val rt = DirectorRuntime(movie(), LingoBundle(emptyList(), emptyList()), media, clock = { 0L })
        rt.start()
        val text = TextRasterizer { member, w, h -> LingoImage(w, h, 32, IntArray(w * h) { if (member.text == "Hi") 0xFF00FF00.toInt() else 0 }) }
        val renderer = StageRenderer(rt, text)
        var frame = renderer.render()
        fun px(x: Int, y: Int) = frame.pixels[y * 40 + x]
        assertEquals("background", blue, px(10, 15))
        assertEquals("scaled 2x, transparent half shows through", blue, px(1, 1))
        assertEquals(red, px(6, 1))
        assertEquals("shape foreColor 255 is black", 0xFF000000.toInt(), px(31, 1))
        assertEquals("text from the platform rasterizer", 0xFF00FF00.toInt(), px(32, 12))

        rt.sprite(2).setProp("blend", LInt(50))
        rt.sprite(2).setProp("flipH", LInt(1))
        frame = renderer.render()
        assertEquals("flipped: red now on the left, half blended over blue", LingoImage.blendPixel(blue, red, 127), px(1, 1))
        assertEquals(blue, px(6, 1))

        // A Flash sprite draws its movie shapes into the sprite rectangle.
        rt.sprite(3).setProp("member", LString("dialog"))
        rt.sprite(3).setProp("rect", LingoValue.LRect(LInt(0), LInt(0), LInt(40), LInt(20)))
        rt.vm.callMethod(rt.sprite(3), "goToFrame", listOf(rt.sprite(3), LString("open")))
        rt.tick()
        frame = renderer.render()
        // ok_mc's 20x10 red box sits at (30,40) in a 200x100 movie: stage (6..10, 8..10).
        assertEquals(red, px(7, 8))
        assertEquals(blue, px(12, 15))
    }

    @Test fun score_colours_colorize_text_white_to_back_and_black_to_fore() {
        val navy = 0xFF20284B.toInt()
        val colored = movie(textBack = LString("#20284b"))
        val rt = DirectorRuntime(colored, LingoBundle(emptyList(), emptyList()), media, clock = { 0L })
        rt.start()
        // Left column white, right column black text pixels.
        val text = TextRasterizer { _, w, h -> LingoImage(w, h, 32, IntArray(w * h) { if (it % w < 3) 0xFFFFFFFF.toInt() else 0xFF000000.toInt() }) }
        val frame = StageRenderer(rt, text).render()
        assertEquals("white shows as the sprite back colour", navy, frame.pixels[12 * 40 + 31])
        assertEquals("black stays the fore colour", 0xFF000000.toInt(), frame.pixels[12 * 40 + 34])
    }
}

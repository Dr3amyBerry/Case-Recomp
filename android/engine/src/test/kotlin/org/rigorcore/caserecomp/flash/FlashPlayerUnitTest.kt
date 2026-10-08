package org.rigorcore.caserecomp.flash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.rigorcore.caserecomp.flash.SwfTestBuilder.action
import org.rigorcore.caserecomp.flash.SwfTestBuilder.function
import org.rigorcore.caserecomp.flash.SwfTestBuilder.label
import org.rigorcore.caserecomp.flash.SwfTestBuilder.op
import org.rigorcore.caserecomp.flash.SwfTestBuilder.place
import org.rigorcore.caserecomp.flash.SwfTestBuilder.push
import org.rigorcore.caserecomp.flash.SwfTestBuilder.showFrame
import org.rigorcore.caserecomp.flash.SwfTestBuilder.solidShape
import org.rigorcore.caserecomp.flash.SwfTestBuilder.sprite
import org.rigorcore.caserecomp.flash.SwfTestBuilder.translate
import java.util.zip.Deflater

/** A dialog movie in the style of Director UI assets: open label, OK button clip, close, then a getURL event. */
object DialogSwf {
    private const val OK = 2
    val bytes: ByteArray by lazy {
        SwfTestBuilder.movie(200, 100, 5,
            solidShape(1, 20, 10, 0xFF0000),
            // Button clip: frame 1 "out" stops, frame 2 "over" stops.
            sprite(OK, 2,
                label("out"), place(1, 1, translate(0, 0)), action(op(0x07)), showFrame(),
                label("over"), action(op(0x07)), showFrame()),
            // Root frame 1: stopped and empty.
            action(op(0x07)), showFrame(),
            // Frame 2 "open": place ok_mc and give it handlers.
            label("open"), place(1, OK, translate(30, 40), name = "ok_mc"),
            action(
                push("ok_mc"), op(0x1C), push("onPress"),
                function(push("close", 1, "_root"), op(0x1C), push("gotoAndPlay"), op(0x52), op(0x17)),
                op(0x4F),
                push("ok_mc"), op(0x1C), push("onRollOver"),
                function(push("over", 1, "this"), op(0x1C), push("gotoAndStop"), op(0x52), op(0x17),
                    push("snd", 0, "Sound"), op(0x40), op(0x1D), push("click", 1, "snd"), op(0x1C), push("attachSound"), op(0x52), op(0x17),
                    push(0, "snd"), op(0x1C), push("start"), op(0x52), op(0x17)),
                op(0x4F)),
            showFrame(),
            action(op(0x07)), showFrame(),
            label("close"), showFrame(),
            // Frame 5: tell the container, then rewind to frame 1.
            action(push("event: done,", 7), op(0x4B), op(0x47), push(""), op(0x9A, byteArrayOf(0)), op(0x81, SwfTestBuilder.u16(0))),
            SwfTestBuilder.tag(28, SwfTestBuilder.u16(1)),
            showFrame())
    }
}

class FlashPlayerUnitTest {
    private val urls = mutableListOf<String>()
    private val sounds = mutableListOf<String>()
    private val warnings = mutableListOf<String>()
    private val host = object : FlashHost {
        override fun getURL(url: String, target: String) { urls += url }
        override fun sound(name: String) { sounds += name }
        override fun warn(message: String) { warnings += message }
    }

    @Test fun parses_header_shapes_sprites_and_labels() {
        val movie = SwfParser.parse(DialogSwf.bytes)
        assertEquals(6, movie.version)
        assertEquals(SwfRect(0.0, 0.0, 200.0, 100.0), movie.bounds)
        assertEquals(30.0, movie.frameRate, 0.0)
        val shape = movie.characters[1] as SwfShape
        assertEquals(SwfRect(0.0, 0.0, 20.0, 10.0), shape.bounds)
        assertEquals(SwfFill.Solid(0xFFFF0000.toInt()), shape.fill)
        assertEquals(2, (movie.characters[2] as SwfSprite).timeline.frames.size)
        assertEquals(2, movie.root.labelFrame("OPEN"))
        assertEquals(5, movie.root.frames.size)
        val compressed = SwfParser.parse(SwfTestBuilder.movie(20, 20, 1, solidShape(3, 5, 5, 0x00FF00), showFrame(), compress = true))
        assertTrue(compressed.characters[3] is SwfShape)
        assertThrows(FlashError::class.java) { SwfParser.parse("GIF89a__".toByteArray()) }
    }

    @Test fun dialog_opens_closes_on_press_and_reports_to_the_container() {
        val player = FlashPlayer(SwfParser.parse(DialogSwf.bytes), host)
        assertEquals(1, player.root.frame)
        assertFalse(player.root.playing)
        assertTrue(player.drawList().isEmpty())

        player.gotoAndPlay("open")
        assertTrue(player.pendingGoto)
        player.advance()
        assertEquals(2, player.root.frame)
        val draw = player.drawList().single()
        assertEquals(30.0, draw.matrix.tx, 0.0)
        assertNotNull(player.buttonAt(35.0, 45.0))
        assertNull(player.buttonAt(5.0, 5.0))
        assertTrue(player.hits(49.0, 49.0)); assertFalse(player.hits(51.0, 49.0))
        player.advance()
        assertEquals("stops on its own frame action", 3, player.root.frame)

        player.mouseMove(35.0, 45.0)
        val ok = player.root.child("ok_mc")!!
        assertEquals("rollover moves the button clip", 2, ok.frame)
        assertEquals(listOf("click"), sounds)
        player.mouseDown(35.0, 45.0); player.mouseUp(35.0, 45.0)
        assertEquals(4, player.root.frame)
        assertTrue(player.root.playing)
        player.advance()
        assertEquals(listOf("event: done,7"), urls)
        assertEquals("gotoFrame 0 in the last frame rewinds without replaying it", 1, player.root.frame)
        assertFalse(player.root.playing)
        assertTrue(player.drawList().isEmpty())
        assertTrue(warnings.isEmpty())
    }

    @Test fun lossless_bitmaps_and_bitmap_fills() {
        // 2x1 ARGB lossless2: opaque red, then half-transparent premultiplied green.
        val raw = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0, 0, 0x80.toByte(), 0, 0x40, 0)
        val deflater = Deflater(); deflater.setInput(raw); deflater.finish()
        val z = ByteArray(64).let { it.copyOf(deflater.deflate(it)) }
        val bitmap = SwfTestBuilder.tag(36, SwfTestBuilder.u16(7) + byteArrayOf(5) + SwfTestBuilder.u16(2) + SwfTestBuilder.u16(1) + z)
        // Shape filled with bitmap 7, fill matrix scale 20 twips per pixel (identity in pixels).
        val fillMatrix = SwfBits().ub(1, 1).ub(5, 23).ub(23, 20 shl 16).ub(23, 20 shl 16).ub(1, 0).ub(5, 0).bytes()
        val shape = SwfTestBuilder.tag(2, SwfTestBuilder.u16(8) + SwfTestBuilder.rect(0, 2, 0, 1) +
            byteArrayOf(1, 0x41) + SwfTestBuilder.u16(7) + fillMatrix + byteArrayOf(0, 0))
        val movie = SwfParser.parse(SwfTestBuilder.movie(10, 10, 1, bitmap, shape, place(1, 8, translate(0, 0)), showFrame()))
        val bmp = movie.characters[7] as SwfBitmap
        assertEquals(0xFFFF0000.toInt(), bmp.argb!![0])
        assertEquals("premultiplied colour is restored", 0x80007F00.toInt(), bmp.argb!![1])
        val fill = (movie.characters[8] as SwfShape).fill as SwfFill.Bitmap
        assertEquals(7, fill.bitmapId)
        assertEquals(1.0, fill.matrix.a, 1e-9)
        assertTrue(fill.clipped)
        assertSame(movie.characters[8], FlashPlayer(movie).drawList().single().shape)
    }

    @Test fun matrices_compose_and_invert() {
        val m = SwfMatrix(2.0, 0.0, 0.0, 3.0, 10.0, 20.0) * SwfMatrix(tx = 1.0, ty = 1.0)
        assertEquals(12.0, m.x(0.0, 0.0), 0.0); assertEquals(23.0, m.y(0.0, 0.0), 0.0)
        val inv = m.inverse()!!
        assertEquals(0.0, inv.x(12.0, 23.0), 1e-9); assertEquals(0.0, inv.y(12.0, 23.0), 1e-9)
        assertNull(SwfMatrix(0.0, 0.0, 0.0, 0.0).inverse())
    }

    @Test fun unsupported_actions_stop_only_their_own_script() {
        val movie = SwfParser.parse(SwfTestBuilder.movie(10, 10, 2,
            action(op(0x24)), showFrame(), action(push("v", 3), op(0x1D), op(0x07)), showFrame()))
        val player = FlashPlayer(movie, host)
        assertTrue(warnings.single().contains("0x24"))
        player.advance()
        assertEquals(3.0, player.root.get("v"))
        assertFalse(player.root.playing)
    }
}

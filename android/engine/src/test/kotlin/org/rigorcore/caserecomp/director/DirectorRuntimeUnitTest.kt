package org.rigorcore.caserecomp.director

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.rigorcore.caserecomp.flash.DialogSwf
import org.rigorcore.caserecomp.lingo.Asm
import org.rigorcore.caserecomp.lingo.LingoBundle
import org.rigorcore.caserecomp.lingo.LingoError
import org.rigorcore.caserecomp.lingo.LingoHandler
import org.rigorcore.caserecomp.lingo.LingoScript
import org.rigorcore.caserecomp.lingo.LingoValue
import org.rigorcore.caserecomp.lingo.LingoValue.LInt
import org.rigorcore.caserecomp.lingo.LingoValue.LPoint
import org.rigorcore.caserecomp.lingo.LingoValue.LPropList
import org.rigorcore.caserecomp.lingo.LingoValue.LRect
import org.rigorcore.caserecomp.lingo.LingoValue.LString
import org.rigorcore.caserecomp.lingo.LingoValue.LSymbol
import org.rigorcore.caserecomp.lingo.ScriptType
import kotlin.random.Random

/**
 * Synthetic movie: frames 1-4, label "menu" on frame 3 whose frame script loops with
 * go(target); sprite 1 is a full-stage background, sprite 2 a button with a behaviour
 * on frames 3-4; castLib 2 starts empty and can load "content.cct".
 */
class DirectorRuntimeUnitTest {
    private val names = mutableListOf<String>()
    private fun asm() = Asm(names)
    private fun handler(name: String, code: Asm, args: List<String> = listOf("me")) = LingoHandler(name, args, emptyList(), code.bytes())
    private fun Asm.bump(global: String) = named(0x49, global).int(1).op(0x05).named(0x4F, global)
    private fun Asm.end() = op(0x01)

    private fun bundle(): LingoBundle {
        val loop = LingoScript("loop", 1, ScriptType.BEHAVIOR, listOf("target"), emptyList(), emptyList(), listOf(
            handler("beginSprite", asm().bump("gFrameBegins").end()),
            handler("exitFrame", asm().named(0x4A, "target").op(0x42, 1).named(0x57, "go").end()),
        ))
        val button = LingoScript("button", 2, ScriptType.BEHAVIOR, listOf("clicks"), emptyList(), emptyList(), listOf(
            handler("beginSprite", asm().bump("gBegins").end()),
            handler("endSprite", asm().bump("gEnds").end()),
            handler("mouseDown", asm().bump("gClicks").op(0x4B, 0).named(0x61, "spriteNum").named(0x4F, "gLastSprite").end()),
            handler("mouseUp", asm().bump("gUps").end()),
            handler("mouseUpOutside", asm().bump("gOutside").end()),
            handler("mouseEnter", asm().int(1).named(0x4F, "gHover").end()),
            handler("mouseLeave", asm().op(0x03).named(0x4F, "gHover").end()),
            handler("flash", asm().bump("gFlashes").end()),
            handler("done", asm().op(0x4B, 1).named(0x4F, "gDone").end(), listOf("me", "n")),
        ))
        val movieScript = LingoScript("main", 6, ScriptType.MOVIE, emptyList(), emptyList(), listOf(LString("keyHandler()")), listOf(
            handler("prepareMovie", asm().op(0x44, 0).int(3).op(0x5D, 0).end(), emptyList()),
            handler("startMovie", asm().int(1).named(0x4F, "gStarted").end(), emptyList()),
            handler("mouseDown", asm().bump("gMiss").end(), emptyList()),
            handler("keyHandler", asm().bump("gKeys").int(3).op(0x42, 1).named(0x57, "go").end(), emptyList()),
            handler("castCount", asm().int(4).op(0x5C, 8).op(0x43, 1).named(0x57, "return").end(), emptyList()),
        ))
        val ticker = LingoScript("ticker", 7, ScriptType.PARENT, listOf("count"), emptyList(), emptyList(), listOf(
            handler("stepFrame", asm().bump("gSteps").end()),
        ))
        return LingoBundle(names, listOf(loop, button, movieScript, ticker))
    }

    private fun movie(): DirectorMovie {
        val internal = CastFile("", listOf(
            MemberData(1, "loop", "script", scriptNumber = 1),
            MemberData(2, "button", "script", scriptNumber = 2),
            MemberData(3, "btn", "bitmap", width = 40, height = 20, regX = 20, regY = 10),
            MemberData(4, "bg", "bitmap", width = 800, height = 600, regX = 400, regY = 300),
            MemberData(5, "label", "xtra", xtra = "text", width = 100, height = 20, text = "Hello"),
            MemberData(8, "dialog", "xtra", xtra = "flash"),
            MemberData(9, "click", "sound"),
        ).associateBy { it.number })
        val content = CastFile("content.cct", mapOf(1 to MemberData(1, "photo", "bitmap", width = 10, height = 10, regX = 5, regY = 5)))
        val o = DirectorMovie.SPRITE_CHANNEL_OFFSET
        return DirectorMovie(
            stageWidth = 800, stageHeight = 600, tempo = 30,
            labels = listOf(1 to "intro", 3 to "menu"),
            castLibs = listOf(CastLibData(1, "Internal", ""), CastLibData(2, "extra", "C:\\old\\empty.cst")),
            internalCast = internal,
            externalCasts = mapOf("content" to content, "empty" to CastFile("empty.cct", emptyMap())),
            frameCount = 4, channelCount = 3 + o,
            sprites = listOf(
                SpriteRun(1, 4, 1 + o, 0, 1, 4, 400, 300, 800, 600, 0, false, false),
                SpriteRun(3, 4, 2 + o, 0, 1, 3, 100, 100, 40, 20, 0, false, false),
            ),
            spans = listOf(
                SpanData(3, 3, 0, listOf(BehaviorRef(1, 1, "[#target: \"menu\"]"))),
                SpanData(1, 4, 1 + o, emptyList()),
                SpanData(3, 4, 2 + o, listOf(BehaviorRef(1, 2, null))),
            ),
        )
    }

    private var now = 1_000L
    private fun runtime(media: DirectorMedia = DirectorMedia.None, sound: SoundOutput = SoundOutput.Silent) =
        DirectorRuntime(movie(), bundle(), media, sound, clock = { now }, random = Random(1))

    private fun DirectorRuntime.g(name: String) = vm.global(name)

    @Test fun only_stretched_bitmap_sprites_use_the_score_rect() {
        val o = DirectorMovie.SPRITE_CHANNEL_OFFSET
        val movie = DirectorMovie(
            stageWidth = 800, stageHeight = 600, tempo = 30, labels = emptyList(),
            castLibs = listOf(CastLibData(1, "Internal", "")),
            internalCast = CastFile("", listOf(
                MemberData(1, "pad", "bitmap", width = 40, height = 20, regX = 20, regY = 10),
                MemberData(2, "box", "shape", width = 4, height = 4),
                MemberData(3, "label", "xtra", xtra = "text", width = 30, height = 10, text = "Hi"),
            ).associateBy { it.number }),
            externalCasts = emptyMap(), frameCount = 1, channelCount = 4 + o,
            sprites = listOf(
                SpriteRun(1, 1, 1 + o, 0, 1, 1, 100, 100, 25, 6, 0, false, false, stretch = false),
                SpriteRun(1, 1, 2 + o, 0, 1, 1, 100, 100, 25, 6, 0, false, false, stretch = true),
                SpriteRun(1, 1, 3 + o, 0, 1, 2, 100, 100, 25, 6, 0, false, false, stretch = false),
                SpriteRun(1, 1, 4 + o, 8, 1, 3, 100, 100, 25, 6, 0, false, false, stretch = false),
            ),
            spans = emptyList(),
        )
        val rt = DirectorRuntime(movie, bundle(), clock = { now })
        rt.start()
        assertEquals(listOf(80, 90, 120, 110), rt.sprite(1).bounds().toList())
        assertEquals(listOf(25, 6), listOf(rt.sprite(2).width, rt.sprite(2).height))
        assertEquals("shapes keep the score rect", 25, rt.sprite(3).width)
        assertEquals("text boxes take the member rect", listOf(30, 10), listOf(rt.sprite(4).width, rt.sprite(4).height))
        // Matte text is solid only over its laid-out characters ("Hi" is ~13 px wide here).
        assertEquals(listOf(true, false), listOf(rt.sprite(4).hit(101, 101), rt.sprite(4).hit(125, 101)))
    }

    @Test fun sprites_without_mouse_handlers_do_not_intercept_clicks() {
        val o = DirectorMovie.SPRITE_CHANNEL_OFFSET
        val movie = DirectorMovie(
            stageWidth = 800, stageHeight = 600, tempo = 30, labels = emptyList(),
            castLibs = listOf(CastLibData(1, "Internal", "")),
            internalCast = CastFile("", listOf(
                MemberData(1, "loop", "script", scriptNumber = 1),
                MemberData(2, "button", "script", scriptNumber = 2),
                MemberData(3, "box", "shape", width = 40, height = 40),
            ).associateBy { it.number }),
            externalCasts = emptyMap(), frameCount = 1, channelCount = 2 + o,
            sprites = listOf(
                SpriteRun(1, 1, 1 + o, 0, 1, 3, 100, 100, 40, 40, 0, false, false),
                SpriteRun(1, 1, 2 + o, 0, 1, 3, 100, 100, 40, 40, 0, false, false),
            ),
            spans = listOf(
                SpanData(1, 1, 1 + o, listOf(BehaviorRef(1, 2, null))),
                // An overlay whose behaviour only runs frame events (like a screen flicker).
                SpanData(1, 1, 2 + o, listOf(BehaviorRef(1, 1, "[#target: 1]"))),
            ),
        )
        val rt = DirectorRuntime(movie, bundle(), clock = { now })
        rt.start()
        assertEquals(1, rt.activeSpriteAt(110, 110)?.number)
        rt.mouseDown(110, 110)
        assertEquals(LInt(1), rt.g("gClicks"))
    }

    @Test fun playback_labels_go_and_frame_script_loop() {
        val rt = runtime()
        rt.start()
        assertEquals(1, rt.frame)
        assertEquals(LInt(1), rt.g("gStarted"))
        assertEquals(LString("intro"), rt.getMovieProp("frameLabel"))
        rt.tick(); assertEquals(2, rt.frame)
        assertEquals(LInt(0), rt.getMovieProp("frameLabel"))
        rt.tick(); assertEquals(3, rt.frame)
        repeat(5) { rt.tick() }
        assertEquals("frame script loops on its label", 3, rt.frame)
        assertEquals("one span, one beginSprite", LInt(1), rt.g("gFrameBegins"))
        assertEquals(LInt(1), rt.g("gBegins"))
        rt.go(LInt(4)); rt.tick()
        assertEquals("the frame script's later go wins", 3, rt.frame)
        rt.frameScripts.single().properties["target"] = LInt(4)
        rt.tick()
        assertEquals(4, rt.frame)
        rt.go(LString("nowhere")); rt.tick()
        assertTrue(rt.warnings.any { "nowhere" in it })
        assertEquals("past the last frame playback wraps", 1, rt.frame)
        assertEquals(LInt(1), rt.g("gEnds"))
    }

    @Test fun mouse_events_hit_test_and_fallback_to_movie_script() {
        val rt = runtime()
        rt.start(); rt.go(LString("menu")); rt.tick()
        assertEquals(3, rt.frame)
        rt.mouseMove(85, 95)
        assertEquals(LInt(1), rt.g("gHover"))
        rt.mouseDown(100, 100); rt.mouseUp(101, 101)
        assertEquals(LInt(1), rt.g("gClicks")); assertEquals(LInt(1), rt.g("gUps"))
        assertEquals(LInt(2), rt.g("gLastSprite"))
        rt.mouseDown(100, 100); rt.mouseUp(500, 500)
        assertEquals(LInt(1), rt.g("gOutside"))
        assertEquals(LInt(0), rt.g("gHover"))
        rt.mouseDown(600, 500)
        assertEquals("background has no behaviour: event reaches the movie script", LInt(1), rt.g("gMiss"))
        assertSame(rt.sprite(1), rt.spriteAt(600, 500))
        assertEquals(LingoValue.TRUE, rt.theBuiltin("mouseDown"))
        assertEquals(LPoint(LInt(600), LInt(500)), rt.getMovieProp("mouseLoc"))
    }

    @Test fun lingo_sprite_changes_last_until_the_span_ends() {
        val rt = runtime()
        rt.start(); rt.go(LInt(3)); rt.tick()
        val button = rt.sprite(2)
        assertEquals(LRect(LInt(80), LInt(90), LInt(120), LInt(110)), button.getProp("rect"))
        button.setProp("loc", LPoint(LInt(10), LInt(10)))
        button.setProp("blend", LInt(50))
        rt.tick()
        assertEquals(LPoint(LInt(10), LInt(10)), button.getProp("loc"))
        assertNull("no hit at the score position any more", rt.spriteAt(100, 100)?.takeIf { it === button })
        rt.frameScripts.single().properties["target"] = LInt(1)
        rt.tick(); rt.go(LInt(3)); rt.tick()
        assertEquals(LPoint(LInt(100), LInt(100)), button.getProp("loc"))
        assertEquals(LInt(100), button.getProp("blend"))
        button.setProp("rect", LRect(LInt(0), LInt(0), LInt(80), LInt(40)))
        assertEquals(LPoint(LInt(40), LInt(20)), button.getProp("loc"))
        assertEquals(LInt(80), button.getProp("width"))
        assertEquals(listOf(1, 2), rt.displayList().map { it.sprite })
        button.setProp("locZ", LInt(0))
        assertEquals(listOf(2, 1), rt.displayList().map { it.sprite })
        button.setProp("visible", LInt(0))
        assertEquals(listOf(1), rt.displayList().map { it.sprite })
    }

    @Test fun messages_to_sprites_reach_their_behaviours() {
        val rt = runtime()
        rt.start(); rt.go(LInt(3)); rt.tick()
        assertEquals(LingoValue.Void, rt.vm.callMethod(rt.sprite(2), "flash", listOf(rt.sprite(2))))
        assertEquals(LInt(1), rt.g("gFlashes"))
        assertThrows(LingoError::class.java) { rt.vm.callFunction("nothing", listOf(rt.sprite(2))) }
        assertEquals(LInt(2), rt.vm.getObjectProp(rt.sprite(2).instances.single(), "spriteNum"))
        val list = rt.sprite(2).getProp("scriptInstanceList") as LingoValue.LList
        assertEquals(1, list.items.size)
    }

    @Test fun members_casts_and_runtime_cast_swaps() {
        val rt = runtime()
        rt.start()
        val call = { name: String, args: List<LingoValue> -> rt.vm.callFunction(name, args) }
        val label = call("member", listOf(LString("LABEL"))) as CastMember
        assertEquals(LSymbol("text"), label.getProp("type"))
        assertEquals(LString("Hello"), label.getProp("text"))
        label.setProp("html", LString("<p>Agent<br>Smith &amp; co</p>"))
        assertEquals("Agent\rSmith & co", label.text)
        assertEquals(LSymbol("empty"), (call("member", listOf(LString("photo"))) as CastMember).getProp("type"))
        val lib = call("castLib", listOf(LString("extra"))) as CastLib
        lib.setProp("fileName", LString("C:\\game\\data\\content.cct"))
        val photo = call("member", listOf(LString("photo"))) as CastMember
        assertEquals(2, photo.lib.number)
        assertEquals(LInt((2 shl 16) or 1), photo.getProp("number"))
        assertSame(photo, call("member", listOf(LInt((2 shl 16) or 1))))
        assertSame(photo, call("member", listOf(LInt(1), LString("extra"))))
        rt.sprite(1).setProp("member", photo)
        assertEquals(LInt(10), rt.sprite(1).getProp("width"))
        lib.setProp("fileName", LString("missing.cct"))
        assertTrue(rt.warnings.any { "missing" in it })
        assertEquals(LSymbol("empty"), photo.getProp("type"))
        assertEquals(LInt(2), rt.vm.callGlobal("castCount", emptyList()))
    }

    @Test fun keys_actor_list_stage_and_environment() {
        val rt = runtime()
        rt.start()
        assertEquals(LString("keyHandler()"), rt.getMovieProp("keyDownScript"))
        rt.keyDown("a")
        assertEquals(LInt(1), rt.g("gKeys"))
        assertEquals("go from an input handler jumps without waiting for exitFrame", 3, rt.frame)
        assertEquals(LInt(1), rt.g("gFrameBegins"))
        assertEquals(LString("a"), rt.theBuiltin("key"))
        assertEquals(LingoValue.TRUE, rt.callBuiltin(rt.vm, "keyPressed", listOf(LString("A"))))
        rt.keyUp("a")
        assertEquals(LingoValue.FALSE, rt.callBuiltin(rt.vm, "keyPressed", listOf(LString("a"))))
        assertThrows(LingoError::class.java) { rt.runStatement("put 1") }
        val ticker = rt.vm.newInstance(rt.vm.bundle.scriptNamed("ticker")!!, emptyList())
        rt.actorList.items += ticker
        rt.tick(); rt.tick()
        assertEquals(LInt(2), rt.g("gSteps"))
        now += 500
        assertEquals(LInt(500), rt.getMovieProp("milliSeconds"))
        assertEquals(LInt(112), rt.theBuiltin("stageLeft"))
        assertEquals(LInt(684), rt.theBuiltin("stageBottom"))
        val env = rt.getMovieProp("environment") as LPropList
        assertTrue(env.entries.any { it.first == LSymbol("platform") })
        assertThrows(LingoError::class.java) { rt.setMovieProp("frame", LInt(2)) }
        rt.callBuiltin(rt.vm, "quit", emptyList())
        rt.tick()
        assertTrue(rt.quitRequested)
    }

    @Test fun flash_sprites_keep_playback_state() {
        val rt = runtime()
        rt.start(); rt.go(LInt(3)); rt.tick()
        val sprite = rt.sprite(2)
        sprite.setProp("member", LString("dialog"))
        rt.vm.callMethod(sprite, "goToFrame", listOf(sprite, LString("openDialog")))
        rt.vm.callMethod(sprite, "play", listOf(sprite))
        rt.vm.callMethod(sprite, "setVariable", listOf(sprite, LString("soundLevel"), LString("80")))
        assertEquals(LString("openDialog"), sprite.getProp("frame"))
        assertEquals(LingoValue.TRUE, sprite.getProp("playing"))
        assertEquals(LString("80"), rt.vm.callMethod(sprite, "getVariable", listOf(sprite, LString("soundLevel"))))
    }

    @Test fun flash_movies_play_take_clicks_and_send_events_to_behaviours() {
        val media = object : DirectorMedia {
            override fun flash(castFile: String, member: MemberData) = if (member.name == "dialog") DialogSwf.bytes else null
        }
        val rt = runtime(media = media)
        rt.start(); rt.go(LInt(3)); rt.tick()
        val sprite = rt.sprite(2)
        sprite.setProp("member", LString("dialog"))
        assertEquals("size and centre registration come from the SWF stage", LRect(LInt(0), LInt(50), LInt(200), LInt(150)), sprite.getProp("rect"))
        rt.vm.callMethod(sprite, "goToFrame", listOf(sprite, LString("open")))
        rt.tick()
        val player = sprite.flash.player!!
        assertEquals(2, player.root.frame)
        assertSame(player, rt.displayList().single { it.sprite == 2 }.flash)
        assertNull("transparent areas of a Flash sprite do not hit", rt.spriteAt(150, 140)?.takeIf { it === sprite })
        rt.mouseMove(35, 95)
        rt.mouseDown(35, 95); rt.mouseUp(35, 95)
        assertEquals(4, player.root.frame)
        rt.tick()
        assertEquals("getURL event reached the behaviour with a parsed argument", LInt(7), rt.g("gDone"))
        assertEquals(LingoValue.FALSE, sprite.getProp("playing"))
        rt.flashURL(sprite, "lingo: keyHandler()")
        assertEquals(LInt(1), rt.g("gKeys"))
        rt.flashURL(sprite, "http://example.invalid/")
        assertTrue(rt.warnings.any { "ignored" in it })
    }

    @Test fun sound_channels_play_fade_and_report_busy() {
        val played = mutableListOf<String>()
        var playing = true
        val output = object : SoundOutput {
            override fun play(channel: Int, member: CastMember, loops: Int) { played += "$channel:${member.name}:$loops" }
            override fun isPlaying(channel: Int) = playing
        }
        val rt = runtime(sound = output)
        rt.start()
        val ch = rt.callBuiltin(rt.vm, "sound", listOf(LInt(1))) as SoundChannel
        rt.vm.callMethod(ch, "play", listOf(ch, LPropList(mutableListOf(LSymbol("member") to rt.member(LString("click"), null), LSymbol("loopCount") to LInt(0)))))
        assertEquals(listOf("1:click:0"), played)
        assertEquals(LingoValue.TRUE, rt.callBuiltin(rt.vm, "soundBusy", listOf(LInt(1))))
        ch.setProp("volume", LInt(200))
        rt.vm.callMethod(ch, "fadeTo", listOf(ch, LInt(0), LInt(1000)))
        now += 500; ch.update()
        assertEquals(LInt(100), ch.getProp("volume"))
        now += 600; ch.update()
        assertEquals(LInt(0), ch.getProp("volume"))
        playing = false
        assertEquals(LInt(0), ch.getProp("status"))
        rt.callBuiltin(rt.vm, "puppetSound", listOf(LInt(2), LString("click")))
        assertEquals("2:click:1", played.last())
    }

    @Test fun images_fill_copy_with_alpha_and_matte_hit_testing() {
        val image = LingoImage(4, 2)
        image.call("fill", listOf(image.rectValue(), LColor(255, 0, 0)))
        assertEquals(0xFFFF0000.toInt(), image.pixels[7])
        val glyph = LingoImage(2, 1, pixels = intArrayOf(0x800000FF.toInt(), 0x00000000))
        image.call("copyPixels", listOf(glyph, LRect(LInt(0), LInt(0), LInt(4), LInt(2)), glyph.rectValue()))
        assertEquals("half-alpha blue over red", 0xFF7F0080.toInt(), image.pixels[0])
        assertEquals("transparent source leaves the target", 0xFFFF0000.toInt(), image.pixels[3])
        val copy = image.call("duplicate", emptyList()) as LingoImage
        copy.call("setPixel", listOf(LInt(0), LInt(0), LColor(0, 255, 0)))
        assertEquals(LColor(0, 255, 0), copy.call("getPixel", listOf(LInt(0), LInt(0))))
        assertTrue(image.pixels[0] != copy.pixels[0])
        assertEquals(LInt(2), (image.call("crop", listOf(LRect(LInt(1), LInt(0), LInt(3), LInt(2)))) as LingoImage).getProp("width"))

        // A matte-ink button whose left half is transparent is only hit on its right half.
        val media = object : DirectorMedia {
            override fun image(castFile: String, member: MemberData): LingoImage? =
                if (member.name != "btn") null else LingoImage(40, 20, pixels = IntArray(800) { i -> if (i % 40 < 20) 0 else -1 })
        }
        val m = movie()
        val o = DirectorMovie.SPRITE_CHANNEL_OFFSET
        val matte = DirectorMovie(m.stageWidth, m.stageHeight, m.tempo, m.labels, m.castLibs, m.internalCast, m.externalCasts,
            m.frameCount, m.channelCount,
            listOf(SpriteRun(1, 4, 2 + o, 8, 1, 3, 100, 100, 40, 20, 0, false, false)),
            listOf(SpanData(1, 4, 2 + o, emptyList())))
        val rt = DirectorRuntime(matte, bundle(), media, clock = { now })
        rt.start()
        assertNull(rt.spriteAt(85, 100))
        assertSame(rt.sprite(2), rt.spriteAt(110, 100))
        rt.sprite(2).setProp("flipH", LInt(1))
        assertSame(rt.sprite(2), rt.spriteAt(85, 100))
    }

    @Test fun parses_movie_bundle_json() {
        val json = """
            {"format":"case-recomp-movie-bundle","version":1,
             "stage":{"width":640,"height":480,"tempo":15},
             "labels":[{"frame":2,"name":"Map"}],
             "cast_libs":[{"number":1,"name":"Internal","file_path":""}],
             "internal_members":[{"number":3,"name":"logo","type":"bitmap","width":4,"height":2,"reg_x":2,"reg_y":1,
               "media":{"ediM":9}},{"number":4,"name":"t","type":"xtra","xtra":"text","text":"Hi","fonts":[]}],
             "external_casts":[{"file":"01.cct","sha256":"x","members":[]}],
             "score":{"frame_count":2,"channel_count":7,"sprite_record_size":48,
               "sprites":[{"start":1,"end":2,"channel":6,"type":16,"ink":36,"trails":false,"stretch":false,"cast_lib":1,
                 "member":3,"x":5,"y":6,"width":4,"height":2,"blend":0,"flip_h":true,"flip_v":false}],
               "spans":[{"start":1,"end":2,"channel":6,"behaviors":[{"cast_lib":1,"member":2,"parameters":null}]}]}}
        """.trimIndent()
        val movie = DirectorMovie.parse(json)
        assertEquals(2, movie.labelFrame("map"))
        assertEquals("Map", movie.labelAt(2))
        assertEquals(9, movie.internalCast.members.getValue(3).media["ediM"])
        assertEquals("Hi", movie.internalCast.members.getValue(4).text)
        assertTrue(movie.runAt(6, 2)!!.flipH)
        assertNull(movie.runAt(6, 3))
        assertEquals(1, movie.spanAt(6, 1)!!.behaviors.size)
        assertSame(movie.externalCast("D:\\x\\01.CCT"), movie.externalCast("01"))
        assertEquals(2, movie.spriteCount)
        assertThrows(LingoError::class.java) { DirectorMovie.parse("""{"format":"other","version":1}""") }
        assertFalse(movie.externalCasts.isEmpty())
    }
}

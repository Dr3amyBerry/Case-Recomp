package org.rigorcore.caserecomp.director

import org.rigorcore.caserecomp.flash.FlashError
import org.rigorcore.caserecomp.flash.FlashPlayer
import org.rigorcore.caserecomp.flash.SwfMovie
import org.rigorcore.caserecomp.flash.SwfParser
import org.rigorcore.caserecomp.lingo.LingoBundle
import org.rigorcore.caserecomp.lingo.LingoError
import org.rigorcore.caserecomp.lingo.LingoHost
import org.rigorcore.caserecomp.lingo.LingoLiteralParser
import org.rigorcore.caserecomp.lingo.LingoValue
import org.rigorcore.caserecomp.lingo.LingoValue.LInstance
import org.rigorcore.caserecomp.lingo.LingoValue.LInt
import org.rigorcore.caserecomp.lingo.LingoValue.LList
import org.rigorcore.caserecomp.lingo.LingoValue.LPoint
import org.rigorcore.caserecomp.lingo.LingoValue.LPropList
import org.rigorcore.caserecomp.lingo.LingoValue.LRect
import org.rigorcore.caserecomp.lingo.LingoValue.LString
import org.rigorcore.caserecomp.lingo.LingoValue.LSymbol
import org.rigorcore.caserecomp.lingo.LingoValue.Void
import org.rigorcore.caserecomp.lingo.LingoVm
import org.rigorcore.caserecomp.lingo.asText
import org.rigorcore.caserecomp.lingo.lingoEquals
import org.rigorcore.caserecomp.lingo.toInt
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.random.Random

/** One stage item to draw, back to front, in stage coordinates. */
data class DisplayItem(
    val sprite: Int, val member: CastMember,
    val left: Int, val top: Int, val right: Int, val bottom: Int,
    val ink: Int, val blend: Int, val rotation: Double, val flipH: Boolean, val flipV: Boolean,
    /** Playing Flash movie of a Flash member sprite, drawn into the sprite rectangle. */
    val flash: FlashPlayer? = null,
    /** Sprite colours (ARGB) that colorize the member: black shows as [fore], white as [back]. */
    val fore: Int = 0xFF000000.toInt(),
    val back: Int = 0xFFFFFFFF.toInt(),
)

/** Projector environment the title sees through `the platform`, `the moviePath` and friends. */
data class DirectorEnvironment(
    val moviePath: String = "",
    val platform: String = "Windows,32",
    val runMode: String = "Projector",
    val desktopWidth: Int = 1024,
    val desktopHeight: Int = 768,
)

/**
 * Subset of the Director 8.5 runtime needed to play a movie bundle with its compiled
 * Lingo: score playback with labels and `go`, sprite spans with behaviours and their
 * events, mouse and keyboard input, casts that can be swapped at runtime, images,
 * sound channels and the actorList. Xtras are supplied through [extensions].
 */
class DirectorRuntime(
    val movie: DirectorMovie,
    bundle: LingoBundle,
    val media: DirectorMedia = DirectorMedia.None,
    val sound: SoundOutput = SoundOutput.Silent,
    private val clock: () -> Long = System::currentTimeMillis,
    random: Random = Random.Default,
    val environment: DirectorEnvironment = DirectorEnvironment(),
    private val extensions: LingoHost? = null,
    val textMetrics: TextMetrics = TextMetrics.Approximate,
) : LingoHost {
    val vm = LingoVm(bundle, this, random)
    private val startTime = clock()
    val castLibs: List<CastLib> = movie.castLibs.sortedBy { it.number }.map { CastLib(this, it.number, it.name, it.filePath) }
    val sprites: List<Sprite> = List(movie.spriteCount) { Sprite(this, it + 1) }
    val soundChannels: List<SoundChannel> = List(8) { SoundChannel(sound, it + 1, ::milliseconds) }
    val actorList = LList()
    val warnings = mutableListOf<String>()
    val log = mutableListOf<String>()

    var frame = 0
        private set
    private var pendingFrame: Int? = null
    private var frameSpan: SpanData? = null
    private val frameInstances = mutableListOf<LInstance>()
    /** Behaviour instances of the current frame-script span. */
    val frameScripts: List<LInstance> get() = frameInstances
    var started = false
        private set
    var quitRequested = false
        private set
    var stageUpdates = 0
        private set

    var mouseX = 0; private set
    var mouseY = 0; private set
    var mouseIsDown = false; private set
    private var downSprite: Sprite? = null
    private var rollSprite: Sprite? = null
    private val pressedKeys = HashSet<String>()
    private var lastKey = ""
    private var keyDownScript: LingoValue = Void
    private val movieProps = HashMap<String, LingoValue>()
    var cursor: LingoValue = Void; private set

    init {
        for (lib in castLibs) {
            val file = if (lib.number == 1) movie.internalCast else movie.externalCast(lib.fileName)
            lib.assign(lib.fileName, file)
        }
    }

    fun milliseconds(): Long = clock() - startTime

    // ---- casts ---------------------------------------------------------------------------

    fun castLib(id: LingoValue): CastLib? = when (id) {
        is CastLib -> id
        is LInt, is LingoValue.LFloat -> castLibs.firstOrNull { it.number == id.toInt() }
        else -> castLibs.firstOrNull { it.name.equals(id.asText(), ignoreCase = true) }
    }

    fun castLib(number: Int): CastLib? = castLibs.firstOrNull { it.number == number }

    /** Swap the cast file behind a library, as `castLib(n).fileName = path` does. */
    fun loadCast(lib: CastLib, fileName: String) {
        val file = movie.externalCast(fileName)
        if (file == null) warnings += "cast file not in bundle: ${DirectorMovie.castKey(fileName)}"
        lib.assign(fileName, file)
    }

    /** `member(id)` / `member(id, castLib)`. Unknown names give an empty member reference. */
    fun member(id: LingoValue, lib: CastLib?): CastMember {
        if (id is CastMember) return id
        if (id is LInt || id is LingoValue.LFloat) {
            val n = id.toInt()
            return when {
                lib != null -> lib.member(n)
                n > 0xFFFF -> (castLib(n shr 16) ?: throw LingoError("castLib ${n shr 16} not found")).member(n and 0xFFFF)
                else -> castLibs.first().member(n)
            }
        }
        val name = id.asText()
        val libs = if (lib != null) listOf(lib) else castLibs
        for (candidate in libs) candidate.memberNamed(name)?.let { return it }
        return emptyMember()
    }

    fun emptyMember(): CastMember = castLibs.first().member(-1)

    internal fun memberChanged(member: CastMember) {}
    internal fun spriteChanged(sprite: Sprite) {}

    fun sprite(number: Int): Sprite = sprites.getOrNull(number - 1) ?: throw LingoError("sprite $number out of range")

    // ---- playback ------------------------------------------------------------------------

    /** prepareMovie, then enter frame 1 (beginSprite, prepareFrame, startMovie, enterFrame). */
    fun start() {
        check(!started) { "movie already started" }
        started = true
        vm.resetBudget()
        vm.callGlobal("prepareMovie", emptyList())
        enterFrame(1, startMovie = true)
    }

    /** One playback tick: exitFrame on the current frame, then enter the next one. */
    fun tick() {
        check(started) { "movie not started" }
        if (quitRequested) return
        vm.resetBudget()
        frameEvent("exitFrame")
        val target = pendingFrame ?: (frame + 1).let { if (it > movie.frameCount) 1 else it }
        pendingFrame = null
        enterFrame(target, startMovie = false)
    }

    fun stop() {
        if (!started) return
        vm.resetBudget()
        for (sprite in sprites) if (sprite.span != null) leaveSpan(sprite)
        vm.callGlobal("stopMovie", emptyList())
    }

    private fun enterFrame(target: Int, startMovie: Boolean) {
        val entering = mutableListOf<Sprite>()
        val newFrameSpan = movie.spanAt(0, target)
        val frameChanged = newFrameSpan !== frameSpan
        if (frameChanged && frameSpan != null) {
            for (instance in frameInstances.toList()) instance.script.handler("endSprite")?.let { vm.callHandler(it, listOf(instance), instance) }
        }
        for (sprite in sprites) {
            val span = movie.spanAt(sprite.number + DirectorMovie.SPRITE_CHANNEL_OFFSET, target)
            if (span !== sprite.span) {
                if (sprite.span != null) leaveSpan(sprite)
                sprite.enterSpan(span)
                if (span != null) entering += sprite
            }
        }
        frame = target
        for (sprite in sprites) sprite.run = movie.runAt(sprite.number + DirectorMovie.SPRITE_CHANNEL_OFFSET, target)
        if (frameChanged) {
            frameSpan = newFrameSpan
            frameInstances.clear()
            newFrameSpan?.behaviors?.mapNotNullTo(frameInstances) { instantiate(it, null) }
            for (instance in frameInstances.toList()) instance.script.handler("beginSprite")?.let { vm.callHandler(it, listOf(instance), instance) }
        }
        for (sprite in entering) {
            sprite.span?.behaviors?.mapNotNullTo(sprite.instances) { instantiate(it, sprite) }
        }
        for (sprite in entering) sendSprite(sprite, "beginSprite", emptyList())
        for (actor in actorList.items.toList()) vm.callMethod(actor, "stepFrame", listOf(actor))
        frameEvent("prepareFrame")
        if (startMovie) vm.callGlobal("startMovie", emptyList())
        frameEvent("enterFrame")
        for (sprite in sprites) if (sprite.span != null) sprite.flash.current()?.advance()
        soundChannels.forEach { it.update() }
        updateRollover()
    }

    private val mediaImages = object : LinkedHashMap<Pair<String, Int>, LingoImage>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<String, Int>, LingoImage>?) = size > MEDIA_IMAGE_CACHE
    }

    /** Decoded member media, cached (least recently used first out); blank and opaque without media. */
    fun mediaImage(member: CastMember, data: MemberData): LingoImage {
        val file = member.lib.file?.file ?: ""
        return mediaImages.getOrPut(file to data.number) {
            media.image(file, data) ?: LingoImage(data.width, data.height).also { it.useAlpha = false }
        }
    }

    private val flashMovies = HashMap<Pair<String, Int>, SwfMovie?>()

    /** Parsed SWF of a Flash member (cached per cast file and slot), or null without media. */
    fun flashMovie(member: CastMember): SwfMovie? {
        val data = member.data ?: return null
        val file = member.lib.file?.file ?: ""
        return flashMovies.getOrPut(file to data.number) {
            media.flash(file, data)?.let { bytes ->
                try { SwfParser.parse(bytes) } catch (e: FlashError) { warnings += "flash ${member.name}: ${e.message}"; null }
            }
        }
    }

    /**
     * getURL from a Flash sprite: "event: handler, args" sends a Lingo event to the sprite's
     * behaviours (arguments parsed as Lingo literals) and "lingo: statement" runs a statement.
     */
    fun flashURL(sprite: Sprite, url: String) {
        val trimmed = url.trim()
        when {
            trimmed.startsWith("event:", ignoreCase = true) -> {
                val parts = trimmed.substring(6).split(',').map { it.trim() }
                val args = parts.drop(1).filter { it.isNotEmpty() }.map { text ->
                    LingoLiteralParser.parseOrVoid(text).takeIf { it != Void } ?: LString(text)
                }
                sendSprite(sprite, parts[0], args)
            }
            trimmed.startsWith("lingo:", ignoreCase = true) -> runStatement(trimmed.substring(6))
            else -> warnings += "flash getURL ignored: $trimmed"
        }
    }

    /** Topmost Flash sprite under a stage point with its movie coordinates. */
    private fun flashAt(x: Int, y: Int): Pair<Sprite, Pair<Double, Double>>? {
        val top = spriteAt(x, y) ?: return null
        val point = top.flash.toMovie(x, y) ?: return null
        return top to point
    }

    private fun leaveSpan(sprite: Sprite) {
        sendSprite(sprite, "endSprite", emptyList())
        if (rollSprite === sprite) rollSprite = null
        if (downSprite === sprite) downSprite = null
        sprite.enterSpan(null)
    }

    /** Behaviour instance for a span, with spriteNum and the score parameters applied. */
    private fun instantiate(ref: BehaviorRef, sprite: Sprite?): LInstance? {
        val script = if (ref.castLib == 1) vm.bundle.scriptNumber(ref.member) else null
        if (script == null) { warnings += "behaviour member ${ref.castLib}:${ref.member} has no script"; return null }
        val instance = LInstance(script)
        instance.properties["spriteNum"] = LInt(sprite?.number ?: 0)
        val params = ref.parameters?.let { LingoLiteralParser.parseOrVoid(it) }
        if (params is LPropList) for ((key, value) in params.entries) {
            val name = key.asText()
            val existing = instance.properties.keys.firstOrNull { it.equals(name, ignoreCase = true) } ?: name
            instance.properties[existing] = value
        }
        script.handler("new")?.let { vm.callHandler(it, listOf(instance), instance) }
        return instance
    }

    /** Send [event] to every behaviour of the sprite that handles it; null when none does. */
    fun sendSprite(sprite: Sprite, event: String, args: List<LingoValue>): LingoValue? {
        var result: LingoValue? = null
        for (instance in sprite.instances.toList()) {
            val handler = instance.script.handler(event) ?: continue
            result = vm.callHandler(handler, listOf(instance) + args, instance)
        }
        return result
    }

    private fun sendFrameScript(event: String, args: List<LingoValue>): Boolean {
        var handled = false
        for (instance in frameInstances.toList()) {
            val handler = instance.script.handler(event) ?: continue
            vm.callHandler(handler, listOf(instance) + args, instance)
            handled = true
        }
        return handled
    }

    /** prepareFrame/enterFrame/exitFrame: every sprite behaviour, then the frame script, else movie scripts. */
    private fun frameEvent(event: String) {
        for (sprite in sprites) if (sprite.instances.isNotEmpty()) sendSprite(sprite, event, emptyList())
        if (!sendFrameScript(event, emptyList())) vm.callGlobal(event, emptyList())
    }

    /** Sprite event with Director's fallback to the frame script and then movie scripts. */
    private fun spriteEvent(sprite: Sprite?, event: String) {
        if (sprite != null && sprite.instances.any { it.script.handler(event) != null }) {
            sendSprite(sprite, event, emptyList()); return
        }
        if (!sendFrameScript(event, emptyList())) vm.callGlobal(event, emptyList())
    }

    fun go(target: LingoValue) {
        val frameNumber = when (target) {
            is LInt, is LingoValue.LFloat -> target.toInt()
            else -> movie.labelFrame(target.asText()) ?: run { warnings += "go: no label ${target.asText()}"; return }
        }
        if (frameNumber !in 1..movie.frameCount) { warnings += "go: frame $frameNumber out of range"; return }
        pendingFrame = frameNumber
    }

    // ---- input ---------------------------------------------------------------------------

    /** Topmost sprite under a stage point (highest locZ, then highest channel). */
    fun spriteAt(x: Int, y: Int): Sprite? = sprites.asReversed()
        .sortedByDescending { it.locZ }
        .firstOrNull { it.span != null && it.hit(x, y) }

    /**
     * Topmost *active* sprite (one with behaviours) under a point: mouse events go there,
     * as `the clickOn` reports; sprites without scripts never intercept the mouse.
     */
    fun activeSpriteAt(x: Int, y: Int): Sprite? = sprites.asReversed()
        .sortedByDescending { it.locZ }
        .firstOrNull { it.span != null && it.instances.isNotEmpty() && it.hit(x, y) }

    fun mouseMove(x: Int, y: Int) {
        mouseX = x; mouseY = y
        flashAt(x, y)?.let { (s, p) -> s.flash.player?.mouseMove(p.first, p.second) }
        updateRollover()
    }

    fun mouseDown(x: Int, y: Int) {
        vm.resetBudget()
        mouseMove(x, y)
        mouseIsDown = true
        flashAt(x, y)?.let { (s, p) -> s.flash.player?.mouseDown(p.first, p.second) }
        downSprite = activeSpriteAt(x, y)
        spriteEvent(downSprite, "mouseDown")
        jumpAfterInput()
    }

    fun mouseUp(x: Int, y: Int) {
        vm.resetBudget()
        mouseMove(x, y)
        mouseIsDown = false
        flashAt(x, y)?.let { (s, p) -> s.flash.player?.mouseUp(p.first, p.second) }
        val target = downSprite
        downSprite = null
        if (target != null && target.span != null && !target.hit(x, y)) {
            sendSprite(target, "mouseUpOutside", emptyList())
        } else {
            spriteEvent(target ?: activeSpriteAt(x, y), "mouseUp")
        }
        jumpAfterInput()
    }

    /**
     * `go` from an input handler moves the playhead at once, without the current frame's
     * exitFrame (so a frame script looping with `go to the frame` cannot cancel a button's go).
     */
    private fun jumpAfterInput() {
        val target = pendingFrame ?: return
        pendingFrame = null
        enterFrame(target, startMovie = false)
    }

    private fun updateRollover() {
        val top = activeSpriteAt(mouseX, mouseY)
        if (top === rollSprite) return
        val previous = rollSprite
        rollSprite = top
        previous?.let { if (it.span != null) sendSprite(it, "mouseLeave", emptyList()) }
        top?.let { sendSprite(it, "mouseEnter", emptyList()) }
    }

    /** Key press: `the keyDownScript` (a Lingo statement) runs first, then keyDown handlers. */
    fun keyDown(key: String) {
        vm.resetBudget()
        lastKey = key
        pressedKeys += key.lowercase()
        (keyDownScript as? LString)?.value?.takeIf { it.isNotBlank() }?.let(::runStatement)
        if (!sendFrameScript("keyDown", emptyList())) vm.callGlobal("keyDown", emptyList())
        jumpAfterInput()
    }

    fun keyUp(key: String) {
        pressedKeys -= key.lowercase()
    }

    private val callStatement = Regex("""^\s*([A-Za-z_]\w*)(?:\.([A-Za-z_]\w*))?\s*\(\s*\)\s*$""")

    /** Run the statement forms titles put in `the keyDownScript`: `handler()` and `global.method()`. */
    fun runStatement(source: String) {
        val match = callStatement.matchEntire(source) ?: throw LingoError("unsupported Lingo statement: $source")
        val (first, second) = match.destructured
        if (second.isEmpty()) vm.callFunction(first, emptyList())
        else {
            val target = vm.global(first)
            vm.callMethod(target, second, listOf(target)) ?: throw LingoError("${target.asText()} has no method $second")
        }
    }

    // ---- stage ---------------------------------------------------------------------------

    /** Visible sprites back to front. */
    fun displayList(): List<DisplayItem> = sprites
        .filter { s -> s.span != null && s.visible && s.member?.type.let { it != null && it != "empty" && it != "script" && it != "sound" } }
        .sortedWith(compareBy({ it.locZ }, { it.number }))
        .map { s ->
            val b = s.bounds()
            DisplayItem(s.number, s.member!!, b[0], b[1], b[2], b[3], s.ink, s.blend, s.rotation, s.flipH, s.flipV, s.flash.current(),
                LColor.of(s.getProp("color")).argb, LColor.of(s.getProp("bgColor")).argb)
        }

    private val stageLeft get() = (environment.desktopWidth - movie.stageWidth) / 2
    private val stageTop get() = (environment.desktopHeight - movie.stageHeight) / 2

    private val stage = object : LingoValue.LHost {
        override val ilk = "window"
        override fun getProp(name: String): LingoValue = when (name.lowercase()) {
            "rect", "drawrect" -> LRect(LInt(stageLeft), LInt(stageTop), LInt(stageLeft + movie.stageWidth), LInt(stageTop + movie.stageHeight))
            "sourcerect" -> LRect(LInt(0), LInt(0), LInt(movie.stageWidth), LInt(movie.stageHeight))
            "title", "name" -> LString("stage")
            "visible" -> LingoValue.TRUE
            "image" -> LingoImage(movie.stageWidth, movie.stageHeight)
            else -> movieProps["stage.${name.lowercase()}"] ?: Void
        }
        override fun setProp(name: String, value: LingoValue) { movieProps["stage.${name.lowercase()}"] = value }
        override fun call(method: String, args: List<LingoValue>): LingoValue? = Void
    }

    // ---- LingoHost -----------------------------------------------------------------------

    override fun getMovieProp(name: String): LingoValue? = when (name.lowercase()) {
        "milliseconds", "ticks" -> if (name.equals("ticks", true)) LInt((milliseconds() * 60 / 1000).toInt()) else LInt(milliseconds().toInt())
        "moviepath", "applicationpath" -> LString(environment.moviePath)
        "framelabel" -> movie.labelAt(frame)?.let(::LString) ?: LInt(0)
        "frame" -> LInt(frame)
        "platform" -> LString(environment.platform)
        "runmode" -> LString(environment.runMode)
        "stage" -> stage
        "actorlist" -> actorList
        "mouseloc" -> LPoint(LInt(mouseX), LInt(mouseY))
        "environment" -> LPropList(mutableListOf(
            LSymbol("shockMachine") to LInt(0), LSymbol("platform") to LString(environment.platform),
            LSymbol("runMode") to LString(environment.runMode), LSymbol("colorDepth") to LInt(32),
            LSymbol("internetConnected") to LSymbol("offline"), LSymbol("uiLanguage") to LString("English"),
            LSymbol("osLanguage") to LString("English"), LSymbol("productBuildVersion") to LString("8.5"),
        ))
        "desktoprectlist" -> LList(mutableListOf(LRect(LInt(0), LInt(0), LInt(environment.desktopWidth), LInt(environment.desktopHeight))))
        "keydownscript" -> keyDownScript
        "number of castlibs" -> LInt(castLibs.size)
        "number of castmembers" -> LInt(castLibs.first().memberCount)
        else -> movieProps[name.lowercase()] ?: extensions?.getMovieProp(name)
    }

    override fun setMovieProp(name: String, value: LingoValue): Boolean {
        when (name.lowercase()) {
            "keydownscript" -> keyDownScript = value
            "framelabel", "frame", "milliseconds", "moviepath", "platform", "runmode", "stage", "actorlist" ->
                throw LingoError("cannot set the $name")
            else -> movieProps[name.lowercase()] = value
        }
        return true
    }

    override fun theBuiltin(name: String): LingoValue? = when (name.lowercase()) {
        "key" -> LString(lastKey)
        "frame" -> LInt(frame)
        "mousedown" -> LingoValue.bool(mouseIsDown)
        "mouseh" -> LInt(mouseX)
        "mousev" -> LInt(mouseY)
        "stageleft" -> LInt(stageLeft)
        "stagetop" -> LInt(stageTop)
        "stageright" -> LInt(stageLeft + movie.stageWidth)
        "stagebottom" -> LInt(stageTop + movie.stageHeight)
        "date" -> LString(LocalDateTime.now().format(DateTimeFormatter.ofPattern("M/d/yy")))
        "time" -> LString(LocalDateTime.now().format(DateTimeFormatter.ofPattern("h:mm a")))
        else -> getMovieProp(name)
    }

    override fun callBuiltin(vm: LingoVm, name: String, args: List<LingoValue>): LingoValue? {
        fun arg(i: Int) = args.getOrElse(i) { Void }
        return when (name.lowercase()) {
            "sprite" -> arg(0).let { if (it is Sprite) it else sprite(it.toInt()) }
            "member" -> member(arg(0), args.getOrNull(1)?.let { castLib(it) ?: throw LingoError("castLib ${it.asText()} not found") })
            "castlib" -> castLib(arg(0)) ?: throw LingoError("castLib ${arg(0).asText()} not found")
            "go" -> { go(arg(0)); Void }
            "updatestage" -> { stageUpdates++; Void }
            "sound" -> soundChannels.getOrNull(arg(0).toInt() - 1) ?: throw LingoError("sound channel ${arg(0).asText()} out of range")
            "puppetsound" -> {
                val (channel, target) = if (args.size >= 2) arg(0).toInt() to arg(1) else 1 to arg(0)
                val ch = soundChannels.getOrNull(channel - 1) ?: throw LingoError("sound channel $channel out of range")
                if (target == Void || target is LInt && target.value == 0) ch.stop() else ch.play(member(target, null))
                Void
            }
            "soundbusy" -> LingoValue.bool(soundChannels.getOrNull(arg(0).toInt() - 1)?.busy ?: false)
            "cursor" -> { cursor = arg(0); Void }
            "rgb" -> if (arg(0) is LString) LColor.of(arg(0)) else LColor(arg(0).toInt(), arg(1).toInt(), arg(2).toInt())
            "image" -> LingoImage(arg(0).toInt(), arg(1).toInt(), args.getOrNull(2)?.toInt() ?: 32)
            "keypressed" -> LingoValue.bool(arg(0).let { k ->
                if (k is LInt) pressedKeys.contains("#${k.value}") else pressedKeys.contains(k.asText().lowercase())
            })
            "quit", "halt" -> { quitRequested = true; Void }
            "alert" -> { warnings += "alert: ${arg(0).asText()}"; Void }
            "put" -> { log += args.joinToString(" ") { it.asText() }; Void }
            "new" -> if (arg(0) is LSymbol) {
                val lib = args.getOrNull(1)?.let { castLib(it) ?: throw LingoError("castLib ${it.asText()} not found") } ?: castLibs.first()
                lib.newMember((arg(0) as LSymbol).name)
            } else extensions?.callBuiltin(vm, name, args)
            "loctocharpos", "charpostoloc" -> (arg(0) as? CastMember ?: member(arg(0), null)).call(name, args.drop(1))
            "framelabel" -> getMovieProp("frameLabel")
            "marker" -> LInt(movie.labels.map { it.first }.lastOrNull { it <= frame } ?: 0)
            "label" -> LInt(movie.labelFrame(arg(0).asText()) ?: 0)
            else -> extensions?.callBuiltin(vm, name, args)
        }
    }

    companion object {
        const val MEDIA_IMAGE_CACHE = 96
    }

    /** Whether a Lingo value is the given symbol (helper for hosts and tests). */
    fun isSymbol(value: LingoValue, name: String) = lingoEquals(value, LSymbol(name))
}

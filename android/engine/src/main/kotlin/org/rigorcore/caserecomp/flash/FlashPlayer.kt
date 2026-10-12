package org.rigorcore.caserecomp.flash

import java.util.TreeMap
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Services a Flash movie asks of its container. */
interface FlashHost {
    /** `getURL(url, target)` — Director turns "event:" and "lingo:" URLs into Lingo. */
    fun getURL(url: String, target: String) {}
    /** A Sound object started a library sound by its export name. */
    fun sound(name: String) {}
    fun warn(message: String) {}
    /** An embedded MP3 sound to play once at [volume] (0..100). */
    fun playSound(mp3: ByteArray, volume: Double) {}
    /** Clock for getTimer and setInterval: the container's, so paused or simulated time is honoured. */
    fun millis(): Long = System.currentTimeMillis()
}

/** One shape to draw: [matrix] maps shape pixels to movie pixels. */
/**
 * A shape to draw. [masks] are the clip layers (PlaceObject clipDepth) covering it: the shape
 * shows only where every layer has at least one of its shapes filled.
 */
class FlashDraw(val shape: SwfShape, val matrix: SwfMatrix, val alpha: Double, val masks: List<List<FlashDraw>> = emptyList())

/** AVM1 `undefined`. */
object Undefined { override fun toString() = "undefined" }
object AvmNull { override fun toString() = "null" }

open class AvmObject {
    val props = LinkedHashMap<String, Any>()
    open fun get(name: String): Any = props[name] ?: Undefined
    open fun set(name: String, value: Any) { props[name] = value }
}

/** Array object: indexed items plus ordinary properties. */
class AvmArray(val items: MutableList<Any> = ArrayList()) : AvmObject() {
    override fun get(name: String): Any {
        if (name == "length") return items.size.toDouble()
        name.toIntOrNull()?.let { return items.getOrNull(it) ?: Undefined }
        return super.get(name)
    }
    override fun set(name: String, value: Any) {
        if (name == "length") { val n = Avm.number(value).toInt().coerceIn(0, MAX_ITEMS); while (items.size > n) items.removeAt(items.size - 1); while (items.size < n) items += Undefined; return }
        val index = name.toIntOrNull()
        if (index != null && index in 0 until MAX_ITEMS) { while (items.size <= index) items += Undefined; items[index] = value; return }
        super.set(name, value)
    }
    companion object { const val MAX_ITEMS = 1_000_000 }
}

/** A built-in function (Math.floor, setInterval...): [fn] receives `this` and the arguments. */
class AvmNative(val fn: (Any, List<Any>) -> Any) : AvmObject()

/**
 * A user function. DefineFunction2 functions keep [registerCount] registers and preload
 * `this`, `arguments`, `_root`... as [flags] ask; each parameter has a register or a name.
 * [outer] is the activation they were defined in, for nested functions' closures.
 */
class AvmFunction(
    val code: ByteArray, val pool: List<String>, val params: List<String>, val home: MovieClip,
    val registerCount: Int = 4, val flags: Int = 0, val paramRegisters: List<Int> = emptyList(),
    val outer: Scope? = null, val v2: Boolean = false,
) : AvmObject()

/** One function activation's local variables, chained to the activation that defined it. */
class Scope(val vars: HashMap<String, Any> = HashMap(), val parent: Scope? = null, val frame: Boolean = false) {
    fun find(name: String): Scope? = if (vars.containsKey(name)) this else parent?.find(name)
}

class AvmSound(private val player: FlashPlayer) : AvmObject() {
    var attached: String? = null
    var volume = 100.0
    fun call(method: String, args: List<Any>): Any = when (method.lowercase()) {
        "attachsound" -> { attached = args.firstOrNull()?.let { Avm.string(it) }; Undefined }
        "start" -> { attached?.let { player.playExported(it, volume) }; Undefined }
        "setvolume" -> { volume = args.firstOrNull()?.let(Avm::number) ?: 100.0; Undefined }
        "getvolume" -> volume
        else -> Undefined
    }
}

/** A placed character on a timeline. */
class FlashInstance(var depth: Int, val character: SwfCharacter, var matrix: SwfMatrix, var colorTransform: SwfColorTransform?,
                    var name: String?, val clip: MovieClip?, var clipDepth: Int = 0)

/** A timeline (the root movie or a sprite) with its display list, variables and playhead. */
class MovieClip(private val player: FlashPlayer, val timeline: SwfTimeline, val parent: MovieClip?, var instance: FlashInstance?) : AvmObject() {
    var frame = 0
        private set
    var playing = true
    var visible = true
    val children = TreeMap<Int, FlashInstance>()
    val frameCount get() = timeline.frames.size

    fun child(name: String): MovieClip? = children.values.firstOrNull { it.name.equals(name, ignoreCase = true) }?.clip

    private var jumps = 0

    /**
     * Move the playhead, rebuilding the display list and running the target frame's
     * actions. A goto made by those actions wins: this one stops where it was redirected.
     */
    fun goTo(target: Int, runActions: Boolean = true) {
        val n = target.coerceIn(1, maxOf(1, frameCount))
        val jump = ++jumps
        if (n < frame) { children.entries.removeIf { it.key < DYNAMIC_DEPTH }; frame = 0 }
        while (frame < n) {
            frame++
            apply(timeline.frames.getOrNull(frame - 1) ?: break, runActions && frame == n)
            if (jumps != jump) return
        }
    }

    fun goTo(target: Any, play: Boolean) {
        playing = play
        val n = when (target) {
            is Double -> target.toInt()
            else -> Avm.string(target).let { label -> label.toIntOrNull() ?: timeline.labelFrame(label) ?: run {
                player.host.warn("Flash label not found: $label"); return
            } }
        }
        goTo(n)
    }

    private fun apply(f: SwfFrame, runActions: Boolean) {
        for (tag in f.tags) when (tag) {
            is SwfTag.Place -> place(tag)
            is SwfTag.Remove -> children.remove(tag.depth)
            else -> Unit
        }
        if (!runActions) return
        for (tag in f.tags) when (tag) {
            is SwfTag.Action -> player.run(tag.code, this)
            is SwfTag.StartSound -> {
                player.movie.exports.entries.firstOrNull { it.value == tag.soundId }?.let { player.host.sound(it.key) }
                player.movie.sounds[tag.soundId]?.let { player.host.playSound(it, 100.0) }
            }
            else -> Unit
        }
    }

    private fun place(tag: SwfTag.Place) {
        val existing = children[tag.depth]
        if (tag.move && existing != null && (tag.characterId == null || tag.characterId == existing.character.id)) {
            tag.matrix?.let { existing.matrix = it }
            tag.colorTransform?.let { existing.colorTransform = it }
            tag.name?.let { existing.name = it }
            return
        }
        val id = tag.characterId ?: return
        val character = player.movie.characters[id] ?: run { player.host.warn("Flash character $id missing"); return }
        add(character, tag.depth, tag.matrix ?: existing?.matrix ?: SwfMatrix.IDENTITY,
            tag.colorTransform ?: existing?.colorTransform, tag.name ?: existing?.name, tag.clipDepth)
    }

    private fun add(character: SwfCharacter, depth: Int, matrix: SwfMatrix, color: SwfColorTransform?, name: String?, clipDepth: Int = 0): FlashInstance {
        val clip = when (character) {
            is SwfSprite -> MovieClip(player, character.timeline, this, null)
            is SwfButton -> MovieClip(player, SwfTimeline(emptyList()), this, null).also { it.button = character }
            else -> null
        }
        val placed = FlashInstance(depth, character, matrix, color, name, clip, clipDepth)
        children[depth] = placed
        clip?.let { it.instance = placed; if (it.button != null) it.showButtonState(BUTTON_UP) else it.goTo(1) }
        return placed
    }

    /** A DefineButton2 instance: its children are the records of the current state. */
    var button: SwfButton? = null
        private set
    private var buttonState = 0

    internal fun showButtonState(state: Int) {
        val b = button ?: return
        if (state == buttonState) return
        buttonState = state
        children.clear()
        for (record in b.records.filter { it.states and state != 0 }.sortedBy { it.depth }) {
            val character = player.movie.characters[record.characterId] ?: continue
            add(character, record.depth, record.matrix, record.colorTransform, null)
        }
    }

    /** Whether a root point falls in this button's hit-area records ([m] maps the button to root). */
    internal fun buttonHit(m: SwfMatrix, x: Double, y: Double): Boolean {
        val b = button ?: return false
        for (record in b.records) {
            if (record.states and BUTTON_HIT == 0) continue
            val rm = m * record.matrix
            when (val c = player.movie.characters[record.characterId]) {
                is SwfShape -> if (player.contains(FlashDraw(c, rm, 1.0), x, y)) return true
                is SwfSprite -> {
                    val temp = MovieClip(player, c.timeline, this, null).also { it.goTo(1, runActions = false) }
                    val draws = mutableListOf<FlashDraw>()
                    player.collect(temp, rm, 1.0, emptyList(), draws)
                    if (draws.any { player.contains(it, x, y) }) return true
                }
                else -> Unit
            }
        }
        return false
    }

    /** duplicateMovieClip: a copy of child clip [source] (same sprite, transform) named [name] at [depth]. */
    fun duplicate(source: MovieClip, name: String, depth: Int) {
        val inst = source.instance ?: return
        add(inst.character, depth, inst.matrix, inst.colorTransform, name)
    }

    /** attachMovie: a library symbol exported as [linkage] placed at [depth]. */
    fun attach(linkage: String, name: String, depth: Int, init: AvmObject?): MovieClip? {
        val id = player.movie.exports.entries.firstOrNull { it.key.equals(linkage, ignoreCase = true) }?.value
        val character = id?.let { player.movie.characters[it] } as? SwfSprite ?: run { player.host.warn("Flash symbol not exported: $linkage"); return null }
        val clip = MovieClip(player, character.timeline, this, null)
        val placed = FlashInstance(depth, character, SwfMatrix.IDENTITY, null, name, clip)
        clip.instance = placed
        children[depth] = placed
        init?.props?.forEach { (k, v) -> clip.set(k, v) }
        clip.goTo(1)
        return clip
    }

    fun createEmpty(name: String, depth: Int): MovieClip {
        val clip = MovieClip(player, SwfTimeline(emptyList()), this, null)
        val placed = FlashInstance(depth, SwfSprite(-1, SwfTimeline(emptyList())), SwfMatrix.IDENTITY, null, name, clip)
        clip.instance = placed
        children[depth] = placed
        return clip
    }

    /** removeMovieClip: drop a child clip from this timeline's display list. */
    fun remove(target: MovieClip) {
        children.entries.removeIf { it.value.clip === target }
    }

    /** swapDepths with a depth or a sibling clip. */
    fun swapDepths(target: Any) {
        val owner = parent ?: return
        val mine = instance ?: return
        val depth = when (target) {
            is MovieClip -> target.instance?.depth ?: return
            else -> Avm.number(target).takeIf { !it.isNaN() }?.toInt()?.plus(DYNAMIC_DEPTH) ?: return
        }
        val other = owner.children[depth]
        owner.children.remove(mine.depth)
        if (other != null) { other.depth = mine.depth; owner.children[mine.depth] = other }
        mine.depth = depth
        owner.children[depth] = mine
    }

    /** Advance one frame if playing (looping), then this clip's children; onEnterFrame runs each frame. */
    fun advance() {
        if (playing && frameCount > 1) goTo(if (frame >= frameCount) 1 else frame + 1)
        for (child in children.values.toList()) child.clip?.advance()
        (props["onEnterFrame"] as? AvmFunction)?.let { player.call(it, this, emptyList()) }
    }

    /** This clip's transform to root (movie) coordinates. */
    fun globalMatrix(): SwfMatrix = (parent?.globalMatrix() ?: SwfMatrix.IDENTITY) * (instance?.matrix ?: SwfMatrix.IDENTITY)

    /** Bounds of what this clip draws, in the coordinates [space] maps it to. */
    fun bounds(space: SwfMatrix): SwfRect? {
        val draws = mutableListOf<FlashDraw>()
        player.collect(this, space, 1.0, emptyList(), draws)
        var r: SwfRect? = null
        for (d in draws) {
            val b = d.shape.bounds
            for ((px, py) in listOf(b.xMin to b.yMin, b.xMax to b.yMin, b.xMin to b.yMax, b.xMax to b.yMax)) {
                val x = d.matrix.x(px, py); val y = d.matrix.y(px, py)
                r = r?.let { SwfRect(minOf(it.xMin, x), minOf(it.yMin, y), maxOf(it.xMax, x), maxOf(it.yMax, y)) } ?: SwfRect(x, y, x, y)
            }
        }
        return r
    }

    /** hitTest(x, y, shapeFlag) in root coordinates, or hitTest(otherClip) by bounding boxes. */
    fun hitTest(args: List<Any>): Boolean {
        val first = args.firstOrNull() ?: return false
        if (first is MovieClip) {
            val a = bounds(globalMatrix()) ?: return false
            val b = first.bounds(first.globalMatrix()) ?: return false
            return a.xMin <= b.xMax && b.xMin <= a.xMax && a.yMin <= b.yMax && b.yMin <= a.yMax
        }
        val x = Avm.number(first); val y = Avm.number(args.getOrElse(1) { Undefined })
        if (Avm.truthy(args.getOrElse(2) { false })) {
            val draws = mutableListOf<FlashDraw>()
            player.collect(this, globalMatrix(), 1.0, emptyList(), draws)
            return draws.any { player.contains(it, x, y) }
        }
        val b = bounds(globalMatrix()) ?: return false
        return x >= b.xMin && x <= b.xMax && y >= b.yMin && y <= b.yMax
    }

    val target: String get() = generateSequence(this) { it.parent }.toList().reversed().drop(1)
        .joinToString("") { "/" + (it.instance?.name ?: "") }.ifEmpty { "/" }

    override fun get(name: String): Any {
        props[name]?.let { return it }
        child(name)?.let { return it }
        val m = instance?.matrix ?: SwfMatrix.IDENTITY
        return when (name.lowercase()) {
            "_x" -> m.tx; "_y" -> m.ty
            "_xscale" -> sqrt(m.a * m.a + m.b * m.b) * 100
            "_yscale" -> sqrt(m.c * m.c + m.d * m.d) * 100
            "_rotation" -> Math.toDegrees(atan2(m.b, m.a))
            "_alpha" -> (instance?.colorTransform?.alphaMult ?: 1.0) * 100
            "_visible" -> visible
            "_currentframe" -> frame.toDouble()
            "_totalframes", "_framesloaded" -> frameCount.toDouble()
            "_name" -> instance?.name ?: ""
            "_parent" -> parent ?: Undefined
            "_root" -> player.root
            "_target" -> target
            "_width" -> bounds(m)?.width ?: 0.0
            "_height" -> bounds(m)?.height ?: 0.0
            "_xmouse" -> globalMatrix().inverse()?.x(player.mouseX, player.mouseY) ?: 0.0
            "_ymouse" -> globalMatrix().inverse()?.y(player.mouseX, player.mouseY) ?: 0.0
            "_droptarget" -> player.dropTarget?.target ?: ""
            else -> Undefined
        }
    }

    override fun set(name: String, value: Any) {
        val inst = instance
        val m = inst?.matrix ?: SwfMatrix.IDENTITY
        when (name.lowercase()) {
            "_x" -> inst?.matrix = m.copy(tx = Avm.number(value))
            "_y" -> inst?.matrix = m.copy(ty = Avm.number(value))
            "_xscale", "_yscale", "_rotation" -> {
                val rotation = if (name.equals("_rotation", true)) Math.toRadians(Avm.number(value)) else atan2(m.b, m.a)
                val sx = if (name.equals("_xscale", true)) Avm.number(value) / 100 else sqrt(m.a * m.a + m.b * m.b)
                val sy = if (name.equals("_yscale", true)) Avm.number(value) / 100 else sqrt(m.c * m.c + m.d * m.d)
                inst?.matrix = m.copy(a = sx * cos(rotation), b = sx * sin(rotation), c = -sy * sin(rotation), d = sy * cos(rotation))
            }
            "_width", "_height" -> {
                val b = bounds(m) ?: return
                val current = if (name.equals("_width", true)) b.width else b.height
                if (current <= 0.0) return
                val k = Avm.number(value) / current
                inst?.matrix = if (name.equals("_width", true)) m.copy(a = m.a * k, b = m.b * k) else m.copy(c = m.c * k, d = m.d * k)
            }
            "_alpha" -> inst?.colorTransform = SwfColorTransform(Avm.number(value) / 100)
            "_visible" -> visible = Avm.truthy(value)
            else -> props[name] = value
        }
    }

    fun call(method: String, args: List<Any>): Any? = when (method.lowercase()) {
        "gotoandplay" -> { goTo(args.firstOrNull() ?: 1.0, play = true); Undefined }
        "gotoandstop" -> { goTo(args.firstOrNull() ?: 1.0, play = false); Undefined }
        "play" -> { playing = true; Undefined }
        "stop" -> { playing = false; Undefined }
        "nextframe" -> { playing = false; goTo(minOf(frame + 1, frameCount)); Undefined }
        "prevframe" -> { playing = false; goTo(maxOf(frame - 1, 1)); Undefined }
        "getbytesloaded", "getbytestotal" -> 1.0
        "duplicatemovieclip" -> {
            parent?.duplicate(this, args.getOrNull(0)?.let(Avm::string) ?: "", Avm.number(args.getOrElse(1) { 0.0 }).toInt() + DYNAMIC_DEPTH); Undefined
        }
        "removemovieclip" -> { parent?.remove(this); Undefined }
        "attachmovie" -> attach(Avm.string(args.getOrElse(0) { "" }), Avm.string(args.getOrElse(1) { "" }),
            Avm.number(args.getOrElse(2) { 0.0 }).toInt() + DYNAMIC_DEPTH, args.getOrNull(3) as? AvmObject) ?: Undefined
        "createemptymovieclip" -> createEmpty(Avm.string(args.getOrElse(0) { "" }), Avm.number(args.getOrElse(1) { 0.0 }).toInt() + DYNAMIC_DEPTH)
        "getnexthighestdepth" -> ((children.keys.maxOrNull() ?: (DYNAMIC_DEPTH - 1)) + 1 - DYNAMIC_DEPTH).coerceAtLeast(0).toDouble()
        "getdepth" -> ((instance?.depth ?: DYNAMIC_DEPTH) - DYNAMIC_DEPTH).toDouble()
        "swapdepths" -> { args.firstOrNull()?.let(::swapDepths); Undefined }
        "hittest" -> hitTest(args)
        "startdrag" -> { player.startDrag(this, args); Undefined }
        "stopdrag" -> { player.stopDrag(); Undefined }
        "getbounds" -> {
            val space = (args.firstOrNull() as? MovieClip)?.globalMatrix()?.inverse()?.let { it * globalMatrix() } ?: globalMatrix()
            val b = bounds(space)
            AvmObject().also { o -> o.set("xMin", b?.xMin ?: 0.0); o.set("yMin", b?.yMin ?: 0.0); o.set("xMax", b?.xMax ?: 0.0); o.set("yMax", b?.yMax ?: 0.0) }
        }
        "localtoglobal", "globaltolocal" -> {
            (args.firstOrNull() as? AvmObject)?.let { p ->
                val m = if (method.equals("localToGlobal", true)) globalMatrix() else globalMatrix().inverse() ?: SwfMatrix.IDENTITY
                val x = Avm.number(p.get("x")); val y = Avm.number(p.get("y"))
                p.set("x", m.x(x, y)); p.set("y", m.y(x, y))
            }
            Undefined
        }
        else -> when (val f = props[method]) {
            is AvmFunction -> player.call(f, this, args)
            is AvmNative -> f.fn(this, args)
            else -> null
        }
    }

    companion object {
        const val BUTTON_UP = 1
        const val BUTTON_OVER = 2
        const val BUTTON_DOWN = 4
        const val BUTTON_HIT = 8

        /**
         * Script depth 0 (attachMovie, swapDepths, duplicateMovieClip...) as a display-list key: above
         * every timeline depth, like the player's -16384 timeline offset. A timeline rewind keeps them.
         */
        const val DYNAMIC_DEPTH = 16384
    }
}

/**
 * Plays a parsed SWF: timelines, a display list of shapes and sprites, the AVM1 subset
 * used by UI and puzzle movies (frame scripts, functions, clip event handlers, Sound,
 * intervals, dragging, getURL) and mouse handling for clips with button handlers.
 */
class FlashPlayer(val movie: SwfMovie, val host: FlashHost = object : FlashHost {}) {
    val root = MovieClip(this, movie.root, null, null)
    internal val globals = AvmObject()
    private var hover: MovieClip? = null
    private var pressed: MovieClip? = null
    private var steps = 0L
    internal val random = Random.Default

    var mouseX = 0.0; private set
    var mouseY = 0.0; private set
    internal var dropTarget: MovieClip? = null

    private var queued: Pair<Any, Boolean>? = null

    private class Interval(val id: Int, val fn: AvmFunction?, val target: Any?, val method: String?, val period: Long, var due: Long, val args: List<Any>)
    private val intervals = LinkedHashMap<Int, Interval>()
    private var nextInterval = 1

    private class Drag(val clip: MovieClip, val dx: Double, val dy: Double, val limits: SwfRect?)
    private var drag: Drag? = null

    init {
        installGlobals()
        root.goTo(1)
    }

    /**
     * One movie frame. A goto requested by the container since the last frame is entered
     * now instead of advancing, so its frame actions never run inside the container's call.
     */
    fun advance() {
        steps = 0
        val goto = queued
        if (goto != null) {
            queued = null
            root.goTo(goto.first, play = goto.second)
            for (child in root.children.values.toList()) child.clip?.advance()
        } else {
            root.advance()
        }
        runIntervals()
    }

    fun gotoAndPlay(target: Any) { queued = target to true }
    fun gotoAndStop(target: Any) { queued = target to false }
    val pendingGoto: Boolean get() = queued != null

    /** Sound.start(): the library sound exported as [name]. */
    internal fun playExported(name: String, volume: Double) {
        host.sound(name)
        val id = movie.exports.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value ?: return
        movie.sounds[id]?.let { host.playSound(it, volume) }
    }

    /** A `_level0` variable (or a _global one) for the container's getVariable. */
    fun variable(name: String): Any {
        val value = root.get(name)
        return if (value !== Undefined) value else globals.get(name)
    }

    /** An ActionScript function on the root timeline, called by the container (`sprite.fn(args)`). */
    fun callRoot(name: String, args: List<Any>): Any? = when (val f = root.props[name] ?: globals.props[name]) {
        is AvmFunction -> { steps = 0; call(f, root, args) }
        else -> null
    }

    // ---- intervals -----------------------------------------------------------------------

    private fun runIntervals() {
        val now = host.millis()
        for (interval in intervals.values.toList()) {
            var fired = 0
            while (intervals.containsKey(interval.id) && now >= interval.due && fired < 4) {
                interval.due += interval.period.coerceAtLeast(1)
                fired++
                steps = 0
                if (interval.fn != null) call(interval.fn, interval.fn.home, interval.args)
                else when (val target = interval.target) {
                    is MovieClip -> target.call(interval.method ?: "", interval.args)
                    is AvmObject -> (target.get(interval.method ?: "") as? AvmFunction)?.let { call(it, target, interval.args) }
                }
            }
            if (now >= interval.due) interval.due = now + interval.period.coerceAtLeast(1)
        }
    }

    private fun setInterval(args: List<Any>): Any {
        val first = args.firstOrNull() ?: return Undefined
        val id = nextInterval++
        val interval = if (first is AvmFunction) {
            val period = Avm.number(args.getOrElse(1) { 0.0 }).toLong()
            Interval(id, first, null, null, period, host.millis() + period.coerceAtLeast(1), args.drop(2))
        } else {
            val period = Avm.number(args.getOrElse(2) { 0.0 }).toLong()
            Interval(id, null, first, Avm.string(args.getOrElse(1) { "" }), period, host.millis() + period.coerceAtLeast(1), args.drop(3))
        }
        if (intervals.size >= 256) return Undefined
        intervals[id] = interval
        return id.toDouble()
    }

    // ---- dragging --------------------------------------------------------------------------

    internal fun startDrag(clip: MovieClip, args: List<Any>) {
        val lock = Avm.truthy(args.getOrElse(0) { false })
        val limits = if (args.size >= 5) SwfRect(Avm.number(args[1]), Avm.number(args[2]), Avm.number(args[3]), Avm.number(args[4])) else null
        val local = parentPoint(clip)
        val dx = if (lock) 0.0 else Avm.number(clip.get("_x")) - local.first
        val dy = if (lock) 0.0 else Avm.number(clip.get("_y")) - local.second
        drag = Drag(clip, dx, dy, limits)
        follow()
    }

    internal fun stopDrag() {
        val d = drag ?: return
        drag = null
        dropTarget = clipUnder(mouseX, mouseY, except = d.clip)
    }

    private fun parentPoint(clip: MovieClip): Pair<Double, Double> {
        val inv = (clip.parent?.globalMatrix() ?: SwfMatrix.IDENTITY).inverse() ?: SwfMatrix.IDENTITY
        return inv.x(mouseX, mouseY) to inv.y(mouseX, mouseY)
    }

    private fun follow() {
        val d = drag ?: return
        val (px, py) = parentPoint(d.clip)
        var x = px + d.dx; var y = py + d.dy
        d.limits?.let { x = x.coerceIn(minOf(it.xMin, it.xMax), maxOf(it.xMin, it.xMax)); y = y.coerceIn(minOf(it.yMin, it.yMax), maxOf(it.yMin, it.yMax)) }
        d.clip.set("_x", x); d.clip.set("_y", y)
    }

    private fun clipUnder(x: Double, y: Double, except: MovieClip): MovieClip? {
        var found: MovieClip? = null
        fun search(clip: MovieClip, parent: SwfMatrix) {
            for (inst in clip.children.values) {
                val child = inst.clip ?: continue
                if (child === except || !child.visible) continue
                val m = parent * inst.matrix
                val draws = mutableListOf<FlashDraw>()
                collect(child, m, 1.0, emptyList(), draws)
                if (draws.any { contains(it, x, y) }) found = child
                search(child, m)
            }
        }
        search(root, SwfMatrix.IDENTITY)
        return found
    }

    // ---- display -------------------------------------------------------------------------

    fun drawList(): List<FlashDraw> = mutableListOf<FlashDraw>().also { collect(root, SwfMatrix.IDENTITY, 1.0, emptyList(), it) }

    internal fun collect(clip: MovieClip, parent: SwfMatrix, alpha: Double, masks: List<List<FlashDraw>>, out: MutableList<FlashDraw>) {
        if (!clip.visible) return
        // Clip layers active in this timeline: (last clipped depth, mask shapes).
        val clips = ArrayList<Pair<Int, List<FlashDraw>>>()
        for (inst in clip.children.values) {
            clips.removeAll { it.first < inst.depth }
            val m = parent * inst.matrix
            if (inst.clipDepth > 0) {
                // A mask is never drawn; its shapes (whatever their fill/alpha) bound the depths above it.
                val shapes = mutableListOf<FlashDraw>()
                when (val c = inst.character) {
                    is SwfShape -> shapes += FlashDraw(c, m, 1.0)
                    is SwfSprite, is SwfButton -> inst.clip?.let { collect(it, m, 1.0, emptyList(), shapes) }
                    else -> Unit
                }
                clips += inst.clipDepth to shapes
                continue
            }
            val a = inst.colorTransform?.alpha(alpha) ?: alpha
            val active = if (clips.isEmpty()) masks else masks + clips.map { it.second }
            when (val c = inst.character) {
                is SwfShape -> if (a > 0) out += FlashDraw(c, m, a, active)
                is SwfSprite, is SwfButton -> inst.clip?.let { collect(it, m, a, active, out) }
                else -> Unit
            }
        }
    }

    /** Whether any drawn shape covers the movie point. */
    fun hits(x: Double, y: Double): Boolean = drawList().any { contains(it, x, y) && unmasked(it, x, y) }

    private fun unmasked(d: FlashDraw, x: Double, y: Double) = d.masks.all { layer -> layer.any { contains(it, x, y) } }

    internal fun contains(d: FlashDraw, x: Double, y: Double): Boolean {
        val inv = d.matrix.inverse() ?: return false
        val lx = inv.x(x, y); val ly = inv.y(x, y)
        val b = d.shape.bounds
        if (lx < b.xMin || lx >= b.xMax || ly < b.yMin || ly >= b.yMax) return false
        return d.shape.edges.isEmpty() || d.shape.fillAt(lx, ly) != 0
    }

    // ---- mouse ---------------------------------------------------------------------------

    private fun hasHandlers(clip: MovieClip) = clip.button != null || BUTTON_EVENTS.any { clip.props[it] is AvmFunction }

    /** Frontmost clip with button handlers whose shapes cover the point. */
    fun buttonAt(x: Double, y: Double): MovieClip? {
        fun search(clip: MovieClip, parent: SwfMatrix): MovieClip? {
            if (!clip.visible) return null
            for (inst in clip.children.descendingMap().values) {
                if (inst.clipDepth > 0) continue
                val m = parent * inst.matrix
                when (val c = inst.character) {
                    is SwfButton -> inst.clip?.let { child -> if (child.visible && child.buttonHit(m, x, y)) return child }
                    is SwfSprite -> inst.clip?.let { child ->
                        if (child !== drag?.clip) search(child, m)?.let { return it }
                    }
                    is SwfShape -> if (contains(FlashDraw(c, m, 1.0), x, y)) {
                        var owner: MovieClip? = clip
                        while (owner != null && !hasHandlers(owner)) owner = owner.parent
                        if (owner != null) return owner
                    }
                    else -> Unit
                }
            }
            return null
        }
        drag?.let { d -> if (hasHandlers(d.clip)) { val draws = mutableListOf<FlashDraw>(); collect(d.clip, d.clip.globalMatrix(), 1.0, emptyList(), draws); if (draws.any { contains(it, x, y) }) return d.clip } }
        return search(root, SwfMatrix.IDENTITY)
    }

    private fun fire(clip: MovieClip?, event: String) {
        clip ?: return
        clip.button?.let { b ->
            val (state, condition) = when (event) {
                "onRollOver" -> MovieClip.BUTTON_OVER to 0x01
                "onRollOut" -> MovieClip.BUTTON_UP to 0x02
                "onPress" -> MovieClip.BUTTON_DOWN to 0x04
                "onRelease" -> MovieClip.BUTTON_OVER to 0x08
                "onReleaseOutside" -> MovieClip.BUTTON_UP to 0x40
                else -> 0 to 0
            }
            if (state != 0) clip.showButtonState(state)
            // Button actions run on the timeline that holds the button.
            for (action in b.actions) if (action.conditions and condition != 0) { steps = 0; run(action.code, clip.parent ?: root) }
        }
        val handler = clip.props[event] as? AvmFunction ?: return
        steps = 0
        call(handler, clip, emptyList())
    }

    fun mouseMove(x: Double, y: Double) {
        mouseX = x; mouseY = y
        follow()
        val target = buttonAt(x, y)
        if (target === hover) return
        fire(hover, "onRollOut")
        hover = target
        fire(target, "onRollOver")
    }

    fun mouseDown(x: Double, y: Double) {
        mouseMove(x, y)
        pressed = hover
        fire(pressed, "onPress")
    }

    fun mouseUp(x: Double, y: Double) {
        mouseX = x; mouseY = y
        val target = buttonAt(x, y)
        val p = pressed
        pressed = null
        if (p != null && p === target) fire(p, "onRelease") else fire(p, "onReleaseOutside")
        mouseMove(x, y)
    }

    // ---- AVM1 ----------------------------------------------------------------------------

    internal fun run(code: ByteArray, clip: MovieClip) {
        try {
            Avm(this, code, emptyList(), clip, clip, Scope(frame = true), arrayOfNulls(4)).execute()
        } catch (e: FlashError) {
            host.warn("ActionScript stopped: ${e.message}")
        }
    }

    internal fun call(f: AvmFunction, self: Any, args: List<Any>): Any = try {
        if (++depth > 64) throw FlashError("ActionScript call depth exceeded")
        val scope = Scope(parent = f.outer)
        val registers = arrayOfNulls<Any>(maxOf(4, f.registerCount))
        val thisObj: Any = if (self === Undefined) f.home else self
        if (f.v2) {
            var r = 1
            val flags = f.flags
            // Preload order: this, arguments, super, _root, _parent, _global.
            if (flags and 0x01 != 0) registers[r++] = thisObj
            if (flags and 0x04 != 0) registers[r++] = AvmArray(args.toMutableList())
            if (flags and 0x10 != 0) registers[r++] = Undefined
            if (flags and 0x40 != 0) registers[r++] = root
            if (flags and 0x80 != 0) registers[r++] = f.home.parent ?: Undefined
            if (flags and 0x100 != 0) registers[r] = globals
            if (flags and 0x08 == 0) scope.vars["arguments"] = AvmArray(args.toMutableList())
            f.params.forEachIndexed { i, p ->
                val reg = f.paramRegisters.getOrElse(i) { 0 }
                val value = args.getOrElse(i) { Undefined }
                if (reg in 1 until registers.size) registers[reg] = value else scope.vars[p] = value
            }
        } else {
            f.params.forEachIndexed { i, p -> scope.vars[p] = args.getOrElse(i) { Undefined } }
            scope.vars["arguments"] = AvmArray(args.toMutableList())
        }
        Avm(this, f.code, f.pool, thisObj, f.home, scope, registers).execute()
    } catch (e: FlashError) {
        host.warn("ActionScript stopped: ${e.message}"); Undefined
    } finally {
        depth--
    }
    private var depth = 0

    internal fun budget(op: Int, pc: Int, clip: MovieClip) {
        if (++steps > 1_000_000) throw FlashError("ActionScript instruction budget exhausted at action 0x${op.toString(16)} pc $pc frame ${clip.frame}")
    }
    internal fun global(name: String): Any = globals.get(name)

    private fun installGlobals() {
        fun n(args: List<Any>, i: Int) = Avm.number(args.getOrElse(i) { Undefined })
        val math = AvmObject()
        mapOf<String, (List<Any>) -> Any>(
            "floor" to { a -> kotlin.math.floor(n(a, 0)) }, "ceil" to { a -> kotlin.math.ceil(n(a, 0)) },
            "round" to { a -> kotlin.math.floor(n(a, 0) + 0.5) }, "abs" to { a -> kotlin.math.abs(n(a, 0)) },
            "random" to { _ -> random.nextDouble() }, "sqrt" to { a -> sqrt(n(a, 0)) },
            "sin" to { a -> sin(n(a, 0)) }, "cos" to { a -> cos(n(a, 0)) }, "tan" to { a -> kotlin.math.tan(n(a, 0)) },
            "atan" to { a -> kotlin.math.atan(n(a, 0)) }, "atan2" to { a -> atan2(n(a, 0), n(a, 1)) },
            "asin" to { a -> kotlin.math.asin(n(a, 0)) }, "acos" to { a -> kotlin.math.acos(n(a, 0)) },
            "pow" to { a -> Math.pow(n(a, 0), n(a, 1)) }, "exp" to { a -> kotlin.math.exp(n(a, 0)) }, "log" to { a -> kotlin.math.ln(n(a, 0)) },
            "min" to { a -> if (a.isEmpty()) Double.POSITIVE_INFINITY else a.minOf { Avm.number(it) } },
            "max" to { a -> if (a.isEmpty()) Double.NEGATIVE_INFINITY else a.maxOf { Avm.number(it) } },
        ).forEach { (name, f) -> math.set(name, AvmNative { _, a -> f(a) }) }
        math.set("PI", Math.PI); math.set("E", Math.E); math.set("SQRT2", sqrt(2.0))
        globals.set("Math", math)
        globals.set("setInterval", AvmNative { _, a -> setInterval(a) })
        globals.set("clearInterval", AvmNative { _, a -> intervals.remove(Avm.number(a.getOrElse(0) { Undefined }).toInt()); Undefined })
        globals.set("getTimer", AvmNative { _, _ -> host.millis().toDouble() })
        globals.set("parseInt", AvmNative { _, a ->
            val s = Avm.string(a.getOrElse(0) { "" }).trim(); val radix = a.getOrNull(1)?.let { Avm.number(it).toInt() }?.takeIf { it in 2..36 } ?: 10
            Regex("^[+-]?[0-9a-zA-Z]+").find(s)?.value?.let { v -> v.dropLastWhile { false }.toLongOrNull(radix)?.toDouble() } ?: Double.NaN
        })
        globals.set("parseFloat", AvmNative { _, a -> Regex("^[+-]?[0-9]*\\.?[0-9]+([eE][+-]?[0-9]+)?").find(Avm.string(a.getOrElse(0) { "" }).trim())?.value?.toDoubleOrNull() ?: Double.NaN })
        globals.set("isNaN", AvmNative { _, a -> Avm.number(a.getOrElse(0) { Undefined }).isNaN() })
        globals.set("String", AvmNative { _, a -> Avm.string(a.getOrElse(0) { "" }) })
        globals.set("Number", AvmNative { _, a -> Avm.number(a.getOrElse(0) { Undefined }) })
        globals.set("Boolean", AvmNative { _, a -> Avm.truthy(a.getOrElse(0) { false }) })
        globals.set("Array", AvmNative { _, a -> Avm.newArray(a) })
        globals.set("Object", AvmNative { _, _ -> AvmObject() })
    }

    companion object {
        val BUTTON_EVENTS = listOf("onPress", "onRelease", "onReleaseOutside", "onRollOver", "onRollOut")
    }
}

/** Interpreter for the AVM1 actions used by Flash 5-8 UI and puzzle movies. */
internal class Avm(
    private val player: FlashPlayer,
    private val code: ByteArray,
    private var pool: List<String>,
    private val thisObj: Any,
    private val self: MovieClip,
    private val scope: Scope,
    private val registers: Array<Any?>,
) {
    private val stack = ArrayList<Any>()

    private fun pop(): Any = if (stack.isEmpty()) Undefined else stack.removeAt(stack.size - 1)
    private fun push(v: Any) { stack += v }

    private fun lookup(name: String): Any {
        scope.find(name)?.let { return it.vars[name] ?: Undefined }
        return self.get(name).takeIf { it !== Undefined } ?: player.root.get(name).takeIf { it !== Undefined } ?: player.global(name)
    }

    private fun resolve(path: String): Any {
        val parts = path.split('.', '/', ':').filter { it.isNotEmpty() }
        if (parts.isEmpty()) return self
        var value: Any = when (val first = parts[0]) {
            "this" -> thisObj
            "_root", "_level0" -> player.root
            "_parent" -> self.parent ?: Undefined
            "_global" -> player.globals
            else -> lookup(first)
        }
        for (part in parts.drop(1)) value = member(value, part)
        return value
    }

    private fun member(target: Any, name: String): Any = when (target) {
        is AvmObject -> target.get(name)
        is String -> if (name == "length") target.length.toDouble() else Undefined
        else -> Undefined
    }

    fun execute(): Any {
        var pc = 0
        while (pc < code.size) {
            val op = code[pc].toInt() and 0xFF
            player.budget(op, pc, self)
            pc++
            if (op == 0) break
            var data = ByteArray(0)
            var next = pc
            if (op >= 0x80) {
                val len = (code[pc].toInt() and 0xFF) or ((code[pc + 1].toInt() and 0xFF) shl 8)
                if (pc + 2 + len > code.size) throw FlashError("action 0x${op.toString(16)} overruns its code")
                data = code.copyOfRange(pc + 2, pc + 2 + len)
                next = pc + 2 + len
            }
            pc = next
            when (op) {
                0x04 -> self.call("nextFrame", emptyList())
                0x05 -> self.call("prevFrame", emptyList())
                0x06 -> self.playing = true
                0x07 -> self.playing = false
                0x0A, 0x0B, 0x0C, 0x0D -> { val b = number(pop()); val a = number(pop())
                    push(when (op) { 0x0A -> a + b; 0x0B -> a - b; 0x0C -> a * b; else -> a / b }) }
                0x0E -> { val b = number(pop()); push(number(pop()) == b) }
                0x0F -> { val b = number(pop()); push(number(pop()) < b) }
                0x10 -> { val b = truthy(pop()); push(truthy(pop()) && b) }
                0x11 -> { val b = truthy(pop()); push(truthy(pop()) || b) }
                0x12 -> push(!truthy(pop()))
                0x13 -> { val b = string(pop()); push(string(pop()) == b) }
                0x14 -> push(string(pop()).length.toDouble())
                0x15 -> { val count = number(pop()).toInt(); val index = number(pop()).toInt(); val s = string(pop())
                    val from = (index - 1).coerceIn(0, s.length); push(s.substring(from, (from + count.coerceAtLeast(0)).coerceAtMost(s.length))) }
                0x17 -> pop()
                0x18 -> push(number(pop()).let { if (it.isNaN()) 0.0 else it.toLong().toDouble() })
                0x1C -> push(resolve(string(pop())))
                0x1D -> { val value = pop(); setVariable(string(pop()), value) }
                0x20 -> pop() // setTarget2: targets are addressed explicitly by the titles we support
                0x21 -> { val b = string(pop()); push(string(pop()) + b) }
                0x22 -> { val index = number(pop()).toInt(); val target = targetOf(pop()); push(target?.get(PROPERTIES.getOrElse(index) { "" }) ?: Undefined) }
                0x23 -> { val value = pop(); val index = number(pop()).toInt(); targetOf(pop())?.set(PROPERTIES.getOrElse(index) { "" }, value) }
                0x24 -> {
                    // CloneSprite: duplicates sit above timeline depths, like the player's +16384 offset.
                    val depth = number(pop()).toInt(); val name = string(pop())
                    val source = targetOf(pop()) as? MovieClip
                    source?.parent?.duplicate(source, name, depth + 16384)
                }
                0x25 -> (targetOf(pop()) as? MovieClip)?.let { clip -> clip.parent?.remove(clip) }
                0x26 -> pop()
                0x27 -> {
                    val target = targetOf(pop()) as? MovieClip; val lock = pop(); val constrain = truthy(pop())
                    val args = if (constrain) { val b = pop(); val r = pop(); val t = pop(); val l = pop(); listOf(lock, l, t, r, b) } else listOf(lock)
                    target?.let { player.startDrag(it, args) }
                }
                0x28 -> player.stopDrag()
                0x30 -> { val n = number(pop()).toInt(); push(if (n <= 0) 0.0 else player.random.nextInt(n).toDouble()) }
                0x32 -> push((string(pop()).firstOrNull()?.code ?: 0).toDouble())
                0x33 -> push(number(pop()).toInt().toChar().toString())
                0x34 -> push(player.host.millis().toDouble())
                0x3A -> { val name = string(pop()); val target = pop(); (target as? AvmObject)?.props?.remove(name); push(true) }
                0x3B -> { val name = string(pop()); scope.find(name)?.vars?.remove(name); push(true) }
                // `var` in frame code declares a timeline variable; in a function, a local.
                0x3C -> { val value = pop(); val name = string(pop()); if (scope.frame) self.set(name, value) else scope.vars[name] = value }
                0x41 -> { val name = string(pop()); if (!scope.frame && !scope.vars.containsKey(name)) scope.vars[name] = Undefined }
                0x3D -> {
                    val name = string(pop()); val args = List(number(pop()).toInt().coerceIn(0, 1024)) { pop() }
                    push(callFunction(name, args))
                }
                0x3E -> return pop()
                0x3F -> { val b = number(pop()); val a = number(pop()); push(a % b) }
                0x40 -> {
                    val name = string(pop()); val args = List(number(pop()).toInt().coerceIn(0, 1024)) { pop() }
                    push(construct(name, args))
                }
                0x42 -> { val n = number(pop()).toInt().coerceIn(0, 1_000_000); push(AvmArray(MutableList(n) { pop() })) }
                0x43 -> { val n = number(pop()).toInt().coerceIn(0, 1_000_000); val o = AvmObject(); repeat(n) { val v = pop(); o.set(string(pop()), v) }; push(o) }
                0x44 -> push(typeOf(pop()))
                0x47 -> { val b = pop(); val a = pop()
                    push(if (a is String || b is String) string(a) + string(b) else number(a) + number(b)) }
                0x48 -> { val b = pop(); val a = pop(); push(if (a is String && b is String) a < b else number(a) < number(b)) }
                0x67 -> { val b = pop(); val a = pop(); push(if (a is String && b is String) a > b else number(a) > number(b)) }
                0x49 -> { val b = pop(); push(equals(pop(), b)) }
                0x66 -> { val b = pop(); push(strictEquals(pop(), b)) }
                0x4A -> push(number(pop()))
                0x4B -> push(string(pop()))
                0x4C -> push(stack.lastOrNull() ?: Undefined)
                0x4D -> { val b = pop(); val a = pop(); push(b); push(a) }
                0x4E -> { val name = string(pop()); push(member(pop(), name)) }
                0x4F -> { val value = pop(); val name = string(pop()); (pop() as? AvmObject)?.set(name, value) }
                0x50 -> push(number(pop()) + 1)
                0x51 -> push(number(pop()) - 1)
                0x52 -> {
                    val name = pop(); val target = pop(); val args = List(number(pop()).toInt().coerceIn(0, 1024)) { pop() }
                    push(callMethod(target, if (name === Undefined) "" else string(name), args))
                }
                0x53 -> {
                    val name = pop(); val target = pop(); val args = List(number(pop()).toInt().coerceIn(0, 1024)) { pop() }
                    val ctor = if (name === Undefined || string(name).isEmpty()) target else member(target, string(name))
                    push(constructWith(ctor, args))
                }
                0x60 -> { val b = number(pop()).toLong().toInt(); push((number(pop()).toLong().toInt() and b).toDouble()) }
                0x61 -> { val b = number(pop()).toLong().toInt(); push((number(pop()).toLong().toInt() or b).toDouble()) }
                0x62 -> { val b = number(pop()).toLong().toInt(); push((number(pop()).toLong().toInt() xor b).toDouble()) }
                0x63 -> { val b = number(pop()).toInt(); push((number(pop()).toLong().toInt() shl (b and 31)).toDouble()) }
                0x64 -> { val b = number(pop()).toInt(); push((number(pop()).toLong().toInt() shr (b and 31)).toDouble()) }
                0x65 -> { val b = number(pop()).toInt(); push(((number(pop()).toLong().toInt() ushr (b and 31)).toLong() and 0xFFFFFFFFL).toDouble()) }
                0x68 -> { val b = string(pop()); push(string(pop()) > b) }
                0x81 -> self.goTo(((data[0].toInt() and 0xFF) or ((data[1].toInt() and 0xFF) shl 8)) + 1)
                0x83 -> {
                    val parts = String(data, Charsets.ISO_8859_1).split('\u0000')
                    player.host.getURL(parts.getOrElse(0) { "" }, parts.getOrElse(1) { "" })
                }
                0x87 -> { val r = data[0].toInt() and 0xFF; if (r < registers.size) registers[r] = stack.lastOrNull() ?: Undefined }
                0x88 -> {
                    val n = (data[0].toInt() and 0xFF) or ((data[1].toInt() and 0xFF) shl 8)
                    pool = String(data, 2, data.size - 2, Charsets.ISO_8859_1).split('\u0000').take(n)
                }
                0x8B -> Unit // setTarget: titles we support only address their own timeline
                0x8C -> self.goTo(String(data, Charsets.ISO_8859_1).substringBefore('\u0000'), play = self.playing)
                0x8E -> pc = defineFunction2(data, pc)
                0x96 -> pushValues(data)
                0x99 -> pc += signed16(data)
                0x9A -> { val target = string(pop()); player.host.getURL(string(pop()), target) }
                0x9B -> {
                    val name = String(data, Charsets.ISO_8859_1).substringBefore('\u0000')
                    var p = name.length + 1
                    val count = (data[p].toInt() and 0xFF) or ((data[p + 1].toInt() and 0xFF) shl 8); p += 2
                    val params = List(count) {
                        val s = String(data, p, data.size - p, Charsets.ISO_8859_1).substringBefore('\u0000'); p += s.length + 1; s
                    }
                    val size = (data[p].toInt() and 0xFF) or ((data[p + 1].toInt() and 0xFF) shl 8)
                    val f = AvmFunction(code.copyOfRange(pc, pc + size), pool, params, self, outer = scope)
                    pc += size
                    if (name.isEmpty()) push(f) else setVariable(name, f)
                }
                0x9D -> { val offset = signed16(data); if (truthy(pop())) pc += offset }
                0x9F -> { val target = pop(); self.goTo(target, play = (data[0].toInt() and 1) == 1) }
                else -> throw FlashError("unsupported action 0x${op.toString(16)}")
            }
        }
        return Undefined
    }

    /** DefineFunction2: name, parameters with registers, preload flags; returns the pc after its body. */
    private fun defineFunction2(data: ByteArray, pc: Int): Int {
        val name = String(data, Charsets.ISO_8859_1).substringBefore('\u0000')
        var p = name.length + 1
        val count = (data[p].toInt() and 0xFF) or ((data[p + 1].toInt() and 0xFF) shl 8); p += 2
        val registerCount = data[p].toInt() and 0xFF; p += 1
        // Bit fields are read high bit first: byte 1 = parent, root, suppressSuper, super,
        // suppressArguments, arguments, suppressThis, this; byte 2 low bit = _global.
        val b1 = data[p].toInt() and 0xFF; val b2 = data[p + 1].toInt() and 0xFF; p += 2
        val flags = b1 or ((b2 and 1) shl 8)
        val registers = ArrayList<Int>(); val params = ArrayList<String>()
        repeat(count) {
            registers += data[p].toInt() and 0xFF; p++
            val s = String(data, p, data.size - p, Charsets.ISO_8859_1).substringBefore('\u0000'); p += s.length + 1
            params += s
        }
        val size = (data[p].toInt() and 0xFF) or ((data[p + 1].toInt() and 0xFF) shl 8)
        if (pc + size > code.size) throw FlashError("function body overruns its code")
        val f = AvmFunction(code.copyOfRange(pc, pc + size), pool, params, self, registerCount.coerceAtLeast(4), flags, registers, scope, v2 = true)
        if (name.isEmpty()) push(f) else setVariable(name, f)
        return pc + size
    }

    private fun setVariable(path: String, value: Any) {
        val dot = path.lastIndexOfAny(charArrayOf('.', '/', ':'))
        if (dot < 0) {
            val owner = scope.find(path)
            if (owner != null) owner.vars[path] = value else self.set(path, value)
        } else (resolve(path.substring(0, dot)) as? AvmObject)?.set(path.substring(dot + 1), value)
    }

    private fun targetOf(value: Any): AvmObject? = when (value) {
        is AvmObject -> value
        else -> string(value).let { if (it.isEmpty()) self else resolve(it) as? AvmObject }
    }

    private fun construct(name: String, args: List<Any>): Any = when (name) {
        "Sound" -> AvmSound(player)
        "Array" -> newArray(args)
        "Object" -> AvmObject()
        else -> constructWith(resolve(name), args)
    }

    private fun constructWith(ctor: Any, args: List<Any>): Any = when (ctor) {
        is AvmFunction -> AvmObject().also { o -> ctor.get("prototype").let { proto -> if (proto is AvmObject) o.props.putAll(proto.props) }; player.call(ctor, o, args) }
        is AvmNative -> ctor.fn(Undefined, args)
        else -> AvmObject()
    }

    private fun callFunction(name: String, args: List<Any>): Any = when (val f = resolve(name)) {
        is AvmFunction -> player.call(f, Undefined, args)
        is AvmNative -> f.fn(Undefined, args)
        else -> when (name) {
            "String" -> string(args.firstOrNull() ?: "")
            "Number" -> number(args.firstOrNull() ?: Undefined)
            "getTimer" -> player.host.millis().toDouble()
            else -> throw FlashError("function $name is not defined")
        }
    }

    private fun callMethod(target: Any, name: String, args: List<Any>): Any {
        if (target is AvmArray) arrayMethod(target, name, args).let { if (it !== NONE) return it }
        return objectMethod(target, name, args)
    }

    private fun objectMethod(target: Any, name: String, args: List<Any>): Any = when {
        name.isEmpty() && target is AvmFunction -> player.call(target, Undefined, args)
        name.isEmpty() && target is AvmNative -> target.fn(Undefined, args)
        target is MovieClip -> target.call(name, args) ?: Undefined
        target is AvmSound -> target.call(name, args)
        target is AvmObject -> when (val f = target.get(name)) {
            is AvmFunction -> player.call(f, target, args)
            is AvmNative -> f.fn(target, args)
            else -> Undefined
        }
        target is String -> stringMethod(target, name, args)
        target is Double -> if (name == "toString") args.firstOrNull()?.let { r -> target.toLong().toString(number(r).toInt().coerceIn(2, 36)) } ?: string(target) else Undefined
        target is Boolean -> if (name == "toString") target.toString() else Undefined
        else -> Undefined
    }

    private fun arrayMethod(a: AvmArray, name: String, args: List<Any>): Any {
        val items = a.items
        return when (name) {
            "push" -> { items.addAll(args); items.size.toDouble() }
            "pop" -> if (items.isEmpty()) Undefined else items.removeAt(items.size - 1)
            "shift" -> if (items.isEmpty()) Undefined else items.removeAt(0)
            "unshift" -> { items.addAll(0, args); items.size.toDouble() }
            "slice" -> {
                fun idx(v: Any?, def: Int) = v?.let { number(it).toInt() }?.let { if (it < 0) (items.size + it).coerceAtLeast(0) else it.coerceAtMost(items.size) } ?: def
                val from = idx(args.getOrNull(0), 0); val to = idx(args.getOrNull(1), items.size)
                AvmArray(if (from < to) items.subList(from, to).toMutableList() else ArrayList())
            }
            "splice" -> {
                val start = number(args.getOrElse(0) { 0.0 }).toInt().let { if (it < 0) (items.size + it).coerceAtLeast(0) else it.coerceAtMost(items.size) }
                val count = args.getOrNull(1)?.let { number(it).toInt().coerceIn(0, items.size - start) } ?: (items.size - start)
                val removed = ArrayList<Any>()
                repeat(count) { removed += items.removeAt(start) }
                items.addAll(start, args.drop(2))
                AvmArray(removed)
            }
            "concat" -> AvmArray(items.toMutableList().also { out -> args.forEach { if (it is AvmArray) out.addAll(it.items) else out += it } })
            "join" -> items.joinToString(args.firstOrNull()?.let(::string) ?: ",") { string(it) }
            "toString" -> items.joinToString(",") { string(it) }
            "reverse" -> { items.reverse(); a }
            "sort" -> {
                val f = args.firstOrNull()
                if (f is AvmFunction) items.sortWith { x, y -> number(player.call(f, Undefined, listOf(x, y))).let { if (it < 0) -1 else if (it > 0) 1 else 0 } }
                else if (args.firstOrNull()?.let { number(it).toInt() and 16 != 0 } == true) items.sortBy { number(it) }
                else items.sortBy { string(it) }
                a
            }
            else -> NONE
        }
    }

    private fun stringMethod(s: String, name: String, args: List<Any>): Any {
        fun i(n: Int) = number(args.getOrElse(n) { Undefined })
        return when (name) {
            "charAt" -> i(0).toInt().let { if (it in s.indices) s[it].toString() else "" }
            "charCodeAt" -> i(0).toInt().let { if (it in s.indices) s[it].code.toDouble() else Double.NaN }
            "indexOf" -> s.indexOf(string(args.getOrElse(0) { "" }), args.getOrNull(1)?.let { number(it).toInt().coerceAtLeast(0) } ?: 0).toDouble()
            "lastIndexOf" -> s.lastIndexOf(string(args.getOrElse(0) { "" })).toDouble()
            "substr" -> { val from = i(0).toInt().let { if (it < 0) (s.length + it).coerceAtLeast(0) else it.coerceAtMost(s.length) }
                val len = args.getOrNull(1)?.let { number(it).toInt().coerceAtLeast(0) } ?: (s.length - from)
                s.substring(from, (from + len).coerceAtMost(s.length)) }
            "substring" -> { val a = i(0).toInt().coerceIn(0, s.length); val b = args.getOrNull(1)?.let { number(it).toInt().coerceIn(0, s.length) } ?: s.length
                s.substring(minOf(a, b), maxOf(a, b)) }
            "slice" -> { fun idx(v: Any?, def: Int) = v?.let { number(it).toInt() }?.let { if (it < 0) (s.length + it).coerceAtLeast(0) else it.coerceAtMost(s.length) } ?: def
                val a = idx(args.getOrNull(0), 0); val b = idx(args.getOrNull(1), s.length); if (a < b) s.substring(a, b) else "" }
            "split" -> AvmArray(args.firstOrNull()?.let { sep -> string(sep).let { d -> if (d.isEmpty()) s.map { it.toString() } else s.split(d) } }?.toMutableList<Any>() ?: mutableListOf(s))
            "toUpperCase" -> s.uppercase()
            "toLowerCase" -> s.lowercase()
            "toString" -> s
            "concat" -> s + args.joinToString("") { string(it) }
            else -> Undefined
        }
    }

    private fun pushValues(data: ByteArray) {
        var p = 0
        while (p < data.size) {
            when (data[p++].toInt() and 0xFF) {
                0 -> { val s = String(data, p, data.size - p, Charsets.ISO_8859_1).substringBefore('\u0000'); p += s.length + 1; push(s) }
                1 -> { push(java.lang.Float.intBitsToFloat(int32(data, p)).toDouble()); p += 4 }
                2 -> push(AvmNull)
                3 -> push(Undefined)
                4 -> { push(registers.getOrNull(data[p].toInt() and 0xFF) ?: Undefined); p++ }
                5 -> { push(data[p] != 0.toByte()); p++ }
                6 -> { val hi = int32(data, p).toLong() and 0xFFFFFFFFL; val lo = int32(data, p + 4).toLong() and 0xFFFFFFFFL
                    push(java.lang.Double.longBitsToDouble((hi shl 32) or lo)); p += 8 }
                7 -> { push(int32(data, p).toDouble()); p += 4 }
                8 -> { push(pool.getOrElse(data[p].toInt() and 0xFF) { "" }); p++ }
                9 -> { push(pool.getOrElse((data[p].toInt() and 0xFF) or ((data[p + 1].toInt() and 0xFF) shl 8)) { "" }); p += 2 }
                else -> throw FlashError("bad push type")
            }
        }
    }

    companion object {
        private val NONE = Any()

        val PROPERTIES = listOf("_x", "_y", "_xscale", "_yscale", "_currentframe", "_totalframes", "_alpha", "_visible",
            "_width", "_height", "_rotation", "_target", "_framesloaded", "_name", "_droptarget", "_url",
            "_highquality", "_focusrect", "_soundbuftime", "_quality", "_xmouse", "_ymouse")

        private fun int32(d: ByteArray, p: Int) = (d[p].toInt() and 0xFF) or ((d[p + 1].toInt() and 0xFF) shl 8) or
            ((d[p + 2].toInt() and 0xFF) shl 16) or ((d[p + 3].toInt() and 0xFF) shl 24)
        private fun signed16(d: ByteArray) = (((d[0].toInt() and 0xFF) or ((d[1].toInt() and 0xFF) shl 8)).toShort()).toInt()

        fun newArray(args: List<Any>): AvmArray =
            if (args.size == 1 && args[0] is Double) AvmArray(MutableList(number(args[0]).toInt().coerceIn(0, AvmArray.MAX_ITEMS)) { Undefined })
            else AvmArray(args.toMutableList())

        fun typeOf(v: Any): String = when (v) {
            is Double -> "number"; is String -> "string"; is Boolean -> "boolean"
            is MovieClip -> "movieclip"; is AvmFunction, is AvmNative -> "function"
            Undefined -> "undefined"; AvmNull -> "null"
            else -> "object"
        }

        fun string(v: Any): String = when (v) {
            is Double -> if (v.isNaN()) "NaN" else if (v == Math.floor(v) && !v.isInfinite() && kotlin.math.abs(v) < 1e15) v.toLong().toString() else v.toString()
            is Boolean -> v.toString()
            is MovieClip -> "_level0" + v.target.replace('/', '.').let { if (it == ".") "" else it }
            is AvmArray -> v.items.joinToString(",") { string(it) }
            is AvmObject -> "[object Object]"
            else -> v.toString()
        }
        fun number(v: Any): Double = when (v) {
            is Double -> v
            is Boolean -> if (v) 1.0 else 0.0
            is String -> v.trim().let { if (it.isEmpty()) 0.0 else it.toDoubleOrNull() ?: Double.NaN }
            else -> if (v === AvmNull) 0.0 else Double.NaN
        }
        fun truthy(v: Any): Boolean = when (v) {
            is Boolean -> v
            is Double -> v != 0.0 && !v.isNaN()
            is String -> v.isNotEmpty()
            Undefined, AvmNull -> false
            else -> true
        }
        fun equals(a: Any, b: Any): Boolean = when {
            a is Double && b is Double -> a == b
            a is String && b is String -> a == b
            a is Boolean && b is Boolean -> a == b
            (a === Undefined || a === AvmNull) && (b === Undefined || b === AvmNull) -> true
            a === Undefined || a === AvmNull || b === Undefined || b === AvmNull -> false
            a is AvmObject || b is AvmObject -> a === b
            else -> number(a) == number(b)
        }
        fun strictEquals(a: Any, b: Any): Boolean = when {
            a is Double && b is Double -> a == b
            a is String && b is String -> a == b
            a is Boolean && b is Boolean -> a == b
            else -> a === b
        }
    }
}

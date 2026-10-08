package org.rigorcore.caserecomp.flash

import java.util.TreeMap
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Services a Flash movie asks of its container. */
interface FlashHost {
    /** `getURL(url, target)` — Director turns "event:" and "lingo:" URLs into Lingo. */
    fun getURL(url: String, target: String) {}
    /** A Sound object started a library sound by its export name. */
    fun sound(name: String) {}
    fun warn(message: String) {}
}

/** One shape to draw: [matrix] maps shape pixels to movie pixels. */
class FlashDraw(val shape: SwfShape, val matrix: SwfMatrix, val alpha: Double)

/** AVM1 `undefined`. */
object Undefined { override fun toString() = "undefined" }
object AvmNull { override fun toString() = "null" }

open class AvmObject {
    val props = LinkedHashMap<String, Any>()
    open fun get(name: String): Any = props[name] ?: Undefined
    open fun set(name: String, value: Any) { props[name] = value }
}

class AvmFunction(val code: ByteArray, val pool: List<String>, val params: List<String>, val home: MovieClip) : AvmObject()

class AvmSound(private val host: FlashHost) : AvmObject() {
    var attached: String? = null
    fun call(method: String, args: List<Any>): Any = when (method.lowercase()) {
        "attachsound" -> { attached = args.firstOrNull()?.let { Avm.string(it) }; Undefined }
        "start" -> { attached?.let(host::sound); Undefined }
        else -> Undefined
    }
}

/** A placed character on a timeline. */
class FlashInstance(val depth: Int, val character: SwfCharacter, var matrix: SwfMatrix, var colorTransform: SwfColorTransform?,
                    var name: String?, val clip: MovieClip?)

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
        if (n < frame) { children.clear(); frame = 0 }
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
            is SwfTag.StartSound -> player.movie.exports.entries.firstOrNull { it.value == tag.soundId }?.let { player.host.sound(it.key) }
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
        val instance = FlashInstance(tag.depth, character, tag.matrix ?: existing?.matrix ?: SwfMatrix.IDENTITY,
            tag.colorTransform ?: existing?.colorTransform, tag.name ?: existing?.name, null)
        val placed = if (character is SwfSprite) {
            val clip = MovieClip(player, character.timeline, this, null)
            FlashInstance(instance.depth, character, instance.matrix, instance.colorTransform, instance.name, clip).also {
                clip.instance = it
                children[tag.depth] = it
                clip.goTo(1)
            }
        } else instance.also { children[tag.depth] = it }
        children[tag.depth] = placed
    }

    /** Advance one frame if playing (looping), then this clip's children; onEnterFrame runs each frame. */
    fun advance() {
        if (playing && frameCount > 1) goTo(if (frame >= frameCount) 1 else frame + 1)
        for (child in children.values.toList()) child.clip?.advance()
        (props["onEnterFrame"] as? AvmFunction)?.let { player.call(it, this, emptyList()) }
    }

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
            else -> Undefined
        }
    }

    override fun set(name: String, value: Any) {
        val inst = instance
        val m = inst?.matrix ?: SwfMatrix.IDENTITY
        when (name.lowercase()) {
            "_x" -> inst?.matrix = m.copy(tx = Avm.number(value))
            "_y" -> inst?.matrix = m.copy(ty = Avm.number(value))
            "_xscale", "_yscale" -> {
                val rotation = atan2(m.b, m.a)
                val sx = if (name.equals("_xscale", true)) Avm.number(value) / 100 else sqrt(m.a * m.a + m.b * m.b)
                val sy = if (name.equals("_yscale", true)) Avm.number(value) / 100 else sqrt(m.c * m.c + m.d * m.d)
                inst?.matrix = m.copy(a = sx * cos(rotation), b = sx * sin(rotation), c = -sy * sin(rotation), d = sy * cos(rotation))
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
        else -> (props[method] as? AvmFunction)?.let { player.call(it, this, args) }
    }
}

/**
 * Plays a parsed SWF: timelines, a display list of shapes and sprites, the AVM1 subset
 * used by simple UI movies (frame scripts, clip event handlers, Sound, getURL) and
 * mouse handling for clips with onPress/onRelease/onRollOver/onRollOut.
 */
class FlashPlayer(val movie: SwfMovie, val host: FlashHost = object : FlashHost {}) {
    val root = MovieClip(this, movie.root, null, null)
    private val globals = AvmObject()
    private var hover: MovieClip? = null
    private var pressed: MovieClip? = null
    private var steps = 0L

    private var queued: Pair<Any, Boolean>? = null

    init { root.goTo(1) }

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
    }

    fun gotoAndPlay(target: Any) { queued = target to true }
    fun gotoAndStop(target: Any) { queued = target to false }
    val pendingGoto: Boolean get() = queued != null

    // ---- display -------------------------------------------------------------------------

    fun drawList(): List<FlashDraw> = mutableListOf<FlashDraw>().also { collect(root, SwfMatrix.IDENTITY, 1.0, it) }

    private fun collect(clip: MovieClip, parent: SwfMatrix, alpha: Double, out: MutableList<FlashDraw>) {
        if (!clip.visible) return
        for (inst in clip.children.values) {
            val m = parent * inst.matrix
            val a = inst.colorTransform?.alpha(alpha) ?: alpha
            when (val c = inst.character) {
                is SwfShape -> if (a > 0) out += FlashDraw(c, m, a)
                is SwfSprite -> inst.clip?.let { collect(it, m, a, out) }
                else -> Unit
            }
        }
    }

    /** Whether any drawn shape covers the movie point. */
    fun hits(x: Double, y: Double): Boolean = drawList().any { contains(it, x, y) }

    private fun contains(d: FlashDraw, x: Double, y: Double): Boolean {
        val inv = d.matrix.inverse() ?: return false
        val lx = inv.x(x, y); val ly = inv.y(x, y)
        val b = d.shape.bounds
        return lx >= b.xMin && lx < b.xMax && ly >= b.yMin && ly < b.yMax
    }

    // ---- mouse ---------------------------------------------------------------------------

    private fun hasHandlers(clip: MovieClip) = BUTTON_EVENTS.any { clip.props[it] is AvmFunction }

    /** Frontmost clip with button handlers whose shapes cover the point. */
    fun buttonAt(x: Double, y: Double): MovieClip? {
        fun search(clip: MovieClip, parent: SwfMatrix): MovieClip? {
            if (!clip.visible) return null
            for (inst in clip.children.descendingMap().values) {
                val m = parent * inst.matrix
                when (val c = inst.character) {
                    is SwfSprite -> inst.clip?.let { child ->
                        search(child, m)?.let { return it }
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
        return search(root, SwfMatrix.IDENTITY)
    }

    private fun fire(clip: MovieClip?, event: String) {
        val handler = clip?.props?.get(event) as? AvmFunction ?: return
        steps = 0
        call(handler, clip, emptyList())
    }

    fun mouseMove(x: Double, y: Double) {
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
        val target = buttonAt(x, y)
        val p = pressed
        pressed = null
        if (p != null && p === target) fire(p, "onRelease") else fire(p, "onReleaseOutside")
        mouseMove(x, y)
    }

    // ---- AVM1 ----------------------------------------------------------------------------

    internal fun run(code: ByteArray, clip: MovieClip) {
        try {
            Avm(this, code, emptyList(), clip, HashMap()).execute()
        } catch (e: FlashError) {
            host.warn("ActionScript stopped: ${e.message}")
        }
    }

    internal fun call(f: AvmFunction, self: MovieClip, args: List<Any>): Any = try {
        val locals = HashMap<String, Any>()
        f.params.forEachIndexed { i, p -> locals[p] = args.getOrElse(i) { Undefined } }
        Avm(this, f.code, f.pool, self, locals).execute()
    } catch (e: FlashError) {
        host.warn("ActionScript stopped: ${e.message}"); Undefined
    }

    internal fun budget(op: Int, pc: Int, clip: MovieClip) {
        if (++steps > 1_000_000) throw FlashError("ActionScript instruction budget exhausted at action 0x${op.toString(16)} pc $pc frame ${clip.frame}")
    }
    internal fun global(name: String): Any = globals.get(name)

    companion object {
        val BUTTON_EVENTS = listOf("onPress", "onRelease", "onReleaseOutside", "onRollOver", "onRollOut")
    }
}

/** Interpreter for the AVM1 action subset used by Flash 5-7 UI movies. */
internal class Avm(
    private val player: FlashPlayer,
    private val code: ByteArray,
    private var pool: List<String>,
    private val self: MovieClip,
    private val locals: HashMap<String, Any>,
) {
    private val stack = ArrayList<Any>()
    private val registers = arrayOfNulls<Any>(4)

    private fun pop(): Any = if (stack.isEmpty()) Undefined else stack.removeAt(stack.size - 1)
    private fun push(v: Any) { stack += v }

    private fun resolve(path: String): Any {
        val parts = path.split('.', '/').filter { it.isNotEmpty() }
        if (parts.isEmpty()) return self
        var value: Any = when (val first = parts[0]) {
            "this" -> self
            "_root", "_level0" -> player.root
            "_parent" -> self.parent ?: Undefined
            "_global" -> Undefined
            else -> locals[first] ?: self.get(first).takeIf { it !== Undefined } ?: player.root.get(first).takeIf { it !== Undefined }
                ?: player.global(first)
        }
        for (part in parts.drop(1)) value = (value as? AvmObject)?.get(part) ?: Undefined
        return value
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
                0x17 -> pop()
                0x18 -> push(number(pop()).toLong().toDouble())
                0x1C -> push(resolve(string(pop())))
                0x1D -> { val value = pop(); val name = string(pop())
                    if (locals.containsKey(name)) locals[name] = value else setPath(name, value) }
                0x21 -> { val b = string(pop()); push(string(pop()) + b) }
                0x22 -> { val index = number(pop()).toInt(); val target = targetOf(pop()); push(target?.get(PROPERTIES.getOrElse(index) { "" }) ?: Undefined) }
                0x23 -> { val value = pop(); val index = number(pop()).toInt(); targetOf(pop())?.set(PROPERTIES.getOrElse(index) { "" }, value) }
                0x26 -> pop()
                0x34 -> push(System.currentTimeMillis().toDouble())
                0x3C -> { val value = pop(); locals[string(pop())] = value }
                0x41 -> locals[string(pop())] = Undefined
                0x3D -> {
                    val name = string(pop()); val args = List(number(pop()).toInt()) { pop() }
                    push(callFunction(name, args))
                }
                0x3E -> return pop()
                0x40 -> {
                    val name = string(pop()); val args = List(number(pop()).toInt()) { pop() }
                    push(if (name == "Sound") AvmSound(player.host) else AvmObject().also { o -> if (name == "Array") args.forEachIndexed { i, v -> o.set(i.toString(), v) } })
                }
                0x42 -> { val n = number(pop()).toInt(); val o = AvmObject(); repeat(n) { o.set(it.toString(), pop()) }; o.set("length", n.toDouble()); push(o) }
                0x43 -> { val n = number(pop()).toInt(); val o = AvmObject(); repeat(n) { val v = pop(); o.set(string(pop()), v) }; push(o) }
                0x47 -> { val b = pop(); val a = pop()
                    push(if (a is String || b is String) string(a) + string(b) else number(a) + number(b)) }
                0x48 -> { val b = pop(); push(number(pop()) < number(b)) }
                0x67 -> { val b = pop(); push(number(pop()) > number(b)) }
                0x49 -> { val b = pop(); push(equals(pop(), b)) }
                0x4A -> push(number(pop()))
                0x4B -> push(string(pop()))
                0x4C -> push(stack.lastOrNull() ?: Undefined)
                0x4D -> { val b = pop(); val a = pop(); push(b); push(a) }
                0x4E -> { val name = string(pop()); push((pop() as? AvmObject)?.get(name) ?: Undefined) }
                0x4F -> { val value = pop(); val name = string(pop()); (pop() as? AvmObject)?.set(name, value) }
                0x50 -> push(number(pop()) + 1)
                0x51 -> push(number(pop()) - 1)
                0x52 -> {
                    val name = string(pop()); val target = pop(); val args = List(number(pop()).toInt()) { pop() }
                    push(callMethod(target, name, args))
                }
                0x81 -> self.goTo(((data[0].toInt() and 0xFF) or ((data[1].toInt() and 0xFF) shl 8)) + 1)
                0x83 -> {
                    val parts = String(data, Charsets.ISO_8859_1).split('\u0000')
                    player.host.getURL(parts.getOrElse(0) { "" }, parts.getOrElse(1) { "" })
                }
                0x87 -> registers[data[0].toInt() and 3] = stack.lastOrNull() ?: Undefined
                0x88 -> {
                    val n = (data[0].toInt() and 0xFF) or ((data[1].toInt() and 0xFF) shl 8)
                    pool = String(data, 2, data.size - 2, Charsets.ISO_8859_1).split('\u0000').take(n)
                }
                0x8B -> Unit // setTarget: titles we support only address their own timeline
                0x8C -> self.goTo(String(data, Charsets.ISO_8859_1).substringBefore('\u0000'), play = self.playing)
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
                    val f = AvmFunction(code.copyOfRange(pc, pc + size), pool, params, self)
                    pc += size
                    if (name.isEmpty()) push(f) else setPath(name, f)
                }
                0x9D -> { val offset = signed16(data); if (truthy(pop())) pc += offset }
                0x9F -> { val target = pop(); self.goTo(target, play = (data[0].toInt() and 1) == 1) }
                else -> throw FlashError("unsupported action 0x${op.toString(16)}")
            }
        }
        return Undefined
    }

    private fun setPath(path: String, value: Any) {
        val dot = path.lastIndexOfAny(charArrayOf('.', '/'))
        if (dot < 0) self.set(path, value) else (resolve(path.substring(0, dot)) as? AvmObject)?.set(path.substring(dot + 1), value)
    }

    private fun targetOf(value: Any): AvmObject? = when (value) {
        is AvmObject -> value
        else -> string(value).let { if (it.isEmpty()) self else resolve(it) as? AvmObject }
    }

    private fun callFunction(name: String, args: List<Any>): Any = when (name) {
        "String" -> string(args.firstOrNull() ?: "")
        "Number" -> number(args.firstOrNull() ?: Undefined)
        "getTimer" -> System.currentTimeMillis().toDouble()
        else -> (resolve(name) as? AvmFunction)?.let { player.call(it, self, args) }
            ?: throw FlashError("function $name is not defined")
    }

    private fun callMethod(target: Any, name: String, args: List<Any>): Any = when {
        name.isEmpty() && target is AvmFunction -> player.call(target, self, args)
        target is MovieClip -> target.call(name, args) ?: Undefined
        target is AvmSound -> target.call(name, args)
        target is AvmObject -> (target.get(name) as? AvmFunction)?.let { player.call(it, self, args) } ?: Undefined
        else -> Undefined
    }

    private fun pushValues(data: ByteArray) {
        var p = 0
        while (p < data.size) {
            when (data[p++].toInt() and 0xFF) {
                0 -> { val s = String(data, p, data.size - p, Charsets.ISO_8859_1).substringBefore('\u0000'); p += s.length + 1; push(s) }
                1 -> { push(java.lang.Float.intBitsToFloat(int32(data, p)).toDouble()); p += 4 }
                2 -> push(AvmNull)
                3 -> push(Undefined)
                4 -> { push(registers[data[p].toInt() and 3] ?: Undefined); p++ }
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
        val PROPERTIES = listOf("_x", "_y", "_xscale", "_yscale", "_currentframe", "_totalframes", "_alpha", "_visible",
            "_width", "_height", "_rotation", "_target", "_framesloaded", "_name")

        private fun int32(d: ByteArray, p: Int) = (d[p].toInt() and 0xFF) or ((d[p + 1].toInt() and 0xFF) shl 8) or
            ((d[p + 2].toInt() and 0xFF) shl 16) or ((d[p + 3].toInt() and 0xFF) shl 24)
        private fun signed16(d: ByteArray) = (((d[0].toInt() and 0xFF) or ((d[1].toInt() and 0xFF) shl 8)).toShort()).toInt()

        fun string(v: Any): String = when (v) {
            is Double -> if (v == Math.floor(v) && !v.isInfinite() && kotlin.math.abs(v) < 1e15) v.toLong().toString() else v.toString()
            is Boolean -> v.toString()
            is MovieClip -> "_level0"
            is AvmObject -> "[object Object]"
            else -> v.toString()
        }
        fun number(v: Any): Double = when (v) {
            is Double -> v
            is Boolean -> if (v) 1.0 else 0.0
            is String -> v.trim().toDoubleOrNull() ?: Double.NaN
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
            a is AvmObject || b is AvmObject -> a === b
            else -> number(a) == number(b)
        }
    }
}

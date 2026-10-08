package org.rigorcore.caserecomp.director

import org.rigorcore.caserecomp.lingo.LingoError
import org.rigorcore.caserecomp.lingo.LingoValue
import org.rigorcore.caserecomp.lingo.LingoValue.LInstance
import org.rigorcore.caserecomp.lingo.LingoValue.LInt
import org.rigorcore.caserecomp.lingo.LingoValue.LList
import org.rigorcore.caserecomp.lingo.LingoValue.LPoint
import org.rigorcore.caserecomp.lingo.LingoValue.LRect
import org.rigorcore.caserecomp.lingo.LingoValue.LString
import org.rigorcore.caserecomp.lingo.LingoValue.LSymbol
import org.rigorcore.caserecomp.lingo.LingoValue.Void
import org.rigorcore.caserecomp.lingo.asText
import org.rigorcore.caserecomp.lingo.isTruthy
import org.rigorcore.caserecomp.lingo.toDouble
import org.rigorcore.caserecomp.lingo.toInt
import kotlin.math.roundToInt

/** Flash member playback state on a sprite (the movie itself is rendered elsewhere). */
class FlashState {
    var frame: LingoValue = LInt(1)
    var playing = false
    val variables = LinkedHashMap<String, String>()
}

/**
 * Sprite channel. Score values apply until Lingo sets a property; Lingo values then
 * persist until the sprite span ends (Director 7+ auto-puppeting).
 */
class Sprite(private val runtime: DirectorRuntime, val number: Int) : LingoValue.LHost {
    override val ilk = "sprite"

    var span: SpanData? = null
        internal set
    internal var run: SpriteRun? = null
    private val set = HashMap<String, LingoValue>()
    val instances = mutableListOf<LInstance>()
    val flash = FlashState()

    val member: CastMember? get() = (set["member"] as? CastMember)
        ?: run?.let { r -> if (r.member > 0) runtime.castLib(r.castLib)?.member(r.member) else null }

    val locH: Int get() = set["loch"]?.toInt() ?: run?.x ?: 0
    val locV: Int get() = set["locv"]?.toInt() ?: run?.y ?: 0
    val ink: Int get() = set["ink"]?.toInt() ?: run?.ink ?: 0
    val blend: Int get() = set["blend"]?.toInt() ?: run?.let { if (it.blend == 0) 100 else 100 - it.blend * 100 / 255 } ?: 100
    val visible: Boolean get() = set["visible"]?.isTruthy() ?: true
    val locZ: Int get() = set["locz"]?.toInt() ?: number
    val rotation: Double get() = set["rotation"]?.toDouble() ?: 0.0
    val flipH: Boolean get() = set["fliph"]?.isTruthy() ?: run?.flipH ?: false
    val flipV: Boolean get() = set["flipv"]?.isTruthy() ?: run?.flipV ?: false

    /** Sprite size: Lingo value, else the score size, else (after a Lingo member change) the member size. */
    val width: Int get() = set["width"]?.toInt()
        ?: if ("member" in set) member?.width ?: 0 else run?.width ?: member?.width ?: 0
    val height: Int get() = set["height"]?.toInt()
        ?: if ("member" in set) member?.height ?: 0 else run?.height ?: member?.height ?: 0

    /** Bounding rect on the stage: registration point scaled to the sprite size. */
    fun bounds(): IntArray {
        val m = member
        val w = width; val h = height
        val (rx, ry) = when (m?.type) {
            "bitmap" -> {
                val mw = m.width; val mh = m.height
                (if (mw > 0) m.regX * w / mw else 0) to (if (mh > 0) m.regY * h / mh else 0)
            }
            "flash" -> w / 2 to h / 2
            else -> 0 to 0
        }
        val left = locH - rx; val top = locV - ry
        return intArrayOf(left, top, left + w, top + h)
    }

    /** Whether the stage point hits this sprite, honouring matte/transparent inks with the member alpha. */
    fun hit(x: Int, y: Int): Boolean {
        val m = member ?: return false
        if (!visible || m.type == "empty") return false
        val b = bounds()
        if (x < b[0] || x >= b[2] || y < b[1] || y >= b[3]) return false
        if (ink != 8 && ink != 36) return true
        val image = m.image ?: return true
        if (!image.useAlpha || b[2] == b[0] || b[3] == b[1]) return true
        var ix = (x - b[0]) * image.width / (b[2] - b[0])
        val iy = (y - b[1]) * image.height / (b[3] - b[1])
        if (flipH) ix = image.width - 1 - ix
        return image.pixels[iy * image.width + ix] ushr 24 != 0
    }

    /** Enter a new span (or none): drop Lingo changes and behaviours of the previous span. */
    internal fun enterSpan(newSpan: SpanData?) {
        span = newSpan
        set.clear()
        instances.clear()
        flash.frame = LInt(1); flash.playing = false; flash.variables.clear()
    }

    fun setMember(value: LingoValue) {
        val m = when (value) {
            is CastMember -> value
            Void -> null
            else -> runtime.member(value, null)
        }
        if (m == null) set.remove("member") else set["member"] = m
    }

    override fun getProp(name: String): LingoValue = when (name.lowercase()) {
        "member" -> member ?: runtime.emptyMember()
        "membernum" -> LInt(member?.number ?: 0)
        "castnum" -> LInt(member?.slot ?: 0)
        "castlibnum" -> LInt(member?.lib?.number ?: 0)
        "spritenum" -> LInt(number)
        "loc" -> LPoint(LInt(locH), LInt(locV))
        "loch" -> LInt(locH)
        "locv" -> LInt(locV)
        "locz" -> LInt(locZ)
        "width" -> LInt(width)
        "height" -> LInt(height)
        "rect" -> bounds().let { LRect(LInt(it[0]), LInt(it[1]), LInt(it[2]), LInt(it[3])) }
        "left" -> LInt(bounds()[0]); "top" -> LInt(bounds()[1])
        "right" -> LInt(bounds()[2]); "bottom" -> LInt(bounds()[3])
        "blend" -> LInt(blend)
        "ink" -> LInt(ink)
        "visible" -> LingoValue.bool(visible)
        "rotation" -> set["rotation"] ?: LInt(0)
        "fliph" -> LingoValue.bool(flipH)
        "flipv" -> LingoValue.bool(flipV)
        "forecolor" -> set["forecolor"] ?: LInt(255)
        "backcolor" -> set["backcolor"] ?: LInt(0)
        "color" -> set["color"] ?: LColor(0, 0, 0)
        "bgcolor" -> set["bgcolor"] ?: LColor(255, 255, 255)
        "scriptinstancelist" -> LList(instances.toMutableList())
        "puppet" -> LingoValue.bool(set.isNotEmpty())
        "cursor" -> set["cursor"] ?: LInt(0)
        "name" -> LString("")
        "playing" -> LingoValue.bool(flash.playing)
        "frame" -> flash.frame
        "ilk" -> LSymbol("sprite")
        else -> set[name.lowercase()] ?: throw LingoError("sprite has no property $name")
    }

    override fun setProp(name: String, value: LingoValue) {
        when (val p = name.lowercase()) {
            "member" -> setMember(value)
            "membernum" -> setMember(LInt(value.toInt()))
            "loc" -> { val pt = value as? LPoint ?: throw LingoError("loc must be a point")
                set["loch"] = LInt(pt.h.toDouble().roundToInt()); set["locv"] = LInt(pt.v.toDouble().roundToInt()) }
            "loch", "locv" -> set[p] = LInt(value.toDouble().roundToInt())
            "rect" -> {
                val r = LingoImage.intRect(value)
                val m = member
                val w = r[2] - r[0]; val h = r[3] - r[1]
                val rx = if (m?.type == "bitmap" && m.width > 0) m.regX * w / m.width else 0
                val ry = if (m?.type == "bitmap" && m.height > 0) m.regY * h / m.height else 0
                set["width"] = LInt(w); set["height"] = LInt(h)
                set["loch"] = LInt(r[0] + rx); set["locv"] = LInt(r[1] + ry)
            }
            "scriptinstancelist" -> {
                instances.clear()
                (value as? LList)?.items?.forEach { if (it is LInstance) instances += it }
            }
            "spritenum", "membernum.count" -> throw LingoError("cannot set sprite property $name")
            else -> set[p] = value
        }
        runtime.spriteChanged(this)
    }

    /** Messages to a sprite go to its behaviours; Flash members also take playback commands. */
    override fun call(method: String, args: List<LingoValue>): LingoValue? {
        val m = method.lowercase()
        if (member?.type == "flash") {
            when (m) {
                "gotoframe" -> { flash.frame = args.firstOrNull() ?: LInt(1); return Void }
                "play" -> { flash.playing = true; return Void }
                "stop" -> { flash.playing = false; return Void }
                "setvariable" -> { flash.variables[args.getOrElse(0) { Void }.asText()] = args.getOrElse(1) { Void }.asText(); return Void }
                "getvariable" -> return LString(flash.variables[args.getOrElse(0) { Void }.asText()] ?: "")
            }
        }
        return runtime.sendSprite(this, method, args)
    }

    override fun toString() = "(sprite $number)"
}

package org.rigorcore.caserecomp.director

import org.rigorcore.caserecomp.lingo.LingoError
import org.rigorcore.caserecomp.lingo.LingoValue
import org.rigorcore.caserecomp.lingo.LingoValue.LInt
import org.rigorcore.caserecomp.lingo.LingoValue.LPoint
import org.rigorcore.caserecomp.lingo.LingoValue.LRect
import org.rigorcore.caserecomp.lingo.LingoValue.LString
import org.rigorcore.caserecomp.lingo.LingoValue.LSymbol
import org.rigorcore.caserecomp.lingo.LingoValue.Void
import org.rigorcore.caserecomp.lingo.asText
import org.rigorcore.caserecomp.lingo.toInt

/** Media the runtime cannot derive from the bundle: decoded member pixels (and later sound). */
interface DirectorMedia {
    /** Decoded ARGB pixels of a bitmap member from cast file [castFile] ("" for the movie's own cast). */
    fun image(castFile: String, member: MemberData): LingoImage? = null

    /** The SWF bytes of a Flash member. */
    fun flash(castFile: String, member: MemberData): ByteArray? = null

    object None : DirectorMedia
}

/** Text layout used by Lingo text functions; platforms provide real font metrics. */
interface TextMetrics {
    fun width(member: CastMember, text: String): Int
    fun lineHeight(member: CastMember): Int

    /** Proportional approximation from the member's font size, for tests and headless runs. */
    object Approximate : TextMetrics {
        override fun width(member: CastMember, text: String) = (text.length * member.fontSize * 0.55).toInt()
        override fun lineHeight(member: CastMember) = (member.fontSize * 1.25).toInt().coerceAtLeast(1)
    }
}

/**
 * A cast library. Its members come from the cast file currently assigned to it, so
 * setting `fileName` (as titles do to swap content casts) replaces every member.
 */
class CastLib(private val runtime: DirectorRuntime, val number: Int, val name: String, fileName: String) : LingoValue.LHost {
    override val ilk = "castLib"
    var fileName: String = fileName
        private set
    var file: CastFile? = null
        private set
    private val members = HashMap<Int, CastMember>()
    /** Lingo-made changes to members (text, image, name...), dropped when the cast file changes. */
    internal val overrides = HashMap<Int, MutableMap<String, LingoValue>>()

    internal fun assign(fileName: String, file: CastFile?) {
        this.fileName = fileName
        this.file = file
        overrides.clear()
    }

    fun data(number: Int): MemberData? = file?.members?.get(number)

    fun member(number: Int): CastMember = members.getOrPut(number) { CastMember(runtime, this, number) }

    fun memberNamed(name: String): CastMember? =
        file?.members?.values?.firstOrNull { it.name.equals(name, ignoreCase = true) }?.let { member(it.number) }
            ?: overrides.entries.firstOrNull { (_, o) -> o["name"]?.asText()?.equals(name, true) == true }?.let { member(it.key) }

    val memberCount: Int get() = maxOf(file?.members?.keys?.maxOrNull() ?: 0, overrides.keys.maxOrNull() ?: 0)

    /** `new(#type)`: a member of that type in the first free slot. */
    fun newMember(type: String): CastMember {
        var number = 1
        while (data(number) != null || overrides[number]?.isNotEmpty() == true) number++
        overrides[number] = hashMapOf("type" to LSymbol(type))
        return member(number)
    }

    override fun getProp(name: String): LingoValue = when (name.lowercase()) {
        "name" -> LString(this.name)
        "number" -> LInt(number)
        "filename" -> LString(fileName)
        "membercount", "member.count" -> LInt(memberCount)
        else -> throw LingoError("castLib has no property $name")
    }

    override fun setProp(name: String, value: LingoValue) {
        when (name.lowercase()) {
            "filename" -> runtime.loadCast(this, value.asText())
            else -> throw LingoError("cannot set castLib property $name")
        }
    }

    override fun call(method: String, args: List<LingoValue>): LingoValue? = when (method.lowercase()) {
        "member" -> args.firstOrNull()?.let { runtime.member(it, this) }
        else -> null
    }

    override fun toString() = "(castLib $number)"
}

/**
 * Reference to a cast member slot (castLib, number). Its content is whatever the
 * cast library currently holds in that slot, plus Lingo-made changes.
 */
class CastMember(private val runtime: DirectorRuntime, val lib: CastLib, val number: Int) : LingoValue.LHost {
    override val ilk = "member"
    val data: MemberData? get() = lib.data(number)
    private val overrides: MutableMap<String, LingoValue> get() = lib.overrides.getOrPut(number) { HashMap() }

    val name: String get() = overrides["name"]?.asText() ?: data?.name ?: ""

    /** Director member type symbol name. */
    val type: String get() = when (val d = data) {
        null -> lib.overrides[number]?.get("type")?.asText() ?: "empty"
        else -> when (d.type) {
            "xtra" -> when (d.xtra) { "text" -> "text"; "flash" -> "flash"; "font" -> "font"; "cursor" -> "cursor"; else -> d.xtra ?: "xtra" }
            else -> d.type
        }
    }

    var text: String
        get() = overrides["text"]?.asText() ?: data?.text ?: ""
        set(value) { overrides["text"] = LString(value) }

    /** Lingo-assigned image, or the decoded member pixels. */
    /** Lingo `member.image`: the member's own image object, which scripts may change in place. */
    val image: LingoImage?
        get() = overrides["image"] as? LingoImage ?: pixels?.duplicate()?.also { overrides["image"] = it }

    /** Pixels to draw and hit-test: a Lingo-assigned image, else the decoded media (shared, read-only). */
    val pixels: LingoImage?
        get() = overrides["image"] as? LingoImage ?: data?.takeIf { it.type == "bitmap" }?.let { runtime.mediaImage(this, it) }

    val width: Int get() = (overrides["image"] as? LingoImage)?.width ?: overrides["width"]?.toInt()
        ?: flashSize()?.first ?: data?.width ?: 0
    val height: Int get() = (overrides["image"] as? LingoImage)?.height ?: overrides["height"]?.toInt()
        ?: flashSize()?.second ?: data?.height ?: 0

    /** Flash members take their size from the movie's stage. */
    private fun flashSize(): Pair<Int, Int>? = if (type != "flash") null else
        runtime.flashMovie(this)?.bounds?.let { it.width.toInt() to it.height.toInt() }
    val regX: Int get() = overrides["regpoint"]?.let { (it as LPoint).h.toInt() } ?: data?.regX ?: 0
    val regY: Int get() = overrides["regpoint"]?.let { (it as LPoint).v.toInt() } ?: data?.regY ?: 0

    val fontSize: Int get() = overrides["fontsize"]?.toInt() ?: data?.textStyle?.fontSize ?: 12

    /** Tab stops (pixels from the left) set by HTML table cell widths; empty for default stops. */
    val tabStops: List<Int> get() = (overrides["tabstops"] as? LingoValue.LList)?.items?.map { it.toInt() } ?: emptyList()

    /** Text layout and face: Lingo values, else the authored style of the member's first run. */
    val alignment: String get() = overrides["alignment"]?.asText()?.lowercase() ?: data?.textStyle?.alignment ?: "left"
    val font: String get() = overrides["font"]?.asText() ?: data?.textStyle?.font ?: "Arial"
    val fontStyle: List<String> get() = (overrides["fontstyle"] as? LingoValue.LList)?.items?.map { it.asText().lowercase() }
        ?: data?.textStyle?.fontStyle ?: emptyList()
    val bold: Boolean get() = "bold" in fontStyle
    val italic: Boolean get() = "italic" in fontStyle

    /** Text colour (ARGB): Lingo `color`, else the authored first-run colour, else black. */
    val textColor: Int get() = (overrides["color"] ?: overrides["forecolor"])?.let { LColor.of(it).argb }
        ?: data?.textStyle?.color ?: 0xFF000000.toInt()

    /** Character number under a member-relative point (length + 1 past the end of the text). */
    fun locToCharPos(x: Int, y: Int): Int {
        val metrics = runtime.textMetrics
        val lines = text.split('\r')
        val lineIndex = (y / metrics.lineHeight(this)).coerceIn(0, lines.size - 1)
        val before = lines.take(lineIndex).sumOf { it.length + 1 }
        val line = lines[lineIndex]
        for (i in line.indices) {
            val mid = (metrics.width(this, line.substring(0, i)) + metrics.width(this, line.substring(0, i + 1))) / 2
            if (x < mid) return before + i + 1
        }
        return before + line.length + 1
    }

    /** Member-relative point of the bottom-left of character [charPos]. */
    fun charPosToLoc(charPos: Int): LPoint {
        val metrics = runtime.textMetrics
        val prefix = text.substring(0, (charPos - 1).coerceIn(0, text.length))
        val line = prefix.count { it == '\r' }
        return LPoint(LInt(metrics.width(this, prefix.substringAfterLast('\r'))), LInt((line + 1) * metrics.lineHeight(this)))
    }

    /** `member.number`: slot number, with the cast library in the high word beyond castLib 1. */
    val slot: Int get() = if (lib.number == 1) number else (lib.number shl 16) or number

    fun prop(name: String): LingoValue? = overrides[name.lowercase()]

    override fun getProp(name: String): LingoValue = when (val p = name.lowercase()) {
        "name" -> LString(this.name)
        "number" -> LInt(slot)
        "membernum" -> LInt(number)
        "castlibnum" -> LInt(lib.number)
        "type" -> LSymbol(type)
        "text" -> LString(text)
        "width" -> LInt(width)
        "height" -> LInt(height)
        "rect" -> LRect(LInt(0), LInt(0), LInt(width), LInt(height))
        "regpoint" -> LPoint(LInt(regX), LInt(regY))
        "image" -> image ?: throw LingoError("member ${this.name} has no image")
        "depth" -> LInt(image?.depth ?: 32)
        "scriptinstancelist" -> LingoValue.LList()
        else -> overrides[p] ?: when (p) {
            "fontsize" -> LInt(fontSize)
            "alignment" -> LingoValue.LSymbol(alignment)
            "font" -> LString(font)
            "fontstyle" -> LingoValue.LList(fontStyle.ifEmpty { listOf("plain") }.map { LingoValue.LSymbol(it) }.toMutableList())
            "fixedlinespace", "charspacing", "scrolltop" -> LInt(0)
            "forecolor" -> LInt(data?.foreColor ?: 255)
            "backcolor" -> LInt(data?.backColor ?: 0)
            "loop" -> LingoValue.TRUE
            "filename" -> LString("")
            "pageheight" -> LInt(height)
            "html" -> LString(text)
            "duration" -> LInt(0)
            else -> throw LingoError("member has no property $name")
        }
    }

    override fun setProp(name: String, value: LingoValue) {
        when (val p = name.lowercase()) {
            "text" -> text = value.asText()
            "html" -> {
                val html = value.asText()
                text = htmlToText(html)
                overrides["html"] = value
                // The first <font> tag styles the member; table cell widths become tab stops.
                FONT_TAG.find(html)?.groupValues?.get(1)?.let { attrs ->
                    attr(attrs, "size")?.toIntOrNull()?.let { overrides["fontsize"] = LInt(HTML_SIZES[it.coerceIn(1, 7) - 1]) }
                    attr(attrs, "color")?.removePrefix("#")?.toIntOrNull(16)?.let {
                        overrides["color"] = LColor((it shr 16) and 0xFF, (it shr 8) and 0xFF, it and 0xFF)
                    }
                    attr(attrs, "face")?.let { overrides["font"] = LString(it) }
                }
                val firstRow = ROW.find(html)?.value ?: ""
                val widths = CELL_WIDTH.findAll(firstRow).map { it.groupValues[1].toInt() }.toList()
                if (widths.size > 1) overrides["tabstops"] = LingoValue.LList(widths.runningReduce(Int::plus).dropLast(1).map { LInt(it) }.toMutableList())
            }
            "image" -> overrides["image"] = (value as? LingoImage)?.duplicate() ?: throw LingoError("member.image needs an image")
            "number", "membernum", "castlibnum", "type", "width", "height", "rect" ->
                throw LingoError("cannot set member property $name")
            else -> overrides[p] = value
        }
        runtime.memberChanged(this)
    }

    override fun call(method: String, args: List<LingoValue>): LingoValue? = when (method.lowercase()) {
        "erase" -> { overrides.clear(); Void }
        "loctocharpos" -> (args.firstOrNull() as? LPoint)?.let { LInt(locToCharPos(it.h.toInt(), it.v.toInt())) }
        "charpostoloc" -> charPosToLoc(args.firstOrNull()?.toInt() ?: 1)
        else -> null
    }

    override fun equals(other: Any?) = other is CastMember && other.lib === lib && other.number == number
    override fun hashCode() = lib.number * 65536 + number
    override fun toString() = "(member $number of castLib ${lib.number})"

    companion object {
        private val TAG = Regex("<[^>]*>")
        private val BREAK = Regex("(?i)<br\\s*/?>|</p>|</tr>")
        private val CELL_END = Regex("(?i)</td>")
        private val FONT_TAG = Regex("(?i)<font\\b([^>]*)>")
        private val ROW = Regex("(?is)<tr\\b.*?</tr>")
        private val CELL_WIDTH = Regex("(?i)<td\\b[^>]*\\bwidth\\s*=\\s*\"?(\\d+)")
        /** HTML <font size> 1..7 in points, as Director's HTML import maps them. */
        private val HTML_SIZES = intArrayOf(8, 10, 12, 14, 18, 24, 36)

        private fun attr(attrs: String, name: String): String? =
            Regex("(?i)\\b$name\\s*=\\s*(?:'([^']*)'|\"([^\"]*)\"|([^\\s>]+))").find(attrs)?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }

        /** Plain text of the simple HTML that titles assign to text members: rows end lines, cells are tab separated. */
        fun htmlToText(html: String): String = html.replace(BREAK, "\r").replace(CELL_END, "\t").replace(TAG, "")
            .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&nbsp;", " ").replace("&amp;", "&")
            .replace(Regex("\t+\r"), "\r").trimEnd('\r', '\t')
    }
}

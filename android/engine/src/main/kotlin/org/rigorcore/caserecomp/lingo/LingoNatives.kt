package org.rigorcore.caserecomp.lingo

import org.rigorcore.caserecomp.lingo.LingoValue.LFloat
import org.rigorcore.caserecomp.lingo.LingoValue.LInt
import org.rigorcore.caserecomp.lingo.LingoValue.LList
import org.rigorcore.caserecomp.lingo.LingoValue.LPoint
import org.rigorcore.caserecomp.lingo.LingoValue.LPropList
import org.rigorcore.caserecomp.lingo.LingoValue.LRect
import org.rigorcore.caserecomp.lingo.LingoValue.LScriptRef
import org.rigorcore.caserecomp.lingo.LingoValue.LString
import org.rigorcore.caserecomp.lingo.LingoValue.LSymbol
import org.rigorcore.caserecomp.lingo.LingoValue.Void
import kotlin.math.PI
import kotlin.math.abs

/** Pure-Lingo functions and the methods/properties of built-in value types. */
internal object LingoNatives {
    private fun arg(args: List<LingoValue>, i: Int) = args.getOrElse(i) { Void }

    private fun index(list: Int, value: LingoValue): Int {
        val i = value.toInt()
        if (i < 1 || i > list) throw LingoError("index $i out of range 1..$list")
        return i - 1
    }

    /** Functions that do not need Director. Returns null for names it does not own. */
    fun function(vm: LingoVm, name: String, args: List<LingoValue>): LingoValue? = when (name.lowercase()) {
        "list" -> LList(args.toMutableList())
        "point" -> LPoint(arg(args, 0), arg(args, 1))
        "rect" -> if (args.size == 2 && args[0] is LPoint && args[1] is LPoint) {
            val a = args[0] as LPoint; val b = args[1] as LPoint; LRect(a.h, a.v, b.h, b.v)
        } else LRect(arg(args, 0), arg(args, 1), arg(args, 2), arg(args, 3))
        "string" -> LString(arg(args, 0).asText())
        "integer" -> arg(args, 0).let { if (it is LString && it.value.trim().toDoubleOrNull() == null) Void else LInt(it.toInt()) }
        "float" -> arg(args, 0).let { if (it is LString && it.value.trim().toDoubleOrNull() == null) it else LFloat(it.toDouble()) }
        "symbol" -> arg(args, 0).let { if (it is LSymbol) it else LSymbol(it.asText()) }
        "value" -> arg(args, 0).let { if (it is LString) LingoLiteralParser.parseOrVoid(it.value) else it }
        "void" -> Void
        "ilk" -> LSymbol(arg(args, 0).ilkOf())
        "voidp" -> LingoValue.bool(arg(args, 0) === Void)
        "listp" -> LingoValue.bool(arg(args, 0).let { it is LList || it is LPropList || it is LPoint || it is LRect })
        "stringp" -> LingoValue.bool(arg(args, 0) is LString)
        "integerp" -> LingoValue.bool(arg(args, 0) is LInt)
        "floatp" -> LingoValue.bool(arg(args, 0) is LFloat)
        "symbolp" -> LingoValue.bool(arg(args, 0) is LSymbol)
        "objectp" -> LingoValue.bool(arg(args, 0).let { it is LingoValue.LInstance || it is LScriptRef || it is LingoValue.LHost })
        "abs" -> arg(args, 0).let { if (it is LInt) LInt(abs(it.value)) else LFloat(abs(it.toDouble())) }
        "min", "max" -> {
            val values = if (args.size == 1 && args[0] is LList) (args[0] as LList).items else args
            if (values.isEmpty()) Void else values.reduce { a, b ->
                val c = lingoCompare(a, b); if (name.equals("min", true)) (if (c <= 0) a else b) else (if (c >= 0) a else b)
            }
        }
        "sin" -> LFloat(kotlin.math.sin(arg(args, 0).toDouble()))
        "cos" -> LFloat(kotlin.math.cos(arg(args, 0).toDouble()))
        "sqrt" -> LFloat(kotlin.math.sqrt(arg(args, 0).toDouble()))
        "power" -> LFloat(Math.pow(arg(args, 0).toDouble(), arg(args, 1).toDouble()))
        "pi" -> LFloat(PI)
        "random" -> arg(args, 0).toInt().let { n -> if (n < 1) LInt(0) else LInt(vm.random.nextInt(n) + 1) }
        "chartonum" -> arg(args, 0).asText().let { if (it.isEmpty()) LInt(0) else LInt(it[0].code) }
        "numtochar" -> LString(arg(args, 0).toInt().toChar().toString())
        "length" -> LInt(arg(args, 0).asText().length)
        "chars" -> {
            val s = arg(args, 0).asText(); val from = arg(args, 1).toInt(); val to = arg(args, 2).toInt()
            if (from < 1 || to < from || from > s.length) LString("") else LString(s.substring(from - 1, minOf(to, s.length)))
        }
        "offset" -> if (arg(args, 0) is LRect) null else
            LInt(arg(args, 1).asText().indexOf(arg(args, 0).asText(), ignoreCase = true) + 1)
        "script" -> arg(args, 0).let { id ->
            val script = if (id is LInt || id is LFloat) vm.bundle.scriptNumber(id.toInt()) else vm.bundle.scriptNamed(id.asText())
            script?.let(::LScriptRef) ?: throw LingoError("script not found: ${id.asText()}")
        }
        "new" -> arg(args, 0).let { if (it is LScriptRef) vm.newInstance(it.script, args.drop(1)) else null }
        "call" -> {
            val handler = arg(args, 0).asText()
            val targets = arg(args, 1).let { if (it is LList) it.items.toList() else listOf(it) }
            var last: LingoValue = Void
            for (target in targets) last = vm.callMethod(target, handler, listOf(target) + args.drop(2)) ?: Void
            last
        }
        "clearglobals" -> { vm.clearGlobals(); Void }
        else -> null
    }

    /** Methods of lists, property lists, points, rects and strings; null when unknown. */
    fun method(vm: LingoVm, target: LingoValue, name: String, args: List<LingoValue>): LingoValue? {
        val m = name.lowercase()
        return when (target) {
            is LList -> listMethod(target, m, args)
            is LPropList -> propListMethod(target, m, args)
            is LPoint -> when (m) {
                "getat" -> listOf(target.h, target.v)[index(2, arg(args, 0))]
                "count" -> LInt(2)
                "duplicate" -> target
                "inside" -> (arg(args, 0) as? LRect)?.let { r ->
                    val x = target.h.toDouble(); val y = target.v.toDouble()
                    LingoValue.bool(x >= r.left.toDouble() && x < r.right.toDouble() && y >= r.top.toDouble() && y < r.bottom.toDouble())
                }
                else -> null
            }
            is LRect -> when (m) {
                "getat" -> listOf(target.left, target.top, target.right, target.bottom)[index(4, arg(args, 0))]
                "count" -> LInt(4)
                "duplicate" -> target
                "offset" -> { val dx = arg(args, 0); val dy = arg(args, 1)
                    LRect(arithmetic(target.left, dx, '+'), arithmetic(target.top, dy, '+'),
                        arithmetic(target.right, dx, '+'), arithmetic(target.bottom, dy, '+')) }
                "inflate" -> { val dx = arg(args, 0); val dy = arg(args, 1)
                    LRect(arithmetic(target.left, dx, '-'), arithmetic(target.top, dy, '-'),
                        arithmetic(target.right, dx, '+'), arithmetic(target.bottom, dy, '+')) }
                else -> null
            }
            is LString -> when (m) {
                // D7+ dot syntax: s.char[n] is s.getProp(#char, n), s.char[a..b] adds the last index,
                // and s.item.count is s.count(#item).
                "count" -> arg(args, 0).let { if (it is LSymbol) LInt(LingoChunks.count(target.value, LingoChunks.kindOf(it.name))) else LInt(target.value.length) }
                "length" -> LInt(target.value.length)
                "getprop", "getpropref" -> {
                    val kind = LingoChunks.kindOf((arg(args, 0) as? LSymbol ?: throw LingoError("chunk type expected")).name)
                    LString(LingoChunks.get(target.value, listOf(ChunkSelector(kind, arg(args, 1).toInt(), arg(args, 2).toInt()))))
                }
                else -> null
            }
            else -> null
        }
    }

    private fun listMethod(list: LList, m: String, args: List<LingoValue>): LingoValue? {
        val items = list.items
        return when (m) {
            "getat" -> items[index(items.size, arg(args, 0))]
            "setat" -> {
                val i = arg(args, 0).toInt()
                if (i < 1) throw LingoError("index $i out of range")
                while (items.size < i) items += Void
                items[i - 1] = arg(args, 1); Void
            }
            "add", "append" -> { items += arg(args, 0); Void }
            "addat" -> {
                val i = arg(args, 0).toInt()
                if (i < 1) throw LingoError("index $i out of range")
                while (items.size < i - 1) items += Void
                items.add(i - 1, arg(args, 1)); Void
            }
            "deleteat" -> { items.removeAt(index(items.size, arg(args, 0))); Void }
            "deleteone" -> { val i = items.indexOfFirst { lingoEquals(it, arg(args, 0)) }; if (i >= 0) items.removeAt(i); Void }
            "deleteall" -> { items.clear(); Void }
            "getpos", "findpos", "getone" -> LInt(items.indexOfFirst { lingoEquals(it, arg(args, 0)) } + 1)
            "count" -> LInt(items.size)
            "duplicate" -> list.duplicate()
            "getlast" -> items.lastOrNull() ?: Void
            "max" -> items.maxWithOrNull(::lingoCompare) ?: Void
            "min" -> items.minWithOrNull(::lingoCompare) ?: Void
            "sort" -> { items.sortWith(::lingoCompare); Void }
            else -> null
        }
    }

    private fun keyIndex(list: LPropList, key: LingoValue): Int = list.entries.indexOfFirst {
        val stored = it.first
        // Property names accept either string or symbol syntax, case-insensitively.
        if ((stored is LSymbol && key is LString) || (stored is LString && key is LSymbol))
            stored.asText().equals(key.asText(), ignoreCase = true)
        else lingoEquals(stored, key)
    }

    fun setPropValue(list: LPropList, key: LingoValue, value: LingoValue) {
        val i = keyIndex(list, key)
        if (i >= 0) list.entries[i] = list.entries[i].first to value else list.entries += key to value
    }

    private fun propListMethod(list: LPropList, m: String, args: List<LingoValue>): LingoValue? {
        val entries = list.entries
        val key = arg(args, 0)
        return when (m) {
            "getat" -> if (key is LInt || key is LFloat) entries[index(entries.size, key)].second
                else entries.getOrNull(keyIndex(list, key))?.second ?: throw LingoError("property not found: ${key.asText()}")
            "setat" -> { if (key is LInt) entries[index(entries.size, key)] = entries[key.value - 1].first to arg(args, 1)
                else setPropValue(list, key, arg(args, 1)); Void }
            "getprop" -> entries.getOrNull(keyIndex(list, key))?.second ?: throw LingoError("property not found: ${key.asText()}")
            "getaprop" -> entries.getOrNull(keyIndex(list, key))?.second ?: Void
            "setprop", "setaprop" -> { setPropValue(list, key, arg(args, 1)); Void }
            "addprop" -> { entries += key to arg(args, 1); Void }
            "deleteprop" -> { keyIndex(list, key).takeIf { it >= 0 }?.let { entries.removeAt(it) }; Void }
            "deleteat" -> { entries.removeAt(index(entries.size, key)); Void }
            "getpropat" -> entries[index(entries.size, key)].first
            "findpos" -> keyIndex(list, key).let { if (it < 0) Void else LInt(it + 1) }
            "getpos" -> LInt(entries.indexOfFirst { lingoEquals(it.second, key) } + 1)
            "getone" -> entries.firstOrNull { lingoEquals(it.second, key) }?.first ?: LInt(0)
            "count" -> LInt(entries.size)
            "duplicate" -> list.duplicate()
            "deleteall" -> { entries.clear(); Void }
            "sort" -> { entries.sortWith { a, b -> lingoCompare(a.first, b.first) }; Void }
            else -> null
        }
    }

    /** `value.prop` on built-in types. */
    fun property(target: LingoValue, name: String): LingoValue {
        val p = name.lowercase()
        if (p == "ilk") return LSymbol(target.ilkOf())
        return when (target) {
            is LList -> if (p == "count") LInt(target.items.size) else throw LingoError("list has no property $name")
            is LPropList -> if (p == "count") LInt(target.entries.size)
                else target.entries.firstOrNull { lingoEquals(it.first, LSymbol(name)) }?.second ?: Void
            is LString -> when (p) { "length", "count" -> LInt(target.value.length); else -> throw LingoError("string has no property $name") }
            is LPoint -> when (p) { "loch" -> target.h; "locv" -> target.v; else -> throw LingoError("point has no property $name") }
            is LRect -> when (p) {
                "left" -> target.left; "top" -> target.top; "right" -> target.right; "bottom" -> target.bottom
                "width" -> arithmetic(target.right, target.left, '-')
                "height" -> arithmetic(target.bottom, target.top, '-')
                else -> throw LingoError("rect has no property $name")
            }
            is LScriptRef -> if (p == "name") LString(target.script.name) else throw LingoError("script has no property $name")
            else -> throw LingoError("${target.ilkOf()} has no property $name")
        }
    }
}

package org.rigorcore.caserecomp.lingo

import java.util.Locale

/** Runtime failure inside Lingo code; carries the script/handler where it happened when known. */
class LingoError(message: String) : RuntimeException(message) {
    /** Lingo handlers the error unwound through, innermost first ("script.handler"). */
    val lingoTrace = mutableListOf<String>()
    override val message: String get() = if (lingoTrace.isEmpty()) super.message!! else "${super.message} [in ${lingoTrace.joinToString(" < ")}]"
}

/**
 * Lingo values. Lists and property lists are mutable reference types, as in Director:
 * assigning a list shares it, and `duplicate()` copies it.
 */
sealed interface LingoValue {
    data object Void : LingoValue
    data class LInt(val value: Int) : LingoValue
    data class LFloat(val value: Double) : LingoValue
    data class LString(val value: String) : LingoValue
    /** Symbols compare case-insensitively; [name] keeps the spelling that created it. */
    class LSymbol(val name: String) : LingoValue {
        override fun equals(other: Any?) = other is LSymbol && other.name.equals(name, ignoreCase = true)
        override fun hashCode() = name.lowercase(Locale.ROOT).hashCode()
        override fun toString() = "#$name"
    }
    class LList(val items: MutableList<LingoValue> = mutableListOf()) : LingoValue
    class LPropList(val entries: MutableList<Pair<LingoValue, LingoValue>> = mutableListOf()) : LingoValue
    data class LPoint(val h: LingoValue, val v: LingoValue) : LingoValue
    data class LRect(val left: LingoValue, val top: LingoValue, val right: LingoValue, val bottom: LingoValue) : LingoValue
    /** Reference to a compiled script (what `script "name"` returns). */
    class LScriptRef(val script: LingoScript) : LingoValue
    /** Instance of a parent script or behaviour: its own property values. */
    class LInstance(val script: LingoScript) : LingoValue {
        val properties: MutableMap<String, LingoValue> =
            script.properties.associateWithTo(linkedMapOf<String, LingoValue>()) { Void }
    }
    /** Host-provided object (sprite, member, sound channel, xtra instance, image...). */
    interface LHost : LingoValue {
        val ilk: String
        fun getProp(name: String): LingoValue
        fun setProp(name: String, value: LingoValue)
        /** Returns null when the host object has no such method. */
        fun call(method: String, args: List<LingoValue>): LingoValue?
    }

    companion object {
        val TRUE = LInt(1)
        val FALSE = LInt(0)
        fun bool(value: Boolean): LingoValue = if (value) TRUE else FALSE
    }
}

internal fun LingoValue.isNumber() = this is LingoValue.LInt || this is LingoValue.LFloat

/** Integer coercion used by indexes, chunk bounds and integer(): floats round half away from zero. */
fun LingoValue.toInt(): Int = when (this) {
    is LingoValue.LInt -> value
    is LingoValue.LFloat -> Math.round(value).toInt()
    is LingoValue.LString -> value.trim().toIntOrNull() ?: value.trim().toDoubleOrNull()?.let { Math.round(it).toInt() } ?: 0
    LingoValue.Void -> 0
    else -> throw LingoError("expected a number, got ${ilkOf()}")
}

fun LingoValue.toDouble(): Double = when (this) {
    is LingoValue.LInt -> value.toDouble()
    is LingoValue.LFloat -> value
    is LingoValue.LString -> value.trim().toDoubleOrNull() ?: 0.0
    LingoValue.Void -> 0.0
    else -> throw LingoError("expected a number, got ${ilkOf()}")
}

/** Lingo truth: only numbers can be tested; VOID is false. */
fun LingoValue.isTruthy(): Boolean = when (this) {
    is LingoValue.LInt -> value != 0
    is LingoValue.LFloat -> value != 0.0
    LingoValue.Void -> false
    is LingoValue.LString -> value.isNotEmpty() && toDouble() != 0.0
    else -> true
}

fun LingoValue.ilkOf(): String = when (this) {
    LingoValue.Void -> "void"
    is LingoValue.LInt -> "integer"
    is LingoValue.LFloat -> "float"
    is LingoValue.LString -> "string"
    is LingoValue.LSymbol -> "symbol"
    is LingoValue.LList -> "list"
    is LingoValue.LPropList -> "propList"
    is LingoValue.LPoint -> "point"
    is LingoValue.LRect -> "rect"
    is LingoValue.LScriptRef -> "script"
    is LingoValue.LInstance -> "instance"
    is LingoValue.LHost -> ilk
}

/** Director's default `the floatPrecision` is 4 decimal places. */
fun formatFloat(value: Double): String = String.format(Locale.ROOT, "%.4f", value)

/** String form used by `&`, `string()` and `put`. */
fun LingoValue.asText(): String = when (this) {
    LingoValue.Void -> ""
    is LingoValue.LInt -> value.toString()
    is LingoValue.LFloat -> formatFloat(value)
    is LingoValue.LString -> value
    is LingoValue.LSymbol -> name
    else -> literalText()
}

/** Lingo literal syntax, used for nested values inside lists. */
fun LingoValue.literalText(): String = when (this) {
    LingoValue.Void -> "<Void>"
    is LingoValue.LString -> "\"$value\""
    is LingoValue.LSymbol -> "#$name"
    is LingoValue.LList -> items.joinToString(", ", "[", "]") { it.literalText() }
    is LingoValue.LPropList -> if (entries.isEmpty()) "[:]" else
        entries.joinToString(", ", "[", "]") { (k, v) -> k.literalText() + ": " + v.literalText() }
    is LingoValue.LPoint -> "point(${h.asText()}, ${v.asText()})"
    is LingoValue.LRect -> "rect(${left.asText()}, ${top.asText()}, ${right.asText()}, ${bottom.asText()})"
    is LingoValue.LScriptRef -> "(script \"${script.name}\")"
    is LingoValue.LInstance -> "<offspring \"${script.name}\">"
    is LingoValue.LHost -> "<$ilk>"
    else -> asText()
}

/** Lingo `=`: numbers numerically, strings and symbols case-insensitively, lists element-wise. */
fun lingoEquals(a: LingoValue, b: LingoValue): Boolean = when {
    a.isNumber() && b.isNumber() -> a.toDouble() == b.toDouble()
    a is LingoValue.LString && b is LingoValue.LString -> a.value.equals(b.value, ignoreCase = true)
    a is LingoValue.LString && b.isNumber() -> a.value.trim().toDoubleOrNull() == b.toDouble()
    a.isNumber() && b is LingoValue.LString -> lingoEquals(b, a)
    a is LingoValue.LSymbol && b is LingoValue.LSymbol -> a == b
    a is LingoValue.LList && b is LingoValue.LList ->
        a.items.size == b.items.size && a.items.indices.all { lingoEquals(a.items[it], b.items[it]) }
    a is LingoValue.LPoint && b is LingoValue.LPoint -> lingoEquals(a.h, b.h) && lingoEquals(a.v, b.v)
    a is LingoValue.LRect && b is LingoValue.LRect -> lingoEquals(a.left, b.left) && lingoEquals(a.top, b.top) &&
        lingoEquals(a.right, b.right) && lingoEquals(a.bottom, b.bottom)
    a === LingoValue.Void || b === LingoValue.Void -> a === b
    else -> a === b
}

/** Ordering for <, >, sort(): numbers numerically, strings case-insensitively. */
fun lingoCompare(a: LingoValue, b: LingoValue): Int = when {
    a.isNumber() && b.isNumber() -> a.toDouble().compareTo(b.toDouble())
    a is LingoValue.LString && b is LingoValue.LString -> a.value.lowercase(Locale.ROOT).compareTo(b.value.lowercase(Locale.ROOT))
    a is LingoValue.LSymbol && b is LingoValue.LSymbol -> a.name.lowercase(Locale.ROOT).compareTo(b.name.lowercase(Locale.ROOT))
    a is LingoValue.LString || b is LingoValue.LString -> a.asText().lowercase(Locale.ROOT).compareTo(b.asText().lowercase(Locale.ROOT))
    else -> a.toDouble().compareTo(b.toDouble())
}

/** Binary arithmetic with int/float promotion and element-wise point/rect/list support. */
fun arithmetic(a: LingoValue, b: LingoValue, op: Char): LingoValue {
    fun elementwise(x: List<LingoValue>, y: (Int) -> LingoValue) = x.mapIndexed { i, item -> arithmetic(item, y(i), op) }
    return when {
        a is LingoValue.LPoint -> {
            val other = b.components(2)
            val (h, v) = elementwise(listOf(a.h, a.v)) { other[it] }
            LingoValue.LPoint(h, v)
        }
        a is LingoValue.LRect -> {
            val other = b.components(4)
            val r = elementwise(listOf(a.left, a.top, a.right, a.bottom)) { other[it] }
            LingoValue.LRect(r[0], r[1], r[2], r[3])
        }
        a is LingoValue.LList -> LingoValue.LList(elementwise(a.items) { if (b is LingoValue.LList) b.items[it] else b }.toMutableList())
        a is LingoValue.LInt && b is LingoValue.LInt -> when (op) {
            '+' -> LingoValue.LInt(a.value + b.value)
            '-' -> LingoValue.LInt(a.value - b.value)
            '*' -> LingoValue.LInt(a.value * b.value)
            '/' -> if (b.value == 0) throw LingoError("division by zero") else LingoValue.LInt(a.value / b.value)
            '%' -> if (b.value == 0) throw LingoError("division by zero") else LingoValue.LInt(a.value % b.value)
            else -> error("unknown op")
        }
        else -> {
            for (side in listOf(a, b)) {
                if (side is LingoValue.LString && side.value.trim().toDoubleOrNull() == null) {
                    throw LingoError("arithmetic on non-numeric string")
                }
            }
            fun integral(v: LingoValue) = v is LingoValue.LInt || v === LingoValue.Void ||
                v is LingoValue.LString && v.value.trim().toIntOrNull() != null
            if (integral(a) && integral(b)) return arithmetic(LingoValue.LInt(a.toInt()), LingoValue.LInt(b.toInt()), op)
            val x = a.toDouble(); val y = b.toDouble()
            LingoValue.LFloat(when (op) {
                '+' -> x + y; '-' -> x - y; '*' -> x * y
                '/' -> if (y == 0.0) throw LingoError("division by zero") else x / y
                '%' -> if (y == 0.0) throw LingoError("division by zero") else x % y
                else -> error("unknown op")
            })
        }
    }
}

private fun LingoValue.components(size: Int): List<LingoValue> = when (this) {
    is LingoValue.LPoint -> listOf(h, v)
    is LingoValue.LRect -> listOf(left, top, right, bottom)
    is LingoValue.LList -> List(size) { items.getOrElse(it) { LingoValue.LInt(0) } }
    else -> List(size) { this }
}

/** Deep copy for `duplicate()`: lists and property lists are copied, everything else is shared. */
fun LingoValue.duplicate(): LingoValue = when (this) {
    is LingoValue.LList -> LingoValue.LList(items.mapTo(mutableListOf()) { it.duplicate() })
    is LingoValue.LPropList -> LingoValue.LPropList(entries.mapTo(mutableListOf()) { (k, v) -> k to v.duplicate() })
    else -> this
}

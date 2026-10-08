package org.rigorcore.caserecomp.lingo

/** Tiny assembler for synthetic Lingo bytecode with forward/backward labels. */
internal class Asm(private val names: MutableList<String>) {
    private sealed interface Item
    private class Op(val code: Int, val arg: Int?, val wide: Boolean = false) : Item
    private class Jump(val code: Int, val label: String) : Item
    private class Label(val name: String) : Item
    private val items = mutableListOf<Item>()

    fun name(n: String): Int = names.indexOf(n).takeIf { it >= 0 } ?: names.size.also { names += n }
    fun op(code: Int) = apply { items += Op(code, null) }
    fun op(code: Int, arg: Int) = apply { items += Op(code, arg) }
    fun named(code: Int, n: String) = op(code, name(n))
    fun int(value: Int) = apply { items += Op(0x41, value, wide = value !in -128..127) }
    fun jump(code: Int, label: String) = apply { items += Jump(code, label) }
    fun label(n: String) = apply { items += Label(n) }

    private fun size(item: Item) = when (item) {
        is Label -> 0
        is Jump -> 3
        is Op -> if (item.arg == null) 1 else if (item.wide || item.arg > 255) 3 else 2
    }

    fun bytes(): ByteArray {
        val positions = mutableMapOf<String, Int>()
        var pos = 0
        for (item in items) { if (item is Label) positions[item.name] = pos; pos += size(item) }
        val out = java.io.ByteArrayOutputStream()
        pos = 0
        for (item in items) {
            when (item) {
                is Label -> Unit
                is Op -> when {
                    item.arg == null -> out.write(item.code)
                    item.wide || item.arg > 255 -> { out.write(item.code + 0x40); out.write((item.arg shr 8) and 0xFF); out.write(item.arg and 0xFF) }
                    else -> { out.write(item.code); out.write(item.arg and 0xFF) }
                }
                is Jump -> {
                    val target = positions.getValue(item.label)
                    val offset = if (item.code == 0x54) pos - target else target - pos
                    out.write(item.code + 0x40); out.write((offset shr 8) and 0xFF); out.write(offset and 0xFF)
                }
            }
            pos += size(item)
        }
        return out.toByteArray()
    }
}

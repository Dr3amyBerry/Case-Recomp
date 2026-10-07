package org.rigorcore.caserecomp

/** Small bounded JSON parser used to keep the content importer dependency-free. */
object MiniJson {
    private const val MAX_CHARS = 8 * 1024 * 1024
    private const val MAX_DEPTH = 32

    fun parse(text: String): Any? {
        require(text.length <= MAX_CHARS) { "JSON document exceeds cap" }
        val parser = Parser(text)
        val value = parser.value(0)
        parser.ws()
        require(parser.end()) { "trailing JSON data" }
        return value
    }

    fun canonical(value: Any?): String = when (value) {
        null -> "null"
        is Boolean -> if (value) "true" else "false"
        is Byte, is Short, is Int, is Long -> value.toString()
        is Float -> { require(value.isFinite()); value.toString() }
        is Double -> { require(value.isFinite()); value.toString() }
        is String -> quote(value)
        is List<*> -> value.joinToString(prefix = "[", postfix = "]", separator = ",") { canonical(it) }
        is Map<*, *> -> value.entries.map { (k, v) ->
            require(k is String) { "JSON object key must be string" }
            k to v
        }.sortedBy { it.first }.joinToString(prefix = "{", postfix = "}", separator = ",") { (k, v) ->
            quote(k) + ":" + canonical(v)
        }
        else -> error("unsupported JSON value ${value::class}")
    }

    private fun quote(value: String): String = buildString {
        append('"')
        value.forEach { ch ->
            when (ch) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000c' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (ch.code < 0x20) append("\\u%04x".format(ch.code)) else append(ch)
            }
        }
        append('"')
    }

    private class Parser(private val text: String) {
        private var p = 0
        fun end(): Boolean = p == text.length
        fun ws() { while (p < text.length && text[p].isWhitespace()) p++ }
        fun value(depth: Int): Any? {
            require(depth <= MAX_DEPTH) { "JSON nesting exceeds cap" }
            ws(); require(p < text.length) { "unexpected end of JSON" }
            return when (text[p]) {
                '{' -> obj(depth + 1)
                '[' -> array(depth + 1)
                '"' -> string()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                '-', in '0'..'9' -> number()
                else -> error("invalid JSON token")
            }
        }
        private fun obj(depth: Int): Map<String, Any?> {
            p++; ws(); val result = linkedMapOf<String, Any?>()
            if (peek('}')) { p++; return result }
            while (true) {
                ws(); require(peek('"')) { "JSON object key expected" }
                val key = string(); require(key !in result) { "duplicate JSON object key" }
                ws(); require(peek(':')) { "JSON colon expected" }; p++
                result[key] = value(depth); ws()
                if (peek('}')) { p++; return result }
                require(peek(',')) { "JSON comma expected" }; p++
            }
        }
        private fun array(depth: Int): List<Any?> {
            p++; ws(); val result = mutableListOf<Any?>()
            if (peek(']')) { p++; return result }
            while (true) {
                result += value(depth); ws()
                if (peek(']')) { p++; return result }
                require(peek(',')) { "JSON comma expected" }; p++
            }
        }
        private fun string(): String {
            require(text[p] == '"'); p++; val out = StringBuilder()
            while (p < text.length) {
                val ch = text[p++]
                when (ch) {
                    '"' -> return out.toString()
                    '\\' -> {
                        require(p < text.length) { "truncated JSON escape" }
                        when (val e = text[p++]) {
                            '"', '\\', '/' -> out.append(e)
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000c')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> {
                                require(p + 4 <= text.length) { "truncated unicode escape" }
                                val hex = text.substring(p, p + 4)
                                out.append(hex.toInt(16).toChar()); p += 4
                            }
                            else -> error("invalid JSON escape")
                        }
                    }
                    else -> { require(ch.code >= 0x20) { "control character in JSON string" }; out.append(ch) }
                }
            }
            error("unterminated JSON string")
        }
        private fun number(): Number {
            val start = p
            if (peek('-')) p++
            require(p < text.length) { "invalid JSON number" }
            if (peek('0')) p++ else {
                require(text[p] in '1'..'9'); while (p < text.length && text[p].isDigit()) p++
            }
            var fractional = false
            if (peek('.')) { fractional = true; p++; require(p < text.length && text[p].isDigit()); while (p < text.length && text[p].isDigit()) p++ }
            if (p < text.length && (text[p] == 'e' || text[p] == 'E')) {
                fractional = true; p++; if (p < text.length && (text[p] == '+' || text[p] == '-')) p++
                require(p < text.length && text[p].isDigit()); while (p < text.length && text[p].isDigit()) p++
            }
            val raw = text.substring(start, p)
            return if (fractional) raw.toDouble().also { require(it.isFinite()) } else raw.toLong()
        }
        private fun <T> literal(word: String, value: T): T {
            require(text.regionMatches(p, word, 0, word.length)); p += word.length; return value
        }
        private fun peek(ch: Char): Boolean = p < text.length && text[p] == ch
    }
}

internal fun Any?.jsonObject(name: String): Map<String, Any?> =
    (this as? Map<*, *>)?.entries?.associate { (k, v) -> require(k is String) { "$name key" }; k to v }
        ?: error("$name must be object")
internal fun Any?.jsonList(name: String): List<Any?> = this as? List<Any?> ?: error("$name must be array")
internal fun Any?.jsonString(name: String): String = this as? String ?: error("$name must be string")
internal fun Any?.jsonInt(name: String): Int {
    val value = this as? Number ?: error("$name must be number")
    val long = value.toLong(); require(value.toDouble() == long.toDouble() && long in Int.MIN_VALUE..Int.MAX_VALUE) { "$name integer" }
    return long.toInt()
}

package org.rigorcore.caserecomp.lingo

/**
 * Parser for Lingo literal text as accepted by `value()`: numbers, quoted strings,
 * #symbols, linear lists, property lists, VOID/TRUE/FALSE and point()/rect().
 * Anything else evaluates to VOID, as Director does for unparsable input.
 */
class LingoLiteralParser private constructor(
    private val text: String,
    private val references: ((kind: String, numbers: List<Int>) -> LingoValue?)?,
) {
    private var pos = 0

    companion object {
        private const val MAX_DEPTH = 64

        /**
         * [references] resolves the object references Director writes in behaviour parameters,
         * e.g. `(sprite 3)`, `(member 5 of castLib 2)`, `(castLib 2)`; without it they are invalid.
         */
        fun parseOrVoid(text: String, references: ((kind: String, numbers: List<Int>) -> LingoValue?)? = null): LingoValue = runCatching {
            val parser = LingoLiteralParser(text, references)
            val value = parser.value(0)
            parser.skipSpace()
            if (parser.pos != text.length) LingoValue.Void else value
        }.getOrDefault(LingoValue.Void)
    }

    private fun skipSpace() { while (pos < text.length && text[pos].isWhitespace()) pos++ }

    private fun expect(ch: Char) {
        skipSpace()
        if (pos >= text.length || text[pos] != ch) throw LingoError("expected '$ch'")
        pos++
    }

    private fun value(depth: Int): LingoValue {
        if (depth > MAX_DEPTH) throw LingoError("literal nesting too deep")
        skipSpace()
        if (pos >= text.length) throw LingoError("unexpected end of literal")
        return when (val ch = text[pos]) {
            '[' -> list(depth)
            '(' -> reference()
            '"' -> string()
            '#' -> { pos++; LingoValue.LSymbol(identifier()) }
            else -> if (ch == '-' || ch == '+' || ch == '.' || ch.isDigit()) number() else word(depth)
        }
    }

    /** `(kind n [of kind2 m])`: an object reference, resolved by the host. */
    private fun reference(): LingoValue {
        val resolve = references ?: throw LingoError("object reference without a resolver")
        pos++
        skipSpace()
        val kind = identifier().lowercase()
        val numbers = mutableListOf<Int>()
        while (true) {
            skipSpace()
            when {
                pos < text.length && text[pos].isDigit() -> {
                    val start = pos
                    while (pos < text.length && text[pos].isDigit()) pos++
                    numbers += text.substring(start, pos).toInt()
                }
                pos < text.length && text[pos].isLetter() -> identifier()  // "of", "castLib"
                else -> break
            }
        }
        expect(')')
        return resolve(kind, numbers) ?: LingoValue.Void
    }

    private fun identifier(): String {
        val start = pos
        while (pos < text.length && (text[pos].isLetterOrDigit() || text[pos] == '_')) pos++
        if (pos == start) throw LingoError("expected identifier")
        return text.substring(start, pos)
    }

    private fun string(): LingoValue {
        pos++
        val end = text.indexOf('"', pos)
        if (end < 0) throw LingoError("unterminated string")
        return LingoValue.LString(text.substring(pos, end)).also { pos = end + 1 }
    }

    private fun number(): LingoValue {
        val start = pos
        if (text[pos] == '-' || text[pos] == '+') pos++
        while (pos < text.length && (text[pos].isDigit() || text[pos] == '.' || text[pos] == 'e' || text[pos] == 'E' ||
                ((text[pos] == '-' || text[pos] == '+') && (text[pos - 1] == 'e' || text[pos - 1] == 'E')))) pos++
        val raw = text.substring(start, pos)
        return raw.toIntOrNull()?.let { LingoValue.LInt(it) }
            ?: raw.toDoubleOrNull()?.let { LingoValue.LFloat(it) }
            ?: throw LingoError("invalid number")
    }

    private fun word(depth: Int): LingoValue {
        val name = identifier()
        return when (name.lowercase()) {
            "void" -> LingoValue.Void
            "true" -> LingoValue.TRUE
            "false" -> LingoValue.FALSE
            "point", "rect" -> {
                expect('(')
                val parts = mutableListOf<LingoValue>()
                skipSpace()
                if (pos < text.length && text[pos] != ')') {
                    parts += value(depth + 1)
                    while (true) { skipSpace(); if (text[pos] == ',') { pos++; parts += value(depth + 1) } else break }
                }
                expect(')')
                if (name.equals("point", true) && parts.size == 2) LingoValue.LPoint(parts[0], parts[1])
                else if (name.equals("rect", true) && parts.size == 4) LingoValue.LRect(parts[0], parts[1], parts[2], parts[3])
                else throw LingoError("bad $name literal")
            }
            else -> throw LingoError("unsupported literal word")
        }
    }

    private fun list(depth: Int): LingoValue {
        pos++
        skipSpace()
        if (pos < text.length && text[pos] == ':') {
            pos++; expect(']'); return LingoValue.LPropList()
        }
        if (pos < text.length && text[pos] == ']') { pos++; return LingoValue.LList() }
        val first = value(depth + 1)
        skipSpace()
        if (pos < text.length && text[pos] == ':') {
            pos++
            val entries = mutableListOf(first to value(depth + 1))
            while (true) {
                skipSpace()
                when (text.getOrNull(pos)) {
                    ',' -> { pos++; val key = value(depth + 1); expect(':'); entries += key to value(depth + 1) }
                    ']' -> { pos++; return LingoValue.LPropList(entries) }
                    else -> throw LingoError("bad property list")
                }
            }
        }
        val items = mutableListOf(first)
        while (true) {
            skipSpace()
            when (text.getOrNull(pos)) {
                ',' -> { pos++; items += value(depth + 1) }
                ']' -> { pos++; return LingoValue.LList(items) }
                else -> throw LingoError("bad list")
            }
        }
    }
}

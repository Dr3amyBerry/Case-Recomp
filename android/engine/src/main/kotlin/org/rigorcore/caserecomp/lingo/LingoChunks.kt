package org.rigorcore.caserecomp.lingo

/** Chunk kinds in the order the compiler nests them: line, then item, then word, then char. */
enum class ChunkKind { LINE, ITEM, WORD, CHAR }

/** One `kind first to last` selector; `last == 0` means a single chunk. */
data class ChunkSelector(val kind: ChunkKind, val first: Int, val last: Int)

/** Resolves Lingo chunk expressions (`char 2 to 4 of item 3 of s`) to string offsets. */
object LingoChunks {
    private fun spans(text: String, kind: ChunkKind, delimiter: Char): List<IntRange> = when (kind) {
        ChunkKind.CHAR -> text.indices.map { it..it }
        ChunkKind.LINE -> split(text, '\r')
        ChunkKind.ITEM -> split(text, delimiter)
        ChunkKind.WORD -> Regex("[^ \t\r\n]+").findAll(text).map { it.range }.toList()
    }

    private fun split(text: String, delimiter: Char): List<IntRange> {
        val out = mutableListOf<IntRange>()
        var start = 0
        for (i in text.indices) if (text[i] == delimiter) { out += start until i; start = i + 1 }
        out += start until text.length
        return out
    }

    /** Absolute [start, end) of the selected text inside [text], or an empty range at the end. */
    fun locate(text: String, selectors: List<ChunkSelector>, delimiter: Char = ','): IntRange {
        var base = 0
        var current = text
        var result = 0 until text.length
        for (selector in selectors) {
            val pieces = spans(current, selector.kind, delimiter)
            val first = selector.first
            val last = if (selector.last == 0) first else selector.last
            if (first < 1 || first > pieces.size || last < first) {
                return (base + current.length) until (base + current.length)
            }
            val start = pieces[first - 1].first
            val end = pieces[minOf(last, pieces.size) - 1].last + 1
            result = (base + start) until (base + end)
            base += start
            current = current.substring(start, end)
        }
        return result
    }

    fun get(text: String, selectors: List<ChunkSelector>, delimiter: Char = ','): String {
        val range = locate(text, selectors, delimiter)
        return text.substring(range.first, range.last + 1)
    }

    /** `delete <chunk> of var`; deleting an item or line also removes one adjacent delimiter. */
    fun delete(text: String, selectors: List<ChunkSelector>, delimiter: Char = ','): String {
        if (selectors.isEmpty()) return ""
        val range = locate(text, selectors, delimiter)
        var start = range.first
        var end = range.last + 1
        val separator = when (selectors.last().kind) { ChunkKind.ITEM -> delimiter; ChunkKind.LINE -> '\r'; else -> null }
        if (separator != null && start < end) {
            if (end < text.length && text[end] == separator) end++ else if (start > 0 && text[start - 1] == separator) start--
        }
        return text.substring(0, start) + text.substring(end)
    }

    /** `put value into|after|before <chunk> of var`. */
    fun put(text: String, selectors: List<ChunkSelector>, value: String, mode: PutMode, delimiter: Char = ','): String {
        val range = if (selectors.isEmpty()) 0 until text.length else locate(text, selectors, delimiter)
        val start = range.first
        val end = range.last + 1
        return when (mode) {
            PutMode.INTO -> text.substring(0, start) + value + text.substring(end)
            PutMode.AFTER -> text.substring(0, end) + value + text.substring(end)
            PutMode.BEFORE -> text.substring(0, start) + value + text.substring(start)
        }
    }
}

enum class PutMode { INTO, AFTER, BEFORE }

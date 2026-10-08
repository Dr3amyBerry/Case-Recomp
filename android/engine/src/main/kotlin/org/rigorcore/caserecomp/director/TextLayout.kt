package org.rigorcore.caserecomp.director

import org.rigorcore.caserecomp.lingo.toInt

/**
 * One laid-out line of a text member: its characters and left offset inside the member box.
 * [segments] are its tab-separated runs with their offsets from [x].
 */
class TextLine(val text: String, val x: Int, val segments: List<Pair<Int, String>> = listOf(0 to text))

/**
 * Line breaking of text members the way Director draws them: paragraphs end at RETURN,
 * words wrap at the box width, and each line is placed by the member's alignment.
 */
object TextLayout {
    fun lines(member: CastMember, width: Int, metrics: TextMetrics): List<TextLine> {
        val out = mutableListOf<TextLine>()
        val style = member.data?.textStyle
        val left = style?.leftIndent ?: 0
        val first = style?.firstIndent ?: 0
        val inner = width - left - (style?.rightIndent ?: 0)
        for (paragraph in member.text.replace("\r\n", "\r").replace('\n', '\r').split('\r')) {
            if ('\t' in paragraph) {
                // Tabbed rows (e.g. HTML tables) are not wrapped; runs start at the member's tab stops.
                val segments = tabbed(member, paragraph, metrics)
                out += TextLine(paragraph, left + first, segments)
                continue
            }
            for ((index, line) in wrap(member, paragraph, inner - first, inner, metrics).withIndex()) {
                val start = left + if (index == 0) first else 0
                val free = (if (index == 0) inner - first else inner) - metrics.width(member, line.trimEnd())
                val x = start + when (member.alignment) {
                    "center" -> free / 2
                    "right" -> free
                    else -> 0
                }
                out += TextLine(line.trimEnd(), maxOf(0, x))
            }
        }
        return out
    }

    private const val DEFAULT_TAB = 48

    private fun tabbed(member: CastMember, line: String, metrics: TextMetrics): List<Pair<Int, String>> {
        val stops = member.tabStops
        var x = 0
        return line.split('\t').mapIndexed { i, run ->
            if (i > 0) {
                val stop = stops.getOrNull(i - 1)
                x = if (stop != null && stop >= x) stop else (x / DEFAULT_TAB + 1) * DEFAULT_TAB
            }
            (x to run).also { x += metrics.width(member, run) }
        }
    }

    /** Line pitch: Lingo's `fixedLineSpace` when set, else the platform metrics. */
    fun lineHeight(member: CastMember, metrics: TextMetrics): Int =
        member.prop("fixedlinespace")?.toInt()?.takeIf { it > 0 } ?: metrics.lineHeight(member)

    /** Greedy word wrap: the first line is [firstWidth] wide, the following ones [width]. */
    private fun wrap(member: CastMember, paragraph: String, firstWidth: Int, width: Int, metrics: TextMetrics): List<String> {
        if (width <= 0 || metrics.width(member, paragraph) <= firstWidth) return listOf(paragraph)
        val lines = mutableListOf<String>()
        var current = paragraph.takeWhile { it == ' ' }
        for (word in Regex("\\S+\\s*").findAll(paragraph).map { it.value }) {
            val candidate = current + word
            if (current.isNotEmpty() && metrics.width(member, candidate.trimEnd()) > (if (lines.isEmpty()) firstWidth else width)) {
                lines += current
                current = word
            } else current = candidate
        }
        lines += current
        return lines
    }
}

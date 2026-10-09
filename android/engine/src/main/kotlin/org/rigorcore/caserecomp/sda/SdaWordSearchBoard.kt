package org.rigorcore.caserecomp.sda

/** Recovered board algorithms. Input coordinates here are cell indices, not screen pixels.
 * CRT case operations use the default ASCII locale; native encoding/locale remains a separate check.
 */
class SdaWordSearchBoard(val rows: Int, val cols: Int, pool: List<String>, seed: Long) {
    val words: List<String>
    val grid: List<String>
    val displayGrid: List<String>
    val placements: Map<String, List<Int>>
    val finalRngState: Long
    private val found = linkedSetOf<String>()
    val foundWords: Set<String> get() = found.toSet()
    var selectedStart: Int? = null
        private set
    val isSolved: Boolean get() = found.size == words.size
    val basePoints: Int get() = found.size * 250

    init {
        require(rows in 2..32 && cols in 2..32) { "invalid wordsearch dimensions" }
        require(pool.size in 1..512 && pool.distinct().size == pool.size && pool.all {
            it.length in 2..maxOf(rows, cols) && it.all { c -> c.code in 1..255 && c.isLetter() }
        }) { "unsupported wordsearch pool" }
        val rng = SdaRng(seed)
        // 0045c0e0 consumes one draw for each cell's font, even though its index truncates to 0.
        repeat(rows * cols) { rng.nextScaled() }
        val selected = pool.toMutableList()
        while (selected.size > 10) selected.removeAt((rng.nextScaled() * selected.size).toInt())
        // 00460950's <=16-element insertion sort is stable; 004614f0 then reverses it.
        words = selected.sortedBy { it.length }.asReversed()
        val logical = CharArray(rows * cols)
        val displayed = CharArray(rows * cols)
        val paths = linkedMapOf<String, List<Int>>()
        for (word in words) {
            var placed = false
            for (attempt in 1 until 1000) {
                val direction = (rng.nextScaled() * 8).toInt()
                val (dx, dy) = DIRECTIONS[direction]
                val x = (rng.nextScaled() * (if (dx == 0) cols else cols - word.length) +
                    (if (dx < 0) word.length else 0)).toInt()
                val y = (rng.nextScaled() * (if (dy == 0) rows else rows - word.length) +
                    (if (dy < 0) word.length else 0)).toInt()
                val cells = path(x, y, dx, dy, word.length) ?: continue
                if (cells.indices.any { logical[cells[it]] != '\u0000' && logical[cells[it]] != word[it] }) continue
                cells.forEachIndexed { index, cell ->
                    val ch = word[index]
                    logical[cell] = ch
                    displayed[cell] = if (ch in 'A'..'Z' || ch in 'a'..'z') {
                        if (rng.nextScaled() < .5) ch else if (ch in 'a'..'z') ch.uppercaseChar() else ch.lowercaseChar()
                    } else ch
                }
                paths[word] = cells
                placed = true
                break
            }
            // The native attempt bound is preserved; diagnose failure instead of inventing a fallback board.
            require(placed) { "native placement attempt budget exhausted" }
        }
        var fillerDraws = 0
        for (cell in logical.indices) {
            if (logical[cell] != '\u0000') continue
            while (true) {
                require(++fillerDraws <= 100000) { "wordsearch filler budget exhausted" }
                val candidate = (rng.nextScaled() * 57 + 65).toInt().toChar()
                if (candidate !in 'A'..'Z' && candidate !in 'a'..'z') continue
                logical[cell] = candidate
                // 0045d540 rejects accidental complete target words starting at this filler cell.
                val accidental = words.any { word -> DIRECTIONS.any { (dx, dy) ->
                    val cells = path(cell % cols, cell / cols, dx, dy, word.length)
                    cells != null && cells.indices.all { asciiLower(logical[cells[it]]) == asciiLower(word[it]) }
                } }
                if (accidental) continue
                displayed[cell] = candidate
                break
            }
        }
        grid = (0 until rows).map { String(logical, it * cols, cols) }
        displayGrid = (0 until rows).map { String(displayed, it * cols, cols) }
        placements = paths.toMap()
        finalRngState = rng.state
    }

    private fun path(x: Int, y: Int, dx: Int, dy: Int, count: Int): List<Int>? {
        val cells = ArrayList<Int>(count)
        for (step in 0 until count) {
            val col = x + dx * step; val row = y + dy * step
            if (col !in 0 until cols || row !in 0 until rows) return null
            cells.add(row * cols + col)
        }
        return cells
    }

    fun begin(cell: Int): Boolean {
        selectedStart = cell.takeIf { it in 0 until rows * cols }
        return selectedStart != null
    }

    /** 0045e850/0045e1f0 accept only the original recorded path, including reverse endpoints. */
    fun end(cell: Int): Boolean {
        val start = selectedStart
        selectedStart = null
        if (start == null || cell !in 0 until rows * cols || start == cell) return false
        val word = placements.entries.firstOrNull { (word, cells) ->
            word !in found && ((cells.first() == start && cells.last() == cell) ||
                (cells.last() == start && cells.first() == cell))
        }?.key ?: return false
        return found.add(word)
    }

    fun cancel() { selectedStart = null }

    companion object {
        private val DIRECTIONS = listOf(0 to -1, 1 to -1, 1 to 0, 1 to 1,
            0 to 1, -1 to 1, -1 to 0, -1 to -1)
        private fun asciiLower(ch: Char): Char = if (ch in 'A'..'Z') ch.lowercaseChar() else ch
    }
}

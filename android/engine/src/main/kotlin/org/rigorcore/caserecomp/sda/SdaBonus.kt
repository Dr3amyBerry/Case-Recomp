package org.rigorcore.caserecomp.sda

import org.w3c.dom.Element
import org.w3c.dom.Node

const val SDA_BONUS_REWARD = 25000

/**
 * Generic interface for SDA bonus minigames.
 */
interface SdaBonusGame {
    val kind: String
    val resourceName: String
    val isSolved: Boolean
    var points: Int
    val rows: Int
    val cols: Int
    fun clickPixel(x: Int, y: Int): Boolean
    fun solve()
    fun state(): Map<String, Any?>
}

/**
 * Tile rotation minigame (.trg): tiles must be rotated to orientation 0.
 */
class SdaTileRotGame(
    override val resourceName: String,
    override val rows: Int = 4,
    override val cols: Int = 6,
    val seed: Long = 0L,
    val bonusImage: String = "",
    rotations: IntArray? = null,
) : SdaBonusGame {
    override val kind: String = "tilerot"
    override var points: Int = SDA_BONUS_REWARD
    val tileRotations: IntArray = rotations?.copyOf() ?: IntArray(rows * cols)

    init {
        if (rotations == null) {
            val rng = SdaRng(seed and 0xFFFFFFFFL)
            for (i in tileRotations.indices) {
                // Ensure at least non-zero initial rotation
                val rot = (rng.next() % 3) + 1
                tileRotations[i] = rot
            }
        }
    }

    // Native 00459ce0/0045a780: completed rows/columns retire their tiles.
    val lockedTiles = BooleanArray(rows * cols)
    var linePoints: Int = 0
        private set

    override val isSolved: Boolean
        get() = lockedTiles.all { it }

    override fun clickPixel(x: Int, y: Int): Boolean = rotatePixel(x, y, clockwise = false)

    /** 00459c20: primary subtracts a quarter turn, secondary adds one. */
    fun rotatePixel(x: Int, y: Int, clockwise: Boolean): Boolean {
        if (isSolved || x !in 172 until 784 || y !in 95 until 503) return false
        val col = (x - 172) * cols / 612
        val row = (y - 95) * rows / 408
        val index = row * cols + col
        if (lockedTiles[index]) return false
        tileRotations[index] = (tileRotations[index] + if (clockwise) 1 else 3) % 4
        val rowIndices = (0 until cols).map { row * cols + it }
        val colIndices = (0 until rows).map { it * cols + col }
        for (line in listOf(rowIndices, colIndices)) {
            if (line.all { tileRotations[it] == 0 }) {
                line.forEach { lockedTiles[it] = true }
                linePoints += 250 // 00459ce0 base score; native fast bonus is still pending.
            }
        }
        return true
    }

    internal fun restoreLocks(locks: List<Boolean>?, score: Int) {
        require(score in 0..((rows + cols) * 250) && score % 250 == 0) { "invalid rotation line score" }
        // Old checkpoints had no retirement bits. Infer completed lines, preserve their points.
        val inferred = BooleanArray(rows * cols)
        for (row in 0 until rows) {
            val line = (0 until cols).map { row * cols + it }
            if (line.all { tileRotations[it] == 0 }) line.forEach { inferred[it] = true }
        }
        for (col in 0 until cols) {
            val line = (0 until rows).map { it * cols + col }
            if (line.all { tileRotations[it] == 0 }) line.forEach { inferred[it] = true }
        }
        require(locks == null || (locks.size == lockedTiles.size &&
            locks.indices.all { locks[it] == inferred[it] })) { "invalid retired rotation tiles" }
        inferred.copyInto(lockedTiles)
        linePoints = score
    }

    override fun solve() {
        tileRotations.fill(0)
        lockedTiles.fill(true)
        points = 0
    }

    override fun state(): Map<String, Any?> = mapOf(
        "kind" to kind,
        "resourceName" to resourceName,
        "rows" to rows,
        "cols" to cols,
        "seed" to seed,
        "bonusImage" to bonusImage,
        "points" to points,
        "rotations" to tileRotations.map { it.toLong() },
        "lockedTiles" to lockedTiles.toList(),
        "linePoints" to linePoints,
    )
}

/**
 * Tile swap minigame (.tgl): two-tile selection and swapping to identity order.
 */
class SdaTileSwapGame(
    override val resourceName: String,
    override val rows: Int = 6,
    override val cols: Int = 6,
    val seed: Long = 0L,
    val bonusImage: String = "",
    tiles: IntArray? = null,
    var selectedIndex: Int? = null,
) : SdaBonusGame {
    override val kind: String = "tilegame"
    override var points: Int = SDA_BONUS_REWARD
    val tilePositions: IntArray = tiles?.copyOf() ?: IntArray(rows * cols) { it }

    val lockedTiles = BooleanArray(rows * cols)
    var placementPoints: Int = 0
        private set

    init {
        require(rows in 1..32 && cols in 1..32 && rows * cols >= 2) { "invalid swap dimensions" }
        require(tilePositions.sorted() == tilePositions.indices.toList()) { "invalid tile permutation" }
        if (tiles == null) {
            // 00457500: forward suffix shuffle, rejecting self and fixed identities.
            val rng = SdaRng(seed and 0xFFFFFFFFL)
            for (index in 0 until tilePositions.size - 1) {
                do {
                    var other: Int
                    do { other = index + rng.next() % (tilePositions.size - index) } while (other == index)
                    val previous = tilePositions[index]
                    tilePositions[index] = tilePositions[other]
                    tilePositions[other] = previous
                } while (tilePositions[index] == index)
            }
        }
    }

    override val isSolved: Boolean
        get() = lockedTiles.all { it }

    override fun clickPixel(x: Int, y: Int): Boolean {
        if (isSolved) return false
        val boardX = 172
        val boardY = 96
        val boardW = 612
        val boardH = 408
        if (x !in boardX until (boardX + boardW) || y !in boardY until (boardY + boardH)) {
            return false
        }
        val col = ((x - boardX) * cols) / boardW
        val row = ((y - boardY) * rows) / boardH
        if (row in 0 until rows && col in 0 until cols) {
            val idx = row * cols + col
            if (lockedTiles[idx]) return false
            val sel = selectedIndex
            if (sel == null) {
                selectedIndex = idx
                return true
            } else if (sel == idx) {
                selectedIndex = null
                return true
            } else {
                val tmp = tilePositions[sel]
                tilePositions[sel] = tilePositions[idx]
                tilePositions[idx] = tmp
                selectedIndex = null
                // 00457ed0/00458e30: retire each newly correct tile, not whole lines.
                for (position in listOf(sel, idx)) {
                    if (tilePositions[position] == position) {
                        lockedTiles[position] = true
                        placementPoints += 250
                    }
                }
                return true
            }
        }
        return false
    }

    override fun solve() {
        for (i in tilePositions.indices) {
            tilePositions[i] = i
        }
        lockedTiles.fill(true)
        selectedIndex = null
        points = 0
    }

    internal fun restoreLocks(locks: List<Boolean>?, score: Int) {
        val values = locks ?: tilePositions.indices.map { tilePositions[it] == it && selectedIndex != it }
        require(values.size == lockedTiles.size && values.indices.all {
            !values[it] || tilePositions[it] == it
        }) { "invalid retired swap tiles" }
        require(selectedIndex == null || !values[selectedIndex!!]) { "selected swap tile is retired" }
        require(score >= 0 && score % 250 == 0 && score <= values.count { it } * 250) { "invalid swap placement score" }
        values.forEachIndexed { index, value -> lockedTiles[index] = value }
        placementPoints = score
    }

    override fun state(): Map<String, Any?> = mapOf(
        "kind" to kind,
        "resourceName" to resourceName,
        "rows" to rows,
        "cols" to cols,
        "seed" to seed,
        "bonusImage" to bonusImage,
        "points" to points,
        "tiles" to tilePositions.map { it.toLong() },
        "selectedIndex" to selectedIndex,
        "lockedTiles" to lockedTiles.toList(),
        "placementPoints" to placementPoints,
    )
}

/**
 * Word search minigame (.wsg).
 */
class SdaWordSearchGame(
    override val resourceName: String,
    override val rows: Int = 8,
    override val cols: Int = 12,
    val seed: Long = 0L,
    val words: List<String>,
    val bonusImage: String = "",
    val foundWords: MutableSet<String> = mutableSetOf(),
) : SdaBonusGame {
    override val kind: String = "wordsearch"
    override var points: Int = SDA_BONUS_REWARD

    override val isSolved: Boolean
        get() = foundWords.containsAll(words)

    override fun clickPixel(x: Int, y: Int): Boolean {
        if (isSolved) return false
        // Word search selection click
        val unFound = words.firstOrNull { it !in foundWords }
        if (unFound != null) {
            foundWords.add(unFound)
            return true
        }
        return false
    }

    override fun solve() {
        foundWords.addAll(words)
        points = 0
    }

    override fun state(): Map<String, Any?> = mapOf(
        "kind" to kind,
        "resourceName" to resourceName,
        "rows" to rows,
        "cols" to cols,
        "seed" to seed,
        "bonusImage" to bonusImage,
        "points" to points,
        "words" to words,
        "foundWords" to foundWords.toList(),
    )
}

/**
 * Jigsaw puzzle minigame (.jsw).
 */
class SdaJigsawGame(
    override val resourceName: String,
    val totalPieces: Int = 24,
    val seed: Long = 0L,
    val bonusImage: String = "",
    val placedPieces: MutableSet<Int> = mutableSetOf(),
) : SdaBonusGame {
    override val kind: String = "jigsaw"
    override val rows: Int = 4
    override val cols: Int = 6
    override var points: Int = SDA_BONUS_REWARD

    override val isSolved: Boolean
        get() = placedPieces.size >= totalPieces

    override fun clickPixel(x: Int, y: Int): Boolean {
        if (isSolved) return false
        for (i in 0 until totalPieces) {
            if (i !in placedPieces) {
                placedPieces.add(i)
                return true
            }
        }
        return false
    }

    override fun solve() {
        for (i in 0 until totalPieces) placedPieces.add(i)
        points = 0
    }

    override fun state(): Map<String, Any?> = mapOf(
        "kind" to kind,
        "resourceName" to resourceName,
        "rows" to rows,
        "cols" to cols,
        "totalPieces" to totalPieces,
        "seed" to seed,
        "bonusImage" to bonusImage,
        "points" to points,
        "placedPieces" to placedPieces.map { it.toLong() },
    )
}

/**
 * Finale Master Riddle minigame (Levels 25 finale: 3 progressive stages).
 */
class SdaMasterRiddleGame(
    var stage: Int = 1,
    val seed: Long = 0L,
    val completedSteps: MutableSet<String> = mutableSetOf(),
) : SdaBonusGame {
    override val kind: String = "master_riddle"
    override val resourceName: String = "ENVS.MSE"
    override val rows: Int = 1
    override val cols: Int = 6
    override var points: Int = SDA_BONUS_REWARD

    companion object {
        val STAGE_1_ITEMS = listOf("suspect_key", "fingerprint", "vault_passcode", "security_card", "heist_blueprint")
        val STAGE_2_ITEMS = listOf("generator_fuse", "bypass_relay", "cooling_valve", "backup_circuit")
        val STAGE_3_ITEMS = listOf("pull_slot_arm", "set_clock", "switch_lever", "insert_coin", "scan_fingerprint", "enter_code")
    }

    fun currentStageRequirements(): List<String> = when (stage) {
        1 -> STAGE_1_ITEMS
        2 -> STAGE_2_ITEMS
        3 -> STAGE_3_ITEMS
        else -> emptyList()
    }

    override val isSolved: Boolean
        get() = stage > 3

    override fun clickPixel(x: Int, y: Int): Boolean {
        if (isSolved) return false
        val reqs = currentStageRequirements()
        val nextUnfinished = reqs.firstOrNull { it !in completedSteps }
        if (nextUnfinished != null) {
            completedSteps.add(nextUnfinished)
            if (reqs.all { it in completedSteps }) {
                stage++
                completedSteps.clear()
            }
            return true
        }
        return false
    }

    override fun solve() {
        stage = 4
        completedSteps.clear()
        points = 0
    }

    override fun state(): Map<String, Any?> = mapOf(
        "kind" to kind,
        "resourceName" to resourceName,
        "rows" to rows,
        "cols" to cols,
        "stage" to stage,
        "seed" to seed,
        "points" to points,
        "completedSteps" to completedSteps.toList(),
    )
}

object SdaBonusLoader {
    fun load(
        sdaContent: SdaContent,
        bonusName: String,
        seed: Long = 0L,
        bonusImage: String = ""
    ): SdaBonusGame {
        val nameUpper = bonusName.uppercase()
        require(nameUpper.endsWith(".TRG") || nameUpper.endsWith(".TGL") ||
            nameUpper.endsWith(".WSG") || nameUpper.endsWith(".JSW")) { "unsupported bonus format: $bonusName" }
        val raw = sdaContent.read(bonusName)
            ?: throw IllegalArgumentException("bonus resource missing: $bonusName")
        val doc = parseBonusXml(raw)

        if (nameUpper.endsWith(".TRG")) {
            val rows = dimension(doc, "rows")
            val cols = dimension(doc, "columns")
            require(rows.toLong() * cols <= 1024) { "bonus board exceeds budget" }
            return SdaTileRotGame(bonusName, rows, cols, seed, bonusImage)
        } else if (nameUpper.endsWith(".TGL")) {
            val rows = dimension(doc, "rows")
            val cols = dimension(doc, "columns")
            require(rows.toLong() * cols <= 1024) { "bonus board exceeds budget" }
            return SdaTileSwapGame(bonusName, rows, cols, seed, bonusImage)
        } else if (nameUpper.endsWith(".WSG")) {
            val wordsRaw = sdaContent.read("WORDSEARCH.TXT")
                ?: throw IllegalArgumentException("wordsearch table missing")
            val reference = doc["text"] ?: throw IllegalArgumentException("wordsearch list reference missing")
            val list = SdaStrings.resolve(reference, SdaStrings.parse(wordsRaw))
            require(!list.startsWith("@")) { "unknown wordsearch list: $reference" }
            val words = list.split(',').map { it.trim().uppercase() }
            require(words.isNotEmpty() && words.none { it.isEmpty() }) { "empty wordsearch list" }
            return SdaWordSearchGame(bonusName, dimension(doc, "rows"), dimension(doc, "columns"), seed, words, bonusImage)
        } else if (nameUpper.endsWith(".JSW")) {
            return SdaJigsawGame(bonusName, 24, seed, bonusImage)
        }
        throw IllegalArgumentException("unsupported bonus format: $bonusName")
    }

    /** Restore the recorded board; reconstruction from its seed is not a checkpoint. */
    fun restore(content: SdaContent, state: Map<String, Any?>, resource: String,
                seed: Long, bonusImage: String): SdaBonusGame {
        require(state["resourceName"] == resource && integer(state["seed"]) == seed) { "bonus identity mismatch" }
        val game = load(content, resource, seed, bonusImage)
        require(state["kind"] == game.kind && integer(state["rows"]) == game.rows.toLong() &&
            integer(state["cols"]) == game.cols.toLong()) { "bonus definition mismatch" }
        require((state["bonusImage"] ?: "") == bonusImage) { "bonus image mismatch" }
        val points = integer(state["points"])
        require(points == 0L || points == SDA_BONUS_REWARD.toLong()) { "invalid bonus reward" }
        fun integers(key: String): List<Int> {
            val values = state[key] as? List<*> ?: throw IllegalArgumentException("missing bonus $key")
            require(values.size <= 1024) { "bonus list exceeds budget" }
            return values.map {
                val value = integer(it)
                require(value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) { "invalid bonus integer" }
                value.toInt()
            }
        }
        when (game) {
            is SdaTileRotGame -> {
                val rotations = integers("rotations")
                require(rotations.size == game.tileRotations.size && rotations.all { it in 0..3 }) { "invalid rotations" }
                rotations.forEachIndexed { index, value -> game.tileRotations[index] = value }
                val locks = state["lockedTiles"]?.let { raw ->
                    val values = raw as? List<*> ?: throw IllegalArgumentException("invalid rotation locks")
                    require(values.all { it is Boolean }) { "invalid rotation locks" }
                    values.map { it as Boolean }
                }
                val score = integer(state["linePoints"] ?: 0)
                require(score in 0..Int.MAX_VALUE.toLong()) { "invalid rotation line score" }
                game.restoreLocks(locks, score.toInt())
            }
            is SdaTileSwapGame -> {
                val tiles = integers("tiles")
                require(tiles.sorted() == game.tilePositions.indices.toList()) { "invalid tile permutation" }
                tiles.forEachIndexed { index, value -> game.tilePositions[index] = value }
                game.selectedIndex = state["selectedIndex"]?.let {
                    val index = integer(it)
                    require(index in 0 until tiles.size.toLong()) { "invalid selected tile" }
                    index.toInt()
                }
                val locks = state["lockedTiles"]?.let { raw ->
                    val values = raw as? List<*> ?: throw IllegalArgumentException("invalid swap locks")
                    require(values.all { it is Boolean }) { "invalid swap locks" }
                    values.map { it as Boolean }
                }
                val score = integer(state["placementPoints"] ?: 0)
                require(score in 0..Int.MAX_VALUE.toLong()) { "invalid swap placement score" }
                game.restoreLocks(locks, score.toInt())
            }
            is SdaWordSearchGame -> {
                require(state["words"] == game.words) { "wordsearch list mismatch" }
                val words = state["foundWords"] as? List<*> ?: throw IllegalArgumentException("missing found words")
                require(words.all { it is String && it in game.words } && words.distinct().size == words.size) { "invalid found words" }
                game.foundWords.addAll(words.map { it as String })
            }
            is SdaJigsawGame -> {
                require(integer(state["totalPieces"]) == game.totalPieces.toLong()) { "jigsaw definition mismatch" }
                val pieces = integers("placedPieces")
                require(pieces.distinct().size == pieces.size && pieces.all { it in 0 until game.totalPieces }) { "invalid placed pieces" }
                game.placedPieces.addAll(pieces)
            }
        }
        game.points = points.toInt()
        return game
    }

    /** Preserve received diagnostic finale checkpoints, without certifying their rules. */
    internal fun restoreLegacyFinale(state: Map<String, Any?>, seed: Long): SdaMasterRiddleGame {
        require(state["kind"] == "master_riddle" && state["resourceName"] == "ENVS.MSE" &&
            integer(state["seed"]) == seed && integer(state["rows"]) == 1L &&
            integer(state["cols"]) == 6L) { "invalid legacy finale identity" }
        val stage = integer(state["stage"])
        require(stage in 1L..4L) { "invalid legacy finale stage" }
        val game = SdaMasterRiddleGame(stage.toInt(), seed)
        val steps = state["completedSteps"] as? List<*> ?: throw IllegalArgumentException("missing legacy finale steps")
        require(steps.distinct().size == steps.size && steps.all {
            it is String && it in game.currentStageRequirements()
        }) { "invalid legacy finale steps" }
        val points = integer(state["points"])
        require(points == 0L || points == SDA_BONUS_REWARD.toLong()) { "invalid legacy finale reward" }
        game.completedSteps.addAll(steps.map { it as String })
        game.points = points.toInt()
        return game
    }

    private fun integer(value: Any?): Long {
        require(value is Int || value is Long) { "bonus integer required" }
        return (value as Number).toLong()
    }

    private fun dimension(attrs: Map<String, String>, name: String): Int {
        val value = attrs[name]?.toIntOrNull()
        require(value != null && value in 1..1024) { "invalid bonus $name" }
        return value
    }

    private fun parseBonusXml(raw: ByteArray): Map<String, String> {
        val attrs = mutableMapOf<String, String>()
        val doc = SdaXml.parse(raw)
        fun walk(node: Node) {
            if (node.nodeType == Node.ELEMENT_NODE) {
                val elem = node as Element
                for (i in 0 until elem.attributes.length) {
                    val item = elem.attributes.item(i)
                    attrs[item.nodeName.lowercase()] = item.nodeValue
                }
            }
            for (i in 0 until node.childNodes.length) walk(node.childNodes.item(i))
        }
        walk(doc.documentElement)
        return attrs
    }
}

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
                // 00459400: floor(scaled rand * 4), rejecting angle zero.
                var quarter: Int
                do { quarter = (rng.nextScaled() * 4).toInt() } while (quarter == 0)
                tileRotations[i] = quarter
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
    words: List<String>,
    val bonusImage: String = "",
    val originX: Int = 0, val originY: Int = 0,
    val cellWidth: Int = 1, val cellHeight: Int = 1,
    val tileImages: Map<String, SdaPixelSource> = emptyMap(),
    checkpoint: Map<String, Any?>? = null,
) : SdaBonusGame {
    override val kind = "wordsearch"
    override var points = SDA_BONUS_REWARD
    private val pool = words.toList()
    val board: SdaWordSearchBoard
    val words: List<String> get() = board.words
    val foundWords: Set<String> get() = board.foundWords
    var selectedEnd: Int? = null
        private set
    var placementPoints = 0
        private set
    override val isSolved: Boolean get() = board.isSolved

    init {
        require(cellWidth in 1..1024 && cellHeight in 1..1024 &&
            originX in -32768..32768 && originY in -32768..32768) { "invalid wordsearch geometry" }
        @Suppress("UNCHECKED_CAST")
        val savedBoard = checkpoint?.let {
            fun matches(key: String, expected: Int): Boolean {
                val value = it[key]
                return (value is Int || value is Long) && (value as Number).toLong() == expected.toLong()
            }
            require(it["pool"] == pool && matches("originX", originX) && matches("originY", originY) &&
                matches("cellWidth", cellWidth) && matches("cellHeight", cellHeight)) { "wordsearch definition mismatch" }
            it["wordBoard"] as? Map<String, Any?>
                ?: throw IllegalArgumentException("legacy simulated wordsearch checkpoint has no board")
        }
        board = SdaWordSearchBoard(rows, cols, pool, seed, savedBoard)
        if (checkpoint != null) {
            fun integer(raw: Any?): Int {
                require(raw is Int || raw is Long) { "wordsearch integer required" }
                val value = (raw as Number).toLong()
                require(value in 0..Int.MAX_VALUE.toLong()) { "invalid wordsearch integer" }
                return value.toInt()
            }
            placementPoints = integer(checkpoint["placementPoints"])
            require(placementPoints % 250 == 0 && placementPoints <= board.basePoints) { "invalid wordsearch score" }
            selectedEnd = checkpoint["selectedEnd"]?.let { integer(it).also { cell ->
                require(board.selectedStart != null && cell in 0 until rows * cols) { "invalid wordsearch end" }
            } }
            points = integer(checkpoint["points"])
            require(points == SDA_BONUS_REWARD || (points == 0 && isSolved)) { "invalid wordsearch reward" }
            require(points == 0 || placementPoints == board.basePoints) { "inconsistent wordsearch score" }
        }
    }
    private fun cell(x: Int, y: Int): Int {
        val dx = x.toLong() - originX; val dy = y.toLong() - originY
        if (dx !in 0 until cols.toLong() * cellWidth || dy !in 0 until rows.toLong() * cellHeight) return -1
        return (dy / cellHeight * cols + dx / cellWidth).toInt()
    }
    fun beginPixel(x: Int, y: Int): Boolean {
        if (isSolved) return false
        val selected = cell(x, y)
        selectedEnd = selected.takeIf { it >= 0 }
        return board.begin(selected)
    }
    fun movePixel(x: Int, y: Int): Boolean {
        if (board.selectedStart == null) return false
        selectedEnd = cell(x, y).takeIf { it >= 0 }
        return selectedEnd != null
    }
    fun endPixel(x: Int, y: Int): Boolean {
        selectedEnd = null
        val found = board.end(cell(x, y))
        if (found) placementPoints += 250
        return found
    }
    val selectedCells: Set<Int> get() {
        val start = board.selectedStart ?: return emptySet()
        val end = selectedEnd ?: return emptySet()
        val dx = end % cols - start % cols; val dy = end / cols - start / cols
        if (dx != 0 && dy != 0 && kotlin.math.abs(dx) != kotlin.math.abs(dy)) return emptySet()
        val count = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
        val sx = dx.compareTo(0); val sy = dy.compareTo(0)
        return (0..count).map { start + it * (sy * cols + sx) }.toSet()
    }
    fun cancelSelection() { board.cancel(); selectedEnd = null }
    override fun clickPixel(x: Int, y: Int): Boolean =
        if (board.selectedStart == null) beginPixel(x, y) else endPixel(x, y)
    override fun solve() { board.skip(); selectedEnd = null; points = 0 }
    override fun state(): Map<String, Any?> = mapOf(
        "kind" to kind, "resourceName" to resourceName, "rows" to rows, "cols" to cols,
        "seed" to seed, "bonusImage" to bonusImage, "points" to points, "pool" to pool,
        "words" to words, "originX" to originX, "originY" to originY,
        "cellWidth" to cellWidth, "cellHeight" to cellHeight, "wordBoard" to board.state(),
        "selectedEnd" to selectedEnd, "placementPoints" to placementPoints)
}

/**
 * Read-only legacy simulated finale checkpoint. Never used for new gameplay.
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

    // Read-only compatibility for old simulated checkpoints; not authentic gameplay.
    override fun clickPixel(x: Int, y: Int): Boolean = false
    override fun solve(): Unit = throw UnsupportedOperationException("legacy simulated finale is read-only")

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
        bonusImage: String = "",
        checkpoint: Map<String, Any?>? = null
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
            val words = list.split(',')
            require(words.isNotEmpty() && words.none { it.isEmpty() }) { "empty wordsearch list" }
            val definition = SdaXml.parse(raw)
            val nodes = definition.getElementsByTagName("*")
            val textures = mutableMapOf<String, String>()
            var tiles: Element? = null
            for (i in 0 until nodes.length) {
                val node = nodes.item(i) as Element
                if ((node.localName ?: node.tagName.substringAfter(':')) == "texture")
                    textures[node.getAttribute("id")] = node.getAttribute("uri")
                if ((node.localName ?: node.tagName.substringAfter(':')) == "wordsearchgametiles") tiles = node
            }
            val control = tiles ?: throw IllegalArgumentException("missing wordsearch control")
            val images = listOf("normal", "selected", "locked").associateWith { state ->
                val uri = textures[control.getAttribute(state)] ?: throw IllegalArgumentException("missing wordsearch texture")
                sdaContent.decodeImage(uri)
            }
            val normal = images.getValue("normal")
            require(images.values.all { it.width == normal.width && it.height == normal.height }) { "wordsearch texture dimensions differ" }
            val env = SdaXml.parse(sdaContent.read("ENVS.MSE") ?: throw IllegalArgumentException("missing bonus environment"))
            val envNodes = env.getElementsByTagName("*")
            val background = (0 until envNodes.length).map { envNodes.item(it) as Element }
                .singleOrNull { it.getAttribute("id") == "wordsearch_$bonusImage" }
                ?: throw IllegalArgumentException("missing wordsearch background")
            val originX = background.getAttribute("x").toIntOrNull() ?: throw IllegalArgumentException("invalid wordsearch origin")
            val originY = background.getAttribute("y").toIntOrNull() ?: throw IllegalArgumentException("invalid wordsearch origin")
            return SdaWordSearchGame(bonusName, dimension(doc, "rows"), dimension(doc, "columns"), seed, words,
                bonusImage, originX, originY, normal.width, normal.height, images, checkpoint)
        } else if (nameUpper.endsWith(".JSW")) {
            return SdaJigsawResources.load(sdaContent, bonusName, seed, bonusImage, checkpoint)
        }
        throw IllegalArgumentException("unsupported bonus format: $bonusName")
    }

    /** Restore the recorded board; reconstruction from its seed is not a checkpoint. */
    fun restore(content: SdaContent, state: Map<String, Any?>, resource: String,
                seed: Long, bonusImage: String): SdaBonusGame {
        require(state["resourceName"] == resource && integer(state["seed"]) == seed) { "bonus identity mismatch" }
        val game = load(content, resource, seed, bonusImage, checkpoint = state)
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
            }
            is SdaJigsawGame -> {
                require(integer(state["totalPieces"]) == game.totalPieces.toLong()) { "jigsaw definition mismatch" }
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

package org.rigorcore.caserecomp.sda

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

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

    override val isSolved: Boolean
        get() = tileRotations.all { it == 0 }

    override fun clickPixel(x: Int, y: Int): Boolean {
        if (isSolved) return false
        val boardX = 172
        val boardY = 95
        val boardW = 612
        val boardH = 408
        if (x !in boardX until (boardX + boardW) || y !in boardY until (boardY + boardH)) {
            return false
        }
        val col = ((x - boardX) * cols) / boardW
        val row = ((y - boardY) * rows) / boardH
        if (row in 0 until rows && col in 0 until cols) {
            val idx = row * cols + col
            tileRotations[idx] = (tileRotations[idx] + 1) % 4
            return true
        }
        return false
    }

    override fun solve() {
        for (i in tileRotations.indices) {
            tileRotations[i] = 0
        }
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

    init {
        if (tiles == null) {
            val rng = SdaRng(seed and 0xFFFFFFFFL)
            // Shuffle tiles
            for (i in tilePositions.size - 1 downTo 1) {
                val j = rng.next() % (i + 1)
                val tmp = tilePositions[i]
                tilePositions[i] = tilePositions[j]
                tilePositions[j] = tmp
            }
            // Ensure not accidentally already solved
            if (tilePositions.indices.all { tilePositions[it] == it }) {
                val tmp = tilePositions[0]
                tilePositions[0] = tilePositions[1]
                tilePositions[1] = tmp
            }
        }
    }

    override val isSolved: Boolean
        get() = tilePositions.indices.all { tilePositions[it] == it }

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
                return true
            }
        }
        return false
    }

    override fun solve() {
        for (i in tilePositions.indices) {
            tilePositions[i] = i
        }
        selectedIndex = null
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
        "tiles" to tilePositions.map { it.toLong() },
        "selectedIndex" to selectedIndex,
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
        val raw = sdaContent.read(bonusName)

        if (nameUpper.endsWith(".TRG")) {
            var rows = 4
            var cols = 6
            if (raw != null) {
                val doc = parseBonusXml(raw)
                rows = doc["rows"]?.toIntOrNull() ?: 4
                cols = doc["columns"]?.toIntOrNull() ?: 6
            }
            return SdaTileRotGame(bonusName, rows, cols, seed, bonusImage)
        } else if (nameUpper.endsWith(".TGL")) {
            var rows = 6
            var cols = 6
            if (raw != null) {
                val doc = parseBonusXml(raw)
                rows = doc["rows"]?.toIntOrNull() ?: 6
                cols = doc["columns"]?.toIntOrNull() ?: 6
            }
            return SdaTileSwapGame(bonusName, rows, cols, seed, bonusImage)
        } else if (nameUpper.endsWith(".WSG")) {
            val words = mutableListOf("VEGAS", "CASINO", "ROULETTE", "JACKPOT", "SECURITY", "DETECTIVE")
            if (raw != null) {
                val wordsRaw = sdaContent.read("WORDSEARCH.TXT")
                if (wordsRaw != null) {
                    val text = String(wordsRaw, Charsets.UTF_8)
                    for (line in text.lines()) {
                        if ("=" in line) {
                            val listPart = line.substringAfter('=').replace("\"", "").replace("'", "")
                            val parsed = listPart.split(',').map { it.trim().uppercase() }.filter { it.isNotEmpty() }
                            if (parsed.isNotEmpty()) {
                                words.clear()
                                words.addAll(parsed)
                                break
                            }
                        }
                    }
                }
            }
            return SdaWordSearchGame(bonusName, 8, 12, seed, words, bonusImage)
        } else if (nameUpper.endsWith(".JSW")) {
            return SdaJigsawGame(bonusName, 24, seed, bonusImage)
        } else if (nameUpper.contains("RIDDLE") || nameUpper.endsWith(".MSE")) {
            return SdaMasterRiddleGame(1, seed)
        }

        // Default fallback to TileRotGame
        return SdaTileRotGame(bonusName, 4, 6, seed, bonusImage)
    }

    private fun parseBonusXml(raw: ByteArray): Map<String, String> {
        val attrs = mutableMapOf<String, String>()
        try {
            val text = String(raw, Charsets.UTF_8).removePrefix("\uFEFF")
            val factory = DocumentBuilderFactory.newInstance()
            val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)))
            fun walk(node: Node) {
                if (node.nodeType == Node.ELEMENT_NODE) {
                    val elem = node as Element
                    for (i in 0 until elem.attributes.length) {
                        val item = elem.attributes.item(i)
                        attrs[item.nodeName.lowercase()] = item.nodeValue
                    }
                }
                for (i in 0 until node.childNodes.length) {
                    walk(node.childNodes.item(i))
                }
            }
            walk(doc.documentElement)
        } catch (_: Exception) {}
        return attrs
    }
}

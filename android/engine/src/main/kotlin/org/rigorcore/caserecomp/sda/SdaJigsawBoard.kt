package org.rigorcore.caserecomp.sda

/** Coordinates are the piece's recovered target origin; dimensions describe its alpha mask. */
data class SdaJigsawPiece(val id: String, val x: Int, val y: Int, val width: Int, val height: Int)

/** Recovered placement kernel. Tray geometry, hit testing and rotation animation remain separate. */
class SdaJigsawBoard(definitions: List<SdaJigsawPiece>, seed: Long, checkpoint: Map<String, Any?>? = null) {
    val pieces: Map<String, SdaJigsawPiece>
    val trayOrder: List<String>
    private val angles = linkedMapOf<String, Int>()
    val quarterTurns: Map<String, Int> get() = angles.toMap()
    private val retired = linkedSetOf<String>()
    val placed: Set<String> get() = retired.toSet()
    var selected: String? = null
        private set
    var heldLeft: Int? = null
        private set
    var heldTop: Int? = null
        private set
    val finalRngState: Long
    val placementPoints: Int get() = retired.size * 250
    val isSolved: Boolean get() = retired.size == pieces.size

    init {
        require(definitions.size in 1..256 && definitions.map { it.id }.distinct().size == definitions.size &&
            definitions.all { it.id.matches(Regex("[A-Za-z0-9_.-]{1,128}")) &&
                it.width in 11..2048 && it.height in 11..2048 && it.x in -32768..32768 && it.y in -32768..32768 }) {
            "invalid jigsaw definitions"
        }
        pieces = definitions.associateBy { it.id }
        if (checkpoint == null) {
            val rng = SdaRng(seed)
            val order = definitions.map { it.id }.toMutableList()
            // 004365b0 swaps every position with an index drawn across the entire array.
            for (index in order.indices) {
                val other = (rng.nextScaled() * order.size).toInt()
                val item = order[index]; order[index] = order[other]; order[other] = item
            }
            trayOrder = order.toList()
            for (id in order) angles[id] = (rng.nextScaled() * 4).toInt()
            finalRngState = rng.state
        } else {
            require(checkpoint["definitions"] == definitionState()) { "jigsaw definition mismatch" }
            trayOrder = strings(checkpoint["trayOrder"])
            require(trayOrder.size == pieces.size && trayOrder.toSet() == pieces.keys) { "invalid jigsaw order" }
            val raw = checkpoint["quarterTurns"] as? Map<*, *> ?: throw IllegalArgumentException("missing jigsaw angles")
            require(raw.keys == pieces.keys) { "invalid jigsaw angles" }
            for (id in trayOrder) angles[id] = integer(raw[id]).also { require(it in 0..3) { "invalid jigsaw angle" } }
            val savedPlaced = strings(checkpoint["placed"])
            require(savedPlaced.distinct().size == savedPlaced.size && savedPlaced.all { it in pieces && angles[it] == 0 }) {
                "invalid placed jigsaw pieces"
            }
            retired.addAll(savedPlaced)
            selected = checkpoint["selected"]?.let {
                require(it is String && it in pieces && it !in retired) { "invalid selected jigsaw piece" }; it as String
            }
            heldLeft = checkpoint["heldLeft"]?.let(::integer)
            heldTop = checkpoint["heldTop"]?.let(::integer)
            require((heldLeft == null) == (heldTop == null) && (selected != null || heldLeft == null)) { "invalid held jigsaw position" }
            val rng = checkpoint["finalRngState"]
            require(rng is Int || rng is Long) { "jigsaw rng integer required" }
            finalRngState = (rng as Number).toLong()
            require(finalRngState in 0..0xffffffffL) { "invalid jigsaw rng" }
        }
    }
    fun select(id: String): Boolean {
        if (selected != null || id !in pieces || id in retired) return false
        selected = id
        return true
    }
    fun moveHeld(left: Int, top: Int): Boolean {
        if (selected == null) return false
        heldLeft = left; heldTop = top
        return true
    }
    /** Native secondary button subtracts 90 degrees; this models the completed rotation only. */
    fun rotateSelected(): Boolean {
        val id = selected ?: return false
        angles[id] = (angles.getValue(id) + 3) % 4
        return true
    }
    fun drop(left: Int, top: Int): Boolean {
        val id = selected ?: return false
        val piece = pieces.getValue(id)
        val centerX = left.toLong() + piece.width / 2
        val centerY = top.toLong() + piece.height / 2
        // 004382d0: rendered rect adds 4/9 pixels; right/bottom subtract 9/14.
        val valid = angles.getValue(id) == 0 && centerX >= piece.x.toLong() + 5 &&
            centerX < piece.x.toLong() + piece.width - 5 && centerY >= piece.y.toLong() + 5 &&
            centerY < piece.y.toLong() + piece.height - 5
        cancel() // 00436780 clears the held piece both after success and return to tray.
        if (valid) retired.add(id)
        return valid
    }
    fun cancel() { selected = null; heldLeft = null; heldTop = null }
    private fun definitionState() = pieces.values.map {
        listOf(it.id, it.x.toLong(), it.y.toLong(), it.width.toLong(), it.height.toLong())
    }
    fun state(): Map<String, Any?> = mapOf("definitions" to definitionState(), "trayOrder" to trayOrder,
        "quarterTurns" to quarterTurns, "placed" to placed.toList(), "selected" to selected,
        "heldLeft" to heldLeft, "heldTop" to heldTop, "finalRngState" to finalRngState)
    companion object {
        private fun strings(value: Any?): List<String> {
            val list = value as? List<*> ?: throw IllegalArgumentException("jigsaw string list required")
            require(list.size <= 256 && list.all { it is String }) { "invalid jigsaw string list" }
            return list.map { it as String }
        }
        private fun integer(value: Any?): Int {
            require(value is Int || value is Long) { "jigsaw integer required" }
            val number = (value as Number).toLong()
            require(number in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) { "invalid jigsaw integer" }
            return number.toInt()
        }
    }
}

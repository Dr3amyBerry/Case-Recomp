package org.rigorcore.caserecomp.sda

/** Background-local hotspot and native placeorder, supplied by resource bindings. */
data class SdaRiddlePiece(val id: String, val placeOrder: Int, val hotspotX: Int, val hotspotY: Int,
                          val hotspotWidth: Int, val hotspotHeight: Int, val hasTarget: Boolean = true)

/** 0044ddd0/0044e760: placement kernel after pick/return animations complete. No score or scroll guessing. */
class SdaRiddleBoard(definitions: List<SdaRiddlePiece>, val required: Int,
                     initialOrder: List<String> = definitions.map { it.id }, checkpoint: Map<String,Any?>? = null) {
    val pieces = definitions.associateBy { it.id }
    private val initial = initialOrder.toList()
    private val tray = initial.toMutableList()
    private val completed = mutableListOf<String>()
    val available: List<String> get() = tray.toList()
    val placed: List<String> get() = completed.toList()
    val currentOrder: Int get() = completed.size
    val isSolved: Boolean get() = currentOrder >= required
    var selected: String? = null
        private set
    private var returnIndex: Int? = null
    init {
        require(definitions.size in 1..256 && pieces.size == definitions.size && required in 1..definitions.size &&
            definitions.all { it.id.matches(Regex("[A-Za-z0-9_.-]{1,128}")) && it.placeOrder in -1..255 &&
                it.hotspotX in -32768..32768 && it.hotspotY in -32768..32768 &&
                (if(it.hasTarget) it.hotspotWidth in 1..4096 && it.hotspotHeight in 1..4096
                 else it.hotspotWidth == 0 && it.hotspotHeight == 0) } &&
            initial.size == pieces.size && initial.toSet() == pieces.keys && definitions.count { it.hasTarget } >= required) { "invalid riddle definition" }
        if (checkpoint != null) {
            require(checkpoint["definitions"] == definitionState() && checkpoint["required"] == required.toLong() &&
                checkpoint["initialOrder"] == initial) { "riddle definition mismatch" }
            val savedTray = strings(checkpoint["available"])
            val savedPlaced = strings(checkpoint["placed"])
            require(savedPlaced.size <= required && savedPlaced.indices.all { i ->
                pieces[savedPlaced[i]]?.let { it.hasTarget && (it.placeOrder == i || it.placeOrder == -1) } == true
            }) { "invalid riddle placement history" }
            selected = checkpoint["selected"]?.let {
                require(it is String && it in pieces) { "invalid selected riddle piece" }; it as String
            }
            returnIndex = checkpoint["returnIndex"]?.let { raw ->
                require(raw is Int || raw is Long) { "riddle return index integer required" }
                val value = (raw as Number).toLong()
                require(value in 0..savedTray.size.toLong()) { "invalid riddle return index" }; value.toInt()
            }
            val accounted = savedTray + savedPlaced + listOfNotNull(selected)
            require(accounted.distinct().size == accounted.size && accounted.toSet() == pieces.keys &&
                (selected != null) == (returnIndex != null) && (savedPlaced.size < required || selected == null)) {
                "inconsistent riddle checkpoint"
            }
            tray.clear(); tray.addAll(savedTray); completed.addAll(savedPlaced)
        }
    }
    fun select(id: String): Boolean {
        if (isSolved || selected != null) return false
        val index = tray.indexOf(id)
        if(index < 0) return false
        selected = id; returnIndex = index; tray.removeAt(index)
        return true
    }
    /** Native event compares the pointer, relative to background origin, with a half-open hotspot. */
    fun dropScreen(x: Int, y: Int, backgroundX: Int, backgroundY: Int): Boolean {
        val id = selected ?: return false
        val piece = pieces.getValue(id)
        val localX = x.toLong() - backgroundX; val localY = y.toLong() - backgroundY
        val valid = piece.hasTarget && (piece.placeOrder == currentOrder || piece.placeOrder == -1) &&
            localX >= piece.hotspotX && localX < piece.hotspotX.toLong() + piece.hotspotWidth &&
            localY >= piece.hotspotY && localY < piece.hotspotY.toLong() + piece.hotspotHeight
        if(valid) completed.add(id) else tray.add(checkNotNull(returnIndex),id)
        selected = null; returnIndex = null
        return valid
    }
    private fun definitionState() = pieces.values.map { listOf(it.id,it.placeOrder.toLong(),it.hotspotX.toLong(),
        it.hotspotY.toLong(),it.hotspotWidth.toLong(),it.hotspotHeight.toLong(),it.hasTarget) }
    fun state(): Map<String,Any?> = mapOf("definitions" to definitionState(), "required" to required.toLong(),
        "initialOrder" to initial, "available" to available, "placed" to placed,
        "selected" to selected, "returnIndex" to returnIndex)
    companion object {
        private fun strings(value: Any?): List<String> {
            val values = value as? List<*> ?: throw IllegalArgumentException("riddle string list required")
            require(values.size <= 256 && values.all { it is String }) { "invalid riddle list" }
            return values.map { it as String }
        }
    }
}

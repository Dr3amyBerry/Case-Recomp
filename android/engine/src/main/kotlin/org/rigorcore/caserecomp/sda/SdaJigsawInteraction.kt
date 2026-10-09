package org.rigorcore.caserecomp.sda

data class SdaJigsawTrayDefinition(val x: Int, val y: Int, val width: Int, val height: Int,
                                   val visibleCount: Int, val padding: Int)

/** Native pick/drop orchestration; transformed pixels and completed rotations are supplied separately. */
class SdaJigsawInteraction(definitions: List<SdaJigsawPiece>, seed: Long,
                           geometry: SdaJigsawTrayDefinition, checkpoint: Map<String, Any?>? = null) {
    val board = SdaJigsawBoard(definitions, seed, section(checkpoint, "board"))
    val tray = SdaJigsawTray(board.trayOrder, geometry.x, geometry.y, geometry.width, geometry.height,
        geometry.visibleCount, geometry.padding, section(checkpoint, "tray"))
    private var returnIndex: Int? = null
    init {
        if (checkpoint != null) {
            returnIndex = checkpoint["returnIndex"]?.let {
                require(it is Int || it is Long) { "jigsaw return index integer required" }
                val index = (it as Number).toLong()
                require(index in 0..tray.order.size.toLong()) { "invalid jigsaw return index" }
                index.toInt()
            }
        }
        require((board.selected != null) == (returnIndex != null)) { "missing jigsaw return index" }
        val accounted = tray.order + board.placed + listOfNotNull(board.selected)
        require(accounted.distinct().size == accounted.size && accounted.toSet() == board.pieces.keys) {
            "inconsistent jigsaw tray and board"
        }
    }
    fun pickPixel(x: Int, y: Int, sizes: Map<String, SdaJigsawTraySize>, images: Map<String, SdaPixelSource>): Boolean {
        if (board.selected != null) return false
        val id = tray.hitTest(x, y, sizes, board.pieces.keys.toList(), images) ?: return false
        check(board.select(id))
        returnIndex = checkNotNull(tray.take(id))
        return true
    }
    fun moveHeld(left: Int, top: Int): Boolean = board.moveHeld(left, top)
    fun rotateHeld(): Boolean = board.rotateSelected()
    fun scroll(direction: Int): Boolean = board.selected == null && tray.scroll(direction)
    fun dropHeld(left: Int, top: Int): Boolean {
        val id = board.selected ?: return false
        val index = checkNotNull(returnIndex)
        val placed = board.drop(left, top)
        if (!placed) check(tray.returnPiece(id, index))
        returnIndex = null
        return placed
    }
    fun state(): Map<String, Any?> = mapOf("board" to board.state(), "tray" to tray.state(), "returnIndex" to returnIndex)
    companion object {
        private fun section(checkpoint: Map<String, Any?>?, key: String): Map<String, Any?>? {
            if (checkpoint == null) return null
            val value = checkpoint[key] as? Map<*, *> ?: throw IllegalArgumentException("missing jigsaw $key")
            require(value.keys.all { it is String }) { "invalid jigsaw $key keys" }
            @Suppress("UNCHECKED_CAST")
            return value as Map<String, Any?>
        }
    }
}

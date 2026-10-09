package org.rigorcore.caserecomp.sda

data class SdaRiddleTrayDefinition(val x: Int, val y: Int, val width: Int, val height: Int, val shown: Int = 4)
data class SdaRiddleCell(val id: String, val x: Int, val y: Int, val width: Int, val height: Int) {
    fun contains(px: Int,py: Int) = px.toLong() >= x && py.toLong() >= y &&
        px.toLong() < x.toLong()+width && py.toLong() < y.toLong()+height
}

/** Native first-riddle tray/pick/return at completed-animation boundaries; rendering/timers are separate. */
class SdaRiddleInteraction(definitions: List<SdaRiddlePiece>, required: Int, seed: Long,
                           val geometry: SdaRiddleTrayDefinition, checkpoint: Map<String,Any?>? = null) {
    val board: SdaRiddleBoard
    val finalRngState: Long
    var firstVisible = 0
        private set
    init {
        require(geometry.x in -32768..32768 && geometry.y in -32768..32768 && geometry.width in 1..4096 &&
            geometry.height in 4..4096 && geometry.shown == 4) { "unsupported riddle tray geometry" }
        require(definitions.size in 1..256)
        if(checkpoint == null) {
            val order=definitions.map { it.id }.toMutableList()
            val rng=SdaRng(seed)
            // 0044c863: same full-array range for every swap; no rejection or quarter draws.
            for(index in order.indices) {
                val other=(rng.nextScaled()*order.size).toInt()
                val value=order[index];order[index]=order[other];order[other]=value
            }
            finalRngState=rng.state
            board=SdaRiddleBoard(definitions,required,order)
        } else {
            require(checkpoint["geometry"] == geometryState()) { "riddle tray geometry mismatch" }
            val nested=checkpoint["board"] as? Map<*,*> ?: throw IllegalArgumentException("missing riddle board")
            require(nested.keys.all { it is String })
            val order=nested["initialOrder"] as? List<*> ?: throw IllegalArgumentException("missing riddle initial order")
            require(order.all { it is String })
            @Suppress("UNCHECKED_CAST")
            board=SdaRiddleBoard(definitions,required,order.map { it as String },nested as Map<String,Any?>)
            finalRngState=integer(checkpoint["finalRngState"])
            require(finalRngState in 0..0xffffffffL) { "invalid riddle rng" }
            val offset=integer(checkpoint["firstVisible"])
            require(offset in 0..maxOf(0,board.available.size-4).toLong()) { "invalid riddle viewport" }
            firstVisible=offset.toInt()
        }
    }
    private fun clamp() { firstVisible=firstVisible.coerceIn(0,maxOf(0,board.available.size-4)) }
    fun cells(): List<SdaRiddleCell> {
        val slotHeight=geometry.height/geometry.shown
        return board.available.drop(firstVisible).take(geometry.shown).mapIndexed { slot,id ->
            SdaRiddleCell(id,geometry.x,geometry.y+slot*slotHeight,geometry.width,slotHeight)
        }
    }
    /** 0044eb00 centers actual transformed image sizes in each slot; caller supplies raster dimensions. */
    fun imageRect(id: String,width: Int,height: Int): SdaRiddleCell {
        require(width in 1..4096 && height in 1..4096)
        val cell=cells().singleOrNull { it.id==id } ?: throw IllegalArgumentException("riddle item not visible")
        return SdaRiddleCell(id,cell.x+(cell.width-width)/2,cell.y+(cell.height-height)/2,width,height)
    }
    fun scroll(direction: Int): Boolean {
        require(direction == -1 || direction == 1)
        if(board.selected != null || board.isSolved) return false
        val before=firstVisible
        firstVisible=(firstVisible+direction).coerceIn(0,maxOf(0,board.available.size-4))
        return before != firstVisible
    }
    fun pickPixel(x: Int,y: Int): Boolean {
        if(board.selected != null || board.isSolved) return false
        val cell=cells().firstOrNull { it.contains(x,y) } ?: return false
        val picked=board.select(cell.id)
        clamp()
        return picked
    }
    fun dropScreen(x: Int,y: Int,backgroundX: Int,backgroundY: Int): Boolean {
        val valid=board.dropScreen(x,y,backgroundX,backgroundY)
        clamp()
        return valid
    }
    private fun geometryState()=listOf(geometry.x,geometry.y,geometry.width,geometry.height,geometry.shown).map { it.toLong() }
    fun state(): Map<String,Any?> = mapOf("geometry" to geometryState(),"board" to board.state(),
        "firstVisible" to firstVisible.toLong(),"finalRngState" to finalRngState)
    companion object {
        private fun integer(raw: Any?): Long {
            require(raw is Int || raw is Long) { "riddle integer required" }
            return (raw as Number).toLong()
        }
    }
}

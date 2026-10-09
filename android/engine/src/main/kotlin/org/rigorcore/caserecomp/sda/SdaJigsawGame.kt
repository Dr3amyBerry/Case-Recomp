package org.rigorcore.caserecomp.sda

/** Authentic pick/rotate/drop kernel with an explicit prototype raster adapter. */
class SdaJigsawGame(override val resourceName: String, definitions: List<SdaJigsawPiece>,
                    val pieceImages: Map<String, SdaArgbPixelSource>, geometry: SdaJigsawTrayDefinition,
                    val seed: Long = 0, val bonusImage: String = "", checkpoint: Map<String, Any?>? = null,
                    val originX: Int = 172, val originY: Int = 96,
                    val referenceImage: SdaPixelSource? = null,
                    val arrowUp: SdaJigsawTrayRect? = null, val arrowDown: SdaJigsawTrayRect? = null,
                    val arrowImages: Map<String, SdaPixelSource> = emptyMap()) : SdaBonusGame {
    override val kind = "jigsaw"
    override val rows = 1
    override val cols = definitions.size
    val totalPieces = definitions.size
    val interaction: SdaJigsawInteraction
    private val transformed = mutableMapOf<Triple<String, Int, Boolean>, SdaArgbPixelSource>()
    private var skipped = false
    override var points = SDA_BONUS_REWARD
    val placementPoints: Int get() = interaction.board.placementPoints
    override val isSolved: Boolean get() = skipped || interaction.board.isSolved
    init {
        require(pieceImages.keys == definitions.map { it.id }.toSet()) { "missing jigsaw rasters" }
        require(definitions.all { pieceImages.getValue(it.id).let { image -> image.width == it.width && image.height == it.height } })
        @Suppress("UNCHECKED_CAST")
        val saved = checkpoint?.let { it["interaction"] as? Map<String, Any?> ?: throw IllegalArgumentException("missing genuine jigsaw checkpoint") }
        interaction = SdaJigsawInteraction(definitions, seed, geometry, saved)
        if (checkpoint != null) {
            require(checkpoint["origin"] == listOf(originX.toLong(), originY.toLong())) { "jigsaw origin mismatch" }
            val reward = checkpoint["points"]
            require(reward is Int || reward is Long) { "jigsaw reward integer required" }
            val value = (reward as Number).toLong()
            require(value == 0L || value == SDA_BONUS_REWARD.toLong()) { "invalid jigsaw reward" }
            points = value.toInt()
            skipped = checkpoint["skipped"] as? Boolean ?: throw IllegalArgumentException("missing jigsaw skip state")
            require(!skipped || checkpoint["points"] == 0L || checkpoint["points"] == 0) { "invalid skipped reward" }
        }
    }
    fun image(id: String, inTray: Boolean): SdaArgbPixelSource {
        val angle = interaction.board.quarterTurns.getValue(id)
        return transformed.getOrPut(Triple(id, angle, inTray)) {
            SdaJigsawRaster.transform(pieceImages.getValue(id), angle, if (inTray) .45f else 1f)
        }
    }
    private fun sizes() = interaction.tray.order.associateWith { id -> image(id, true).let {
        SdaJigsawTraySize(it.width, it.height, it.width + 4, it.height + 9)
    } }
    fun trayRectangles(): List<SdaJigsawTrayRect> = interaction.tray.rectangles(sizes())
    fun movePixel(x: Int, y: Int): Boolean {
        val id = interaction.board.selected ?: return false
        val raster = image(id, false)
        return interaction.moveHeld(x - raster.width / 2, y - raster.height / 2)
    }
    fun rotateHeld(): Boolean = !isSolved && interaction.rotateHeld()
    override fun clickPixel(x: Int, y: Int): Boolean {
        if (isSolved) return false
        if (interaction.board.selected != null) {
            movePixel(x, y)
            return interaction.dropHeld(interaction.board.heldLeft!!, interaction.board.heldTop!!)
        }
        if (arrowUp?.contains(x,y) == true) return interaction.scroll(-1)
        if (arrowDown?.contains(x,y) == true) return interaction.scroll(1)
        val picked = interaction.pickPixel(x, y, sizes(), interaction.tray.order.associateWith { image(it, true) })
        if (picked) movePixel(x, y)
        return picked
    }
    override fun solve() { skipped = true; points = 0 } // Skip does not manufacture placed pieces.
    override fun state(): Map<String, Any?> = mapOf("kind" to kind, "resourceName" to resourceName,
        "rows" to rows, "cols" to cols, "totalPieces" to totalPieces, "seed" to seed,
        "bonusImage" to bonusImage, "points" to points, "origin" to listOf(originX.toLong(),originY.toLong()),
        "skipped" to skipped, "interaction" to interaction.state())
}

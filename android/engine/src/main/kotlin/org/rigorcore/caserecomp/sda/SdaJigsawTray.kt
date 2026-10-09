package org.rigorcore.caserecomp.sda

/** Layout uses transformed image dimensions; hit testing uses the actual component rectangle. */
data class SdaJigsawTraySize(val imageWidth: Int, val imageHeight: Int, val hitWidth: Int, val hitHeight: Int)
data class SdaJigsawTrayRect(val id: String, val x: Int, val y: Int, val width: Int, val height: Int) {
    fun contains(px: Int, py: Int): Boolean = px.toLong() >= x && py.toLong() >= y &&
        px.toLong() < x.toLong() + width && py.toLong() < y.toLong() + height
}

/** 00439320/00439580 layout, 00439230 removal, 00439090 reinsertion. No image-size guessing. */
class SdaJigsawTray(ids: List<String>, val x: Int, val y: Int, val width: Int, val height: Int,
                    val visibleCount: Int, val padding: Int, checkpoint: Map<String, Any?>? = null) {
    private val identities = ids.toList()
    private val items = ids.toMutableList()
    val order: List<String> get() = items.toList()
    var firstVisible = 0
        private set
    init {
        require(ids.size in 1..256 && ids.distinct().size == ids.size && ids.all { it.isNotEmpty() } &&
            x in -32768..32768 && y in -32768..32768 && width in 1..4096 && height in 1..4096 &&
            visibleCount in 1..256 && visibleCount <= height && padding in 0..height / 2) { "invalid jigsaw tray" }
        if (checkpoint != null) {
            require(checkpoint["identities"] == identities && checkpoint["geometry"] == geometry()) { "jigsaw tray definition mismatch" }
            val saved = checkpoint["order"] as? List<*> ?: throw IllegalArgumentException("missing jigsaw tray order")
            require(saved.size <= ids.size && saved.distinct().size == saved.size && saved.all { it is String && it in ids }) {
                "invalid jigsaw tray order"
            }
            items.clear(); items.addAll(saved.map { it as String })
            val raw = checkpoint["firstVisible"]
            require(raw is Int || raw is Long) { "jigsaw viewport integer required" }
            val value = (raw as Number).toLong()
            require(value in 0..maxOf(0, items.size - visibleCount).toLong()) { "invalid jigsaw viewport" }
            firstVisible = value.toInt()
        }
    }
    private fun clamp() { firstVisible = firstVisible.coerceIn(0, maxOf(0, items.size - visibleCount)) }
    fun scroll(direction: Int): Boolean {
        require(direction == -1 || direction == 1) { "invalid jigsaw scroll direction" }
        val before = firstVisible
        firstVisible = (firstVisible + direction).coerceIn(0, maxOf(0, items.size - visibleCount))
        return firstVisible != before
    }
    fun take(id: String): Int? {
        val index = items.indexOf(id)
        if (index < 0) return null
        items.removeAt(index); clamp()
        return index
    }
    fun returnPiece(id: String, index: Int): Boolean {
        if (id !in identities || id in items || index !in 0..items.size) return false
        items.add(index, id)
        if (index == items.lastIndex) firstVisible++
        clamp()
        return true
    }
    fun rectangles(sizes: Map<String, SdaJigsawTraySize>): List<SdaJigsawTrayRect> {
        val spacing = height / visibleCount // native integer division, then stored as float.
        return items.drop(firstVisible).take(visibleCount).mapIndexed { slot, id ->
            val size = sizes[id] ?: throw IllegalArgumentException("missing transformed jigsaw dimensions")
            require(listOf(size.imageWidth, size.imageHeight, size.hitWidth, size.hitHeight).all { it in 1..4096 }) {
                "invalid transformed jigsaw dimensions"
            }
            val left = x + (width - size.imageWidth) / 2
            val top = (y + padding + slot * spacing + (spacing - size.imageHeight) * .5).toInt()
            SdaJigsawTrayRect(id, left, top, size.hitWidth, size.hitHeight)
        }
    }
    /** Native manager scans component order backwards; caller supplies that order separately from tray order. */
    fun hitTest(px: Int, py: Int, sizes: Map<String, SdaJigsawTraySize>, componentOrder: List<String>, images: Map<String, SdaPixelSource>): String? {
        require(componentOrder.distinct().size == componentOrder.size && componentOrder.all { it in identities }) {
            "invalid jigsaw component order"
        }
        val rects = rectangles(sizes).associateBy { it.id }
        return componentOrder.asReversed().firstOrNull { id ->
            val rect = rects[id]
            if (rect == null || !rect.contains(px, py)) false else {
                val image = images[id] ?: throw IllegalArgumentException("missing transformed jigsaw pixels")
                val size = sizes.getValue(id)
                require(image.width == size.imageWidth && image.height == size.imageHeight) { "jigsaw pixel dimensions mismatch" }
                val localX = px - rect.x; val localY = py - rect.y
                localX in 0 until image.width && localY in 0 until image.height && image.getAlpha(localX, localY) > 0
            }
        }
    }
    private fun geometry() = listOf(x, y, width, height, visibleCount, padding).map { it.toLong() }
    fun state(): Map<String, Any?> = mapOf("identities" to identities, "geometry" to geometry(),
        "order" to order, "firstVisible" to firstVisible)
}

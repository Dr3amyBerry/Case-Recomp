package org.rigorcore.caserecomp.app

/** Pure, testable aspect-fit transform. Touches outside the stage are rejected. */
internal class DirectorViewport(private val stageWidth: Int, private val stageHeight: Int) {
    init { require(stageWidth > 0 && stageHeight > 0) }
    data class Area(val left: Float, val top: Float, val width: Float, val height: Float)

    fun fit(viewWidth: Int, viewHeight: Int): Area {
        if (viewWidth <= 0 || viewHeight <= 0) return Area(0f, 0f, 0f, 0f)
        val scale = minOf(viewWidth.toFloat() / stageWidth, viewHeight.toFloat() / stageHeight)
        val width = stageWidth * scale
        val height = stageHeight * scale
        return Area((viewWidth - width) / 2, (viewHeight - height) / 2, width, height)
    }

    fun stagePoint(x: Float, y: Float, viewWidth: Int, viewHeight: Int): Pair<Int, Int>? {
        val area = fit(viewWidth, viewHeight)
        if (area.width <= 0 || area.height <= 0 || x < area.left || x >= area.left + area.width ||
            y < area.top || y >= area.top + area.height) return null
        val sx = ((x - area.left) * stageWidth / area.width).toInt().coerceIn(0, stageWidth - 1)
        val sy = ((y - area.top) * stageHeight / area.height).toInt().coerceIn(0, stageHeight - 1)
        return sx to sy
    }
}

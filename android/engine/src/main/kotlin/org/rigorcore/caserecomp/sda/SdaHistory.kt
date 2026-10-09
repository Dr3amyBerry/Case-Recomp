package org.rigorcore.caserecomp.sda

data class SdaRect(val left: Int, val top: Int, val width: Int, val height: Int)

fun sdaStrictPointInside(rect: SdaRect, x: Int, y: Int): Boolean =
    x > rect.left && x < rect.left + rect.width && y > rect.top && y < rect.top + rect.height

data class HistoryMark(val text: String, val x: Int, val y: Int) {
    fun splitVariant(): Pair<String, Int> {
        val start = text.indexOf('[')
        if (start > 1) {
            val bracketPart = text.substring(start)
            val match = Regex("""\[\s*([+-]?[0-9]+)""").find(bracketPart)
            val variant = match?.groupValues?.get(1)?.toIntOrNull() ?: -1
            return text.substring(0, start - 1) to variant
        }
        return text to -1
    }

    companion object {
        fun create(caption: String, scene: String, variant: Int, x: Int, y: Int): HistoryMark =
            HistoryMark("$caption ($scene) [$variant]", x, y)
    }
}

data class HistoryReplay(
    val candidates: List<List<String>>,
    val retired: Set<String>,
    val hidden: Set<String>,
    val removedSets: List<List<String>>,
)

/**
 * 0x00423680: Replays history marks against scene object rectangles and target sets.
 */
fun replayHistory(
    sets: List<List<String>>,
    variants: Map<List<String>, List<String>>,
    rectangles: Map<String, SdaRect>,
    marks: List<HistoryMark>,
    scene: String,
    currentVariant: Int,
): HistoryReplay {
    val pool = sets.toMutableList()
    val retired = mutableSetOf<String>()
    val hidden = mutableSetOf<String>()
    val removedSets = mutableListOf<List<String>>()

    var index = 0
    while (index < pool.size) {
        val ids = pool[index]
        var removedSet = false
        for (mark in marks) {
            val count = ids.count { it in retired }
            val remaining = ids.size - count
            val captionList = variants[ids] ?: emptyList()
            val caption = if (count < captionList.size) captionList[count] else ""
            val (base, variant) = mark.splitVariant()
            if (base != "$caption ($scene)") {
                continue
            }
            if (currentVariant != variant) {
                if (currentVariant != 0 && variant != 0) {
                    removedSet = true
                } else {
                    continue
                }
            } else if (remaining > 1) {
                for (identity in ids) {
                    val rect = rectangles[identity]
                    if (identity !in retired && rect != null && sdaStrictPointInside(rect, mark.x, mark.y)) {
                        retired.add(identity)
                        hidden.add(identity)
                    }
                }
                continue
            } else {
                var chosen = ids[0]
                if (ids.size > 1) {
                    chosen = ids.firstOrNull { identity ->
                        val rect = rectangles[identity]
                        identity !in retired && rect != null && sdaStrictPointInside(rect, mark.x, mark.y)
                    } ?: chosen
                }
                hidden.add(chosen)
                removedSet = true
            }
            if (removedSet) {
                removedSets.add(ids)
                pool.removeAt(index)
                break
            }
        }
        index++
    }
    return HistoryReplay(pool, retired, hidden, removedSets)
}

/**
 * 0x00423000: Clear non-[0] scene history if fewer than ten sets remain fresh.
 */
fun pruneSceneHistory(
    sets: List<List<String>>,
    variants: Map<List<String>, List<String>>,
    marks: List<HistoryMark>,
    scene: String,
): Pair<List<HistoryMark>, Boolean> {
    val scope = " ($scene)"
    val scoped = marks.filter { scope in it.text && "[0]" !in it.text }
    val affected = sets.count { ids ->
        val captions = variants[ids] ?: emptyList()
        captions.any { caption -> scoped.any { mark -> caption in mark.text } }
    }
    val reset = sets.size - affected < 10
    if (!reset) {
        return marks to false
    }
    val pruned = marks.filterNot { scope in it.text && "[0]" !in it.text }
    return pruned to true
}

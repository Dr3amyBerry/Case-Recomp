package org.rigorcore.caserecomp.app

import org.rigorcore.caserecomp.director.*

/** Repair the confirmed empty loc12 item channel in the identified Prime source. */
internal object PrimeSceneRepairs {
    fun apply(movie: DirectorMovie, titleId: String): DirectorMovie {
        if (titleId != DirectorPresentationProfiles.PRIME_ID) return movie
        val frame = movie.labelFrame("loc12") ?: return movie
        val channel = 62 + DirectorMovie.SPRITE_CHANNEL_OFFSET
        if (movie.runAt(channel, frame) != null || movie.spanAt(channel, frame) != null) return movie
        val cast = movie.castLibs.singleOrNull { it.number == 13 && it.name == "12" } ?: return movie
        // Original libraries begin as empty.cst; the location manager loads 12.cct.
        val item = movie.externalCast("${cast.name}.cct")?.members?.get(44) ?: return movie
        if (item.name != "plastic bug" || item.type != "bitmap" || item.width != 38 || item.height != 39) return movie
        if (movie.internalCast.members[13]?.type != "script") return movie

        val runs = linkedSetOf<SpriteRun>()
        val spans = linkedSetOf<SpanData>()
        for (c in 0 until movie.channelCount) for (f in 1..movie.frameCount) {
            movie.runAt(c, f)?.let(runs::add)
            movie.spanAt(c, f)?.let(spans::add)
        }
        // New placement: the original Score contains no position for this item.
        runs.add(SpriteRun(frame, frame, channel, 36, 13, 44, 305, 470,
            item.width, item.height, 0, false, false, stretch = false))
        spans.add(SpanData(frame, frame, channel, listOf(BehaviorRef(1, 13, null))))
        return DirectorMovie(movie.stageWidth, movie.stageHeight, movie.tempo, movie.labels,
            movie.castLibs, movie.internalCast, movie.externalCasts, movie.frameCount,
            movie.channelCount, runs.toList(), spans.toList())
    }
}

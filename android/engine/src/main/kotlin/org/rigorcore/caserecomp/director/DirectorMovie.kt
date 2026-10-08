package org.rigorcore.caserecomp.director

import org.rigorcore.caserecomp.MiniJson
import org.rigorcore.caserecomp.lingo.LingoError

/** Cast member description from the movie bundle (no media; media is resolved by [DirectorMedia]). */
class MemberData(
    val number: Int,
    val name: String,
    val type: String,
    val xtra: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val regX: Int = 0,
    val regY: Int = 0,
    val text: String? = null,
    val scriptNumber: Int? = null,
    val media: Map<String, Int> = emptyMap(),
    val shape: String? = null,
    val foreColor: Int = 255,
    val backColor: Int = 0,
    val filled: Boolean = true,
)

/** Members of one cast file (the movie's internal cast or an external .cct/.cxt). */
class CastFile(val file: String, val members: Map<Int, MemberData>)

class CastLibData(val number: Int, val name: String, val filePath: String)

/** Score state of one channel over a run of frames. */
class SpriteRun(
    val start: Int, val end: Int, val channel: Int,
    val ink: Int, val castLib: Int, val member: Int,
    val x: Int, val y: Int, val width: Int, val height: Int,
    val blend: Int, val flipH: Boolean, val flipV: Boolean,
)

class BehaviorRef(val castLib: Int, val member: Int, val parameters: String?)

/** One Director sprite span: begin/endSprite boundaries and its behaviours. */
class SpanData(val start: Int, val end: Int, val channel: Int, val behaviors: List<BehaviorRef>)

/**
 * The `case-recomp-movie-bundle` produced by `python -m caserecomp movie-bundle`.
 * Score channels are numbered as stored: channel 0 is the frame script and sprite
 * `n` lives in channel `n + SPRITE_CHANNEL_OFFSET`.
 */
class DirectorMovie(
    val stageWidth: Int,
    val stageHeight: Int,
    val tempo: Int,
    val labels: List<Pair<Int, String>>,
    val castLibs: List<CastLibData>,
    val internalCast: CastFile,
    val externalCasts: Map<String, CastFile>,
    val frameCount: Int,
    val channelCount: Int,
    sprites: List<SpriteRun>,
    spans: List<SpanData>,
) {
    private val runsByChannel = sprites.groupBy { it.channel }.mapValues { (_, v) -> v.sortedBy { it.start } }
    private val spansByChannel = spans.groupBy { it.channel }.mapValues { (_, v) -> v.sortedBy { it.start } }
    private val labelFrames = labels.associate { (frame, name) -> name.lowercase() to frame }
    private val labelsByFrame = labels.associate { (frame, name) -> frame to name }

    val spriteCount: Int get() = maxOf(0, channelCount - SPRITE_CHANNEL_OFFSET)

    fun labelFrame(name: String): Int? = labelFrames[name.lowercase()]
    fun labelAt(frame: Int): String? = labelsByFrame[frame]
    fun spanAt(channel: Int, frame: Int): SpanData? = find(spansByChannel[channel], frame, { it.start }, { it.end })
    fun runAt(channel: Int, frame: Int): SpriteRun? = find(runsByChannel[channel], frame, { it.start }, { it.end })

    /** External cast by file name, ignoring directory and extension (`castLib.fileName` may use either). */
    fun externalCast(fileName: String): CastFile? =
        externalCasts[castKey(fileName)]

    private fun <T> find(rows: List<T>?, frame: Int, start: (T) -> Int, end: (T) -> Int): T? {
        rows ?: return null
        var lo = 0; var hi = rows.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val row = rows[mid]
            when {
                frame < start(row) -> hi = mid - 1
                frame > end(row) -> lo = mid + 1
                else -> return row
            }
        }
        return null
    }

    companion object {
        const val SPRITE_CHANNEL_OFFSET = 5

        fun castKey(fileName: String): String =
            fileName.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.').lowercase()

        fun parse(text: String): DirectorMovie {
            val root = MiniJson.parse(text) as? Map<*, *> ?: throw LingoError("movie bundle root must be an object")
            if (root["format"] != "case-recomp-movie-bundle" || root["version"] != 1L) throw LingoError("unsupported movie bundle")
            val stage = root.obj("stage")
            val score = root.obj("score")
            val external = root.list("external_casts").map { row ->
                val cast = row as? Map<*, *> ?: throw LingoError("external cast must be an object")
                val file = cast.str("file")
                CastFile(file, members(cast.list("members")))
            }
            return DirectorMovie(
                stageWidth = stage.int("width"), stageHeight = stage.int("height"), tempo = stage.int("tempo"),
                labels = root.list("labels").map { (it as Map<*, *>).let { l -> l.int("frame") to l.str("name") } },
                castLibs = root.list("cast_libs").map {
                    (it as Map<*, *>).let { c -> CastLibData(c.int("number"), c.str("name"), c["file_path"] as? String ?: "") }
                },
                internalCast = CastFile("", members(root.list("internal_members"))),
                externalCasts = external.associateBy { castKey(it.file) },
                frameCount = score.int("frame_count"),
                channelCount = score.int("channel_count"),
                sprites = score.list("sprites").map {
                    val s = it as Map<*, *>
                    SpriteRun(s.int("start"), s.int("end"), s.int("channel"), s.int("ink"), s.int("cast_lib"),
                        s.int("member"), s.int("x"), s.int("y"), s.int("width"), s.int("height"), s.int("blend"),
                        s["flip_h"] == true, s["flip_v"] == true)
                },
                spans = score.list("spans").map {
                    val s = it as Map<*, *>
                    SpanData(s.int("start"), s.int("end"), s.int("channel"), s.list("behaviors").map { b ->
                        val ref = b as Map<*, *>
                        BehaviorRef(ref.int("cast_lib"), ref.int("member"), ref["parameters"] as? String)
                    })
                },
            )
        }

        private fun members(rows: List<*>): Map<Int, MemberData> = rows.associate { row ->
            val m = row as? Map<*, *> ?: throw LingoError("member must be an object")
            val number = m.int("number")
            number to MemberData(
                number = number,
                name = m["name"] as? String ?: "",
                type = m.str("type"),
                xtra = m["xtra"] as? String,
                width = m.optInt("width"), height = m.optInt("height"),
                regX = m.optInt("reg_x"), regY = m.optInt("reg_y"),
                text = m["text"] as? String,
                scriptNumber = (m["script_number"] as? Long)?.toInt(),
                media = (m["media"] as? Map<*, *>).orEmpty().entries.associate { (k, v) -> k.toString() to (v as Long).toInt() },
                shape = m["shape"] as? String,
                foreColor = m.optInt("fore_color", 255), backColor = m.optInt("back_color"),
                filled = m["filled"] != false,
            )
        }

        private fun Map<*, *>.obj(key: String) = this[key] as? Map<*, *> ?: throw LingoError("movie bundle $key missing")
        private fun Map<*, *>.list(key: String) = this[key] as? List<*> ?: throw LingoError("movie bundle $key missing")
        private fun Map<*, *>.str(key: String) = this[key] as? String ?: throw LingoError("movie bundle $key missing")
        private fun Map<*, *>.int(key: String) = (this[key] as? Long)?.toInt() ?: throw LingoError("movie bundle $key missing")
        private fun Map<*, *>.optInt(key: String, default: Int = 0) = (this[key] as? Long)?.toInt() ?: default
    }
}

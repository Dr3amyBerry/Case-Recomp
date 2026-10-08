package org.rigorcore.caserecomp.director

import org.rigorcore.caserecomp.lingo.LingoError
import org.rigorcore.caserecomp.lingo.LingoValue
import org.rigorcore.caserecomp.lingo.LingoValue.LInt
import org.rigorcore.caserecomp.lingo.LingoValue.LPropList
import org.rigorcore.caserecomp.lingo.LingoValue.LSymbol
import org.rigorcore.caserecomp.lingo.LingoValue.Void
import org.rigorcore.caserecomp.lingo.lingoEquals
import org.rigorcore.caserecomp.lingo.toInt

/** Audio back end. The runtime keeps channel state itself; outputs only play sound. */
interface SoundOutput {
    fun play(channel: Int, member: CastMember, loops: Int) {}
    fun stop(channel: Int) {}
    fun setVolume(channel: Int, volume: Int) {}
    /** Whether the channel is still playing; null when the back end cannot tell (assume finished). */
    fun isPlaying(channel: Int): Boolean? = null

    object Silent : SoundOutput
}

/** `sound(n)`: one of Director's sound channels. */
class SoundChannel(private val output: SoundOutput, val number: Int, private val clock: () -> Long) : LingoValue.LHost {
    override val ilk = "sound"
    var member: CastMember? = null
        private set
    var volume = 255
        private set
    private var loops = 1
    private var playing = false
    private var fade: Pair<Int, Int>? = null  // from and to volume
    private var fadeStart = 0L
    private var fadeDuration = 0L

    val busy: Boolean get() = playing && (output.isPlaying(number) ?: false).also { if (!it) playing = false }

    fun play(member: CastMember, loopCount: Int = 1) {
        this.member = member
        loops = loopCount
        playing = true
        output.play(number, member, loopCount)
    }

    fun stop() { playing = false; output.stop(number) }

    fun setVolume(value: Int) {
        volume = value.coerceIn(0, 255)
        fade = null
        output.setVolume(number, volume)
    }

    /** Advance volume fades; called once per frame. */
    fun update() {
        val f = fade ?: return
        val t = clock() - fadeStart
        val v = if (fadeDuration <= 0 || t >= fadeDuration) f.second
            else f.first + ((f.second - f.first) * t / fadeDuration).toInt()
        volume = v
        output.setVolume(number, v)
        if (t >= fadeDuration) fade = null
    }

    override fun getProp(name: String): LingoValue = when (name.lowercase()) {
        "volume" -> LInt(volume)
        "member" -> member ?: Void
        "status" -> LInt(if (busy) 3 else 0)
        "loopcount" -> LInt(loops)
        "channelcount" -> LInt(2)
        "ilk" -> LSymbol("sound")
        else -> throw LingoError("sound has no property $name")
    }

    override fun setProp(name: String, value: LingoValue) {
        when (name.lowercase()) {
            "volume" -> setVolume(value.toInt())
            "loopcount" -> loops = value.toInt()
            else -> throw LingoError("cannot set sound property $name")
        }
    }

    override fun call(method: String, args: List<LingoValue>): LingoValue? = when (method.lowercase()) {
        "play" -> {
            when (val target = args.firstOrNull()) {
                is CastMember -> play(target)
                is LPropList -> {
                    fun prop(key: String) = target.entries.firstOrNull { lingoEquals(it.first, LSymbol(key)) }?.second
                    val m = prop("member") as? CastMember ?: throw LingoError("sound play list needs #member")
                    play(m, prop("loopCount")?.toInt() ?: 1)
                }
                null -> member?.let { play(it, loops) }
                else -> throw LingoError("sound play needs a member")
            }
            Void
        }
        "stop" -> { stop(); Void }
        "isbusy" -> LingoValue.bool(busy)
        "fadeto" -> {
            fade = Pair(volume, args.getOrElse(0) { LInt(0) }.toInt().coerceIn(0, 255))
            fadeStart = clock(); fadeDuration = args.getOrElse(1) { LInt(1000) }.toInt().toLong()
            Void
        }
        "fadein" -> { fade = Pair(0, volume); fadeStart = clock(); fadeDuration = args.getOrElse(0) { LInt(1000) }.toInt().toLong(); Void }
        "fadeout" -> { fade = Pair(volume, 0); fadeStart = clock(); fadeDuration = args.getOrElse(0) { LInt(1000) }.toInt().toLong(); Void }
        else -> null
    }

    override fun toString() = "(sound $number)"
}

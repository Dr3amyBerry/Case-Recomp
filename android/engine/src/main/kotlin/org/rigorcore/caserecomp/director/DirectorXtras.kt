package org.rigorcore.caserecomp.director

import org.rigorcore.caserecomp.lingo.LingoError
import org.rigorcore.caserecomp.lingo.LingoHost
import org.rigorcore.caserecomp.lingo.LingoValue
import org.rigorcore.caserecomp.lingo.LingoValue.LInt
import org.rigorcore.caserecomp.lingo.LingoValue.LList
import org.rigorcore.caserecomp.lingo.LingoValue.LPropList
import org.rigorcore.caserecomp.lingo.LingoValue.LString
import org.rigorcore.caserecomp.lingo.LingoValue.LSymbol
import org.rigorcore.caserecomp.lingo.LingoValue.Void
import org.rigorcore.caserecomp.lingo.LingoVm
import org.rigorcore.caserecomp.lingo.asText
import org.rigorcore.caserecomp.lingo.toInt

/** Durable string storage standing in for the Windows registry and the title's own files. */
interface DirectorStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
}

class MemoryStore(val values: MutableMap<String, String> = linkedMapOf()) : DirectorStore {
    override fun get(key: String) = values[key]
    override fun put(key: String, value: String) { values[key] = value }
    override fun remove(key: String) { values.remove(key) }
}

/** `xtra("name")`. */
class XtraRef(val name: String) : LingoValue.LHost {
    override val ilk = "xtra"
    override fun getProp(name: String): LingoValue = if (name.equals("name", true)) LString(this.name) else Void
    override fun setProp(name: String, value: LingoValue) = throw LingoError("cannot set xtra property $name")
    override fun call(method: String, args: List<LingoValue>): LingoValue? = null
    override fun toString() = "<Xtra \"$name\">"
}

/**
 * Substitutes for the Xtras a Director 8.5 Windows projector title typically uses:
 * Buddy API registry/file/window calls over a [DirectorStore], FileIO over the same
 * store, a display "enhancer" that reports fixed modes, and network calls that
 * behave as an offline machine. Desktop/window effects are no-ops.
 */
class StandardXtras(
    private val store: DirectorStore,
    private val castFileSize: (String) -> Int = { -1 },
    private val screenWidth: Int = 1024,
    private val screenHeight: Int = 768,
    private val screenDepth: Int = 32,
    private val windowsPaths: Boolean = true,
) : LingoHost {
    private var nextNetId = 1
    private val netUrls = HashMap<Int, String>()

    private fun registryKey(root: LingoValue, key: LingoValue, value: LingoValue) =
        "registry/${root.asText().ifEmpty { "HKEY_CURRENT_USER" }}\\${key.asText()}\\${value.asText()}".lowercase()

    override fun callBuiltin(vm: LingoVm, name: String, args: List<LingoValue>): LingoValue? {
        fun arg(i: Int) = args.getOrElse(i) { Void }
        return when (name.lowercase()) {
            "xtra" -> XtraRef(arg(0).asText())
            "new" -> (arg(0) as? XtraRef)?.let { newInstance(it, args.drop(1)) }
            "baregister" -> LInt(1)
            "bareadregstring" -> store.get(registryKey(arg(3), arg(0), arg(1)))?.let(::LString) ?: arg(2)
            "bawriteregstring" -> { store.put(registryKey(arg(3), arg(0), arg(1)), arg(2).asText()); LInt(1) }
            // REG_MULTI_SZ values are lists of strings; a missing value reads as [default].
            "bareadregmulti" -> LList((store.get(registryKey(arg(3), arg(0), arg(1)))?.split(MULTI_SEPARATOR) ?: listOf(arg(2).asText()))
                .mapTo(mutableListOf()) { LString(it) })
            "bawriteregmulti" -> {
                val items = (arg(2) as? LList)?.items?.map { it.asText() } ?: arg(2).asText().split('\r')
                store.put(registryKey(arg(3), arg(0), arg(1)), items.joinToString(MULTI_SEPARATOR)); LInt(1)
            }
            "badeletereg" -> { store.remove(registryKey(arg(2), arg(0), arg(1))); LInt(1) }
            "bafilesize" -> LInt(castFileSize(arg(0).asText()).takeIf { it >= 0 } ?: store.get(fileKey(arg(0).asText()))?.length ?: -1)
            "bafileexists" -> LingoValue.bool(castFileSize(arg(0).asText()) >= 0 || store.get(fileKey(arg(0).asText())) != null)
            "bacreatefolder", "basetdisplay", "bawindowtofront", "basetwindowstate" -> LInt(1)
            "bascreeninfo" -> when (arg(0).asText().lowercase()) {
                "width" -> LInt(screenWidth); "height" -> LInt(screenHeight); "depth" -> LInt(screenDepth)
                else -> LInt(0)
            }
            "bawinhandle", "baactivewindow" -> LInt(1)
            "bawindowinfo" -> LString("normal")
            "postnettext", "downloadnetthing", "getnettext", "preloadnetthing" ->
                LInt(nextNetId++).also { netUrls[it.value] = arg(0).asText() }
            "netdone" -> LingoValue.TRUE
            "neterror" -> LString("noNetwork")
            "nettextresult" -> LString("")
            "getstreamstatus" -> LPropList(mutableListOf(
                LSymbol("URL") to LString(netUrls[arg(0).toInt()] ?: ""), LSymbol("state") to LString("Error"),
                LSymbol("bytesSoFar") to LInt(0), LSymbol("bytesTotal") to LInt(0), LSymbol("error") to LString("noNetwork"),
            ))
            else -> null
        }
    }

    private fun newInstance(xtra: XtraRef, args: List<LingoValue>): LingoValue = when (xtra.name.lowercase()) {
        "fileio" -> FileIo()
        "enhancer" -> Enhancer()
        else -> throw LingoError("Xtra ${xtra.name} is not available")
    }

    fun fileKey(path: String) = "file/" + path.replace('/', '\\').lowercase()

    private companion object {
        const val MULTI_SEPARATOR = "\u0000"
    }

    /** Paths FileIO cannot open on Windows (Mac-style ':' separators after the drive). */
    private fun badPath(path: String) = windowsPaths && path.indexOf(':', startIndex = 2) >= 0

    private inner class Enhancer : LingoValue.LHost {
        override val ilk = "instance"
        override fun getProp(name: String): LingoValue = Void
        override fun setProp(name: String, value: LingoValue) {}
        override fun call(method: String, args: List<LingoValue>): LingoValue? = when (method.lowercase()) {
            "get_display_modes" -> LList(listOf(640 to 480, 800 to 600, screenWidth to screenHeight).distinct().mapTo(mutableListOf()) { (w, h) ->
                LList(mutableListOf(LInt(w), LInt(h), LInt(screenDepth)))
            })
            else -> Void
        }
    }

    /** FileIO instance: one open file at a time, contents kept in the store. */
    private inner class FileIo : LingoValue.LHost {
        override val ilk = "instance"
        private var path: String? = null
        private var position = 0
        private var status = 0

        override fun getProp(name: String): LingoValue = Void
        override fun setProp(name: String, value: LingoValue) {}

        private fun content(): String? = path?.let { store.get(fileKey(it)) }

        override fun call(method: String, args: List<LingoValue>): LingoValue? {
            fun arg(i: Int) = args.getOrElse(i) { Void }
            return when (method.lowercase()) {
                "openfile" -> {
                    val p = arg(0).asText()
                    status = when {
                        badPath(p) -> -37
                        store.get(fileKey(p)) == null -> -43
                        else -> 0
                    }
                    path = if (status == 0) p else null
                    position = 0
                    Void
                }
                "createfile" -> {
                    val p = arg(0).asText()
                    status = if (badPath(p)) -37 else 0.also { if (store.get(fileKey(p)) == null) store.put(fileKey(p), "") }
                    Void
                }
                "closefile" -> { path = null; Void }
                "status" -> LInt(status)
                "error" -> LString(when (arg(0).toInt()) { 0 -> "OK"; -37 -> "Bad file name"; -43 -> "File not found"; else -> "Unknown error" })
                "readfile" -> content()?.let { val s = it.substring(minOf(position, it.length)); position = it.length; LString(s) } ?: Void
                "readline" -> content()?.let {
                    if (position >= it.length) return@let LString("")
                    val end = it.indexOf('\r', position).let { e -> if (e < 0) it.length else e + 1 }
                    LString(it.substring(position, end)).also { position = end }
                } ?: Void
                "writestring" -> {
                    val p = path ?: run { status = -43; return Void }
                    val old = store.get(fileKey(p)) ?: ""
                    val text = arg(0).asText()
                    store.put(fileKey(p), old.substring(0, minOf(position, old.length)) + text)
                    position += text.length
                    Void
                }
                "getlength" -> LInt(content()?.length ?: 0)
                "getposition" -> LInt(position)
                "setposition" -> { position = arg(0).toInt(); Void }
                "delete" -> { path?.let { store.remove(fileKey(it)) }; path = null; Void }
                "filename" -> LString(path ?: "")
                "setfiltermask", "setfinderinfo" -> Void
                else -> null
            }
        }
    }
}

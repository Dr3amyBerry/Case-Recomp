package org.rigorcore.caserecomp.lingo

import org.rigorcore.caserecomp.MiniJson
import java.util.Base64

enum class ScriptType { BEHAVIOR, MOVIE, PARENT, UNKNOWN }

class LingoHandler(
    val name: String,
    val arguments: List<String>,
    val locals: List<String>,
    val code: ByteArray,
) {
    lateinit var script: LingoScript
        internal set
}

/** One compiled script: its member identity, names, literals and handlers. */
class LingoScript(
    val name: String,
    val memberNumber: Int?,
    val type: ScriptType,
    val properties: List<String>,
    val globals: List<String>,
    val literals: List<LingoValue>,
    val handlers: List<LingoHandler>,
) {
    private val byName = handlers.associateBy { it.name.lowercase() }

    init { handlers.forEach { it.script = this } }

    /** Lingo handler lookup is case-insensitive. */
    fun handler(name: String): LingoHandler? = byName[name.lowercase()]
}

/** The `case-recomp-lingo-bundle` produced by `python -m caserecomp lingo-bundle`. */
class LingoBundle(val names: List<String>, val scripts: List<LingoScript>) {
    private val byMemberName = scripts.filter { it.name.isNotEmpty() }.associateBy { it.name.lowercase() }
    private val byMemberNumber = scripts.filter { it.memberNumber != null }.associateBy { it.memberNumber!! }

    fun scriptNamed(name: String): LingoScript? = byMemberName[name.lowercase()]
    fun scriptNumber(number: Int): LingoScript? = byMemberNumber[number]
    val movieScripts: List<LingoScript> get() = scripts.filter { it.type == ScriptType.MOVIE }

    companion object {
        fun parse(text: String): LingoBundle {
            @Suppress("UNCHECKED_CAST")
            val root = MiniJson.parse(text) as? Map<String, Any?> ?: throw LingoError("bundle root must be an object")
            if (root["format"] != "case-recomp-lingo-bundle" || root["version"] != 1L) throw LingoError("unsupported Lingo bundle")
            val names = (root["names"] as? List<*>)?.map { it as? String ?: throw LingoError("bundle name must be string") }
                ?: throw LingoError("bundle names missing")
            val scripts = (root["scripts"] as? List<*> ?: throw LingoError("bundle scripts missing")).map { item ->
                val row = item as? Map<*, *> ?: throw LingoError("script must be an object")
                val member = row["member"] as? Map<*, *>
                LingoScript(
                    name = member?.get("name") as? String ?: "",
                    memberNumber = (member?.get("number") as? Long)?.toInt(),
                    type = when (member?.get("script_type")) {
                        "behavior" -> ScriptType.BEHAVIOR
                        "movie" -> ScriptType.MOVIE
                        "parent" -> ScriptType.PARENT
                        else -> ScriptType.UNKNOWN
                    },
                    properties = strings(row["properties"]),
                    globals = strings(row["globals"]),
                    literals = (row["literals"] as? List<*>).orEmpty().map(::literal),
                    handlers = (row["handlers"] as? List<*>).orEmpty().map { h ->
                        val handler = h as? Map<*, *> ?: throw LingoError("handler must be an object")
                        LingoHandler(
                            name = handler["name"] as? String ?: throw LingoError("handler name"),
                            arguments = strings(handler["arguments"]),
                            locals = strings(handler["locals"]),
                            code = Base64.getDecoder().decode(handler["bytecode"] as? String ?: throw LingoError("handler bytecode")),
                        )
                    },
                )
            }
            return LingoBundle(names, scripts)
        }

        private fun strings(value: Any?): List<String> =
            (value as? List<*>).orEmpty().map { it as? String ?: throw LingoError("expected string list") }

        private fun literal(value: Any?): LingoValue {
            val row = value as? Map<*, *> ?: throw LingoError("literal must be an object")
            return when (row["type"]) {
                "int" -> LingoValue.LInt((row["value"] as? Long ?: throw LingoError("int literal")).toInt())
                "float" -> LingoValue.LFloat((row["value"] as? Number ?: throw LingoError("float literal")).toDouble())
                "string" -> LingoValue.LString(row["value"] as? String ?: throw LingoError("string literal"))
                else -> throw LingoError("unknown literal type")
            }
        }
    }
}

package org.rigorcore.caserecomp.sda

/**
 * Native SDA string table decoding (0x0046ebf4 / 0x0046ed14).
 * Extracts key-value mappings from STRINGS.TXT and scene string definitions.
 */
object SdaStrings {
    fun parse(raw: ByteArray): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val text = String(raw, Charsets.UTF_8).removePrefix("\uFEFF")
        for (line in text.lineSequence()) {
            val first = line.indexOf('"')
            val last = line.lastIndexOf('"')
            val keyStart = line.indexOf('I')
            val equal = line.indexOf('=')
            if (keyStart in 0 until equal && equal < first && first < last) {
                val key = line.substring(keyStart, equal).trimEnd(' ', '\t', '\r', '\n')
                val value = line.substring(first + 1, last)
                result[key] = value
            }
        }
        return result
    }

    /**
     * 0x00471872: Resolves @KEY references against string table; retains original if lookup fails.
     */
    fun resolve(value: String, strings: Map<String, String>): String {
        return if (value.startsWith("@")) {
            strings[value.substring(1)] ?: value
        } else {
            value
        }
    }
}

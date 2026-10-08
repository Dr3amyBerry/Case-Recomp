package org.rigorcore.caserecomp.director

import org.rigorcore.caserecomp.MiniJson
import org.rigorcore.caserecomp.lingo.LingoBundle
import org.rigorcore.caserecomp.lingo.LingoError
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

/** Platform image decoding: PNG/JPEG bytes to straight-alpha ARGB pixels. */
fun interface ImageDecoder {
    fun decode(bytes: ByteArray): LingoImage?
}

/**
 * Reader for the private `case-recomp-director-content` zip built by
 * `python -m caserecomp director-content`: the movie and Lingo bundles plus member
 * media keyed by cast file and member number. Every entry is checked against the
 * manifest's size and SHA-256 when read.
 */
class DirectorContent private constructor(
    private val zip: ZipFile,
    private val entries: Map<String, Pair<Long, String>>,
    private val decoder: ImageDecoder,
) : DirectorMedia, AutoCloseable {
    val movie: DirectorMovie = DirectorMovie.parse(String(read("movie.json") ?: throw LingoError("content has no movie.json"), Charsets.UTF_8))
    val lingo: LingoBundle = LingoBundle.parse(String(read("lingo.json") ?: throw LingoError("content has no lingo.json"), Charsets.UTF_8))

    /** Verified bytes of a manifest entry, or null when the package does not list it. */
    fun read(path: String): ByteArray? {
        val (size, digest) = entries[path] ?: return null
        val entry = zip.getEntry(path) ?: throw LingoError("content entry missing: $path")
        if (entry.size != size || size > MAX_ENTRY_BYTES) throw LingoError("content entry size mismatch: $path")
        val data = zip.getInputStream(entry).use { it.readBytes() }
        if (data.size.toLong() != size || sha256(data) != digest) throw LingoError("content entry hash mismatch: $path")
        return data
    }

    private fun mediaPath(castFile: String, member: MemberData, extension: String) =
        "media/${DirectorMovie.castKey(castFile).ifEmpty { "internal" }}/${member.number}.$extension"

    override fun image(castFile: String, member: MemberData): LingoImage? =
        read(mediaPath(castFile, member, "png"))?.let(decoder::decode)

    override fun flash(castFile: String, member: MemberData): ByteArray? = read(mediaPath(castFile, member, "swf"))

    /** Sound data and its container type ("wav" or "mp3"). */
    fun sound(castFile: String, member: MemberData): Pair<ByteArray, String>? =
        listOf("wav", "mp3").firstNotNullOfOrNull { ext -> read(mediaPath(castFile, member, ext))?.let { it to ext } }

    override fun close() = zip.close()

    companion object {
        const val FORMAT = "case-recomp-director-content"
        private const val MAX_ENTRY_BYTES = 256L * 1024 * 1024

        fun open(file: File, decoder: ImageDecoder): DirectorContent {
            val zip = ZipFile(file)
            try {
                val manifestEntry = zip.getEntry("manifest.json") ?: throw LingoError("content has no manifest")
                if (manifestEntry.size > 16L * 1024 * 1024) throw LingoError("content manifest too large")
                val manifest = MiniJson.parse(zip.getInputStream(manifestEntry).use { String(it.readBytes(), Charsets.UTF_8) }) as? Map<*, *>
                    ?: throw LingoError("content manifest must be an object")
                if (manifest["format"] != FORMAT || manifest["version"] != 1L) throw LingoError("unsupported content package")
                val entries = (manifest["entries"] as? List<*> ?: throw LingoError("content manifest has no entries")).associate { row ->
                    val e = row as? Map<*, *> ?: throw LingoError("content entry must be an object")
                    val path = e["path"] as? String ?: throw LingoError("content entry path")
                    if (path.startsWith("/") || ".." in path.split('/') || '\\' in path) throw LingoError("unsafe content path: $path")
                    path to ((e["bytes"] as? Long ?: throw LingoError("content entry size")) to
                        (e["sha256"] as? String ?: throw LingoError("content entry hash")))
                }
                return DirectorContent(zip, entries, decoder)
            } catch (e: Throwable) {
                zip.close()
                throw e
            }
        }

        private fun sha256(data: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }
    }
}

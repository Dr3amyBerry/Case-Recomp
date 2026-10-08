package org.rigorcore.caserecomp.director

import org.rigorcore.caserecomp.MiniJson
import org.rigorcore.caserecomp.lingo.LingoBundle
import org.rigorcore.caserecomp.lingo.LingoError
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
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
        val data = zip.getInputStream(entry).use { input ->
            val result = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                total += n
                if (total > size || total > MAX_ENTRY_BYTES) throw LingoError("content entry exceeds declared size: $path")
                result.write(buffer, 0, n)
            }
            result.toByteArray()
        }
        if (data.size.toLong() != size || sha256(data) != digest) throw LingoError("content entry hash mismatch: $path")
        return data
    }

    /** Streaming verification of *every* entry, including rarely used casts, before importing private content. */
    fun verifyAll(requireSourceBinding: Boolean = false) {
        val buffer = ByteArray(64 * 1024)
        for ((path, record) in entries) {
            val (expectedSize, expectedHash) = record
            val entry = zip.getEntry(path) ?: throw LingoError("content entry missing: $path")
            if (entry.size != expectedSize) throw LingoError("content entry size mismatch: $path")
            var total = 0L
            val md = MessageDigest.getInstance("SHA-256")
            zip.getInputStream(entry).use { input ->
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > expectedSize) throw LingoError("content entry exceeds declared size: $path")
                    md.update(buffer, 0, count)
                }
            }
            if (total != expectedSize || md.digest().toHex() != expectedHash) throw LingoError("content entry hash mismatch: $path")
        }
        if (requireSourceBinding) {
            val manifest = parseObject(readManifest())
            val sources = manifest["source_sha256"] as? Map<*, *> ?: throw LingoError("source manifest missing")
            val source = sources["internal"] as? String ?: throw LingoError("main source digest missing")
            if (!isHash(source) || sources.values.any { it !is String || !isHash(it) }) throw LingoError("invalid source digests")
            for (path in listOf("movie.json", "lingo.json")) {
                val data = read(path) ?: throw LingoError("content has no $path")
                val root = parseObject(String(data, Charsets.UTF_8))
                if (root["source_sha256"] != source) throw LingoError("$path source does not match manifest")
            }
        }
    }

    private fun readManifest(): String {
        val entry = zip.getEntry("manifest.json") ?: throw LingoError("content has no manifest")
        if (entry.size !in 1..MAX_MANIFEST_BYTES) throw LingoError("content manifest size invalid")
        val bytes = zip.getInputStream(entry).use { boundedRead(it, MAX_MANIFEST_BYTES) }
        if (bytes.size.toLong() != entry.size) throw LingoError("content manifest size mismatch")
        return String(bytes, Charsets.UTF_8)
    }

    private fun mediaPath(castFile: String, member: MemberData, extension: String) =
        "media/${DirectorMovie.castKey(castFile).ifEmpty { "internal" }}/${member.number}.$extension"

    override fun image(castFile: String, member: MemberData): LingoImage? =
        read(mediaPath(castFile, member, "png"))?.let(decoder::decode)

    override fun flash(castFile: String, member: MemberData): ByteArray? = read(mediaPath(castFile, member, "swf"))

    /** Sound data and its container type ("wav" or "mp3"). */
    fun sound(castFile: String, member: MemberData): Pair<ByteArray, String>? =
        listOf("wav", "mp3").firstNotNullOfOrNull { ext -> read(mediaPath(castFile, member, ext))?.let { it to ext } }

    /**
     * Size `baFileSize`/FileIO report for a bundled external cast, -1 when the movie has no such
     * cast. The original file is not packaged, so this is its packaged media plus a header: titles
     * compare it against thresholds to tell a downloaded cast from a stub (an empty cast stays small).
     */
    fun castFileSize(path: String): Int {
        val cast = movie.externalCast(path) ?: return -1
        val prefix = "media/${DirectorMovie.castKey(cast.file)}/"
        val media = entries.entries.sumOf { (name, record) -> if (name.startsWith(prefix)) record.first else 0L }
        return (CAST_HEADER_BYTES + media).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    override fun close() = zip.close()

    companion object {
        const val FORMAT = "case-recomp-director-content"
        private const val MAX_MANIFEST_BYTES = 16L * 1024 * 1024
        private const val MAX_ENTRY_BYTES = 256L * 1024 * 1024
        private const val MAX_PACKAGE_BYTES = 1024L * 1024 * 1024
        private const val MAX_ENTRIES = 20_000
        private const val CAST_HEADER_BYTES = 1024L
        private val HASH = Regex("[0-9a-f]{64}")

        fun open(file: File, decoder: ImageDecoder): DirectorContent {
            val zip = ZipFile(file)
            try {
                val zipEntries = zip.entries().asSequence().toList()
                if (zipEntries.size !in 3..MAX_ENTRIES) throw LingoError("invalid content entry count")
                val realPaths = HashSet<String>()
                for (entry in zipEntries) {
                    if (entry.isDirectory || !validPath(entry.name) || !realPaths.add(entry.name)) {
                        throw LingoError("invalid or duplicate content entry")
                    }
                    if (entry.size < 0 || entry.size > MAX_ENTRY_BYTES && entry.name != "manifest.json") {
                        throw LingoError("content entry size invalid")
                    }
                }
                val manifestEntry = zip.getEntry("manifest.json") ?: throw LingoError("content has no manifest")
                if (manifestEntry.size !in 1..MAX_MANIFEST_BYTES) throw LingoError("content manifest too large")
                val raw = zip.getInputStream(manifestEntry).use { boundedRead(it, MAX_MANIFEST_BYTES) }
                if (raw.size.toLong() != manifestEntry.size) throw LingoError("content manifest size mismatch")
                val manifest = parseObject(String(raw, Charsets.UTF_8))
                if (manifest["format"] != FORMAT || manifest["version"] != 1L) throw LingoError("unsupported content package")
                val records = manifest["entries"] as? List<*> ?: throw LingoError("content manifest has no entries")
                if (records.size !in 2..MAX_ENTRIES - 1) throw LingoError("invalid manifest entry count")
                var total = 0L
                val entries = LinkedHashMap<String, Pair<Long, String>>()
                for (row in records) {
                    val e = row as? Map<*, *> ?: throw LingoError("content entry must be an object")
                    val path = e["path"] as? String ?: throw LingoError("content entry path")
                    val size = e["bytes"] as? Long ?: throw LingoError("content entry size")
                    val digest = e["sha256"] as? String ?: throw LingoError("content entry hash")
                    if (!validPath(path) || path == "manifest.json" || size !in 0..MAX_ENTRY_BYTES ||
                        !isHash(digest) || entries.containsKey(path)
                    ) throw LingoError("invalid or duplicate content manifest entry")
                    val found = zip.getEntry(path) ?: throw LingoError("content entry missing: $path")
                    if (found.size != size) throw LingoError("content entry size mismatch: $path")
                    total += size
                    if (total > MAX_PACKAGE_BYTES) throw LingoError("content package exceeds size cap")
                    entries[path] = size to digest
                }
                if (realPaths != entries.keys + "manifest.json") throw LingoError("unlisted or missing content entries")
                if (!entries.containsKey("movie.json") || !entries.containsKey("lingo.json")) {
                    throw LingoError("required Director bundles missing")
                }
                return DirectorContent(zip, entries, decoder)
            } catch (e: Throwable) {
                zip.close()
                throw e
            }
        }

        /** No Java 9 readNBytes(): runnable down to Android API 26 with a strict inflation cap. */
        private fun boundedRead(input: InputStream, limit: Long): ByteArray {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                total += n
                if (total > limit) throw LingoError("Director content JSON exceeds size cap")
                output.write(buffer, 0, n)
            }
            return output.toByteArray()
        }

        private fun validPath(path: String): Boolean =
            path.isNotEmpty() && path.length <= 512 && !path.startsWith("/") && '\\' !in path &&
                path.split('/').none { it.isBlank() || it == "." || it == ".." }

        private fun isHash(hash: String) = HASH.matches(hash)

        private fun parseObject(text: String): Map<*, *> =
            MiniJson.parse(text) as? Map<*, *> ?: throw LingoError("content JSON must be an object")

        private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

        private fun sha256(data: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(data).toHex()
    }
}

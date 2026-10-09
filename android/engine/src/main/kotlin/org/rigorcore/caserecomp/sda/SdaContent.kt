package org.rigorcore.caserecomp.sda

import org.rigorcore.caserecomp.MiniJson
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipFile

fun interface SdaImageDecoder {
    fun decode(bytes: ByteArray): SdaPixelSource?
}

/**
 * Reader for private SDA content packages (e.g. Vegas Heist).
 * Validates manifest, verifies SHA-256 for all package entries,
 * decodes images through SdaImageDecoder, and instantiates SdaScene instances.
 */
class SdaContent private constructor(
    private val zip: ZipFile,
    private val entries: Map<String, Pair<Long, String>>,
    private val decoder: SdaImageDecoder,
    val coverPath: String? = null,
    val gameId: String = "vegas_heist",
) : AutoCloseable {

    fun read(path: String): ByteArray? {
        val entryKey = if (path in entries) path else entries.keys.firstOrNull { it.equals(path, ignoreCase = true) } ?: return null
        val (size, digest) = entries[entryKey] ?: return null
        val entry = zip.getEntry(entryKey)
            ?: zip.entries().asSequence().firstOrNull { it.name.equals(entryKey, ignoreCase = true) }
            ?: throw IllegalArgumentException("content entry missing: $path")
        if (entry.size != size || size > MAX_ENTRY_BYTES) {
            throw IllegalArgumentException("content entry size mismatch: $path")
        }
        val data = zip.getInputStream(entry).use { input ->
            val result = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                total += n
                if (total > size || total > MAX_ENTRY_BYTES) {
                    throw IllegalArgumentException("content entry exceeds declared size: $path")
                }
                result.write(buffer, 0, n)
            }
            result.toByteArray()
        }
        if (data.size.toLong() != size || sha256(data) != digest) {
            throw IllegalArgumentException("content entry hash mismatch: $path")
        }
        return data
    }

    fun coverPng(): ByteArray? = coverPath?.let(::read)

    fun loadStrings(sceneName: String): Map<String, String> {
        val baseStringsRaw = read("STRINGS.TXT")
        val strings = if (baseStringsRaw != null) SdaStrings.parse(baseStringsRaw).toMutableMap() else mutableMapOf()
        val sceneTxtName = sceneName.substringBeforeLast('.') + ".TXT"
        val sceneStringsRaw = read(sceneTxtName)
        if (sceneStringsRaw != null) {
            strings.putAll(SdaStrings.parse(sceneStringsRaw))
        }
        return strings
    }

    fun loadScene(
        sceneResource: String,
        seed: Long? = null,
        history: List<HistoryMark> = emptyList(),
        historyVariant: Int? = null,
        prunePreviousHistory: Boolean = false,
    ): SdaScene {
        val rawXui = read(sceneResource) ?: throw IllegalArgumentException("scene resource not found: $sceneResource")
        val doc = SdaXui.parse(rawXui)
        val strings = loadStrings(sceneResource)

        val imageCache = mutableMapOf<String, SdaPixelSource>()
        fun getImage(uri: String): SdaPixelSource {
            return imageCache.getOrPut(uri) {
                val imgBytes = read(uri) ?: throw IllegalArgumentException("image resource not found: $uri")
                decoder.decode(imgBytes) ?: throw IllegalArgumentException("unable to decode image: $uri")
            }
        }

        val drawOrder = mutableListOf<SdaSprite>()
        val sprites = mutableMapOf<String, SdaSprite>()
        for (img in doc.images) {
            val texture = doc.textures[img.tex]
                ?: throw IllegalArgumentException("unknown texture ${img.tex} for image ${img.id}")
            val pixelSource = getImage(texture.uri)
            val sprite = SdaSprite(img.id, img.x, img.y, pixelSource)
            drawOrder.add(sprite)
            if (img.isEyeSpy) {
                sprites[img.id] = sprite
            }
        }

        val captionsMap = mutableMapOf<List<String>, List<String>>()
        val targetSets = doc.targetSets.map { set ->
            val resolvedList = SdaStrings.resolve(set.itemNameList, strings).split(',').map { it.trim() }
            captionsMap[set.objects] = resolvedList
            set.objects
        }

        return SdaScene(
            objects = sprites,
            targetSets = targetSets,
            captions = captionsMap,
            seed = seed,
            name = sceneResource,
            history = history,
            historyVariant = historyVariant,
            prunePreviousHistory = prunePreviousHistory,
            drawOrder = drawOrder,
        )
    }

    fun verifyAll() {
        val buffer = ByteArray(64 * 1024)
        for ((path, pair) in entries) {
            val (size, digest) = pair
            val entry = zip.getEntry(path) ?: throw IllegalArgumentException("missing entry: $path")
            if (entry.size != size) throw IllegalArgumentException("entry size mismatch: $path")
            val md = MessageDigest.getInstance("SHA-256")
            var readTotal = 0L
            zip.getInputStream(entry).use { input ->
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    readTotal += n
                    md.update(buffer, 0, n)
                }
            }
            if (readTotal != size) throw IllegalArgumentException("entry truncated: $path")
            val hash = md.digest().joinToString("") { "%02x".format(it) }
            if (hash != digest) throw IllegalArgumentException("entry digest mismatch: $path")
        }
    }

    override fun close() {
        zip.close()
    }

    companion object {
        const val MAX_ENTRY_BYTES = 64 * 1024 * 1024L

        @Suppress("UNCHECKED_CAST")
        fun open(file: File, decoder: SdaImageDecoder): SdaContent {
            require(file.isFile) { "archive not found: ${file.path}" }
            val zip = ZipFile(file)
            try {
                val manifestEntry = zip.getEntry("manifest.json")
                    ?: throw IllegalArgumentException("archive has no manifest.json")
                val manifestText = zip.getInputStream(manifestEntry).use { String(it.readBytes(), Charsets.UTF_8) }
                val manifest = MiniJson.parse(manifestText) as? Map<String, Any?>
                    ?: throw IllegalArgumentException("invalid manifest.json")

                require(manifest["format"] == "case-recomp-sda-content") { "unsupported archive format" }
                val gameId = manifest["game_id"] as? String ?: "vegas_heist"
                val cover = manifest["cover"] as? String
                val filesMap = manifest["files"] as? Map<String, List<Any?>>
                    ?: throw IllegalArgumentException("manifest has no files table")

                val entries = mutableMapOf<String, Pair<Long, String>>()
                for ((path, spec) in filesMap) {
                    val size = (spec[0] as Number).toLong()
                    val hash = spec[1] as String
                    entries[path] = size to hash
                }
                return SdaContent(zip, entries, decoder, cover, gameId)
            } catch (t: Throwable) {
                zip.close()
                throw t
            }
        }

        private fun sha256(bytes: ByteArray): String {
            val md = MessageDigest.getInstance("SHA-256")
            return md.digest(bytes).joinToString("") { "%02x".format(it) }
        }
    }
}

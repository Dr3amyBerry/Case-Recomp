package org.rigorcore.caserecomp.app

import android.content.Context
import org.rigorcore.caserecomp.PrivateContentManifestParser
import org.rigorcore.caserecomp.PrivateContentManifestV1
import org.rigorcore.caserecomp.PrivateTracePlanParser
import org.rigorcore.caserecomp.ScenarioJsonV1
import org.rigorcore.caserecomp.ScenarioV1
import org.rigorcore.caserecomp.sha256Hex
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

private const val MAX_IMPORT_BYTES = 768L * 1024L * 1024L
private const val MAX_ENTRY_BYTES = 256L * 1024L * 1024L
private const val MAX_ENTRIES = 20_000

private fun safeRelative(name: String): String {
    require(name.isNotBlank() && !name.startsWith('/') && '\\' !in name && name.length <= 512)
    val parts = name.split('/')
    require(parts.none { it.isBlank() || it == "." || it == ".." })
    return name
}

data class LoadedPrivateContent(
    val manifest: PrivateContentManifestV1,
    val scenario: ScenarioV1,
    val root: File,
    val tracePlanSourceSha256: String? = null,
) {
    fun fileForAsset(id: String): File? {
        val asset = manifest.asset(id) ?: return null
        val file = File(root, asset.path)
        val canonicalRoot = root.canonicalFile
        val canonical = file.canonicalFile
        if (canonical != canonicalRoot && canonicalRoot !in generateSequence(canonical.parentFile) { it.parentFile }.toSet()) return null
        if (!canonical.isFile || canonical.length() != asset.bytes) return null
        if (canonical.inputStream().use { digest(it) } != asset.sha256) return null
        return canonical
    }
    fun backgroundAsset(scene: String): String? = manifest.bindings.sceneBackgrounds[scene]
    fun targetAsset(scene: String, target: String): String? = manifest.bindings.targets[scene]?.get(target)
    fun audioAsset(cue: String): String? = manifest.bindings.audio[cue]
}

class PrivateContentRepository(private val context: Context) {
    private val base = File(context.filesDir, "private-content")
    private val packages = File(base, "packages")
    private val prefs = context.getSharedPreferences("case-recomp-private-content", Context.MODE_PRIVATE)

    fun importBundle(input: InputStream): LoadedPrivateContent {
        base.mkdirs(); packages.mkdirs()
        val staging = File(base, ".import-${System.nanoTime()}")
        require(staging.mkdir()) { "cannot create private import staging" }
        try {
            extract(input, staging)
            val manifestFile = File(staging, "content-manifest.json")
            require(manifestFile.isFile && manifestFile.length() <= 8L * 1024L * 1024L) { "missing private content manifest" }
            val manifest = PrivateContentManifestParser.parseAndMigrate(manifestFile.readText(Charsets.UTF_8))
            val scenarioFile = checkedFile(staging, manifest.scenarioPath)
            require(scenarioFile.inputStream().use { digest(it) } == manifest.scenarioSha256) { "scenario hash mismatch" }
            val scenario = ScenarioJsonV1.parse(scenarioFile.readText(Charsets.UTF_8))
            require(scenario.id == manifest.scenarioId) { "scenario id mismatch" }
            manifest.assets.forEach { asset ->
                val file = checkedFile(staging, asset.path)
                require(file.length() == asset.bytes) { "asset size mismatch" }
                require(file.inputStream().use { digest(it) } == asset.sha256) { "asset hash mismatch" }
            }
            verifyTracePlan(staging, manifest, "trace-plan")
            val finalDir = File(packages, manifest.packageId)
            if (finalDir.exists()) {
                val existingValid = runCatching { loadPackage(finalDir) }.isSuccess
                if (existingValid) {
                    staging.deleteRecursively()
                } else {
                    val damaged = File(packages, manifest.packageId + ".damaged-" + System.nanoTime())
                    require(finalDir.renameTo(damaged)) { "cannot quarantine damaged private package" }
                    if (!staging.renameTo(finalDir)) {
                        damaged.renameTo(finalDir)
                        error("cannot replace damaged private package")
                    }
                    damaged.deleteRecursively()
                }
            } else {
                require(staging.renameTo(finalDir)) { "cannot commit private content import" }
            }
            prefs.edit().putString("active-package", manifest.packageId).commit()
            return loadPackage(finalDir)
        } catch (failure: Throwable) {
            staging.deleteRecursively()
            throw failure
        }
    }

    fun loadActive(): LoadedPrivateContent? {
        val id = prefs.getString("active-package", null) ?: return null
        if (!id.matches(Regex("[0-9a-f]{64}"))) return null
        val root = File(packages, id)
        return runCatching { loadPackage(root) }.getOrNull()
    }

    fun clearActive(removeFiles: Boolean = false): Boolean {
        val id = prefs.getString("active-package", null)
        val cleared = prefs.edit().remove("active-package").commit()
        if (removeFiles && id != null && id.matches(Regex("[0-9a-f]{64}"))) {
            File(packages, id).deleteRecursively()
        }
        return cleared
    }

    fun removePackage(packageId: String): Boolean {
        require(packageId.matches(Regex("[0-9a-f]{64}"))) { "invalid private package id" }
        if (prefs.getString("active-package", null) == packageId) {
            prefs.edit().remove("active-package").commit()
        }
        val root = File(packages, packageId)
        return !root.exists() || root.deleteRecursively()
    }

    private fun loadPackage(root: File): LoadedPrivateContent {
        require(root.isDirectory && root.parentFile?.canonicalFile == packages.canonicalFile)
        val manifestFile = checkedFile(root, "content-manifest.json")
        val manifest = PrivateContentManifestParser.parseAndMigrate(manifestFile.readText(Charsets.UTF_8))
        require(root.name == manifest.packageId) { "private package directory/id mismatch" }
        val scenarioFile = checkedFile(root, manifest.scenarioPath)
        require(scenarioFile.inputStream().use { digest(it) } == manifest.scenarioSha256)
        val scenario = ScenarioJsonV1.parse(scenarioFile.readText(Charsets.UTF_8))
        require(scenario.id == manifest.scenarioId)
        manifest.assets.forEach { asset ->
            val file = checkedFile(root, asset.path)
            require(file.length() == asset.bytes) { "stored asset size mismatch" }
            require(file.inputStream().use { digest(it) } == asset.sha256) { "stored asset hash mismatch" }
        }
        val tracePlanSourceSha256 = verifyTracePlan(root, manifest, "stored trace-plan")
        return LoadedPrivateContent(manifest, scenario, root, tracePlanSourceSha256)
    }

    private fun verifyTracePlan(root: File, manifest: PrivateContentManifestV1, label: String): String? {
        val path = manifest.tracePlanPath ?: return null
        val expectedHash = requireNotNull(manifest.tracePlanSha256) { "$label digest missing" }
        val file = checkedFile(root, path)
        require(file.length() in 1L..8L * 1024L * 1024L) { "$label size" }
        require(file.inputStream().use { digest(it) } == expectedHash) { "$label hash mismatch" }
        return PrivateTracePlanParser.parse(file.readText(Charsets.UTF_8)).sourceSha256
    }

    private fun extract(input: InputStream, destination: File) {
        var entries = 0; var total = 0L
        val names = mutableSetOf<String>()
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries++; require(entries <= MAX_ENTRIES) { "too many private content entries" }
                val name = safeRelative(entry.name); require(names.add(name)) { "duplicate private content entry" }
                require(!entry.isDirectory) { "directory ZIP entries are not accepted" }
                val file = checkedFile(destination, name, mayNotExist = true)
                file.parentFile?.mkdirs()
                var entryBytes = 0L
                FileOutputStream(file).use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = zip.read(buffer)
                        if (count < 0) break
                        entryBytes += count; total += count
                        require(entryBytes <= MAX_ENTRY_BYTES && total <= MAX_IMPORT_BYTES) { "private content expands beyond cap" }
                        out.write(buffer, 0, count)
                    }
                    out.fd.sync()
                }
                zip.closeEntry()
            }
        }
        require(entries >= 2) { "private content archive is empty" }
    }

    private fun checkedFile(root: File, relative: String, mayNotExist: Boolean = false): File {
        safeRelative(relative)
        val canonicalRoot = root.canonicalFile
        val file = File(root, relative).canonicalFile
        require(file.path.startsWith(canonicalRoot.path + File.separator)) { "private content path escapes root" }
        if (!mayNotExist) require(file.isFile) { "private content file missing" }
        return file
    }
}

private fun digest(input: InputStream): String {
    val md = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(64 * 1024)
    while (true) { val n = input.read(buffer); if (n < 0) break; md.update(buffer, 0, n) }
    return md.digest().joinToString("") { "%02x".format(it) }
}

package org.rigorcore.caserecomp.app

import android.content.Context
import org.rigorcore.caserecomp.VerifiedSceneProofParser
import org.rigorcore.caserecomp.VerifiedSceneProofV1
import org.rigorcore.caserecomp.sha256Hex
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

private const val MAX_SCENE_PROOF_BYTES = 4 * 1024 * 1024

/**
 * App-private storage for `.crscene` proofs, one file per (package, scene). A proof is
 * accepted only when it is bound to the active private content: package, scenario
 * identity and hash, the packaged trace-plan source digest, an existing scene and a
 * design that contains every hit mask. Stored proofs are re-validated on every load.
 */
class PrivateVerifiedSceneRepository(context: Context) {
    private val root = File(context.filesDir, "private-scene").apply { mkdirs() }

    fun importProof(input: InputStream, content: LoadedPrivateContent): VerifiedSceneProofV1 {
        val text = readBounded(input).toString(Charsets.UTF_8)
        val proof = VerifiedSceneProofParser.parse(text)
        requireBound(proof, content)
        val canonical = VerifiedSceneProofParser.canonicalize(text).toByteArray(Charsets.UTF_8)
        val directory = File(root, content.manifest.packageId).apply { mkdirs() }
        val target = File(directory, sha256Hex(proof.sceneId) + ".crscene")
        val temp = File(directory, target.name + ".tmp")
        try {
            FileOutputStream(temp).use { out -> out.write(canonical); out.fd.sync() }
            require(temp.renameTo(target) || (target.delete() && temp.renameTo(target))) { "cannot commit verified scene" }
        } finally {
            if (temp.exists()) temp.delete()
        }
        return proof
    }

    fun loadFor(content: LoadedPrivateContent): List<VerifiedSceneProofV1> {
        val directory = File(root, content.manifest.packageId)
        val files = directory.listFiles { file -> file.isFile && file.name.endsWith(".crscene") }.orEmpty()
        return files.sortedBy { it.name }.mapNotNull { file ->
            if (file.length() !in 1..MAX_SCENE_PROOF_BYTES.toLong()) return@mapNotNull null
            runCatching {
                VerifiedSceneProofParser.parse(file.readText(Charsets.UTF_8)).also {
                    requireBound(it, content)
                    require(file.name == sha256Hex(it.sceneId) + ".crscene") { "verified scene stored under wrong name" }
                }
            }.getOrNull()
        }
    }

    fun clearFor(content: LoadedPrivateContent): Boolean = File(root, content.manifest.packageId).let {
        !it.exists() || it.deleteRecursively()
    }

    private fun requireBound(proof: VerifiedSceneProofV1, content: LoadedPrivateContent) {
        require(proof.packageId == content.manifest.packageId) { "verified scene package mismatch" }
        require(proof.scenarioId == content.scenario.id) { "verified scene scenario mismatch" }
        require(proof.scenarioSha256 == content.manifest.scenarioSha256) { "verified scene scenario hash mismatch" }
        val traceSource = requireNotNull(content.tracePlanSourceSha256) { "verified scene requires a validated packaged trace plan" }
        require(proof.sourceSha256 == traceSource) { "verified scene source hash mismatch" }
        require(content.scenario.scenes.any { it.id == proof.sceneId }) { "verified scene is not present in active scenario" }
        val width = content.scenario.designWidth
        val height = content.scenario.designHeight
        require(proof.targets.all { it.mask.left + it.mask.width <= width && it.mask.top + it.mask.height <= height }) {
            "verified scene hit mask exceeds scenario design"
        }
        val region = proof.acknowledgeRegion
        require(region.right <= width && region.bottom <= height) { "verified scene acknowledge region exceeds design" }
    }

    private fun readBounded(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        input.use {
            while (true) {
                val count = it.read(buffer)
                if (count < 0) break
                require(out.size() + count <= MAX_SCENE_PROOF_BYTES) { "verified scene exceeds size cap" }
                out.write(buffer, 0, count)
            }
        }
        require(out.size() > 0) { "verified scene is empty" }
        return out.toByteArray()
    }
}

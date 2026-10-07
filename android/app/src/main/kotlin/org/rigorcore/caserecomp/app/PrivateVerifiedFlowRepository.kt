package org.rigorcore.caserecomp.app

import android.content.Context
import org.rigorcore.caserecomp.VerifiedFlowProofParser
import org.rigorcore.caserecomp.VerifiedFlowProofV2
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

private const val MAX_FLOW_PROOF_BYTES = 1024 * 1024

class PrivateVerifiedFlowRepository(context: Context) {
    private val directory = File(context.filesDir, "private-flow").apply { mkdirs() }

    fun importProof(input: InputStream, content: LoadedPrivateContent): VerifiedFlowProofV2 {
        val bytes = readBounded(input)
        val text = bytes.toString(Charsets.UTF_8)
        val proof = VerifiedFlowProofParser.parse(text)
        requireBound(proof, content)

        // Persist only the validated canonical document, never caller-controlled
        // whitespace/ordering/encoding bytes that happened to parse equivalently.
        val canonical = VerifiedFlowProofParser.canonicalize(text)
        val canonicalBytes = canonical.toByteArray(Charsets.UTF_8)
        require(canonicalBytes.size <= MAX_FLOW_PROOF_BYTES) { "canonical verified flow exceeds size cap" }

        val target = fileFor(content)
        recoverInterruptedCommit(target, content)
        val temp = File(directory, target.name + ".tmp")
        val backup = File(directory, target.name + ".bak")
        require(!temp.exists() || temp.delete()) { "cannot clear stale verified-flow staging file" }

        try {
            FileOutputStream(temp).use { out -> out.write(canonicalBytes); out.fd.sync() }
            require(!backup.exists() || backup.delete()) { "cannot clear stale verified-flow backup" }
            if (target.exists()) require(target.renameTo(backup)) { "cannot stage previous verified flow" }
            if (!temp.renameTo(target)) {
                if (backup.exists()) require(backup.renameTo(target)) { "cannot restore previous verified flow" }
                error("cannot commit verified flow")
            }
            require(!backup.exists() || backup.delete()) { "cannot clear committed verified-flow backup" }
            return proof
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    fun loadFor(content: LoadedPrivateContent): VerifiedFlowProofV2? {
        val file = fileFor(content)
        recoverInterruptedCommit(file, content)
        return parseBoundFile(file, content)
    }

    fun clearFor(content: LoadedPrivateContent): Boolean {
        val target = fileFor(content)
        val temp = File(directory, target.name + ".tmp")
        val backup = File(directory, target.name + ".bak")
        return listOf(target, temp, backup).all { !it.exists() || it.delete() }
    }

    private fun parseBoundFile(file: File, content: LoadedPrivateContent): VerifiedFlowProofV2? {
        if (!file.isFile || file.length() !in 1..MAX_FLOW_PROOF_BYTES.toLong()) return null
        return runCatching {
            val proof = VerifiedFlowProofParser.parse(file.readText(Charsets.UTF_8))
            requireBound(proof, content)
            proof
        }.getOrNull()
    }

    private fun recoverInterruptedCommit(target: File, content: LoadedPrivateContent) {
        val temp = File(directory, target.name + ".tmp")
        val backup = File(directory, target.name + ".bak")
        if (temp.exists()) temp.delete()

        val targetValid = parseBoundFile(target, content) != null
        if (targetValid) {
            if (backup.exists()) backup.delete()
            return
        }

        val backupValid = parseBoundFile(backup, content) != null
        if (!backupValid) {
            if (backup.exists()) backup.delete()
            return
        }

        // If an interrupted replacement left a corrupt/missing target, prefer the
        // last fully validated package-bound proof. Never recover an unvalidated file.
        if (target.exists() && !target.delete()) return
        if (!backup.renameTo(target)) backup.delete()
    }

    private fun requireBound(proof: VerifiedFlowProofV2, content: LoadedPrivateContent) {
        require(proof.packageId == content.manifest.packageId) { "verified flow package mismatch" }
        require(proof.scenarioId == content.scenario.id) { "verified flow scenario mismatch" }
        require(proof.scenarioSha256 == content.manifest.scenarioSha256) { "verified flow scenario hash mismatch" }
        val sceneIds = content.scenario.scenes.map { it.id }.toSet()
        require(proof.rules.filter { it.toScreen == org.rigorcore.caserecomp.Screen.SCENE }
            .all { it.sceneId in sceneIds }) { "verified flow scene is not present in active scenario" }
    }

    private fun fileFor(content: LoadedPrivateContent): File =
        File(directory, content.manifest.packageId + ".crflow")

    private fun readBounded(input: InputStream): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        input.use {
            while (true) {
                val count = it.read(buffer)
                if (count < 0) break
                require(out.size() + count <= MAX_FLOW_PROOF_BYTES) { "verified flow exceeds size cap" }
                out.write(buffer, 0, count)
            }
        }
        require(out.size() > 0) { "verified flow is empty" }
        return out.toByteArray()
    }
}

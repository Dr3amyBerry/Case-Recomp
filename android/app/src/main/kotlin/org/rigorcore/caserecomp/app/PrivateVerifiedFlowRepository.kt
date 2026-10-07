package org.rigorcore.caserecomp.app

import android.content.Context
import org.rigorcore.caserecomp.VerifiedFlowProofParser
import org.rigorcore.caserecomp.VerifiedFlowProofV1
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

private const val MAX_FLOW_PROOF_BYTES = 1024 * 1024

class PrivateVerifiedFlowRepository(context: Context) {
    private val directory = File(context.filesDir, "private-flow").apply { mkdirs() }

    fun importProof(input: InputStream, content: LoadedPrivateContent): VerifiedFlowProofV1 {
        val bytes = readBounded(input)
        val proof = VerifiedFlowProofParser.parse(bytes.toString(Charsets.UTF_8))
        requireBound(proof, content)
        val target = fileFor(content)
        val temp = File(directory, target.name + ".tmp")
        FileOutputStream(temp).use { out -> out.write(bytes); out.fd.sync() }
        val backup = File(directory, target.name + ".bak")
        if (backup.exists()) backup.delete()
        if (target.exists()) require(target.renameTo(backup)) { "cannot stage previous verified flow" }
        if (!temp.renameTo(target)) {
            if (backup.exists()) backup.renameTo(target)
            error("cannot commit verified flow")
        }
        backup.delete()
        return proof
    }

    fun loadFor(content: LoadedPrivateContent): VerifiedFlowProofV1? {
        val file = fileFor(content)
        if (!file.isFile || file.length() !in 1..MAX_FLOW_PROOF_BYTES.toLong()) return null
        return runCatching {
            val proof = VerifiedFlowProofParser.parse(file.readText(Charsets.UTF_8))
            requireBound(proof, content)
            proof
        }.getOrNull()
    }

    fun clearFor(content: LoadedPrivateContent): Boolean {
        val file = fileFor(content)
        return !file.exists() || file.delete()
    }

    private fun requireBound(proof: VerifiedFlowProofV1, content: LoadedPrivateContent) {
        require(proof.scenarioId == content.scenario.id) { "verified flow scenario mismatch" }
        require(proof.scenarioSha256 == content.manifest.scenarioSha256) { "verified flow scenario hash mismatch" }
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

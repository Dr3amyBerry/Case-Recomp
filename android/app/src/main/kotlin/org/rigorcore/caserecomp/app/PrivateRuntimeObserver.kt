package org.rigorcore.caserecomp.app

import android.content.Context
import org.rigorcore.caserecomp.Input
import org.rigorcore.caserecomp.RenderFrame
import org.rigorcore.caserecomp.RuntimeObserver
import org.rigorcore.caserecomp.RuntimeTraceRecorder
import org.rigorcore.caserecomp.StepResult
import java.io.File

/** App-private runtime draft. It is intentionally not sufficient to promote original rules. */
class AppPrivateRuntimeObserver(context: Context, packageId: String) : RuntimeObserver {
    private val recorder = RuntimeTraceRecorder(packageId)
    private val directory = File(context.filesDir, "private-traces").apply { mkdirs() }
    val file = File(directory, "runtime-draft-$packageId.json")

    override fun record(atMillis: Long, input: Input, result: StepResult, render: RenderFrame) {
        recorder.record(atMillis, input, result, render)
        val tmp = File(directory, file.name + ".tmp")
        tmp.writeText(recorder.encode(), Charsets.UTF_8)
        if (file.exists()) file.delete()
        require(tmp.renameTo(file)) { "cannot persist private runtime trace" }
    }
}

package org.rigorcore.caserecomp.app

import android.content.Context
import android.util.Log

/**
 * Personal builds may carry the player's own converted games under assets/bundled
 * (see `bundledContentDir` in build.gradle.kts). Each one is imported once, on the first
 * launch, exactly as if it had been picked with Importar. Public builds carry none.
 */
object BundledContent {
    private const val DIR = "bundled"
    private const val TAG = "CaseRecompBundled"

    fun pending(context: Context): List<String> {
        val done = context.getSharedPreferences("case-recomp-bundled", Context.MODE_PRIVATE).getStringSet("imported", emptySet()).orEmpty()
        return context.assets.list(DIR).orEmpty().filter { it.endsWith(".zip") && it !in done }.sorted()
    }

    /** Imports every pending package; runs off the main thread. */
    fun importPending(context: Context, director: PrivateDirectorRepository, sda: PrivateSdaRepository) {
        val preferences = context.getSharedPreferences("case-recomp-bundled", Context.MODE_PRIVATE)
        for (name in pending(context)) {
            val result = runCatching {
                context.assets.open("$DIR/$name").use { input ->
                    if (name.endsWith(".sda.zip")) sda.import(input)
                    else director.import(input, name.substringBefore(".director.zip").takeIf(DirectorPresentationProfiles::supportsTitle))
                }
            }
            result.exceptionOrNull()?.let { Log.e(TAG, "Bundled import failed: $name", it) }
            if (result.isSuccess) {
                Log.i(TAG, "Bundled import succeeded: $name")
                val done = preferences.getStringSet("imported", emptySet()).orEmpty() + name
                preferences.edit().putStringSet("imported", done).commit()
            }
        }
    }
}

package org.rigorcore.caserecomp.app

import android.content.ComponentCallbacks2
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.rigorcore.caserecomp.DeterministicClock
import org.rigorcore.caserecomp.GameRuntime
import org.rigorcore.caserecomp.InMemorySessionStore
import org.rigorcore.caserecomp.Input
import org.rigorcore.caserecomp.MiniJson
import org.rigorcore.caserecomp.NoopAudioPort
import org.rigorcore.caserecomp.Screen
import org.rigorcore.caserecomp.sha256Hex
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class PrivateContentInstrumentationTest {
    private lateinit var context: Context

    @Before fun clean() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("case-recomp-private-content", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("case-recomp-session-slots", Context.MODE_PRIVATE).edit().clear().commit()
        java.io.File(context.filesDir, "private-content").deleteRecursively()
        java.io.File(context.filesDir, "private-traces").deleteRecursively()
    }

    @Test fun private_import_renderer_input_slots_and_observer_stay_app_private() {
        val bytes = privateBundle()
        val repository = PrivateContentRepository(context)
        val loaded = repository.importBundle(ByteArrayInputStream(bytes))
        assertEquals("instrumented-private", loaded.scenario.id)
        assertTrue(loaded.root.canonicalPath.startsWith(context.filesDir.canonicalPath))
        assertNotNull(repository.loadActive())

        val imageId = loaded.manifest.assets.first { it.mediaType == "image/png" }.id
        assertNotNull(loaded.fileForAsset(imageId))
        val bitmapLoader = AppPrivateBitmapAssetLoader(loaded)
        assertNotNull(bitmapLoader.load(imageId))

        val slots = SharedPreferencesSlotSessionStore(context)
        slots.save("slot-1", "one"); slots.save("slot-2", "two")
        assertEquals(setOf("slot-1", "slot-2"), slots.slots())
        assertEquals("one", slots.load("slot-1"))

        val observer = AppPrivateRuntimeObserver(context, loaded.manifest.packageId)
        val runtime = GameRuntime(loaded.scenario, DeterministicClock(10), InMemorySessionStore(), NoopAudioPort, observer)
        runtime.onCreate(); runtime.onStart(); runtime.onResume()
        runtime.dispatch(Input.Start); runtime.dispatch(Input.EnterScene("room"))

        var view: GameShellView? = null
        val rendered = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view = GameShellView(context, runtime, loaded, AppPrivateBitmapAssetLoader(loaded))
            view!!.measure(android.view.View.MeasureSpec.makeMeasureSpec(200, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(100, android.view.View.MeasureSpec.EXACTLY))
            view!!.layout(0, 0, 200, 100)
            view!!.draw(Canvas(rendered))
            val event = MotionEvent.obtain(0, 1, MotionEvent.ACTION_UP, 30f, 30f, 0)
            view!!.onTouchEvent(event); event.recycle()
        }
        assertEquals(Screen.COMPLETE, runtime.session.screen)
        val red = rendered.getPixel(30, 30)
        assertTrue(android.graphics.Color.red(red) > android.graphics.Color.blue(red))
        assertTrue(observer.file.canonicalPath.startsWith(context.filesDir.canonicalPath))
        assertTrue(observer.file.readText().contains("\"promotion_allowed\":false"))

        val assetFile = loaded.fileForAsset(imageId)!!
        assetFile.writeBytes(byteArrayOf(1, 2, 3, 4))
        assertNotNull(bitmapLoader.load(imageId))
        bitmapLoader.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
        assertEquals(null, bitmapLoader.load(imageId))

        val reloaded = repository.importBundle(ByteArrayInputStream(bytes))
        assertEquals(loaded.manifest.packageId, reloaded.manifest.packageId)
        assertTrue(repository.clearActive(removeFiles = true))
        assertEquals(null, repository.loadActive())
        assertTrue(!loaded.root.exists())
    }

    @Test fun importer_rejects_asset_hash_mismatch_without_activating_package() {
        val bytes = privateBundle(tamperAssetHash = true)
        val repository = PrivateContentRepository(context)
        assertTrue(runCatching { repository.importBundle(ByteArrayInputStream(bytes)) }.isFailure)
        assertEquals(null, repository.loadActive())
    }

    private fun privateBundle(tamperAssetHash: Boolean = false): ByteArray {
        val image = ByteArrayOutputStream().also { out ->
            Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFFFF0000.toInt()) }
                .compress(Bitmap.CompressFormat.PNG, 100, out)
        }.toByteArray()
        val imageHash = sha256Hex(image)
        val declaredHash = if (tamperAssetHash) "0".repeat(64) else imageHash
        val assetId = "sha256-$declaredHash"
        val scenario = mapOf<String, Any?>(
            "format" to "case-recomp-scenario", "version" to 1, "id" to "instrumented-private",
            "design" to mapOf("width" to 100, "height" to 50),
            "scenes" to listOf(mapOf("id" to "room", "frame_start" to 1, "frame_end" to 2,
                "targets" to listOf(mapOf("id" to "target", "rect" to listOf(10, 10, 30, 30), "z" to 1)))),
            "events" to emptyList<Any>(),
        )
        val scenarioBytes = (MiniJson.canonical(scenario) + "\n").toByteArray()
        val conversion = "b".repeat(64)
        val base = linkedMapOf<String, Any?>(
            "format" to "case-recomp-private-content", "version" to 1,
            "scenario" to mapOf("path" to "scenario.json", "sha256" to sha256Hex(scenarioBytes), "id" to "instrumented-private"),
            "assets" to listOf(mapOf("id" to assetId, "path" to "assets/$declaredHash.png", "sha256" to declaredHash,
                "bytes" to image.size, "media_type" to "image/png")),
            "bindings" to mapOf("scene_backgrounds" to emptyMap<String,String>(),
                "targets" to mapOf("room" to mapOf("target" to assetId)), "audio" to emptyMap<String,String>()),
            "source" to mapOf("conversion_manifest_sha256" to conversion),
        )
        val packageId = sha256Hex(MiniJson.canonical(base) + "\n")
        val manifest = base + ("package_id" to packageId)
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            fun put(name: String, data: ByteArray) { zip.putNextEntry(ZipEntry(name)); zip.write(data); zip.closeEntry() }
            put("content-manifest.json", (MiniJson.canonical(manifest) + "\n").toByteArray())
            put("scenario.json", scenarioBytes)
            put("assets/$declaredHash.png", image)
        }
        return output.toByteArray()
    }
}

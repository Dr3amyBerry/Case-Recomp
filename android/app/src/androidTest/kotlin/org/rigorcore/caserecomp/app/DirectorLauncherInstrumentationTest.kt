package org.rigorcore.caserecomp.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * A fully synthetic movie exercises the *Android* private ZIP → Lingo/Director → stage
 * path. Never use the proprietary game in CI; this does NOT certify Huntsville playability.
 */
@RunWith(AndroidJUnit4::class)
class DirectorLauncherInstrumentationTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val source = "a".repeat(64)

    private fun sha(data: ByteArray) = MessageDigest.getInstance("SHA-256")
        .digest(data).joinToString("") { "%02x".format(it) }

    private fun zip(sourceInLingo: String = source): ByteArray {
        val movie = """
            {"format":"case-recomp-movie-bundle","version":1,"source_sha256":"$source",
             "stage":{"width":64,"height":48,"tempo":15},
             "labels":[{"frame":1,"name":"menu"}],
             "cast_libs":[{"number":1,"name":"Internal","file_path":""}],
             "internal_members":[{"number":3,"name":"synthetic rectangle","type":"bitmap","width":64,"height":48,"reg_x":0,"reg_y":0}],
             "external_casts":[],
             "score":{"frame_count":2,"channel_count":7,
              "sprites":[{"start":1,"end":2,"channel":6,"ink":0,"cast_lib":1,
                "member":3,"x":0,"y":0,"width":64,"height":48,"blend":0,
                "flip_h":false,"flip_v":false}],
              "spans":[{"start":1,"end":2,"channel":6,"behaviors":[]}]}}
        """.trimIndent().toByteArray()
        val lingo = """{"format":"case-recomp-lingo-bundle","version":1,
            "source_sha256":"$sourceInLingo","names":[],"scripts":[]}""".toByteArray()
        val bitmap = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.GREEN)
        val image = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, image)
        bitmap.recycle()
        val files = linkedMapOf(
            "movie.json" to movie,
            "lingo.json" to lingo,
            "media/internal/3.png" to image.toByteArray(),
        )
        val records = files.map { (name, bytes) ->
            """{"path":"$name","bytes":${bytes.size},"sha256":"${sha(bytes)}"}"""
        }.joinToString(",")
        val manifest = """{"format":"case-recomp-director-content","version":1,
            "source_sha256":{"internal":"$source"},"entries":[$records]}"""
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { z ->
            for ((name, bytes) in files + ("manifest.json" to manifest.toByteArray())) {
                z.putNextEntry(ZipEntry(name)); z.write(bytes); z.closeEntry()
            }
        }
        return output.toByteArray()
    }

    @Test fun private_synthetic_director_package_loads_and_paints_through_android_activity() {
        context.getSharedPreferences("case-recomp-director", Context.MODE_PRIVATE).edit().clear().commit()
        val installed = PrivateDirectorRepository(context).import(zip().inputStream())
        assertTrue(installed.path.isFile)
        ActivityScenario.launch(DirectorLauncherActivity::class.java).use { scenario ->
            var painted = false
            var lastState = "not inspected"
            // API 26 emulator software rendering and async ZIP load can be slower
            // than newer images; failure still requires a visible, correctly coloured stage.
            for (attempt in 0 until 300) {
                scenario.onActivity { activity ->
                    val root = activity.findViewById<ViewGroup>(android.R.id.content)
                    val frame = root.getChildAt(0) as? android.widget.FrameLayout
                    val stage = frame?.getChildAt(0) as? DirectorStageView
                    if (stage != null) {
                        stage.measure(
                            android.view.View.MeasureSpec.makeMeasureSpec(640, android.view.View.MeasureSpec.EXACTLY),
                            android.view.View.MeasureSpec.makeMeasureSpec(480, android.view.View.MeasureSpec.EXACTLY))
                        stage.layout(0, 0, 640, 480)
                        val output = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
                        try {
                            stage.draw(Canvas(output))
                            val pixel = output.getPixel(320, 240)
                            painted = pixel == Color.GREEN
                            lastState = "stage ready, center=0x" + Integer.toHexString(pixel)
                        } finally { output.recycle() }
                    } else {
                        val child = root.getChildAt(0)
                        val text = (child as? android.widget.LinearLayout)?.getChildAt(0) as? android.widget.TextView
                        lastState = "root=" + child?.javaClass?.simpleName + ", message=" + text?.text
                    }
                }
                if (painted) break
                Thread.sleep(100)
            }
            assertTrue("synthetic Director content must reach Android StageRenderer: $lastState", painted)
        }
    }

    @Test fun import_rejects_manifest_source_mismatch_before_activation() {
        context.getSharedPreferences("case-recomp-director", Context.MODE_PRIVATE).edit().clear().commit()
        val repository = PrivateDirectorRepository(context)
        assertThrows(Exception::class.java) {
            repository.import(zip(sourceInLingo = "b".repeat(64)).inputStream())
        }
        assertEquals(null, repository.loadActive())
    }
}

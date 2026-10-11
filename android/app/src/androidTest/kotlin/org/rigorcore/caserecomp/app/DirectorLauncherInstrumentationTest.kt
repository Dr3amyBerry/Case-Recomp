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
    @RequiresPrivateContent
    @Test fun prime_inventory_survives_android_activity_recreation() {
        val args = androidx.test.platform.app.InstrumentationRegistry.getArguments()
        val path = args.getString("privateSoundPackage")
        org.junit.Assume.assumeTrue(path != null && java.io.File(path).isFile)
        java.io.File(path!!).inputStream().use { PrivateDirectorRepository(context).import(it, "prime_suspects") }
        val intent = android.content.Intent(context, DirectorLauncherActivity::class.java)
            .putExtra("title_id", "prime_suspects").putExtra("play", true).putExtra("import_save", "prime-check.json")
        ActivityScenario.launch<DirectorLauncherActivity>(intent).use { scenario ->
            fun checkInventory() {
                var checked = false
                val deadline = android.os.SystemClock.uptimeMillis() + 15000
                while (!checked && android.os.SystemClock.uptimeMillis() < deadline) {
                    scenario.onActivity { activity ->
                        val rt = DirectorLauncherActivity::class.java.getDeclaredField("runtime").apply { isAccessible = true }.get(activity)
                            as? org.rigorcore.caserecomp.director.DirectorRuntime ?: return@onActivity
                        val user = rt.vm.global("gUserData")
                        val rows = rt.vm.getObjectProp(user, "m_userDataArray") as? org.rigorcore.caserecomp.lingo.LingoValue.LList ?: return@onActivity
                        val row = rows.items.filterIsInstance<org.rigorcore.caserecomp.lingo.LingoValue.LList>()
                            .single { it.items.first() == org.rigorcore.caserecomp.lingo.LingoValue.LInt(18) }
                        val found = row.items.drop(1).filterIsInstance<org.rigorcore.caserecomp.lingo.LingoValue.LList>()
                            .count { it.items[1] == org.rigorcore.caserecomp.lingo.LingoValue.LInt(1) }
                        assertEquals("original inventory restored", 3, found)
                        checked = true
                    }
                    if (!checked) android.os.SystemClock.sleep(50)
                }
                assertTrue("Prime runtime loaded", checked)
            }
            checkInventory()
            scenario.recreate()
            checkInventory()
        }
    }

    @RequiresPrivateContent
    @Test fun director_audio_prepared_after_pause_waits_for_resume() {
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val path = androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("privateSoundPackage")
        org.junit.Assume.assumeTrue(path != null && java.io.File(path).isFile)
        val content = org.rigorcore.caserecomp.director.DirectorContent.open(java.io.File(path!!), AndroidDirectorImageDecoder)
        val runtime = org.rigorcore.caserecomp.director.DirectorRuntime(content.movie, content.lingo, media = content)
        val member = runtime.member(org.rigorcore.caserecomp.lingo.LingoValue.LInt(631), null)
        val audio = AndroidDirectorSound(context, content)
        try {
            // Hold the main looper until pause: prepareAsync's callback cannot run first.
            instrumentation.runOnMainSync { audio.play(1, member, 0); audio.pauseAll() }
            val deadline = android.os.SystemClock.uptimeMillis() + 5000
            var prepared = false
            while (!prepared && android.os.SystemClock.uptimeMillis() < deadline) {
                instrumentation.runOnMainSync {
                    val pending = AndroidDirectorSound::class.java.getDeclaredField("pending").apply { isAccessible = true }.get(audio) as Set<*>
                    prepared = pending.isEmpty()
                }
                if (!prepared) android.os.SystemClock.sleep(20)
            }
            assertTrue("decoder prepared while the activity was paused", prepared)
            instrumentation.runOnMainSync {
                val channels = AndroidDirectorSound::class.java.getDeclaredField("channels").apply { isAccessible = true }.get(audio) as Map<*, *>
                val slot = channels[1] ?: error("audio preparation failed")
                val player = slot.javaClass.getDeclaredField("player").apply { isAccessible = true }.get(slot) as android.media.MediaPlayer
                org.junit.Assert.assertFalse("prepared callback must not start background audio", player.isPlaying)
                assertTrue("paused sound remains busy to the original scripts", audio.isPlaying(1) == true)
                audio.resumeAll()
                assertTrue("resume starts the prepared track", player.isPlaying)
            }
        } finally {
            instrumentation.runOnMainSync { audio.close() }
            content.close()
        }
    }

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
        val intent = android.content.Intent(context, DirectorLauncherActivity::class.java)
            .putExtra("title_id", installed.titleId)
        ActivityScenario.launch<DirectorLauncherActivity>(intent).use { scenario ->
            var painted = false
            var lastState = "not inspected"
            // Await asynchronous content import, then inspect the Android stage output.
            for (attempt in 0 until 100) {
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
                            // API 26 tinted the focused stage with the theme's focus
                            // highlight (#00F000, then #19FF19 on a dark theme); the
                            // stage now disables it. Require opaque saturated green
                            // (not a blank stage), allowing <= 15/255 difference.
                            painted = Color.alpha(pixel) == 255 &&
                                Color.red(pixel) == 0 && Color.blue(pixel) == 0 &&
                                Color.green(pixel) in 240..255
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

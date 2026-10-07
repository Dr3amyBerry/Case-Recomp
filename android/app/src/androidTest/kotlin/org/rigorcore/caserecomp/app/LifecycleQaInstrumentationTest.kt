package org.rigorcore.caserecomp.app

import android.content.ComponentCallbacks2
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.rigorcore.caserecomp.DeterministicClock
import org.rigorcore.caserecomp.GameRuntime
import org.rigorcore.caserecomp.Screen
import org.rigorcore.caserecomp.Session
import org.rigorcore.caserecomp.SessionSnapshotCodecV1
import org.rigorcore.caserecomp.SlotSessionAdapter
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class LifecycleQaInstrumentationTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before fun cleanPersistentState() {
        context.getSharedPreferences("case-recomp-private-content", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("case-recomp-session-slots", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("case-recomp-session", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        java.io.File(context.filesDir, "private-content").deleteRecursively()
        java.io.File(context.filesDir, "private-flow").deleteRecursively()
        java.io.File(context.filesDir, "private-traces").deleteRecursively()
    }

    @Test fun activity_recreation_process_boundary_low_memory_and_aspect_ratios_preserve_state() {
        val activityScenario = ActivityScenario.launch(MainActivity::class.java)
        activityScenario.onActivity { activity ->
            val view = ((activity.findViewById(android.R.id.content) as ViewGroup).getChildAt(0) as GameShellView)
            val event = MotionEvent.obtain(0, 1, MotionEvent.ACTION_UP, 40f, 40f, 0)
            view.onTouchEvent(event); event.recycle()
        }

        activityScenario.recreate()
        activityScenario.onActivity { activity ->
            val encoded = SharedPreferencesSlotSessionStore(activity).load("slot-1")
            val snapshot = encoded?.let(SessionSnapshotCodecV1::decode)
            assertNotNull(snapshot)
            assertEquals(Screen.MAP, snapshot!!.screen)

            val view = ((activity.findViewById(android.R.id.content) as ViewGroup).getChildAt(0) as GameShellView)
            for ((width, height) in listOf(320 to 180, 180 to 320, 400 to 400)) {
                view.measure(
                    android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec.makeMeasureSpec(height, android.view.View.MeasureSpec.EXACTLY),
                )
                view.layout(0, 0, width, height)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                assertTrue(bitmap.getPixel(width / 2, height / 2) != 0)
            }
            activity.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
        }
        activityScenario.close()

        val freshStore = SharedPreferencesSlotSessionStore(context)
        val restarted = GameRuntime(
            SyntheticContent.scenario(),
            DeterministicClock(500),
            SlotSessionAdapter(freshStore, "slot-1"),
        )
        restarted.onCreate()
        assertEquals(Screen.MAP, restarted.session.screen)
    }

    @Test fun legacy_autosave_migrates_once_and_corrupt_slot_fails_closed() {
        fun sha(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
        val body = listOf(
            "case-recomp-session-v0",
            "scenario=android-shell-synthetic",
            "screen=MAP",
            "selected=-",
            "frame=1",
        ).joinToString("\n")
        val legacy = body + "\nsha256=" + sha(body) + "\n"
        context.getSharedPreferences("case-recomp-session", android.content.Context.MODE_PRIVATE)
            .edit().putString("snapshot", legacy).commit()

        val migratedStore = SharedPreferencesSlotSessionStore(context)
        assertEquals(legacy, migratedStore.load("autosave"))
        assertFalse(context.getSharedPreferences("case-recomp-session", android.content.Context.MODE_PRIVATE)
            .contains("snapshot"))

        migratedStore.save("slot-1", "corrupt")
        val runtime = GameRuntime(
            SyntheticContent.scenario(),
            DeterministicClock(),
            SlotSessionAdapter(SharedPreferencesSlotSessionStore(context), "slot-1"),
        )
        runtime.onCreate()
        assertEquals(Session(), runtime.session)
    }
}

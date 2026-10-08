package org.rigorcore.caserecomp.app

import android.content.Context
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.rigorcore.caserecomp.Screen

/** The old shell's custom touch handler must not swallow its long-click import/debug entry. */
@RunWith(AndroidJUnit4::class)
class DirectorEntryGestureInstrumentationTest {
    @Test fun long_press_opens_entry_without_triggering_a_synthetic_game_tap() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.getSharedPreferences("case-recomp-private-content", Context.MODE_PRIVATE).edit().clear().commit()
        ctx.getSharedPreferences("case-recomp-session-slots", Context.MODE_PRIVATE).edit().clear().commit()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.findViewById<ViewGroup>(android.R.id.content)
                val shell = root.getChildAt(0) as GameShellView
                var longClicked = false
                shell.setOnLongClickListener { longClicked = true; true }
                val down = MotionEvent.obtain(1000, 1000, MotionEvent.ACTION_DOWN, 40f, 40f, 0)
                val up = MotionEvent.obtain(1000, 1800, MotionEvent.ACTION_UP, 40f, 40f, 0)
                try { shell.onTouchEvent(down); shell.onTouchEvent(up) }
                finally { down.recycle(); up.recycle() }
                assertTrue("Director debug chooser must be reachable", longClicked)
                assertEquals(Screen.MENU, activity.javaClass.getDeclaredField("runtime").let { field ->
                    field.isAccessible = true
                    (field.get(activity) as org.rigorcore.caserecomp.GameRuntime).session.screen
                })
            }
        }
    }
}

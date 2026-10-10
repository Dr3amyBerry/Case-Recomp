package org.rigorcore.caserecomp.app
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Isolated preference fault test, not commercial UI fidelity evidence. */
@RunWith(AndroidJUnit4::class)
class SdaPreferenceRollbackInstrumentationTest {
 @Test fun failed_commit_restores_previous_values_and_absent_keys_even_after_memory_mutation() {
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val name="qa-sda-rollback-"+java.util.UUID.randomUUID()
  val backing=context.getSharedPreferences(name,0)
  try {
   assertTrue(backing.edit().putString("existing","previous").commit())
   val failing=object:SharedPreferences by backing {
    override fun edit():SharedPreferences.Editor {
     val editor=backing.edit()
     return object:SharedPreferences.Editor by editor {
      override fun commit():Boolean { assertTrue(editor.commit());return false }
     }
    }
   }
   val preferences=AndroidSdaPreferences(failing)
   assertFalse(preferences.setStrings(mapOf("existing" to "changed","absent" to "introduced")))
   assertEquals("previous",backing.getString("existing",null))
   assertFalse(backing.contains("absent"))
   assertFalse(preferences.setString("existing","changed again"))
   assertEquals("previous",backing.getString("existing",null))
  } finally { assertTrue(context.deleteSharedPreferences(name)) }
 }
}

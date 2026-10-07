package org.rigorcore.caserecomp.app

import android.content.Context
import android.os.SystemClock
import org.rigorcore.caserecomp.GameClock
import org.rigorcore.caserecomp.SessionStore
import org.rigorcore.caserecomp.SlotSessionStore

class AndroidMonotonicClock : GameClock {
    override fun nowMillis(): Long = SystemClock.elapsedRealtime()
}

class SharedPreferencesSlotSessionStore(context: Context) : SlotSessionStore {
    private val preferences = context.getSharedPreferences("case-recomp-session-slots", Context.MODE_PRIVATE)
    private val legacy = context.getSharedPreferences("case-recomp-session", Context.MODE_PRIVATE)

    init {
        if (!preferences.contains("slot.autosave")) {
            legacy.getString("snapshot", null)?.let { encoded ->
                if (preferences.edit().putString("slot.autosave", encoded).commit()) {
                    legacy.edit().remove("snapshot").commit()
                }
            }
        }
    }
    private fun key(slot: String): String { require(slot.matches(Regex("[A-Za-z0-9._-]{1,64}"))); return "slot.$slot" }
    override fun load(slot: String): String? = preferences.getString(key(slot), null)
    override fun save(slot: String, encoded: String) { preferences.edit().putString(key(slot), encoded).commit() }
    override fun clear(slot: String) { preferences.edit().remove(key(slot)).commit() }
    override fun slots(): Set<String> = preferences.all.keys.filter { it.startsWith("slot.") }.map { it.removePrefix("slot.") }.toSet()
}

/** Compatibility adapter retained for callers that still want one autosave slot. */
class SharedPreferencesSessionStore(context: Context) : SessionStore {
    private val slots = SharedPreferencesSlotSessionStore(context)
    override fun load(): String? = slots.load("autosave")
    override fun save(encoded: String) = slots.save("autosave", encoded)
    override fun clear() = slots.clear("autosave")
}

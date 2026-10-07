package org.rigorcore.caserecomp.app

import android.content.Context
import android.os.SystemClock
import org.rigorcore.caserecomp.GameClock
import org.rigorcore.caserecomp.SessionStore

class AndroidMonotonicClock : GameClock {
    override fun nowMillis(): Long = SystemClock.elapsedRealtime()
}

class SharedPreferencesSessionStore(context: Context) : SessionStore {
    private val preferences = context.getSharedPreferences("case-recomp-session", Context.MODE_PRIVATE)
    override fun load(): String? = preferences.getString("snapshot", null)
    override fun save(encoded: String) {
        preferences.edit().putString("snapshot", encoded).apply()
    }
    override fun clear() {
        preferences.edit().remove("snapshot").apply()
    }
}

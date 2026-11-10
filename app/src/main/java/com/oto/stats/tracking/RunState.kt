package com.oto.stats.tracking

import android.content.Context

/**
 * Remembers whether the last run ended normally. After a restart, an unclean ending
 * means the system or the phone's battery manager killed the app.
 */
object RunState {
    class Previous(val aliveAtMs: Long, val clean: Boolean)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("oto_run", Context.MODE_PRIVATE)

    fun previous(context: Context): Previous {
        val p = prefs(context)
        return Previous(p.getLong("alive_at", 0L), p.getBoolean("clean", true))
    }

    fun markConnected(context: Context) {
        prefs(context).edit()
            .putBoolean("clean", false)
            .putLong("alive_at", System.currentTimeMillis())
            .apply()
    }

    fun beat(context: Context) {
        prefs(context).edit().putLong("alive_at", System.currentTimeMillis()).apply()
    }

    fun markClean(context: Context) {
        prefs(context).edit().putBoolean("clean", true).apply()
    }
}

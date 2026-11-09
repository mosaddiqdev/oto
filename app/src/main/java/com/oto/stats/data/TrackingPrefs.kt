package com.oto.stats.data

import android.content.Context
import com.oto.stats.tracking.MusicApps
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Small settings store for tracking. Moves to DataStore with the Settings screen. */
class TrackingPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("oto_prefs", Context.MODE_PRIVATE)

    private val _trackAllApps = MutableStateFlow(prefs.getBoolean(KEY_TRACK_ALL, false))
    val trackAllApps: StateFlow<Boolean> = _trackAllApps

    fun setTrackAllApps(value: Boolean) {
        prefs.edit().putBoolean(KEY_TRACK_ALL, value).apply()
        _trackAllApps.value = value
    }

    fun isAllowed(packageName: String): Boolean {
        if (packageName in MusicApps.neverTrack) return false
        return _trackAllApps.value || packageName in MusicApps.known
    }

    private companion object {
        const val KEY_TRACK_ALL = "track_all_apps"
    }
}

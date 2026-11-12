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

    private val _fetchArt = MutableStateFlow(prefs.getBoolean(KEY_FETCH_ART, true))
    /** Fetch artist photos from Deezer (sends the artist name only). On Wi-Fi or any unmetered network. */
    val fetchArt: StateFlow<Boolean> = _fetchArt

    fun setFetchArt(value: Boolean) {
        prefs.edit().putBoolean(KEY_FETCH_ART, value).apply()
        _fetchArt.value = value
    }

    /** Whether the notification permission prompt was already shown once. */
    var notificationsAsked: Boolean
        get() = prefs.getBoolean(KEY_NOTIF_ASKED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_NOTIF_ASKED, value).apply()
        }

    fun setTrackAllApps(value: Boolean) {
        prefs.edit().putBoolean(KEY_TRACK_ALL, value).apply()
        _trackAllApps.value = value
    }

    fun isAllowed(packageName: String): Boolean {
        if (packageName in MusicApps.neverTrack) return false
        return _trackAllApps.value || packageName in MusicApps.known
    }

    fun blockReason(packageName: String): String =
        if (packageName in MusicApps.neverTrack) {
            "messaging app, never tracked"
        } else {
            "not on the music app list; turn on Track all apps to include it"
        }

    private companion object {
        const val KEY_TRACK_ALL = "track_all_apps"
        const val KEY_FETCH_ART = "fetch_art"
        const val KEY_NOTIF_ASKED = "notifications_asked"
    }
}

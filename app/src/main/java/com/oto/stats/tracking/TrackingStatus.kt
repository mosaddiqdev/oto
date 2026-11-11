package com.oto.stats.tracking

import android.graphics.Bitmap
import com.oto.stats.tracking.engine.NowPlayingSnapshot
import kotlinx.coroutines.flow.MutableStateFlow

/** Live tracking state shared between the listener service and the UI (same process). */
object TrackingStatus {
    val listenerConnected = MutableStateFlow(false)
    /** True while the keep-alive foreground notification is active. */
    val foreground = MutableStateFlow(false)
    val nowPlaying = MutableStateFlow<NowPlayingSnapshot?>(null)
    /** Cover art of whatever is in [nowPlaying], when the player provides it. */
    val artwork = MutableStateFlow<Bitmap?>(null)
}

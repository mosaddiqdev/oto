package com.oto.stats.tracking

import com.oto.stats.tracking.engine.NowPlayingSnapshot
import kotlinx.coroutines.flow.MutableStateFlow

/** Live tracking state shared between the listener service and the UI (same process). */
object TrackingStatus {
    val listenerConnected = MutableStateFlow(false)
    val nowPlaying = MutableStateFlow<NowPlayingSnapshot?>(null)
}

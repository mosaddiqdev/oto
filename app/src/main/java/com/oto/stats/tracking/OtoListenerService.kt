package com.oto.stats.tracking

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.util.Log
import com.oto.stats.AppContainer
import com.oto.stats.OtoApp
import com.oto.stats.tracking.engine.Clock
import com.oto.stats.tracking.engine.ListenResult
import com.oto.stats.tracking.engine.Normalize
import com.oto.stats.tracking.engine.OpenSession
import com.oto.stats.tracking.engine.PlayState
import com.oto.stats.tracking.engine.PlaybackTracker
import com.oto.stats.tracking.engine.SessionRecovery
import com.oto.stats.tracking.engine.TrackMeta
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Notification access is only used to be allowed to read active media sessions.
 * We never read notification content.
 *
 * One [PlaybackTracker] per active media session, fed by MediaController callbacks
 * plus a light position ticker while something is playing.
 *
 * Open listens are saved every ~30 s. If the process is killed, the next connection
 * resumes them (same track still playing) or saves them (anything else).
 */
class OtoListenerService : NotificationListenerService() {

    private val handler = Handler(Looper.getMainLooper())
    private val tracked = HashMap<MediaSession.Token, TrackedSession>()

    /** Saved sessions from a previous run, keyed by package, waiting for their player to show up. */
    private val pendingRestore = HashMap<String, OpenSession>()

    private var container: AppContainer? = null
    private var scope: CoroutineScope? = null
    private var tickerJob: Job? = null
    private var sessionManager: MediaSessionManager? = null
    private var component: ComponentName? = null

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        syncSessions(controllers.orEmpty())
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        val appContainer = (application as OtoApp).container
        container = appContainer

        val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = serviceScope

        pendingRestore.clear()
        appContainer.openSessions.all().forEach { pendingRestore[it.packageName] = it }

        val manager = getSystemService(MediaSessionManager::class.java)
        val name = ComponentName(this, OtoListenerService::class.java)
        sessionManager = manager
        component = name

        try {
            manager.addOnActiveSessionsChangedListener(sessionsListener, name, handler)
            syncSessions(manager.getActiveSessions(name))
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification access is not granted", e)
        }

        // Anything saved for a player that is no longer active has ended: save it now.
        recoverLeftovers()

        TrackingStatus.listenerConnected.value = true

        // Re-scan when the user toggles "track all apps".
        serviceScope.launch {
            appContainer.prefs.trackAllApps.drop(1).collect { resync() }
        }
    }

    override fun onListenerDisconnected() {
        teardown()
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    private fun teardown() {
        tickerJob?.cancel()
        tickerJob = null
        runCatching { sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener) }
        tracked.values.toList().forEach { it.stop() }
        tracked.clear()
        // Everything was closed and handed to the database, so nothing is left open.
        container?.openSessions?.clear()
        pendingRestore.clear()
        scope?.cancel()
        scope = null
        TrackingStatus.listenerConnected.value = false
        TrackingStatus.nowPlaying.value = null
    }

    private fun resync() {
        val manager = sessionManager ?: return
        val name = component ?: return
        try {
            syncSessions(manager.getActiveSessions(name))
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot read sessions", e)
        }
    }

    /** Makes the set of tracked sessions match the allowed active sessions. */
    private fun syncSessions(controllers: List<MediaController>) {
        val prefs = container?.prefs ?: return
        val allowed = controllers.filter { prefs.isAllowed(it.packageName) }
        val liveTokens = allowed.map { it.sessionToken }.toSet()

        tracked.keys.filter { it !in liveTokens }.forEach { tracked.remove(it)?.stop() }

        for (controller in allowed) {
            val token = controller.sessionToken
            if (token !in tracked) {
                val session = TrackedSession(controller)
                tracked[token] = session
                session.start(pendingRestore.remove(controller.packageName))
            }
        }
        publish()
        updateTicker()
        saveOpenSessions()
    }

    private fun removeSession(token: MediaSession.Token) {
        tracked.remove(token)?.stop()
        publish()
        updateTicker()
        saveOpenSessions()
    }

    private fun recoverLeftovers() {
        if (pendingRestore.isEmpty()) return
        val now = System.currentTimeMillis()
        for (open in pendingRestore.values) {
            SessionRecovery.toResult(open, now)?.let { persist(it) }
        }
        pendingRestore.clear()
        saveOpenSessions()
    }

    private fun publish() {
        val snapshots = tracked.values.mapNotNull { it.tracker.snapshot() }
        TrackingStatus.nowPlaying.value =
            snapshots.firstOrNull { it.state == PlayState.PLAYING }
                ?: snapshots.firstOrNull { it.state == PlayState.PAUSED }
    }

    private fun saveOpenSessions() {
        val store = container?.openSessions ?: return
        store.replaceAll(tracked.values.mapNotNull { it.tracker.openSession() })
    }

    /** Runs the position ticker only while something is playing. */
    private fun updateTicker() {
        val serviceScope = scope ?: return
        val anyPlaying = tracked.values.any { it.isPlaying() }
        if (anyPlaying && tickerJob?.isActive != true) {
            tickerJob = serviceScope.launch {
                var ticks = 0
                while (isActive && tracked.values.any { it.isPlaying() }) {
                    delay(TICK_MS)
                    tracked.values.forEach { it.tick() }
                    publish()
                    if (++ticks % SAVE_EVERY_TICKS == 0) saveOpenSessions()
                }
            }
        }
    }

    private fun persist(result: ListenResult) {
        val appContainer = container ?: return
        appContainer.appScope.launch {
            try {
                appContainer.repository.record(result)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save listen", e)
            }
        }
    }

    private fun clock() = Clock(SystemClock.elapsedRealtime(), System.currentTimeMillis())

    private inner class TrackedSession(private val controller: MediaController) {
        val tracker = PlaybackTracker(controller.packageName) { persist(it) }

        private val callback = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                handleState(state)
                publish()
                updateTicker()
                saveOpenSessions()
            }

            override fun onMetadataChanged(metadata: MediaMetadata?) {
                handleMetadata(metadata)
                publish()
                saveOpenSessions()
            }

            override fun onSessionDestroyed() {
                removeSession(controller.sessionToken)
            }
        }

        fun start(restored: OpenSession?) {
            if (restored != null) tracker.restore(restored)
            controller.registerCallback(callback, handler)
            handleMetadata(controller.metadata)
            handleState(controller.playbackState)
        }

        fun stop() {
            runCatching { controller.unregisterCallback(callback) }
            tracker.shutdown(clock())
        }

        fun isPlaying(): Boolean = tracker.snapshot()?.state == PlayState.PLAYING

        fun tick() {
            val state = controller.playbackState ?: return
            val now = clock()
            tracker.onTick(positionOf(state, now), state.playbackSpeed, now)
        }

        private fun handleMetadata(metadata: MediaMetadata?) {
            tracker.onMetadata(metadata?.toTrackMeta(), clock())
        }

        private fun handleState(state: PlaybackState?) {
            val mapped = mapState(state) ?: return
            val now = clock()
            tracker.onPlaybackState(mapped, positionOf(state!!, now), state.playbackSpeed, now)
        }
    }

    private companion object {
        const val TAG = "OtoListener"
        const val TICK_MS = 4_000L
        const val SAVE_EVERY_TICKS = 8 // about every 32 seconds
    }
}

/** Buffering, connecting and skipping states are ignored so they keep the previous state. */
private fun mapState(state: PlaybackState?): PlayState? = when (state?.state) {
    PlaybackState.STATE_PLAYING -> PlayState.PLAYING
    PlaybackState.STATE_PAUSED -> PlayState.PAUSED
    PlaybackState.STATE_STOPPED, PlaybackState.STATE_NONE, PlaybackState.STATE_ERROR -> PlayState.STOPPED
    else -> null
}

/**
 * The reported position is only accurate at lastPositionUpdateTime,
 * so project it forward while playing. Players that report no position
 * fall back to the monotonic clock, which makes play time equal wall time.
 */
private fun positionOf(state: PlaybackState, now: Clock): Long {
    if (state.position < 0) return now.elapsedMs
    if (state.state != PlaybackState.STATE_PLAYING) return state.position
    val sinceUpdate = (SystemClock.elapsedRealtime() - state.lastPositionUpdateTime).coerceAtLeast(0)
    return state.position + (sinceUpdate * state.playbackSpeed).toLong()
}

private fun MediaMetadata.toTrackMeta(): TrackMeta? {
    val title = (getString(MediaMetadata.METADATA_KEY_TITLE)
        ?: getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE))
        ?.trim()
        .orEmpty()
    if (title.isBlank()) return null

    val artist = Normalize.cleanArtist(
        getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
    )
    val album = getString(MediaMetadata.METADATA_KEY_ALBUM)?.trim()?.takeIf { it.isNotEmpty() }
    val duration = getLong(MediaMetadata.METADATA_KEY_DURATION).takeIf { it > 0 }
    return TrackMeta(title = title, artist = artist, album = album, durationMs = duration)
}

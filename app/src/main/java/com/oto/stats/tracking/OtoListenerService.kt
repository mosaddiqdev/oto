package com.oto.stats.tracking

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import androidx.core.content.ContextCompat
import com.oto.stats.AppContainer
import com.oto.stats.OtoApp
import com.oto.stats.diag.DiagLog
import com.oto.stats.tracking.engine.Clock
import com.oto.stats.tracking.engine.ListenResult
import com.oto.stats.tracking.engine.Normalize
import com.oto.stats.tracking.engine.NowPlayingSnapshot
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
 * Staying alive, in layers:
 * 1. The service is also a sticky foreground service with a quiet notification, so the phone
 *    treats it as something the user is relying on and restarts it if it is killed.
 * 2. Open listens are saved every few seconds, so a kill loses almost nothing.
 * 3. A saved session resumes after a restart; leftovers are recovered.
 * 4. A boot receiver and a watchdog ask the system to bind the listener again.
 *
 * One [PlaybackTracker] per active media session, fed by MediaController callbacks
 * plus a light position ticker while something is playing.
 */
class OtoListenerService : NotificationListenerService() {

    private val handler = Handler(Looper.getMainLooper())
    private val tracked = HashMap<MediaSession.Token, TrackedSession>()

    /** Saved sessions from a previous run, keyed by package, waiting for their player to show up. */
    private val pendingRestore = HashMap<String, OpenSession>()
    private val reportedIgnored = HashSet<String>()

    private var container: AppContainer? = null
    private var scope: CoroutineScope? = null
    private var tickerJob: Job? = null
    private var heartbeatJob: Job? = null
    private var sessionManager: MediaSessionManager? = null
    private var component: ComponentName? = null

    private var isForeground = false
    private var lastNotificationText: String? = null
    private var lastForegroundError: String? = null

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        syncSessions(controllers.orEmpty())
    }

    override fun onCreate() {
        super.onCreate()
        DiagLog.log("svc", "Service created")
        promoteToForeground("created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Opening the app asks again every time; only log it when it actually changes something.
        if (intent == null || !isForeground) {
            DiagLog.log("svc", "Start command (${intent?.action ?: "system restart"})")
        }
        // Always answer a startForegroundService() call right away, and never stop here.
        promoteToForeground("start command")
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        DiagLog.log("svc", "App swiped away from Recents")
        super.onTaskRemoved(rootIntent)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        val appContainer = (application as OtoApp).container
        container = appContainer
        DiagLog.log("svc", "Listener connected")

        val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = serviceScope

        RunState.markConnected(this)
        heartbeatJob = serviceScope.launch {
            while (isActive) {
                delay(HEARTBEAT_MS)
                RunState.beat(this@OtoListenerService)
                // Close listens that were left paused for too long, then forget them.
                val now = clock()
                tracked.values.forEach { it.tracker.onIdleCheck(now) }
                saveOpenSessions()
            }
        }

        promoteToForeground("listener connected")

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
            DiagLog.log("svc", "Cannot read media sessions: notification access is missing")
        }

        // Anything saved for a player that is no longer active has ended: save it now.
        recoverLeftovers()

        TrackingStatus.listenerConnected.value = true
        DiagLog.log("svc", "Ready · tracking ${tracked.size} player(s)")

        // Re-scan when the user toggles "track all apps".
        serviceScope.launch {
            appContainer.prefs.trackAllApps.drop(1).collect { resync() }
        }
    }

    override fun onListenerDisconnected() {
        DiagLog.log("svc", "Listener disconnected by the system")
        teardown()
        if (ListenerHealth.isAccessGranted(this)) {
            ListenerHealth.requestRebind(this)
        } else {
            DiagLog.log("svc", "Notification access was turned off; stopping")
            runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
            isForeground = false
            TrackingStatus.foreground.value = false
            stopSelf()
        }
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        DiagLog.log("svc", "Service destroyed")
        teardown()
        isForeground = false
        TrackingStatus.foreground.value = false
        super.onDestroy()
    }

    /** Shows the quiet notification and enters foreground mode. Safe to call repeatedly. */
    private fun promoteToForeground(reason: String) {
        try {
            val text = lastNotificationText ?: IDLE_TEXT
            val notification = ServiceNotification.build(this, text)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    ServiceNotification.ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
                )
            } else {
                startForeground(ServiceNotification.ID, notification)
            }
            lastNotificationText = text
            if (!isForeground) DiagLog.log("svc", "Foreground mode ON ($reason)")
            isForeground = true
            lastForegroundError = null
        } catch (e: Exception) {
            // The system can refuse when the app is in the background. Opening the app retries.
            isForeground = false
            val summary = "${e.javaClass.simpleName}: ${e.message?.take(100)}"
            if (summary != lastForegroundError) {
                DiagLog.log("svc", "Foreground mode refused ($reason): $summary")
            }
            lastForegroundError = summary
        }
        TrackingStatus.foreground.value = isForeground
    }

    private fun teardown() {
        tickerJob?.cancel()
        tickerJob = null
        heartbeatJob?.cancel()
        heartbeatJob = null
        runCatching { sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener) }
        tracked.values.toList().forEach { it.stop() }
        tracked.clear()
        // Everything was closed and handed to the database, so nothing is left open.
        container?.openSessions?.clear()
        pendingRestore.clear()
        reportedIgnored.clear()
        RunState.markClean(this)
        scope?.cancel()
        scope = null
        TrackingStatus.listenerConnected.value = false
        TrackingStatus.nowPlaying.value = null
        TrackingStatus.artwork.value = null
    }

    private fun resync() {
        val manager = sessionManager ?: return
        val name = component ?: return
        reportedIgnored.clear()
        try {
            syncSessions(manager.getActiveSessions(name))
        } catch (e: SecurityException) {
            DiagLog.log("svc", "Cannot read media sessions")
        }
    }

    /** Makes the set of tracked sessions match the allowed active sessions. */
    private fun syncSessions(controllers: List<MediaController>) {
        val prefs = container?.prefs ?: return

        for (controller in controllers) {
            val pkg = controller.packageName
            if (!prefs.isAllowed(pkg) && reportedIgnored.add(pkg)) {
                DiagLog.log("sync", "Ignoring media from $pkg (${prefs.blockReason(pkg)})")
            }
        }

        val allowed = controllers.filter { prefs.isAllowed(it.packageName) }
        val liveTokens = allowed.map { it.sessionToken }.toSet()

        tracked.keys.filter { it !in liveTokens }.forEach { tracked.remove(it)?.stop() }

        for (controller in allowed) {
            val token = controller.sessionToken
            if (token !in tracked) {
                DiagLog.log("sync", "Tracking ${MusicApps.labelFor(controller.packageName)}")
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
            val result = SessionRecovery.toResult(open, now)
            if (result != null) {
                DiagLog.log("svc", "Recovered an unfinished listen: ${open.meta.title}")
                submit(result)
            }
        }
        pendingRestore.clear()
        saveOpenSessions()
    }

    private fun publish() {
        val snapshots = tracked.values.mapNotNull { it.tracker.snapshot() }
        val current = snapshots.firstOrNull { it.state == PlayState.PLAYING }
            ?: snapshots.firstOrNull { it.state == PlayState.PAUSED }
        // Stamped so the screen can keep the counter moving between the 4 second ticks.
        TrackingStatus.nowPlaying.value = current?.copy(atElapsedMs = SystemClock.elapsedRealtime())
        val art = current?.let { c ->
            tracked.values.firstOrNull { it.tracker.snapshot()?.packageName == c.packageName }?.art
        }
        TrackingStatus.artwork.value = art
        if (current != null && art != null) keepArt(current, art)
        updateNotificationText(current)
    }

    /** Keeps a small copy of the cover on disk, so History can show it long after the song ends. */
    private fun keepArt(current: NowPlayingSnapshot, art: Bitmap) {
        val c = container ?: return
        val key = Normalize.trackIdentity(current.meta.title, current.meta.artist)
        if (c.artStore.isKnown(key)) return
        c.appScope.launch { c.artStore.saveIfMissing(key, art) }
    }

    private fun updateNotificationText(current: NowPlayingSnapshot?) {
        if (!isForeground) return
        val text = if (current != null && current.state == PlayState.PLAYING) {
            "${current.meta.title} · ${current.meta.artist}"
        } else {
            IDLE_TEXT
        }
        if (text == lastNotificationText) return
        lastNotificationText = text
        runCatching {
            getSystemService(NotificationManager::class.java)
                .notify(ServiceNotification.ID, ServiceNotification.build(this, text))
        }
    }

    private fun saveOpenSessions() {
        val store = container?.openSessions ?: return
        store.replaceAll(tracked.values.mapNotNull { it.tracker.openSession() })
    }

    /** Saves the progress of every open listen, so it shows up and survives a kill. */
    private fun checkpoint() {
        tracked.values.forEach { session -> session.tracker.snapshotResult()?.let { submit(it) } }
        saveOpenSessions()
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
                    if (++ticks % CHECKPOINT_EVERY_TICKS == 0) checkpoint()
                }
            }
        }
    }

    private fun submit(result: ListenResult) {
        container?.repository?.submit(result)
    }

    private fun clock() = Clock(SystemClock.elapsedRealtime(), System.currentTimeMillis())

    private inner class TrackedSession(private val controller: MediaController) {
        private val label = MusicApps.labelFor(controller.packageName)
        private var lastLoggedState: PlayState? = null
        private var lastLoggedTrack: String? = null

        /** Cover art the player attached to the current track, if any. */
        var art: Bitmap? = null
            private set
        private var artTrackKey: String? = null

        val tracker = PlaybackTracker(
            controller.packageName,
            onDiscarded = { DiagLog.log("play", it) },
        ) { submit(it) }

        private val callback = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                handleState(state)
                publish()
                updateTicker()
                checkpoint()
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
            val meta = metadata?.toTrackMeta()
            // Some players send a second metadata update without the picture. Keep the cover
            // while it is the same track, drop it when the track changes.
            val trackKey = meta?.let { "${it.title}|${it.artist}" }
            val fresh = metadata?.artwork()
            when {
                fresh != null -> { art = fresh; artTrackKey = trackKey }
                trackKey != artTrackKey -> { art = null; artTrackKey = trackKey }
            }
            if (meta != null) {
                val key = "${meta.title}|${meta.artist}"
                if (key != lastLoggedTrack) {
                    lastLoggedTrack = key
                    val length = meta.durationMs?.let { " (${it / 60_000}:%02d)".format(it / 1000 % 60) } ?: ""
                    DiagLog.log("play", "$label: ${meta.title} · ${meta.artist}$length")
                }
            }
            tracker.onMetadata(meta, clock())
        }

        private fun handleState(state: PlaybackState?) {
            val mapped = mapState(state) ?: return
            if (mapped != lastLoggedState) {
                lastLoggedState = mapped
                DiagLog.log("play", "$label: ${mapped.name.lowercase()}")
            }
            val now = clock()
            tracker.onPlaybackState(mapped, positionOf(state!!, now), state.playbackSpeed, now)
        }
    }

    companion object {
        const val ACTION_ENSURE_FOREGROUND = "com.oto.stats.ENSURE_FOREGROUND"

        private const val TICK_MS = 4_000L
        private const val CHECKPOINT_EVERY_TICKS = 4 // about every 16 seconds
        private const val HEARTBEAT_MS = 60_000L
        private const val IDLE_TEXT = "Listening for music"

        /**
         * Starts the service in foreground mode. Call only while the app is on screen: from
         * the background the system may refuse, and a boot receiver is not allowed to start
         * a media-playback foreground service on Android 15 and later.
         */
        fun ensureForeground(context: Context) {
            if (!ListenerHealth.isAccessGranted(context)) return
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, OtoListenerService::class.java).setAction(ACTION_ENSURE_FOREGROUND),
                )
            } catch (e: Exception) {
                DiagLog.log("svc", "Could not start the service: ${e.javaClass.simpleName} ${e.message?.take(100)}")
            }
        }
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

/**
 * The cover as a bitmap of our own, shrunk so a few of them never weigh much memory. It is a copy:
 * the player owns the one in its metadata and may recycle it, and drawing a recycled bitmap
 * crashes the app.
 */
private fun MediaMetadata.artwork(): Bitmap? {
    val raw = getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
        ?: getBitmap(MediaMetadata.METADATA_KEY_ART)
        ?: getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        ?: return null
    return runCatching {
        if (raw.isRecycled) return null
        val longest = maxOf(raw.width, raw.height)
        val scale = if (longest > 720) 720f / longest else 1f
        val width = (raw.width * scale).toInt().coerceAtLeast(1)
        val height = (raw.height * scale).toInt().coerceAtLeast(1)
        val soft = raw.copy(Bitmap.Config.ARGB_8888, false) ?: return null
        if (width == soft.width && height == soft.height) soft
        else Bitmap.createScaledBitmap(soft, width, height, true).also { if (it !== soft) soft.recycle() }
    }.getOrNull()
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

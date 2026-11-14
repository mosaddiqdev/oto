package com.oto.stats

import com.oto.stats.data.ArtStore
import android.app.Application
import android.content.Context
import android.os.Build
import com.oto.stats.data.ListeningRepository
import com.oto.stats.data.OpenSessionStore
import com.oto.stats.data.OtoDatabase
import com.oto.stats.data.TrackingPrefs
import com.oto.stats.diag.DiagLog
import com.oto.stats.stats.DetailModel
import com.oto.stats.stats.StatsModel
import com.oto.stats.tracking.ListenerWatchdogWorker
import com.oto.stats.tracking.RunState
import com.oto.stats.data.ArtistPhotos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Hand-wired dependencies. Small enough that a DI framework would be overkill. */
class AppContainer(context: Context) {
    /** Lives as long as the process, so database writes finish even if the service is torn down. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database = OtoDatabase.build(context)
    val repository = ListeningRepository(database, appScope)
    val stats = StatsModel(database.listeningDao(), appScope)
    val details = DetailModel(database.listeningDao())
    val prefs = TrackingPrefs(context)
    val openSessions = OpenSessionStore(context)
    val artStore = ArtStore(context)
    val artistPhotos = ArtistPhotos(context, artStore, prefs)
}

class OtoApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        DiagLog.init(this)
        logProcessStart()
        container = AppContainer(this)
        ListenerWatchdogWorker.schedule(this)
    }

    private fun logProcessStart() {
        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull()
        DiagLog.log(
            "app",
            "Process started · Oto $version · ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        )
        val previous = RunState.previous(this)
        if (previous.aliveAtMs > 0) {
            val ago = DiagLog.ago(System.currentTimeMillis() - previous.aliveAtMs)
            if (previous.clean) {
                DiagLog.log("app", "Last run ended normally (last seen $ago ago)")
            } else {
                DiagLog.log("app", "Last run was KILLED by the system or phone, or the app was updated (last sign of life $ago ago)")
            }
        }
    }
}

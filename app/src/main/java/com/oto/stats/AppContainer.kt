package com.oto.stats

import android.app.Application
import android.content.Context
import com.oto.stats.data.ListeningRepository
import com.oto.stats.data.OtoDatabase
import com.oto.stats.data.TrackingPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Hand-wired dependencies. Small enough that a DI framework would be overkill. */
class AppContainer(context: Context) {
    /** Lives as long as the process, so database writes finish even if the service is torn down. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database = OtoDatabase.build(context)
    val repository = ListeningRepository(database)
    val prefs = TrackingPrefs(context)
}

class OtoApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

package com.oto.stats.tracking

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay

/**
 * Some phones stop binding the notification listener after a process kill.
 * Every 30 minutes: if access is granted but the listener is not connected,
 * ask for a rebind, and if that does not work, toggle the component.
 */
class ListenerWatchdogWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!ListenerHealth.isAccessGranted(context)) return Result.success()
        if (TrackingStatus.listenerConnected.value) return Result.success()

        ListenerHealth.requestRebind(context)
        delay(REBIND_WAIT_MS)
        if (!TrackingStatus.listenerConnected.value) {
            ListenerHealth.toggleComponent(context)
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "listener-watchdog"
        private const val REBIND_WAIT_MS = 10_000L

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ListenerWatchdogWorker>(30, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}

package com.oto.stats.tracking

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat

/** Helpers for checking and repairing the notification listener binding. */
object ListenerHealth {

    fun component(context: Context): ComponentName =
        ComponentName(context, OtoListenerService::class.java)

    fun isAccessGranted(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    /** Asks the system to bind the listener again. */
    fun requestRebind(context: Context) {
        runCatching { NotificationListenerService.requestRebind(component(context)) }
    }

    /** Disabling then enabling the component forces the system to bind the listener again. */
    fun toggleComponent(context: Context) {
        val packageManager = context.packageManager
        val name = component(context)
        runCatching {
            packageManager.setComponentEnabledSetting(
                name,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
            packageManager.setComponentEnabledSetting(
                name,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
    }
}

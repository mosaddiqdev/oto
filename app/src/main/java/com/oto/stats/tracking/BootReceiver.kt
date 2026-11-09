package com.oto.stats.tracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** After a reboot or an app update, nudge the system to bind the listener again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!ListenerHealth.isAccessGranted(context)) return
        ListenerHealth.requestRebind(context)
    }
}

package com.oto.stats.tracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.oto.stats.diag.DiagLog

/** After a reboot or an app update, nudge the system to bind the listener again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!ListenerHealth.isAccessGranted(context)) return
        // Android 15 does not allow starting a media-playback foreground service from here,
        // so only ask the system to bind the listener. Foreground mode starts when the app opens.
        DiagLog.log("boot", "${intent.action?.substringAfterLast('.')}: asking the system to bind the listener")
        ListenerHealth.requestRebind(context)
    }
}

package com.oto.stats

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.oto.stats.tracking.ListenerHealth
import com.oto.stats.ui.debug.DebugScreen
import com.oto.stats.ui.theme.OtoTheme

class MainActivity : ComponentActivity() {

    /** Bumped on every resume so statuses are re-checked after returning from Settings. */
    private var resumeTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        val container = (application as OtoApp).container
        setContent {
            OtoTheme {
                val accessGranted = remember(resumeTick) { ListenerHealth.isAccessGranted(this) }
                val batteryExempt = remember(resumeTick) { isBatteryExempt() }
                DebugScreen(
                    container = container,
                    accessGranted = accessGranted,
                    onOpenAccessSettings = {
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                    batteryExempt = batteryExempt,
                    onRequestBatteryExemption = ::requestBatteryExemption,
                    onOpenAppSettings = ::openAppSettings,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
        // Opening the app is a cheap moment to make sure the listener is bound.
        if (ListenerHealth.isAccessGranted(this)) ListenerHealth.requestRebind(this)
    }

    private fun isBatteryExempt(): Boolean =
        getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

    private fun requestBatteryExemption() {
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:$packageName"))
        runCatching { startActivity(direct) }.onFailure {
            runCatching { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        }
    }

    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:$packageName")),
        )
    }
}

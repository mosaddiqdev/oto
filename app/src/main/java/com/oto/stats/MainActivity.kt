package com.oto.stats

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.oto.stats.diag.DiagLog
import com.oto.stats.tracking.ListenerHealth
import com.oto.stats.tracking.OemHelper
import com.oto.stats.tracking.OemShortcut
import com.oto.stats.tracking.OtoListenerService
import com.oto.stats.tracking.TrackingStatus
import com.oto.stats.ui.debug.DebugActions
import com.oto.stats.ui.debug.DebugScreen
import com.oto.stats.ui.debug.DebugState
import com.oto.stats.ui.theme.OtoTheme

class MainActivity : ComponentActivity() {

    private lateinit var container: AppContainer

    /** Bumped on every resume so statuses are re-checked after returning from Settings. */
    private var resumeTick by mutableIntStateOf(0)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            resumeTick++
            // The notification can only appear once the permission is decided, so retry now.
            OtoListenerService.ensureForeground(this)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        container = (application as OtoApp).container

        val actions = DebugActions(
            openAccessSettings = {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            },
            requestNotifications = ::requestNotifications,
            requestBatteryExemption = ::requestBatteryExemption,
            openAppSettings = ::openAppSettings,
            openOemShortcut = ::openOemShortcut,
            copyLog = ::copyDiagnostics,
        )

        setContent {
            OtoTheme {
                val state = remember(resumeTick) {
                    DebugState(
                        accessGranted = ListenerHealth.isAccessGranted(this),
                        notificationsGranted = notificationsGranted(),
                        batteryExempt = isBatteryExempt(),
                    )
                }
                DebugScreen(container = container, state = state, actions = actions)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
        if (!ListenerHealth.isAccessGranted(this)) return

        // The app is on screen, so the system allows entering foreground mode now.
        OtoListenerService.ensureForeground(this)
        if (!TrackingStatus.listenerConnected.value) ListenerHealth.requestRebind(this)
        askForNotificationsOnce()
    }

    private fun notificationsGranted(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun askForNotificationsOnce() {
        if (notificationsGranted() || container.prefs.notificationsAsked) return
        container.prefs.notificationsAsked = true
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun requestNotifications() {
        if (!container.prefs.notificationsAsked) {
            container.prefs.notificationsAsked = true
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // The system will not show the prompt again, so open the settings page instead.
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
            )
        }
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

    private fun openOemShortcut(shortcut: OemShortcut) {
        if (!OemHelper.launch(this, shortcut)) {
            Toast.makeText(this, "Could not open it directly. Opening app settings.", Toast.LENGTH_SHORT).show()
            openAppSettings()
        }
    }

    private fun copyDiagnostics() {
        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull()
        val header = "Oto $version · ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("Oto diagnostics", DiagLog.exportText(header)))
        Toast.makeText(this, "Log copied", Toast.LENGTH_SHORT).show()
    }
}

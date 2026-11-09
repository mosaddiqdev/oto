package com.oto.stats

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import com.oto.stats.ui.debug.DebugScreen
import com.oto.stats.ui.theme.OtoTheme

class MainActivity : ComponentActivity() {

    /** Bumped on every resume so the access status is re-checked after returning from Settings. */
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
                val accessGranted = remember(resumeTick) { isListenerAccessGranted() }
                DebugScreen(
                    container = container,
                    accessGranted = accessGranted,
                    onOpenAccessSettings = {
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
    }

    private fun isListenerAccessGranted(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
}

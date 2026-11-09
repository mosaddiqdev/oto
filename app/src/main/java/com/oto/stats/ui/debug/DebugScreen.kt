package com.oto.stats.ui.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oto.stats.AppContainer
import com.oto.stats.data.EventRow
import com.oto.stats.data.Totals
import com.oto.stats.tracking.MusicApps
import com.oto.stats.tracking.TrackingStatus
import com.oto.stats.tracking.engine.PlayState
import com.oto.stats.ui.theme.OtoColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

/** Temporary developer screen for checking the tracking engine on a real phone. */
@Composable
fun DebugScreen(
    container: AppContainer,
    accessGranted: Boolean,
    onOpenAccessSettings: () -> Unit,
    batteryExempt: Boolean,
    onRequestBatteryExemption: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    val connected by TrackingStatus.listenerConnected.collectAsState()
    val nowPlaying by TrackingStatus.nowPlaying.collectAsState()
    val trackAll by container.prefs.trackAllApps.collectAsState()
    val totals by remember(container) { container.repository.totals() }
        .collectAsState(initial = Totals(0, 0L, 0))
    val events by remember(container) { container.repository.recentEvents(50) }
        .collectAsState(initial = emptyList())
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val insets = WindowInsets.safeDrawing.asPaddingValues()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = insets.calculateTopPadding() + 16.dp,
            bottom = insets.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text("oto", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onBackground)
                Text("Tracking engine · debug screen", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        item {
            DebugCard {
                StatusRow("Notification access", if (accessGranted) "On" else "Off", if (accessGranted) OtoColors.Success else OtoColors.Warning)
                StatusRow("Listener service", if (connected) "Connected" else "Not connected", if (connected) OtoColors.Success else OtoColors.TextTertiary)
                if (!accessGranted) {
                    Button(onClick = onOpenAccessSettings, modifier = Modifier.fillMaxWidth()) {
                        Text("Grant notification access")
                    }
                    Text(
                        "If the switch is greyed out: App info, then the three-dot menu, then \"Allow restricted settings\". Android blocks this for apps installed outside the Play Store.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OtoColors.TextTertiary,
                    )
                }
            }
        }

        item {
            DebugCard {
                Text("Background protection", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                StatusRow(
                    "Battery restrictions",
                    if (batteryExempt) "Unrestricted" else "Restricted",
                    if (batteryExempt) OtoColors.Success else OtoColors.Warning,
                )
                if (!batteryExempt) {
                    Button(onClick = onRequestBatteryExemption, modifier = Modifier.fillMaxWidth()) {
                        Text("Allow background activity")
                    }
                }
                OutlinedButton(onClick = onOpenAppSettings, modifier = Modifier.fillMaxWidth()) {
                    Text("Open app settings")
                }
                Text(
                    "Some phones (Xiaomi, Oppo, Vivo, OnePlus, Samsung) also need Autostart turned on and the app locked in Recents, or they stop tracking when the screen is off.",
                    style = MaterialTheme.typography.bodySmall,
                    color = OtoColors.TextTertiary,
                )
            }
        }

        item {
            DebugCard {
                Text("Now playing", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val np = nowPlaying
                if (np == null) {
                    Text("Nothing playing", style = MaterialTheme.typography.bodyLarge, color = OtoColors.TextTertiary)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .background(if (np.state == PlayState.PLAYING) OtoColors.Accent else OtoColors.TextTertiary, CircleShape),
                        )
                        Text(
                            np.meta.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(np.meta.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "${MusicApps.labelFor(np.packageName)} · ${np.state.name.lowercase()} · counted ${formatClock(np.countedMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = OtoColors.TextTertiary,
                    )
                    if (!np.countable) {
                        Text("Filtered out (ad or not music), will not be counted", style = MaterialTheme.typography.bodySmall, color = OtoColors.TextTertiary)
                    }
                }
            }
        }

        item {
            DebugCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Track all apps", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            "For testing with players not on the built-in list. Messaging, browser and video apps stay blocked.",
                            style = MaterialTheme.typography.bodySmall,
                            color = OtoColors.TextTertiary,
                        )
                    }
                    Switch(checked = trackAll, onCheckedChange = { container.prefs.setTrackAllApps(it) })
                }
            }
        }

        item {
            DebugCard {
                Text("Totals", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Stat("Plays", totals.plays.toString())
                    Stat("Unique tracks", totals.uniqueTracks.toString())
                    Stat("Time", formatClock(totals.totalPlayedMs))
                }
            }
        }

        item {
            Text("Recent plays", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        }

        if (events.isEmpty()) {
            item {
                Text(
                    "No plays saved yet. Play a song for 25+ seconds, then pause or skip.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OtoColors.TextTertiary,
                )
            }
        } else {
            items(events, key = { it.id }) { row -> EventItem(row) }
        }

        item {
            OutlinedButton(
                onClick = { scope.launch { container.repository.clearAll() } },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Clear all data")
            }
        }
    }
}

@Composable
private fun DebugCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
private fun StatusRow(label: String, value: String, valueColor: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor)
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.bodySmall, color = OtoColors.TextTertiary)
    }
}

@Composable
private fun EventItem(row: EventRow) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(row.title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(row.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val tag = if (row.skipped) " · skipped" else ""
        Text(
            "${formatWhen(row.startedAt)} · ${MusicApps.labelFor(row.sourcePackage)} · ${formatClock(row.playedMs)} · ${row.completionPct}%$tag",
            style = MaterialTheme.typography.bodySmall,
            color = OtoColors.TextTertiary,
        )
        HorizontalDivider(Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

private fun formatClock(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

private fun formatWhen(epochMs: Long): String =
    SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(epochMs))
